# Phase 4 总结:集群与弹性(限流 · 熔断降级 · 多副本负载均衡 · Redis 高可用)

## 一句话概括
业务闭环建好后,给集群装上**抗压与容错**能力:接入 **Sentinel** 做服务方法级流控 + 网关路由级限流 + Feign 熔断降级(防雪崩),把关键服务跑成**多副本 + 网关 LoadBalancer 轮询**(实例故障 Nacos 自动摘除),再把单点 Redis 换成 **Redis Sentinel 高可用(1 主 2 从 3 哨兵,主库故障 ~6s 自动转移、客户端透明重连)**。分四个子阶段:**4a-1**(access 服务级弹性)→ **4a-2**(网关限流)→ **4b**(多副本 + LB)→ **4c**(Redis HA)。

---

## 一、本阶段交付物

| 子阶段 | 能力 | 改动面 |
|---|---|---|
| **4a-1** | access 服务方法级流控 + Feign 熔断降级 | access-service pom/yaml + BlockHandler + 2 个 Fallback |
| **4a-2** | 网关路由级限流(边缘第一道闸) | gateway pom/yaml + gw-flow 规则 |
| **4b** | access 多副本 + 网关 lb:// 轮询 + 故障摘除 | 零架构改动(同 jar 起第 2 实例) |
| **4c** | Redis Sentinel 高可用 | docker-compose Redis 拓扑 + access/dataset yaml |

**锁定决策**(用户拍板):①Sentinel 规则用 **Nacos 数据源持久化**(不用 Dashboard 内存);②4a 先做服务级熔断降级再做网关限流;③4c 用 Sentinel 哨兵方案。

---

## 二、4a-1:access 服务级弹性

首次引入 **Sentinel**。pom 加 `spring-cloud-starter-alibaba-sentinel` + `sentinel-datasource-nacos`。**规则源用 Nacos 的关键好处**:规则 Nacos→客户端**直推**,不依赖 Dashboard↔客户端回连(容器↔宿主那条链路在 Windows 上常不稳),限流生效与否与 Dashboard 无关。

### 两大能力

**① 流控**——controller.create 加注解:
```java
@SentinelResource(value="access:create",
    blockHandler="createBlocked", blockHandlerClass=AccessBlockHandler.class)
```
`AccessBlockHandler.createBlocked(...)` 静态方法签名 = 原方法参数 + 末尾 `BlockException`,返回 `code=429` 的 `Result` 优雅降级。规则 `synapse-access-service-flow-rules.json`(grade=1 QPS,count=5)。

**② Feign 熔断降级**——两个 `@FeignClient` 加 `fallback=XxxFallback.class`:
```
ConsentClientFallback / DatasetClientFallback
   @Component implements 接口,返回 Result.fail(SERVICE_UNAVAILABLE=503,...)
```
下游宕机/异常时,编排层 `unwrap()` 把 503 翻成干净业务失败、**不雪崩**。degrade 规则绑 3 个 Feign 资源(资源名格式 `HTTPMETHOD:http://服务名/path/{占位}`,如 `GET:http://synapse-dataset-service/api/datasets/{id}`,异常比例 0.5,minRequestAmount=5,timeWindow=10)。common 的 `ResultCode` 加 `TOO_MANY_REQUESTS(429)` + `SERVICE_UNAVAILABLE(503)`。

### 真机验证(`verify-phase4a.ps1`)
| 场景 | 结果 |
|---|---|
| 突发 30 连发(不存在 datasetId 避免落库) | ✅ ~6 放行 + 24 返回 429 |
| 杀 dataset-service 后提交 | ✅ code=503「dataset service unavailable」|
| 断路器/无实例短路后延迟 | ✅ 1292ms **骤降到 16–30ms**(快速失败不堆线程)|
| Phase 3 全链路回归 | ✅ 8/8 不受影响 |

---

## 三、4a-2:网关路由级限流(边缘第一道闸)

gateway pom 加 `spring-cloud-alibaba-sentinel-gateway`(SCG 适配器)。`synapse-gateway.yaml` 配 `spring.cloud.sentinel.scg.fallback`(mode=response,status=429,自定义 body `{"code":429,"message":"gateway throttled...","data":null}`——message 特意标 gateway,与服务级 blockHandler 的「system busy」区分)+ datasource.gw-flow 挂 nacos 源(rule-type=**gw-flow**)。

规则 `synapse-gateway-flow-rules.json` = `GatewayFlowRule` 数组(resource=**路由 id** `synapse-access-service`,grade=1 QPS,count=10)。启动日志见 `SentinelSCGAutoConfiguration` 注册 `SentinelGatewayFilter`(order=Integer.MIN_VALUE 最先跑)。

### 网关级 vs 服务级的关键区别
| | 触发 | 响应 |
|---|---|---|
| **网关路由级** | 路由 QPS 超阈 | **真 HTTP 429** |
| **服务方法级** | `@SentinelResource` QPS 超阈 | HTTP 200 + body.code=429 |

> **真机验证**:40 个 `GET /api/access/mine`(该路径无服务级流控)在 397ms 内发出 → **恰好 10×HTTP200 + 30×HTTP429**(精确命中 10 QPS)。**PS 坑**:`Invoke-RestMethod` 对真 429 会 throw,读 body 用 `$_.ErrorDetails.Message`。**WebFlux 安全**:sentinel starter 的 servlet 自动配置 `@ConditionalOnWebApplication(SERVLET)` 不会在响应式网关误装。

