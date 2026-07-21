# Phase 2 总结:授权规则 + 数据集/定价两大业务服务

## 一句话概括
接着 Phase 1 的骨架,往里填**真正的业务**:拆出 **consent-service**(数据授权规则的增删查 + 匹配)和 **dataset-service**(数据集 CRUD + CSV 上传对象存储 + 后台异步 Schema 推断 + 定价配置/报价引擎),第一次引入 **MinIO 对象存储**、**Redis 缓存**、**@Async 异步任务**三样企业级中间件能力,并把 Phase 1 定下的编码约定与「网关传身份、下游只信 `X-User-Id`」的安全模型完整复用到两个新服务上。

---

## 一、本阶段交付物

| 模块 | 角色 | 端口 | 库 |
|---|---|---|---|
| **synapse-consent-service** | 数据授权规则:创建/撤销/查询/匹配,owner 归属隔离 | 8082 | synapse_consent |
| **synapse-dataset-service** | 数据集 CRUD + CSV 上传 + 异步解析 + 定价/报价 | 8083 | synapse_dataset |

两个服务都:注册进 Nacos、配置外置 Nacos、经网关统一鉴权、沿用 `Result<T>` 统一响应 + `entity/dto/vo` 分包 + service 接口/impl 拆分 + `@Autowired` 字段注入。

启动后全链路为 4 服务:`gateway 8080 → auth 8081 / consent 8082 / dataset 8083`。

---

## 二、consent-service:一个「薄但对」的服务

功能不复杂(规则的 CRUD + 按 datasetId 匹配),价值在于**把 Phase 1 的身份模型落到数据归属上**:

```
网关注入 X-User-Id
      │
      ▼
Controller @RequestHeader(USER_ID_HEADER) String userId
      │
      ▼
create → rule.ownerId = userId          ← 谁建的记谁名下
list   → WHERE owner_id = userId         ← 只看自己的
revoke → 先按 id 查,owner 不符 → NOT_FOUND ← 不是 403
```

**关键点:越权访问返回 404 而非 403** —— 撤销/查询别人的规则时,不告诉调用方"这条存在但你没权限",直接当"不存在"。避免通过状态码泄露资源是否存在(资源枚举防护)。这个 `mustOwn() → NOT_FOUND` 的模式在 dataset-service 里被完整复用。

> Review 修的 HIGH:原代码规则**没有 owner 归属**,任何登录用户能查/撤任何规则。补 `owner_id` 列 + 三个端点全部 owner-scoped。

---

## 三、dataset-service:本阶段的重头戏

单体里数据集的 `fieldsSchema` 是**前端手填**的——不可信、也没有真实文件。本服务把它做成了真正的「上传文件 → 存对象存储 → 后台解析出结构」的闭环。

### 3.1 CSV 上传与异步解析(三件新中间件的主场)

```
PUT /{id}/file (multipart)
   │
   ├─ 存 MinIO(对象存储,不进 DB / 不占服务磁盘)
   ├─ parseStatus = UPLOADED,立即返回 ← 不阻塞请求线程
   │
   └─▶ @Async 后台线程 parseAsync(datasetId)
         parseStatus = PARSING
         try-with-resources: MinIO 流 → UTF-8 Reader → CSVParser
           · 全量计 recordCount
           · 前 200 行采样喂给 SchemaInferer
         推断出 fieldsSchema / fields → parseStatus = READY
         catch → parseStatus = FAILED   ← 状态机报错,不抛异常
```

**三个刻意的设计决策:**

1. **异步任务用「状态字段」报错,不 throw**:`@Async` 跑在别的线程,没有调用方去 catch,异常也到不了 `GlobalExceptionHandler`(它只管同步请求线程)。所以失败就把 `parseStatus` 打成 `FAILED`,前端轮询 `/{id}/parse-status` 得知。
2. **`parse-status` 端点不走缓存**:它就是给「上传后轮询到 READY」用的,缓存会让轮询永远看到旧状态。其余读接口 `@Cacheable`,写接口(update/remove/upload)`@CacheEvict`。
3. **对象存储走端口-适配器**:业务只依赖 `StorageService`(port,在 `storage/`),`MinioStorageService`(adapter,在 `storage/impl/`)是唯一 `import io.minio.*` 的地方。将来换 S3/OSS 只换适配器,业务零改动。

### 3.2 Schema 推断(SchemaInferer)

纯静态工具类(无状态 → 进 `utils`,符合约定④)。对每列采样值做**「全票制」投票**:`allInt/allDecimal/allBool/allDate` 初始为 true,遇到一个反例就翻 false;空列不误判成某类型(靠 `any` 标志兜底)。返回优先级 `boolean > integer > decimal > date > string`。另按列名关键字(name/email/phone/salary…)标 `sensitive`,喂给定价引擎的敏感字段倍率。

### 3.3 定价引擎与报价

- `PricingConfig`(每数据集一份,`upsert` 语义):`perAccessBase` 基础费 + `perField` 每字段费 + `sensitiveFieldMultiplier` 敏感倍率 + `bulkDiscountJson`(字段数阶梯折扣)+ `purposeMultiplierJson`(用途倍率)。
- `quote`:`总价 = (基础费 + (普通字段费 + 敏感字段费) × (1 - 阶梯折扣)) × 用途倍率`,`setScale(2, HALF_UP)`。对任意登录用户开放;改配置仅 owner。

