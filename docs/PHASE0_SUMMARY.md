# Phase 0 总结:工程骨架与基础设施

## 一句话概括
在动任何业务代码之前,先把**多模块工程骨架**和**一套本地中间件环境**搭起来——用 Docker Compose 拉起微服务集群依赖的全部中间件,建好 8 个业务库,并用 Maven 父工程统一管理版本。这是后续所有 Phase 的地基。

---

## 一、基础设施:`infra/`

### Docker Compose 六中间件
一条 `docker compose up -d` 拉起集群依赖的全部中间件容器:

| 容器 | 镜像 | 宿主端口 | 作用 |
|---|---|---|---|
| synapse-nacos | nacos/nacos-server:v2.3.2 | 8848 / 9848 | 服务注册 + 配置中心 |
| synapse-mysql | mysql:8.0 | 3307→3306 | 业务数据库 |
| synapse-redis | redis:7-alpine | 6379 | 缓存 |
| synapse-rabbitmq | rabbitmq:3.13-management | 5672 / 15672 | 消息队列 |
| synapse-minio | minio/minio | 9000 / 9001 | S3 兼容对象存储(存 CSV) |
| synapse-sentinel | bladex/sentinel-dashboard:1.8.8 | 8858 | 流控/熔断控制台 |

要点:
- **命名卷持久化**(nacos-data / mysql-data / redis-data / rabbitmq-data / minio-data):`stop`/`start` 数据不丢;只有 `down -v` 才会清。
- **健康检查**:各容器配了 healthcheck,`docker ps` 能看到 `healthy`。

### 数据库初始化:`mysql/init/init.sql`
容器首次启动(空卷)时自动执行,建 **8 个业务库、11 张表**,提前按微服务边界分库:

| 库 | 表 | 归属服务(后续) |
|---|---|---|
| synapse_auth | users | auth-service |
| synapse_dataset | datasets, pricing_config | dataset-service |
| synapse_consent | consent_rules | consent-service |
| synapse_access | access_requests, outbox_message | access-service |
| synapse_payment | payment_order, outbox_message | payment-service |
| synapse_billing | billing_records | billing-service |
| synapse_audit | audit_logs | audit-service |
| synapse_notification | notification | notification-service |

> `outbox_message` 表提前埋好——为 Phase 3「本地消息表 + MQ」的最终一致方案打底。
>
> ⚠️ **坑**:`init.sql` 只在**空卷首次启动**时运行。卷一旦建好,改 init.sql 不会重新执行,需手动执行或 `down -v` 重建。

### 凭据管理:`.env` / `.env.example`
- `.env.example`:占位符模板,进 git。
- `.env`:真实本地口令,**已 gitignore**,严禁提交。
- 所有中间件密码从 `.env` 注入,代码库里无明文。

---

## 二、工程骨架:`services/`

### Maven 多模块父工程
`services/pom.xml` 作为父 POM,统一管理:
- **Spring Boot 3.3.4** 作为 parent。
- **BOM 版本锁定**:Spring Cloud 2023.0.3、Spring Cloud Alibaba 2023.0.3.2、MyBatis-Plus、jjwt。
- 子模块目前:`synapse-common`(Phase 0)、后续逐步加入。

### 公共模块 `synapse-common`
第一个子模块,提供全集群共享的基础设施代码(详见 Phase 1,大部分在 Phase 0 奠基):
统一返回体 `Result` / `ResultCode`、`BusinessException` + `GlobalExceptionHandler`、`JwtUtil` + `JwtProperties`、`SecurityConstants` / `MqConstants`、领域事件 `event/*`,以及 `@AutoConfiguration` 自动装配。

---

## 三、构建环境的坑(影响每个新模块)

- 本机 **JDK 25**、系统无 `mvn`,用复制到 `services/` 的 **Maven Wrapper**(`mvnw.cmd`)构建。
- JDK 23+ 下 Lombok 必须:①用新版(父 POM 覆盖 `lombok.version=1.18.46`);②在 `maven-compiler-plugin` 显式声明 `annotationProcessorPaths`,否则 `@Data` 等静默不生成、报大量"找不到符号"。
- `synapse-common` 里 Lombok 是 `optional`(不传递),**每个新服务模块要自己声明 lombok 依赖**。

---

## 四、验证结果
- 六容器全部 `healthy`。
- 8 库 11 表建成。
- 父 POM + `synapse-common` 编译通过。

---

## Phase 0 该带走的一句话
> 微服务不是"先写代码再补环境",而是**先立地基**:一套可一键拉起的中间件环境 + 统一版本的多模块骨架 + 按服务边界预分的数据库。地基稳了,后面每拆一个服务都是往上添砖,而不是推倒重来。

---

## 安全备注(持续有效)
- 单体旧代码里的**生产 RDS 凭据**和 **JWT 默认密钥**已进 git 历史,**生产必须轮换**。
- 新架构一律外置凭据(`.env` / Nacos),不直连线上库;不把真实密钥写进代码或提交。
