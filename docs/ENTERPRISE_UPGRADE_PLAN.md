# Synapse 数据平台 —— 企业级微服务升级方案

> 技术生态:**Spring Cloud Alibaba** · 消息队列:**RabbitMQ** · 支付:**Stripe(沙箱)** · 对象存储:**MinIO** · 目标:**真正可运行的微服务集群** · 编排:**Docker Compose**
>
> 本文档是升级蓝图,同时也是简历素材来源。每个组件的引入都对应现有代码里的**真实痛点**,而不是堆砌名词。

---

## 1. 现状(升级起点)

当前是一个**结构清晰的单体** Spring Boot 3.5 应用 + React 前端,单个 AWS RDS MySQL。
领域边界已经很清楚(这是能干净拆分的前提):

| 现有模块 | 职责 | 对应表 |
|---|---|---|
| `user/account` | 登录、注册、JWT、用户信息 | `user` |
| `dataset` | 数据集 CRUD | `dataset` |
| `pricingconfig` + `PricingEngine` | 定价配置 + 动态定价引擎 | `pricing_config` |
| `consentmanagement` + `ConsentMatchingEngine` | 授权规则 + 字段级匹配引擎 | `consent_rule` |
| `datamarket/AccessRequest` | **编排中枢**:匹配→定价→计费→审计 | `access_request` |
| `billing` | 账单记录 | `billing_record` |
| `auditlog` | 审计日志 | `audit_log` |

**核心痛点(升级动机):** `AccessRequestServiceImpl.processAccessRequest()` 在**一个 `@Transactional` 里同步**完成「匹配 → 定价 → 写访问记录 → 生成账单 → 写审计日志」。这条链路:
- 把读多写少的数据集/定价配置**反复查库**(应进 Redis 缓存);
- 把**非关键路径**(计费落库、审计落库、通知)和关键路径强耦合,任一环节慢/失败都拖累整个请求(应通过 **MQ 异步解耦**);
- 无法独立伸缩——定价计算是 CPU 密集,而审计是 IO 密集,挤在一个进程里(应**拆分微服务**独立伸缩);
- 数据集 schema 靠**手动粘贴 JSON**,不真实(应支持**真实文件上传 + 自动解析**);
- 申请**一步到位无审批**、**无真实支付**,缺少企业级业务闭环(应加**审批状态机 + 真实支付**)。

> 这段就是简历里「为什么要做微服务化」最有说服力的论据:**有真实瓶颈,不是为了拆而拆。**

---

## 2. 目标架构

```
                       ┌─────────────┐
   React 前端 ────────▶│   Gateway   │  JWT 前置鉴权 · 路由 · 限流
                       └──────┬──────┘
       ┌──────────┬──────────┼───────────┬──────────────┐
       ▼          ▼          ▼           ▼              ▼
   auth-svc   dataset-svc  consent-svc  access-svc   payment-svc
   用户/JWT   上传/解析/定价  授权匹配     交易编排+审批   Stripe 支付
                  │                       │              │
                  ▼(异步解析)              │ 发事件        │ 支付回调(webhook)
              ┌────────┐                  ▼              ▼
              │ MinIO  │          ┌──────────────────────────┐
              │对象存储 │          │        RabbitMQ          │ + 死信队列(DLQ)
              └────────┘          └───┬────────┬────────┬────┘
                                      ▼        ▼        ▼
                                 billing-svc audit-svc notification-svc
                                 (异步落账单) (异步审计) (审批/支付通知,事件扇出)

   横切基础设施:
     Nacos(注册中心 + 配置中心)   ·  Redis(缓存 / 分布式锁 / 限流计数)
     Sentinel(限流熔断降级)       ·  OpenFeign + LoadBalancer(声明式 RPC + 客户端负载均衡)
     Docker Compose(一键编排)
```

### 基础设施
- **Nacos** —— 服务注册中心 + 配置中心(动态配置、不停机改参数)
- **Spring Cloud Gateway** —— API 网关:统一路由、JWT 前置鉴权、跨域、限流入口
- **Sentinel** —— 限流、熔断、降级(替代单体里没有的容错能力)
- **Redis** —— 缓存、分布式锁、分布式限流计数、幂等键、热点数据集排行(ZSet)
- **RabbitMQ** —— 计费/审计/通知/支付回调事件异步化,死信队列兜底
- **MinIO** —— S3 兼容对象存储,保存上传的数据集文件
- **OpenFeign + LoadBalancer** —— 服务间声明式调用 + 客户端负载均衡