---

## 四、Review 修掉的问题(按 severity 精确处置)

**consent —— 只修 HIGH:**
- 🔴 授权规则无 owner 归属 → 补 `owner_id` + 三端点 owner-scoped + 越权返 404。

**dataset —— 4 个 MEDIUM 全修:**
- 🟡 **M1 市场列表**:`/all` 只出 `parseStatus=READY` 的、加分页、返回轻量 `DatasetSummaryVO`(不含 `fieldsSchema`),列表页不拖大字段。
- 🟡 **M2 轮询端点**:`/{id}/parse-status` 直读 DB 不走缓存,否则永远轮到旧状态。
- 🟡 **M3 定价校验**:`perAccessBase/perField` `@PositiveOrZero`、`sensitiveFieldMultiplier` `@Positive`;折扣率 ∈ [0,1)、用途倍率 > 0 在 service 内校验。
- 🟡 **M4 上传校验**:拒绝空文件 / 非 `.csv`,`safeName()` 剥路径分隔符防目录穿越。

> LOW 用户选择暂不修:`parseStatus` 魔法字符串未提枚举;`SchemaInferer` 的 `headers.indexOf(h)` 是 O(n²) 且遇重复列名会错位。

---

## 五、真机验证(全部经网关 8080)

**consent(7 请求):**

| 场景 | 结果 |
|---|---|
| 创建规则 | ✅ 200,ownerId = 当前用户 |
| 列规则 | ✅ 仅自己的 |
| 匹配(部分命中) | ✅ 200 |
| 撤销后再匹配 | ✅ 已撤销的不再命中 |
| 撤销别人的规则 | ✅ 404(不泄露存在性) |

**dataset(13 请求,含 CSV 上传闭环):**

| 场景 | 结果 |
|---|---|
| 创建 + 手填 schema | ✅ 200 |
| 我的列表(分页/无 fieldsSchema) | ✅ 200 |
| 定价:负数入参 | ✅ 400(业务码,HTTP 仍 200) |
| 报价 | ✅ total=30 |
| 删除后取详情 | ✅ 404 |
| **上传 `sample-users.csv` → 轮询 READY** | ✅ recordCount=6,8 列类型 + 敏感标记全对 |

CSV 推断实测:`name→string+敏感`、`age→integer`、`email→string+敏感`、`phone→integer+敏感`、`salary→decimal+敏感`、`signup_date→date`、`active→boolean`、`city→string`。

接口集合已按模块拆成 `postman/synapse-{auth,consent,dataset}-collection.json`;测试样本见 `postman/csv/sample-users.csv`。

---

## 六、Windows / PowerShell 5.1 踩坑记录(验证环节)

这些是**测试脚本的坑,不是代码 bug**:
1. `Invoke-RestMethod` 对 HTTP 200 不抛异常 → 业务码失败(body.code=400)得手动查 `.code`。
2. `@{5=1.5}` 整数键 hashtable 无法 `ConvertTo-Json` → 用字符串键 `@{"5"=1.5}`。
3. `Set-Content -Value ""` 会写 5 字节 BOM(不是真空文件)→ 测「空文件上传」得用 `[System.IO.File]::WriteAllText($p,"")`。
4. PS5.1 以 GBK 读 UTF-8 → JSON/YAML 中文乱码(沿用 Phase 1 的 `ReadAllText(..., UTF8)`)。

---

## 七、运行前置清单(在 Phase 1 基础上新增)

1. `infra/.env` 补 MinIO(`synapse` / `change_me_minio_at_least_8_chars`)。
2. `docker compose up -d` 需包含 MinIO(9000/9001)与 Redis 容器。
3. `import-config.ps1` 多推 `synapse-dataset-service.yaml`;网关配置加 dataset 路由(`Path=/api/datasets/**`,非白名单 → 需 JWT)。
4. MySQL 建 `synapse_consent` / `synapse_dataset` 两库,`init.sql` 建 `consent_rules` / `datasets` / `pricing_config` 表。
5. `mvnw install` 全模块;`mvnw -pl synapse-consent-service spring-boot:run`、dataset 同法。

---

## Phase 2 该带走的一句话
> 微服务不只是拆服务,更是拆**中间件职责**:把「文件」交给对象存储、把「热点读」交给缓存、把「慢活」交给异步线程——而异步一旦引入,错误处理就得从「抛异常」换成「状态机」,因为异常再也回不到请求线程了。

---

## 沉淀/复用的约定
- 编码约定①②③④ 全程照 Phase 1 执行,两服务零偏差。
- 安全模型:`@RequestHeader(USER_ID_HEADER)` 取身份 → owner 归属 → 越权一律 `NOT_FOUND`,已成跨服务通用模式。
- **待 Phase 3 解决的耦合**:`DatasetVO.ownerName` 暂为 null——跨服务查用户名要等 Phase 3 引入 OpenFeign 再回填。
