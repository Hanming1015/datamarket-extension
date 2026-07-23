# Phase 3 总结:访问申请编排 + 审批状态机 + RabbitMQ 事件扇出 + Stripe 支付

## 一句话概括
把整个平台**真正的业务闭环**建起来:consumer 提交访问申请 → 编排层 Feign 三连(consent 预筛 + dataset 快照 + 定价报价)→ owner 人工审批 → 支付闸门(Stripe 托管收银)→ 支付成功后**事件扇出**到计费/审计/通知三方。这是升级的**核心动机**——把旧单体那个「在一个 `@Transactional` 里同步跑完匹配→定价→写记录→计费→审计」的大方法,拆成 **access 编排 + RabbitMQ 异步解耦 + 最终一致**。分三个子阶段落地:**3a**(全同步编排 + 审批)→ **3b**(MQ 异步 + billing/audit 拆消费者)→ **3c**(payment + outbox 补一致性 + notification)。

---

## 一、本阶段交付物

| 模块 | 角色 | 端口 | 库 | 子阶段 |
|---|---|---|---|---|
| **synapse-access-service** | 交易编排 + 审批状态机 + Redis 幂等锁 + outbox 生产者 | 8084 | synapse_access | 3a→3c |
| **synapse-payment-service** | 支付单 + Mock 收银台 + 幂等 webhook + outbox | 8085 | synapse_payment | 3c-1 |
| **synapse-billing-service** | 消费 access.approved / payment.succeeded 写账单 + 对账 | 8086 | synapse_billing | 3b→3c |
| **synapse-audit-service** | 消费 access.# 写追加式审计日志 | 8087 | synapse_audit | 3b |
| **synapse-notification-service** | 消费 access.# + payment.# 落站内通知 | 8088 | synapse_notification | 3c-2 |

启动后全链路 9 服务:`gateway 8080 → auth/consent/dataset/access/payment/billing/audit/notification (8081–8088)`。RabbitMQ 4 业务队列各 1 消费者 + 4 DLQ。

**三个锁定决策**(overview 阶段拍板):①审批模型 = **人工审批(数据集 owner 拍板)**,匹配引擎降级为预筛;②DTO 用**客户端侧镜像**(不建 synapse-api、服务间不互相依赖 jar);③状态机用 **枚举 + 流转校验**(把单体的魔法字符串技术债一次做对)。

---

## 二、3a:access-service —— 编排 + 审批(全同步)

首次引入 **OpenFeign + LoadBalancer**。编排链路:

```
POST /api/access (consumer)
   │
   ├─ Redis SETNX 幂等锁(防并发双击,finally 释放)
   ├─ DB 查 PENDING 防重复受理
   ├─ Feign ConsentClient.match      ← 预筛(拒绝直接 REJECTED)
   ├─ Feign DatasetClient.getDataset ← 取 owner + name 快照
   ├─ Feign DatasetClient.quote      ← 动态报价(cost 记在 access 行)
   └─ 落库 PENDING_APPROVAL
```

Feign 直连 `lb://服务名`、**不走网关**,内部调用无需 `X-User-Id`;返回 `Result<T>` 由 `unwrap()` 校验 code 解包。

**审批状态机**:`PENDING_APPROVAL → GRANTED / REJECTED`(引擎 deny 直接 REJECTED 不进审批)。owner 归属校验:越权审批返 `NOT_FOUND`(不泄露存在性,沿用 Phase 2 模式)。`init.sql` 的 `access_requests` 补 `owner_id` 列(建单时快照数据集 owner,供 owner 查待审)。

> 实测 `cost=25.00` 与定价公式吻合,证明 Feign quote 链路端到端通。Postman `postman/synapse-access-collection.json`(17 步)。

---

## 三、3b:RabbitMQ —— 从「同步调用」到「事件扇出」

首次引入 **RabbitMQ**。核心是把 billing/audit 从 access 的同步事务里**摘出去**,变成独立服务独立库、异步消费。

### 生产者:提交后发布(故意留缺口)
access 的 approve/reject 在 `@Transactional` 方法内 `publishEvent(AccessEvent)`,由 `@TransactionalEventListener(AFTER_COMMIT)` 在**提交后**才 `convertAndSend`。**3b 用这种朴素「提交后发布」,故意留 dual-write 缺口(catch+log 不抛),留给 3c 的 outbox 补**——这是有意的教学岔路。

### 拓扑:一次发布,多方消费
```
                    topic: synapse.event.exchange
                              │
        ┌─────────────────────┴─────────────────────┐
   rk=access.approved                          rk=access.#
        │                                           │
   billing 队列(只批准入账)              audit 队列(批准+驳回都记)
```
同一条 `access.approved` 被两队列各收一份 = 真正的**扇出**。载荷用新建的 `AccessEvent extends BaseEvent`(带 decision 的字段并集)——没复用 scaffolding 的 BillingEvent/AuditEvent,因为 reject 无 cost、单一 BillingEvent 装不下驳回。

### DLQ + 幂等分级
- **DLQ**:队列声明 `x-dead-letter-exchange` + 监听器 `default-requeue-rejected:false` + retry `max-attempts=3`,毒消息重试耗尽落 `*.dlq`。
- **幂等分级**:billing 涉钱**必须幂等**(按 `access_request_id` 查重防重复入账);audit 是追加日志,走 at-least-once 容忍偶发重复。