> 一致性策略:支付与账单等跨服务一致性采用**最终一致(本地消息表 + MQ + 补偿重试)**,不引入 Seata 强一致框架——务实、可演示,且面试可清晰讲出取舍。

### 业务微服务划分(8 个业务服务 + 网关)

| 服务 | 拆自 | 职责 | 拥有数据 | 关键技术点 |
|---|---|---|---|---|
| **gateway** | — | 统一入口:路由、JWT 前置鉴权、跨域、限流 | — | Spring Cloud Gateway |
| **auth-service** | user/account | 登录、注册、签发/校验 JWT、用户查询 | `user` | Redis 存 token 黑名单/刷新 |
| **dataset-service** | dataset + pricingconfig + PricingEngine | 数据集 CRUD、**CSV 上传与 schema 解析**、定价配置、定价计算 | `dataset`, `pricing_config` | MinIO + 异步解析 + Redis 缓存 |
| **consent-service** | consentmanagement + ConsentMatchingEngine | 授权规则 CRUD、字段级匹配决策 | `consent_rule` | 纯计算服务,可独立伸缩 |
| **access-service** | datamarket/AccessRequest | **交易编排 + 审批状态机**:Feign 调 consent/dataset,落访问记录,**发 MQ 事件** | `access_request` | Redis 分布式锁幂等,RabbitMQ 生产者 |
| **payment-service** | 新增 | Stripe 托管收银、支付状态机、**幂等 webhook** 处理 | `payment_order` | Stripe + 幂等 + 本地消息表 |
| **billing-service** | billing | 消费计费事件落账单,核对支付状态,账单查询 | `billing_record` | RabbitMQ 消费者 + DLQ |
| **audit-service** | auditlog | 消费审计事件落日志,审计查询 | `audit_log` | RabbitMQ 消费者,高写入 |
| **notification-service** | 新增 | 消费审批/支付事件,发站内通知(**事件扇出**) | `notification` | RabbitMQ 消费者 |

### 公共模块
- **synapse-common** —— 统一返回体 `Result<T>`、全局异常、JWT 工具、MQ 消息体定义、常量、本地消息表通用结构
- **synapse-api**(可选)—— 各服务的 Feign 客户端接口 + DTO,供调用方依赖

---

## 3. 各「关键词」落在哪里(简历可逐条对应)

| 关键词 | 在本项目的具体落点 | 解决的真实问题 |
|---|---|---|
| **微服务** | 1 个单体 → 8 个独立部署服务 + 网关,按领域 + 伸缩特性拆分 | 独立部署/伸缩,故障隔离 |
| **服务注册与发现** | Nacos,服务上下线自动感知,Feign + LoadBalancer | 去除硬编码地址 |
| **配置中心** | Nacos Config,定价系数、限流阈值等热更新 | 改参数不重启 |
| **API 网关** | Spring Cloud Gateway,统一 JWT 鉴权 + 路由 + 限流 | 鉴权下沉,前端只对接一个入口 |
| **Redis 缓存** | 数据集/定价配置缓存(读多写少),命中率优化 | 减少重复查库,降延迟 |
| **Redis 分布式锁/幂等** | access 防重复提交;支付 webhook 幂等键防重复入账 | 防重复下单/扣费 |
| **分布式限流** | Sentinel(QPS 限流/熔断)+ Redis 计数兜底 | 防刷、保护下游 |
| **消息队列 (RabbitMQ)** | 计费、审计、通知、支付回调异步化,生产者/消费者 + DLQ | 关键路径解耦,削峰,失败重试 |
| **事件扇出 (fan-out)** | 一条审批/支付事件被 billing/audit/notification 各自消费 | 一次发布,多方订阅 |
| **对象存储 (MinIO)** | Owner 上传 CSV 存 MinIO,异步解析推断 schema | 真实文件存储,去掉手动粘 JSON |
| **异步处理 / 状态机** | 上传 `UPLOADED→PARSING→READY/FAILED`;申请 `PENDING→APPROVED/REJECTED`;支付 `UNPAID→PAID/...` | 大文件不阻塞、业务闭环 |
| **第三方支付集成** | Stripe 托管收银,创建支付单 + 校验签名 webhook,不碰卡号 | 真实支付,规避 PCI |
| **分布式事务 / 最终一致** | 支付成功 → 账单状态收敛,本地消息表 + MQ + 补偿 | 跨库数据最终一致 |
| **集群架构** | 关键服务多副本 + Nacos 负载均衡;Redis 集群;RabbitMQ | 横向扩展,高可用 |
| **容器编排** | Docker Compose 一键拉起全套(可选附 K8s 清单) | 环境一致,可复现 |