---

## 四、4b:access 多副本 + 负载均衡

**无需改架构**:同一 access jar 起第 2 实例(`java -jar ... --server.port=8094` 覆盖端口),两实例**同名** `synapse-access-service` 注册进 Nacos → 网关 `lb://` 经 Spring Cloud LoadBalancer 轮询。为肉眼可见加了探针 `GET /api/access/whoami`(`@Value("${server.port}")` 注入返回本实例端口)。

### 真机验证(`verify-phase4b.ps1`)
| 场景 | 结果 |
|---|---|
| 经网关连调 12 次 whoami(间隔 150ms 避开网关 10 QPS) | ✅ 端口 `8084→8094→8084...` 精确交替、6/6 平分(轮询)|
| 杀 8094 等 30s | ✅ Nacos 健康检查剔除死实例 + LB 刷新 → 后续 12 次全落 8084、零错误(故障自动摘除)|

> 注:多副本时第 2 实例的 Sentinel 客户端 transport 端口默认 8719 被占会自动 +1,无需手配。

---

## 五、4c:Redis Sentinel 高可用

docker-compose 把单 `redis` 换成 **1 主(redis-master:6379)+ 2 从(6380/6381)+ 3 哨兵(26379/80/81)**,quorum=2,down-after 5s。用 Redis 的服务(access 幂等锁 + dataset 缓存)的 `spring.data.redis` 从 host/port 改成 `sentinel.master: mymaster` + `sentinel.nodes: 127.0.0.1:26379,26380,26381`。

> **命名冲突**:compose 已有个叫 `sentinel` 的服务(bladex 限流控制台),Redis 哨兵必须叫 `redis-sentinel-*`。

### 最大的坑:Docker Desktop 地址壁垒
服务跑**宿主**、Redis 在**容器**,`host.docker.internal` 在宿主解析成 `10.14.212.154`(仅 127.0.0.1 被端口代理→不可达)、在容器里解析成 `192.168.65.254`(**另一个 IP**)——宿主/容器对同一名字解析到**不同且互不可达**的 IP → 哨兵把 `host.docker.internal` 返给宿主客户端 → Lettuce `RedisConnectionException`。

**解法**:找一个**两侧都可达**的统一地址 = **宿主真实 LAN IP**(宿主 `Test-NetConnection` 通、容器 `nc` 也通),参数化为 `.env` 的 `REDIS_ANNOUNCE_IP`(DHCP 变了改一行 + 重启 redis 容器)。compose 里主/从 `--replica-announce-ip ${REDIS_ANNOUNCE_IP}`、哨兵 `sentinel monitor mymaster ${REDIS_ANNOUNCE_IP} 6379 2` + `announce-ip`。

> **另一个坑**:哨兵配置最初用 `printf 'a\nb'` 写 conf,`\n` 被外层 shell 吞成字面 `n` 致配置塌成一行崩溃 → 改用 **list 形式 command + `|` 字面块 + 逐行 echo**(真换行、无反斜杠、无嵌套引号);哨兵 conf 写 `/tmp`(运行时会 rewrite 需可写)。

### 真机验证(`verify-phase4c.ps1`)
| 场景 | 结果 |
|---|---|
| 哨兵拓扑 | ✅ master + 2 从 + 2 其他哨兵,quorum=2 |
| Phase 3 全链路回归(幂等锁 + 缓存经哨兵) | ✅ 8/8 |
| 停主库 | ✅ **~6s 完成故障转移**(6379→6381 提升 replica-2)|
| 转移后经网关提交申请 | ✅ 返回正常业务码(Lettuce 经 +switch-master 通知透明重连新主,零 500)|
| 重启老主库 | ✅ 以从库身份自愈回归,回到 1 主 2 从 |

Docker 至此 11 容器(6 redis + nacos/mysql/rabbitmq/minio/sentinel-dashboard)。

---

## Phase 4 该带走的一句话
> 弹性有**两个方向**:对**流量**(限流:网关边缘挡洪峰 + 服务方法级护核心)和对**故障**(熔断降级快速失败防雪崩 + 多副本 LB 摘死实例 + Redis 哨兵主从自动切换)。而在 Docker Desktop 上搭 Redis 哨兵最难的不是 Redis,是**宿主与容器的网络地址壁垒**——统一用宿主 LAN IP 对外通告,是让「哨兵返回的主库地址两侧都可达」的唯一解。

---

## 沉淀/复用的约定
- Sentinel 规则一律 **Nacos 数据源**持久化(`*-rules.json`,type=json,import-config.ps1 已支持发 json)。
- 网关级返真 429 / 服务级返 body.code=429,PS 测前者读 `$_.ErrorDetails.Message`。
- 多副本 = 同 jar `--server.port` 覆盖 + 同名注册 Nacos,零架构改动。
- 容器网络涉及宿主↔容器互访:统一用宿主 LAN IP(`REDIS_ANNOUNCE_IP`),别用 `host.docker.internal`。
- 验证脚本一律 UTF-8 BOM。
