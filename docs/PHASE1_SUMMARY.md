# Phase 1 总结:抽离 auth-service + API 网关

## 一句话概括
把单体里的登录/注册/用户查询拆成独立的 **auth-service**,前面架一个 **API 网关**做统一入口和 JWT 集中鉴权,配置全部外置到 **Nacos**——第一次跑通「**前端 → 网关 → 微服务 → Nacos → MySQL**」的完整微服务链路。

---

## 一、四个交付物

| 模块 | 角色 | 端口 |
|---|---|---|
| **synapse-gateway** | API 网关(WebFlux 响应式),统一路由 + JWT 集中鉴权 | 8080 |
| **synapse-auth-service** | 认证服务(Servlet),登录/注册/查用户,签发 JWT | 8081 |
| **synapse-common** | 公共**库**(非服务),统一返回体/异常/JwtUtil/常量 | 内嵌 |
| **Nacos 配置 ×3** | 路由、白名单、数据源、JWT 密钥(占位符 + `.env` 替换) | 8848 |

> `synapse-common` 不是服务:它没有 main/端口/注册,而是被打包**内嵌**进 auth 和 gateway 里运行。

---

## 二、最核心的设计:网关集中鉴权

```
请求带 Bearer token
      │
      ▼
┌─────────────────────────────────┐
│  网关 AuthGlobalFilter (order=-100,最先跑) │
│  ① 白名单?→ 剥掉 X-User-Id 后放行  │  ← login/register 免验
│  ② 验 token 签名 + 过期            │
│  ③ set 注入 X-User-Id 头(覆盖)   │  ← 防伪造命门
└─────────────────────────────────┘
      │  (验过一次)
      ▼
┌─────────────────────────────────┐
│  下游服务(auth 等)              │
│  直接信任 X-User-Id 头,不再验 token │
└─────────────────────────────────┘
```

**三个关键点:**
1. **token 只在网关验一次**,下游读 `X-User-Id` 头即知"你是谁",不重复验签。
2. **`set` 覆盖注入(非 add)**:客户端自带的假 `X-User-Id` 被真值顶掉;白名单放行时主动**剥掉**该头。两道锁防伪造。
3. **下游不装整套 Spring Security**:auth 只引 `spring-security-crypto` 拿 BCrypt 校验密码。

---

## 三、关键技术决策

- **JWT 线兼容**:新 `JwtUtil` 用与单体相同的密钥 / HS256 / issuer / subject=userId,**老 token 依然有效**,迁移对用户无感(绞杀者模式)。
- **Servlet vs WebFlux**:网关无 `DispatcherServlet`,common 的 MVC `@RestControllerAdvice` 用 `@ConditionalOnClass(DispatcherServlet)` 守卫,只在 servlet 服务加载。
- **配置外置 Nacos**:路由/白名单/数据源/密钥都放 Nacos;密钥用 `__VAR__` 占位、由 `import-config.ps1` 从 `.env` 替换——**代码库无明文密钥**。
- **`lb://` 服务发现路由**:路由写 `lb://synapse-auth-service`(服务名非 IP),经 Nacos 查实例 + 客户端负载均衡挑一个转发——为 Phase 4 多副本弹性打底。
- **统一响应风格**:HTTP 层恒 200,成败看 body 的 `code`(网关自身拒绝时例外,设真实 401)。

---

## 四、Review 修掉的代码质量问题

- 🔴 **注册越权**:`role` 原由客户端传 → 硬编码 `consumer`,删除 `RegisterRequest.role`。
- 🟡 **并发唯一性**:`register` catch `DuplicateKeyException`,靠 DB 唯一键 `uk_users_username` 兜底 + 友好提示。
- 🟡 **异常覆盖**:`GlobalExceptionHandler` 补 `ConstraintViolation` / `HttpMessageNotReadable` / `MissingRequestHeader` 三个 handler,客户端错误不再误报 500。

---

## 五、真机验证抓到的两个真 bug(编译过 ≠ 能跑)

1. **`characterEncoding=utf8mb4`** → MySQL 驱动不认(服务端字符集,非 Java 编码)→ 登录 500。改 `UTF-8`。
2. **`import-config.ps1` 用 GBK 读 UTF-8 yaml** → Nacos 配置中文乱码、`synapse:` 被吞进注释行 → **白名单整块失效**。改 `[System.IO.File]::ReadAllText(..., UTF8)`。

**验证结果(全部经网关 8080):**

| 场景 | 结果 |
|---|---|
| 登录 | ✅ 200 + token + 用户 |
| 无 token /info | ✅ 401 missing token |
| 带 token /info | ✅ 200 + 用户 |
| 伪造 token | ✅ 401 invalid token |
| 只塞假 X-User-Id 头 | ✅ 被剥离,仍 401 |

接口测试集合见 `postman/datamarket-extension.postman_collection.json`(含自动抓 token + 断言)。

---

## 六、运行前置清单(复现用)

1. `infra/.env` 补 `NACOS_USERNAME/PASSWORD`(nacos/nacos)与 `JWT_SECRET`。
2. `docker compose up -d`(六容器)。
3. 种子用户 test/123456:卷已存在时 `init.sql` 不重跑,需手动 INSERT。
4. `import-config.ps1` 推 3 份 Nacos 配置(脚本需 UTF-8 BOM,否则 PS5.1 解析崩)。
5. `mvnw install` 全模块装本地仓库(否则单模块 `spring-boot:run` 找不到 common)。
6. `mvnw -pl synapse-auth-service spring-boot:run` + gateway 同法启动。

---

## Phase 1 该带走的一句话
> 微服务拆分不只是"把类挪到新模块",核心是**把横切关注点(鉴权)上提到网关统一做,让业务服务变薄、只信任上游传下来的身份**;配置外置、服务发现、统一响应/异常则是让这套东西能协作、能运维的骨架。

---

## 沉淀的编码约定(已统一,后续服务照此办)
① service 拆接口 + `impl`  ② `@Autowired` 字段注入  ③ POJO 分 `entity`/`dto`/`vo`  ④ 要读配置/换实现的做 Bean,纯函数才进 `utils`。
