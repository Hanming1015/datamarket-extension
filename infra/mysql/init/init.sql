-- =====================================================================
-- Synapse 数据平台 —— 数据库初始化脚本(MySQL 8)
-- 策略:database-per-service 的务实版 —— 单实例内一服务一 schema。
-- 表结构由现有单体 POJO 反推(@TableName / @TableField 为准),
-- 并前置 Phase 2/3 所需的新列/新表(见 [NEW] 注释),避免后续迁移。
-- 命名:MyBatis-Plus 默认 camelCase -> snake_case;ID 为 ASSIGN_UUID -> VARCHAR(64)。
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- synapse_auth :用户与鉴权(拆自 user/account)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_auth;

CREATE TABLE IF NOT EXISTS users (
    id           VARCHAR(64)  NOT NULL,
    name         VARCHAR(255),
    email        VARCHAR(255),
    organization VARCHAR(255),
    role         VARCHAR(64),
    created_at   DATETIME,
    username     VARCHAR(128) NOT NULL,
    password     VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_dataset :数据集 + 定价(拆自 dataset + pricingconfig)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_dataset DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_dataset;

CREATE TABLE IF NOT EXISTS datasets (
    id             VARCHAR(64) NOT NULL,
    name           VARCHAR(255),
    description    TEXT,
    fields         JSON,                       -- List<String>
    fields_schema  JSON,                       -- List<Map<String,Object>>
    owner_id       VARCHAR(64),
    record_count   INT,
    category       VARCHAR(128),
    created_at     DATETIME,
    -- [NEW] Phase 2 CSV 上传支持
    file_object_key VARCHAR(512),              -- MinIO 对象键
    file_name       VARCHAR(255),
    file_size       BIGINT,
    parse_status    VARCHAR(32) DEFAULT 'NONE', -- NONE/UPLOADED/PARSING/READY/FAILED
    PRIMARY KEY (id),
    KEY idx_datasets_owner (owner_id)
    -- 注:ownerName 在 POJO 中为 @TableField(exist=false),不建列
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pricing_config (
    id                        VARCHAR(64) NOT NULL,
    dataset_id                VARCHAR(64),
    per_access_base           DECIMAL(18,4),
    per_field                 DECIMAL(18,4),
    sensitive_field_multiplier DECIMAL(10,4),
    bulk_discount_json        JSON,            -- Map<Integer,BigDecimal>
    purpose_multiplier_json   JSON,            -- Map<String,BigDecimal>
    updated_at                DATETIME,
    PRIMARY KEY (id),
    KEY idx_pricing_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_consent :授权规则(拆自 consentmanagement)
-- 注意:表名为复数 consent_rules(核对源码 @TableName 得到)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_consent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_consent;

CREATE TABLE IF NOT EXISTS consent_rules (
    id               VARCHAR(64) NOT NULL,
    dataset_id       VARCHAR(64),
    allowed_roles    JSON,                     -- List<String>
    allowed_purposes JSON,
    allowed_fields   JSON,
    denied_fields    JSON,
    valid_from       DATE,
    valid_until      DATE,
    status           VARCHAR(32),
    revoked_at       DATETIME,
    created_at       DATETIME,
    PRIMARY KEY (id),
    KEY idx_consent_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_access :访问申请编排 + 审批(拆自 datamarket/AccessRequest)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_access DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_access;

CREATE TABLE IF NOT EXISTS access_requests (
    id               VARCHAR(64) NOT NULL,
    dataset_id       VARCHAR(64),
    dataset_name     VARCHAR(255),
    requester_id     VARCHAR(64),
    requester_name   VARCHAR(255),
    consumer_type    VARCHAR(64),
    purpose          VARCHAR(255),
    requested_fields JSON,
    allowed_fields   JSON,
    denied_fields    JSON,
    denial_reasons   JSON,                     -- Map<String,String>
    status           VARCHAR(32),              -- pending/approved/rejected/partial
    cost             DECIMAL(18,4),
    requested_at     DATETIME,
    responded_at     DATETIME,
    -- [NEW] Phase 3 审批状态机
    approver_id      VARCHAR(64),
    approved_at      DATETIME,
    PRIMARY KEY (id),
    KEY idx_access_requester (requester_id),
    KEY idx_access_dataset (dataset_id),
    KEY idx_access_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- [NEW] 本地消息表(outbox 模式)——保证"改状态"与"发事件"最终一致
CREATE TABLE IF NOT EXISTS outbox_message (
    id             VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64),                -- 如 AccessRequest
    aggregate_id   VARCHAR(64),
    event_type     VARCHAR(64),                -- 如 REQUEST_APPROVED
    payload        JSON,
    status         VARCHAR(16) DEFAULT 'PENDING', -- PENDING/SENT/FAILED
    retry_count    INT DEFAULT 0,
    created_at     DATETIME,
    sent_at        DATETIME,
    PRIMARY KEY (id),
    KEY idx_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_payment :Stripe 支付(新增服务)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_payment;

CREATE TABLE IF NOT EXISTS payment_order (
    id                       VARCHAR(64) NOT NULL,
    access_request_id        VARCHAR(64),
    billing_record_id        VARCHAR(64),
    user_id                  VARCHAR(64),
    amount                   DECIMAL(18,4),
    currency                 VARCHAR(8) DEFAULT 'usd',
    status                   VARCHAR(32) DEFAULT 'UNPAID', -- UNPAID/PAID/EXPIRED/FAILED
    stripe_session_id        VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    idempotency_key          VARCHAR(128),     -- 防重复入账
    created_at               DATETIME,
    updated_at               DATETIME,
    paid_at                  DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_idempotency (idempotency_key),
    KEY idx_payment_access (access_request_id),
    KEY idx_payment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 支付侧本地消息表(结构同 access 的 outbox)
CREATE TABLE IF NOT EXISTS outbox_message (
    id             VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64),
    aggregate_id   VARCHAR(64),
    event_type     VARCHAR(64),
    payload        JSON,
    status         VARCHAR(16) DEFAULT 'PENDING',
    retry_count    INT DEFAULT 0,
    created_at     DATETIME,
    sent_at        DATETIME,
    PRIMARY KEY (id),
    KEY idx_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_billing :账单(拆自 billing)
-- 注意:sensitive_cost 列名对应 POJO 的 sensitiveFieldCost(@TableField)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_billing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_billing;

CREATE TABLE IF NOT EXISTS billing_records (
    id                 VARCHAR(64) NOT NULL,
    user_id            VARCHAR(64),
    user_name          VARCHAR(255),
    dataset_id         VARCHAR(64),
    dataset_name       VARCHAR(255),
    access_request_id  VARCHAR(64),
    query_count        INT,
    records_accessed   INT,
    base_cost          DECIMAL(18,4),
    field_cost         DECIMAL(18,4),
    sensitive_cost     DECIMAL(18,4),          -- <- sensitiveFieldCost
    purpose_multiplier DECIMAL(10,4),
    bulk_discount      DECIMAL(10,4),
    cost               DECIMAL(18,4),
    date               DATE,
    created_at         DATETIME,
    -- [NEW] Phase 3 与支付对账
    payment_status     VARCHAR(32) DEFAULT 'UNPAID',
    PRIMARY KEY (id),
    KEY idx_billing_user (user_id),
    KEY idx_billing_access (access_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_audit :审计日志(拆自 auditlog)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_audit DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_audit;

CREATE TABLE IF NOT EXISTS audit_logs (
    id           VARCHAR(64) NOT NULL,
    timestamp    DATETIME,
    user_id      VARCHAR(64),
    user_name    VARCHAR(255),
    action       VARCHAR(128),
    dataset_id   VARCHAR(64),
    dataset_name VARCHAR(255),
    details      TEXT,
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id),
    KEY idx_audit_ts (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- synapse_notification :站内通知(新增服务)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS synapse_notification DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE synapse_notification;

CREATE TABLE IF NOT EXISTS notification (
    id         VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64),
    type       VARCHAR(64),                    -- APPROVAL/PAYMENT/...
    title      VARCHAR(255),
    content    TEXT,
    ref_id     VARCHAR(64),                    -- 关联业务 id
    is_read    TINYINT(1) DEFAULT 0,
    created_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_notification_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