---

## 4. 演进路线(分阶段,每阶段可独立验收)

> 原则:**保留现有单体作参照,新结构增量长出**;每阶段结束都能 `docker compose up` 跑通已迁移部分。

- **Phase 0 — 工程骨架与基础设施**
  改造为 Maven 多模块;建 `synapse-common`;`docker-compose` 拉起 Nacos / MySQL / Redis / RabbitMQ / MinIO / Sentinel;导出现有表结构为 `init.sql`;凭据外置到 `.env` / Nacos。

- **Phase 1 — 抽离 auth-service + 网关**
  第一个服务注册进 Nacos,配置走 Nacos Config;网关路由 `/api/auth/**` 并做 JWT 前置鉴权。打通「前端→网关→服务→Nacos」主链路。

- **Phase 2 — dataset-service + consent-service**
  迁移定价与授权两条核心引擎;引入 OpenFeign 服务间调用;**dataset-service 接入 MinIO 上传 + 异步 CSV 解析 + Redis 缓存**。

- **Phase 3 — access-service 编排 + 审批 + 支付 + RabbitMQ 异步化**(技术高潮)
  拆分交易编排,加**审批状态机**;计费/审计/通知改为发 MQ 事件,billing/audit/notification 三个消费者落库(**事件扇出**);加死信队列 + Redis 分布式锁幂等;
  新建 **payment-service** 接 **Stripe 沙箱**(托管收银 + 幂等 webhook),支付结果经**本地消息表 + MQ** 与账单**最终一致**。

- **Phase 4 — 集群与弹性**
  关键服务多副本 + 网关负载均衡;Redis 改集群/哨兵;接 Sentinel 限流熔断降级。

- **Phase 5 — 可观测性与收尾**
  Actuator + Prometheus + Grafana(可选)、链路追踪(可选 Zipkin/SkyWalking);完善 README 与架构图;沉淀简历话术。

---

## 5. 简历话术(实现后按实际数据填充)

> 项目名:**Synapse —— 基于动态定价引擎的数据交易微服务平台**

- 主导将单体数据交易系统**微服务化**,基于 **Spring Cloud Alibaba(Nacos / Gateway / Sentinel)** 按领域与伸缩特性拆分为 **8 个独立服务**,通过 **OpenFeign + LoadBalancer** 完成服务间调用与客户端负载均衡。
- 针对「访问申请」热点链路,用 **RabbitMQ** 将计费、审计、通知**异步解耦**并实现**事件扇出**,引入**死信队列**保证失败可重试,接口 P99 延迟下降约 **XX%**(填实测)。
- 集成 **Stripe** 第三方支付(托管收银,规避 PCI),设计支付状态机与**幂等 webhook**;支付结果经**本地消息表 + MQ** 与账单达成**最终一致**,杜绝重复入账。
- 实现 Owner **CSV 数据集上传**:文件存 **MinIO(S3 兼容)**,**异步解析**自动推断字段类型与敏感标记,替代手动 schema 录入。
- 用 **Redis** 缓存数据集与定价配置(读多写少),命中率约 **XX%**;基于 **Redis 分布式锁**实现下单幂等。
- 基于 **Nacos 配置中心**实现定价系数/限流阈值**热更新**;接入 **Sentinel** 完成 QPS 限流与熔断降级。
- 使用 **Docker Compose** 编排 Nacos / Redis / RabbitMQ / MinIO / MySQL 及各业务服务,实现一键启动与环境一致性。

---

## 6. 安全清理(必须做)

历史 README 与 `application.properties` **明文暴露了生产 RDS 地址、用户名、密码**(`admin / Zhm1015.`),测试账号密码也写进了文档。升级时:
1. 凭据迁移到 **Nacos 配置中心 / 环境变量 / `.env`**,代码与文档不再硬编码(README 已清理);
2. **轮换该数据库密码**(已泄露在仓库历史中,删文档无法消除历史);
3. `.gitignore` 屏蔽本地敏感配置;
4. 支付一律走 **Stripe 托管收银**,系统**不接触银行卡号**,只处理支付单创建与**签名校验**后的 webhook。