反序列化靠 common 的 `RabbitSupportConfig`(`@ConditionalOnClass(RabbitTemplate)`):Jackson2Json + JavaTimeModule(LocalDateTime)+ 只信任 `com.synapse.common.event` 包。

> **踩坑**:①本机 PATH 的 `java` 是 Oracle javapath 残桩(跑啥都秒退无输出),必须用 `$env:JAVA_HOME\bin\java.exe`(JDK25);②common 的 GlobalExceptionHandler 引 `ConstraintViolationException`,servlet 消费服务漏 `spring-boot-starter-validation` → 启动 `NoClassDefFound` 秒退,补依赖即可。

---

## 四、3c:支付闸门 + outbox 最终一致 + 通知

### 3c-1:payment-service + outbox
插入支付闸门,主链路改为:
```
PENDING_APPROVAL ─(owner批准)→ PENDING_PAYMENT ─(消费者建单+付款)→
   webhook → payment_order=PAID → outbox 发 payment.succeeded → 扇出:
      ├─ access   → GRANTED
      └─ billing  → 对账 PAID
```

- **PaymentProvider 抽象** + `MockPaymentProvider`(`@ConditionalOnProperty synapse.payment.provider=mock` 默认,零外部依赖;将来加 `StripePaymentProvider` 翻配置即切)。
- `POST /api/payments` 建单(`idempotency_key=accessRequestId` 防重);webhook 在 `POST /webhooks/payment/mock`(**不挂网关路由**、直连 8085、不带 JWT;真 Stripe 时加 `/stripe` 验签)标 PAID + **同事务写 outbox**。
- **outbox 模式**:业务表更新与 outbox 行写在**同一 `@Transactional`**;`@Scheduled` relay(2s 轮询)投递并标 SENT,失败 `retry_count++` 到 5 转 FAILED。这就补上了 3b 那个 dual-write 缺口。

**access 改造**:approve→PENDING_PAYMENT(不再直接 GRANTED),把 3b 的 AFTER_COMMIT 朴素发布**换成 outbox**;新增 `/internal/access/{id}`(不挂网关=仅服务间可达);access 队列绑 `payment.succeeded`→`markGrantedByPayment`(已 GRANTED 则跳过=幂等)。**billing 改造**:队列再绑 `payment.succeeded`,监听器改**类级 `@RabbitListener` + 多 `@RabbitHandler`**(AccessEvent→UNPAID 入账 / PaymentEvent→PAID 对账 / isDefault 兜底),两路径都按 `accessRequestId` 幂等。

### 3c-2:notification-service
纯消费者(结构同 audit)。队列同绑 `access.#` + `payment.#`,多 `@RabbitHandler`:AccessEvent→APPROVAL 通知(批准/驳回不同文案)、PaymentEvent→PAYMENT 通知;按 `(userId, refId, type)` 查重幂等。只读 `GET /api/notifications/mine` + `PUT /{id}/read`(UpdateWrapper 限 id+user_id,非本人影响 0 行 = 天然幂等 + 越权保护)。

---

## 五、真机验证

| 子阶段 | 验证 | 结果 |
|---|---|---|
| 3a | access 编排 + 审批全链路(Postman 17 步) | ✅ 20/20,cost=25.00 吻合公式 |
| 3b | 扇出 + DLQ + 幂等(`verify-phase3b.ps1`,WaitUntil 轮询最终一致) | ✅ 15/15 |
| 3c-1 | 支付闭环 + outbox(`verify-phase3c1.ps1`,webhook 直连 8085) | ✅ 17/17,两库 outbox 均 SENT=1 无残留 |
| 3c-2 | 通知扇出(`verify-phase3c2.ps1`) | ✅ 8/8 |

**全链路闭环**:提交 → 审批 → PENDING_PAYMENT → 建单 → Mock 收银台 → webhook → PAID + outbox → `payment.succeeded` 扇出到 access(GRANTED)/ billing(对账 PAID)/ notification(通知)。

> **PS 5.1 踩坑**:`Invoke-RestMethod` 把无 charset 的 UTF-8 响应体按 Latin-1 解码 → 中文断言乱码假失败(DB 实为正确 utf8mb4);脚本加 `Utf8()`(GetEncoding(28591)→UTF8 重解)修复。验证脚本一律 **UTF-8 BOM** 否则 PS5.1 解析崩。

---

## Phase 3 该带走的一句话
> 异步解耦的代价是**一致性**:一旦把「计费」从下单事务里摘出去,就必须回答「下单成功但计费消息丢了怎么办」——3b 故意留下这个 dual-write 缺口,3c 用 **outbox 本地消息表**(与业务同事务落库 + relay 补偿投递)把它补上。事件扇出让「一次审批」自然驱动计费、审计、通知三条独立演进的链路,这正是微服务相较单体大方法的价值所在。

---

## 沉淀/复用的约定
- Feign `lb://` 内部直连、不走网关;`Result<T>` + `unwrap()` 解包。
- 状态机 = 枚举 + 流转校验;越权一律 `NOT_FOUND`。
- 涉钱幂等(查重)/ 日志类 at-least-once(容忍重复);DLQ 兜毒消息。
- outbox = 业务表 + 消息表同事务 + `@Scheduled` relay 补偿,是跨服务最终一致的主武器(明确不用 Seata)。
