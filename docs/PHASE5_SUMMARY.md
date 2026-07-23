# Phase 5 总结:可观测性与收尾

## 一句话概括
给已经拆好、能抗压、能高可用的 9 服务集群装上**眼睛**:统一接入 **Micrometer + Actuator** 暴露 Prometheus 指标(**Grafana** 出 QPS/P99/错误率/JVM 面板)、**Micrometer Tracing + Zipkin** 打通跨服务链路追踪(traceId 随网关路由与 Feign 透传);再用一轮**端到端压测**把简历话术里的 `XX%` 全部换成实测数字,最后刷新 README / 架构图 / 本总结收尾整个企业级升级。

---

## 一、本阶段交付物

| 类别 | 交付 |
|---|---|
| **指标(5a)** | 父 pom 统一加 Actuator + Micrometer;`synapse-common.yaml` 共享 management/tracing 配置;`infra/prometheus/prometheus.yml` 抓 9 服务;`infra/grafana` provisioning(数据源 + 总览仪表盘) |
| **追踪(5b)** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` + `feign-micrometer`;100% 采样上报 Zipkin |
| **中间件** | docker-compose 新增 `prometheus`(9090)/ `grafana`(3000)/ `zipkin`(9411),集群共 14 容器 |
| **压测** | `infra/verify-phase5.ps1`:缓存冷/热对比 + 并发读吞吐 + 命中率 + access 全编排延迟 + Prometheus 服务端分位 |
| **文档** | 回填 `ENTERPRISE_UPGRADE_PLAN.md` 简历数字;更新 `README.md`;本总结 |

---

## 二、指标:一处加依赖,九服务全覆盖

父工程 `services/pom.xml` 原本只有 `dependencyManagement`(管版本、不引入),**没有全局 `<dependencies>`**。Phase 5 在父 pom 加了一个顶层 `<dependencies>` 块——所有子模块(9 服务 + common)**自动继承**,一处改动全覆盖:

```
spring-boot-starter-actuator          # 暴露 /actuator/health、/actuator/prometheus
micrometer-registry-prometheus        # 指标渲染成 Prometheus 抓取格式
micrometer-tracing-bridge-brave       # 生成/传播 traceId、spanId
zipkin-reporter-brave                 # span 上报 Zipkin
feign-micrometer                      # Feign 跳的 traceId 透传
```

版本全部由 Spring Boot 3.3.4 BOM 托管,无需写版本号。

配置放在**共享**的 `synapse-common.yaml`(9 服务都 import),关键三条:
- `management.metrics.tags.application=${spring.application.name}` —— 每条指标带**服务名标签**,Grafana 里能按服务拆分;
- `management.metrics.distribution.percentiles-histogram.http.server.requests=true` —— **发布 `_bucket` 直方图序列**,Prometheus 端才能用 `histogram_quantile` 算 P95/P99(不开这条就没有分位数);
- `management.tracing.sampling.probability=1.0` + `management.zipkin.tracing.endpoint` —— 100% 采样(本地演示)并指向宿主 Zipkin。

### Prometheus 抓取:容器 → 宿主的方向问题
服务跑在**宿主机**、Prometheus 在**容器**里,靠 `host.docker.internal:8080..8088` 反向访问宿主。这与 Phase 4c 那个坑**方向相反**:4c 是宿主客户端要连容器里的 Redis 哨兵(`host.docker.internal` 在两侧解析到不同且不可达的 IP,只能用宿主真实 LAN IP);而这里是**容器→宿主**方向,Docker Desktop 本就支持 `host.docker.internal`,不受 `REDIS_ANNOUNCE_IP` 影响。compose 里补了 `extra_hosts: host.docker.internal:host-gateway` 保证 Linux 上也通。

Grafana 用 provisioning **零点击**装配:`datasources/` 指向 `http://prometheus:9090`,`dashboards/` 从 `/var/lib/grafana/dashboards` 加载 `synapse-overview.json`(5 面板:服务存活 / QPS / P99 / 5xx / JVM 堆,均按 service 维度)。

---

## 三、追踪:一条访问申请横跨几个服务

`feign-micrometer` 让 traceId 随 Feign 调用的 HTTP 头透传,`micrometer-tracing-bridge-brave` 让网关路由跳也透传。真机验证抓到干净的三层扇出:

```
gateway → access-service → dataset-service      (Feign)
```

即 traceId 跨了**两个 HTTP 边界**(网关路由跳 + access→dataset 的 Feign 跳)。用真实数据集时链路会更长:`gateway → access → dataset(getDataset) → consent(match) → dataset(quote)`。

**验证踩坑**:
1. 造流量别用突发连发——`access:create` 有 Phase 4a 的 5 QPS 限流,25 连发大多被 blockHandler 拦成 429、根本没走到 Feign。发**单个低速**请求才出扇出链路。
2. Zipkin 最近 trace 会被 Prometheus **每 5s 抓 `/actuator/prometheus` 生成的单服务 actuator trace 刷屏**。查真实业务链路要按 `serviceName=synapse-gateway` 过滤(actuator 抓取直连服务、不经网关)。

---

## 四、压测:把简历里的 XX% 换成实测

`infra/verify-phase5.ps1` 先端到端造真实数据(owner 上传 `sample-users.csv` → 异步解析出 8 字段 READY → 设定价),再压测。实测(本机 Docker Desktop,仅供自证,非生产基准):

| 指标 | 实测 |
|---|---|
| 数据集详情:冷读(DB)→ 热读(Redis) | 9.6ms → 3.3ms,**降 66%** |
| 数据集详情:服务端 P50 / P95 / P99 | 2.3 / 3.7 / **6.6ms** |
| 读路径吞吐(8 并发,3200 请求 0 失败) | **~1450 req/s** |
| 缓存命中率(压测期 Redis keyspace 增量) | **>99%**(3200 读仅首读穿透) |
| access 全编排(gateway→access→Feign×3)P50 / P99 | 27.6 / 354.9ms(P99 系首请求 JIT 预热离群) |

> **命中率的诚实口径**:先 `DEL dataset::<id>` 清缓存做冷读,再连读做热读;并发压测期间同一 key 只有首读穿透到 DB,其余全部命中 Redis,故 `keyspace_misses` 增量为 0。这是「读多写少」缓存的真实表现,不是刷出来的数字。

---

## 五、收尾清单

- ✅ 简历话术 `docs/ENTERPRISE_UPGRADE_PLAN.md` 第 5 节 `XX%` 全部回填实测
- ✅ `README.md`:架构图加观测栈、NFR-1 填真实数字、Getting Started 加观测控制台 URL + 微服务启动步骤、Roadmap 标 Phase 4/5 完成
- ✅ 本总结 `docs/PHASE5_SUMMARY.md`
- ⏳ 押后(非本阶段):5c 日志聚合(Loki/ELK);真 Stripe 接入(留作开关);登录计时侧信道(低优先级 backlog)

至此 5 个阶段的企业级升级全部完成:**单体 → 网关 + 8 业务服务 + Nacos/Redis/RabbitMQ/MinIO + Sentinel 弹性 + Redis 哨兵高可用 + Prometheus/Grafana/Zipkin 可观测**。
