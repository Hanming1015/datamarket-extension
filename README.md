# Synapse — Data Trading Platform on a Dynamic Pricing Engine

> A fine-grained, consent-driven **data marketplace** that connects **Data Owners** (e.g. healthcare providers) with **Data Consumers** (e.g. research institutions), featuring a dynamic pricing engine, field-level consent control, real payments, and an event-driven microservice backend.

> **⚠️ Status: Active migration.** This project is being upgraded from a Spring Boot monolith into a **Spring Cloud Alibaba microservice cluster**. The legacy monolith under `/backend` is kept as a working reference while services are extracted incrementally. See **[docs/ENTERPRISE_UPGRADE_PLAN.md](./docs/ENTERPRISE_UPGRADE_PLAN.md)** for the full architecture blueprint, phased roadmap, and résumé notes.

---

## 1. Overview

Synapse lets data owners publish datasets with rich, field-level pricing and consent rules, and lets consumers discover datasets, request access for a declared purpose, pay for approved access, and receive only the fields they are entitled to. Every access decision is priced by a **dynamic pricing engine** and recorded in an **immutable audit trail**.

The platform is being re-architected around **domain-aligned microservices** with **asynchronous, event-driven** processing for the non-critical path (billing, audit, notifications), **Redis** for caching and idempotency, and **RabbitMQ** for decoupling and reliability.

---

## 2. Roles

| Role | Capabilities |
|---|---|
| **Data Owner** | Upload datasets (CSV, auto-parsed), configure pricing, define field-level consent rules, **approve / reject** incoming access requests. |
| **Data Consumer** | Browse the marketplace, submit access requests for a stated purpose and field set, **pay** for approved access (Stripe), view billing and granted data. |

> Access approval is performed by the **Owner of the requested dataset** (or by automatic consent rules). There is no separate platform-admin role in the current scope.

---

## 3. Architecture

### 3.1 High-level topology

```
                       ┌─────────────┐
   React SPA ─────────▶│   Gateway   │  JWT auth · routing · rate limiting
                       └──────┬──────┘
       ┌──────────┬──────────┼───────────┬──────────────┐
       ▼          ▼          ▼           ▼              ▼
   auth-svc   dataset-svc  consent-svc  access-svc   payment-svc
   user/JWT   upload/parse  consent      orchestration  Stripe
              + pricing     matching     + approval FSM  + webhook
                  │                       │              │
                  ▼ (async parse)         │ publish      │ webhook
              ┌────────┐                  ▼ events       ▼
              │ MinIO  │          ┌──────────────────────────┐
              │ object │          │         RabbitMQ         │  + Dead Letter Queue
              │ store  │          └───┬────────┬────────┬────┘
              └────────┘              ▼        ▼        ▼
                                 billing-svc audit-svc notification-svc
                                 (async)     (async)   (approval/payment)

   Cross-cutting infrastructure:
     Nacos (service registry + config center)  ·  Redis (cache / lock / rate-limit)
     Sentinel (flow control & circuit breaking) ·  OpenFeign + LoadBalancer (RPC)
     Docker Compose (orchestration)
```

### 3.2 Business microservices

| Service | Extracted from | Responsibility | Owns data |
|---|---|---|---|
| **gateway** | — | Unified entry: routing, JWT pre-auth, CORS, rate-limit ingress | — |
| **auth-service** | `user/account` | Login, register, issue/verify JWT, user lookup | `user` |
| **dataset-service** | `dataset` + `pricingconfig` + `PricingEngine` | Dataset CRUD, **CSV upload & schema parsing**, pricing config, pricing calculation | `dataset`, `pricing_config` |
| **consent-service** | `consentmanagement` + `ConsentMatchingEngine` | Consent rule CRUD, field-level access matching | `consent_rule` |
| **access-service** | `datamarket/AccessRequest` | **Trade orchestration** + **approval state machine**; calls consent/dataset via Feign; publishes events | `access_request` |
| **payment-service** | _new_ | Stripe hosted checkout, payment state machine, idempotent webhook handling | `payment_order` |
| **billing-service** | `billing` | Consumes billing events → billing records; billing queries | `billing_record` |
| **audit-service** | `auditlog` | Consumes audit events → audit log; audit queries | `audit_log` |
| **notification-service** | _new_ | Consumes approval/payment events → in-app notifications (event fan-out) | `notification` |

### 3.3 Infrastructure components

| Component | Role in the platform |
|---|---|
| **Nacos** | Service registry + dynamic configuration center (hot-reload of pricing/limit params) |
| **Spring Cloud Gateway** | API gateway: routing, centralized JWT auth, rate-limit ingress |
| **Sentinel** | Flow control, circuit breaking, graceful degradation |
| **Redis** | Hot-data cache (datasets/pricing), distributed lock & idempotency keys, rate-limit counters |
| **RabbitMQ** | Asynchronous events (billing, audit, notifications, payment callbacks) with Dead Letter Queue |
| **MinIO** | S3-compatible object storage for uploaded dataset files |
| **OpenFeign + LoadBalancer** | Declarative inter-service calls with client-side load balancing |
| **Docker Compose** | One-command local orchestration of the full cluster |

---

## 4. Functional Requirements (FR)

### FR-1 · Authentication & Users (`auth-service`)
- Users can **register** and **log in**; the system issues a **JWT** for stateless auth.
- Requests are authenticated at the **gateway** before reaching business services.
- Users carry a role (**Owner** / **Consumer**) that gates capabilities.

### FR-2 · Dataset Management & Upload (`dataset-service`)
- Owners **upload a dataset as a CSV file**; the system enforces **format and size limits**.
- Uploaded files are stored in **MinIO**; parsing runs **asynchronously** (`UPLOADED → PARSING → READY / FAILED`).
- The parser **infers a schema**: column names, data types (int/float/string/date), and flags **sensitive fields** by heuristic (e.g. id / name / email).
- Owners configure **pricing**: base access fee, per-field fee, sensitive-field multiplier, purpose multipliers, bulk discounts.

### FR-3 · Consent Management (`consent-service`)
- Owners define **field-level consent rules**: allowed roles, allowed purposes, allowed/denied fields, validity period.
- The **matching engine** decides, per request, the outcome: `approved` / `partial` / `rejected`, with allowed/denied fields and reasons.

### FR-4 · Marketplace & Access Requests (`access-service`)
- Consumers **browse** datasets and **submit an access request** (purpose + requested fields).
- Each request enters an **approval state machine**: `PENDING → APPROVED / REJECTED`.
- On approval, the system runs consent matching + **dynamic pricing**, persists the access record, and **publishes events** (billing, audit, notification) to RabbitMQ.
- Duplicate submissions are prevented via a **Redis-based idempotency / distributed lock**.

### FR-5 · Dynamic Pricing Engine (in `dataset-service`)
- Computes total cost from: normal-field cost, sensitive-field cost (× multiplier), bulk discount tier, base access fee, and purpose multiplier — deterministically and auditable end-to-end.

### FR-6 · Payment (`payment-service`)
- For an approved, billed request, the Consumer pays via **Stripe hosted checkout** (the platform never handles card data — PCI scope is offloaded).
- A **payment state machine** tracks `UNPAID → PAYING → PAID / FAILED / REFUNDED`.
- Stripe **webhooks are processed idempotently** (callbacks may be re-delivered); payment results converge to billing via **eventual consistency** (local message table + MQ).

### FR-7 · Billing (`billing-service`)
- **Consumes billing events asynchronously** to create billing records; reconciles paid status from payment events.
- Provides billing queries for dashboards.

### FR-8 · Audit (`audit-service`)
- **Consumes audit events asynchronously** to maintain an append-only audit trail of all access decisions.

### FR-9 · Notifications (`notification-service`)
- **Consumes approval and payment events** (event fan-out) to deliver in-app notifications to the relevant Consumer/Owner.

---

## 5. Non-Functional Requirements (NFR)

> Targets are deliberately set at a small-but-credible "enterprise scenario" scale to justify each architectural component. They are the design goals the architecture is built to meet and demonstrate.

| # | NFR | Target | Mechanism | Headline |
|---|---|---|---|---|
| NFR-1 | **Latency** | Browse / request APIs **P99 < 200ms** | Redis cache for hot datasets & pricing | "Cache hit-rate X%, P99 cut from Y→Z" |
| NFR-2 | **Throughput / peak shaving** | Sustain bursts of **5k+ access requests** without failure | MQ buffering + async billing | "Moved DB writes off the sync path via MQ" |
| NFR-3 | **Scalability** | Pricing / consent services **scale horizontally & independently** | Stateless services + replicas + LB | "Split by scaling profile, not just domain" |
| NFR-4 | **Availability** | A non-core service outage **does not block the order path** | Service isolation + Sentinel degradation | "Audit down ⇒ orders still flow; events queue up" |
| NFR-5 | **Reliability (no message loss)** | Billing / payment events **zero loss** | Persistence + manual ACK + DLQ + local message table | "Triple guarantee: outbox, manual ack, DLQ" |
| NFR-6 | **Consistency** | Payment success → billing state **converges within seconds** | Eventual consistency (local message table) | "Traded strong consistency for availability + compensation" |
| NFR-7 | **Idempotency** | Webhooks / duplicate submits **never double-charge** | Redis idempotency keys + unique constraints | "Webhooks re-deliver; idempotency key ⇒ exactly-once" |
| NFR-8 | **Security** | No secrets in code; **no card data handled** | Nacos/env config; Stripe hosted checkout | "Secrets externalized; payment kept out of PCI scope" |

---

## 6. Technology Stack

**Frontend:** React, TypeScript, Vite, TailwindCSS, Axios

**Backend:** Java 17, Spring Boot 3.5, Spring Cloud Alibaba (Nacos, Gateway, Sentinel), OpenFeign, Spring Security + JWT, MyBatis-Plus

**Messaging / Data / Infra:** RabbitMQ, Redis, MinIO (S3-compatible), MySQL 8, Stripe (sandbox), Docker Compose

---

## 7. Repository Structure

```
/frontend/dataMarketFrondEnd   React + TypeScript + Vite SPA
/backend                       Legacy Spring Boot monolith (reference, being decomposed)
/services                      Spring Cloud Alibaba microservices (Maven multi-module)
/infra                         docker-compose + middleware config + DB init scripts
/docs/ENTERPRISE_UPGRADE_PLAN.md   Architecture blueprint, phased roadmap, résumé notes
TESTING_GUIDE.md               End-to-end functional walkthrough of the pricing/consent flow
```

---

## 8. Getting Started

> Microservices and infrastructure are being added phase by phase (see the roadmap). The instructions below describe the target setup; until a given service is migrated, use the monolith under `/backend`.

### Prerequisites
- Docker & Docker Compose
- JDK 17, Maven
- Node.js 16.14+, pnpm

### 1) Configuration (no secrets in the repo)
All credentials (database, Redis, RabbitMQ, Stripe keys) are provided via **environment variables / `.env`** and the **Nacos config center** — never hardcoded. Copy the example file and fill in your own values:
```bash
cp infra/.env.example infra/.env   # then edit infra/.env
```

### 2) Start infrastructure
```bash
cd infra
docker compose up -d   # Nacos, MySQL, Redis, RabbitMQ, MinIO, Sentinel
```

### 3) Backend (current monolith)
```bash
cd backend
mvn spring-boot:run    # starts on http://localhost:8080
```

### 4) Frontend
```bash
cd frontend/dataMarketFrondEnd
pnpm install
pnpm run dev           # http://localhost:5173
```

For a step-by-step feature walkthrough (pricing math, consent rules, billing verification), see **[TESTING_GUIDE.md](./TESTING_GUIDE.md)**.

---

## 9. Roadmap

The migration is delivered in phases, each independently runnable:

- **Phase 0** — Multi-module skeleton, `synapse-common`, infra `docker-compose`, DB init scripts
- **Phase 1** — Extract `auth-service` + API gateway (Nacos registry & config)
- **Phase 2** — `dataset-service` (+ MinIO upload/parse) and `consent-service`; OpenFeign + Redis cache
- **Phase 3** — `access-service` orchestration + approval FSM; RabbitMQ async billing/audit/notify; payment-service (Stripe)
- **Phase 4** — Clustering & resilience: multi-replica + LB, Redis cluster, Sentinel
- **Phase 5** — Observability & polish; final docs and résumé write-up

Full detail in **[docs/ENTERPRISE_UPGRADE_PLAN.md](./docs/ENTERPRISE_UPGRADE_PLAN.md)**.

---

## 10. Security Notes

- **No secrets in source.** Database/Redis/RabbitMQ/Stripe credentials live in environment variables and the Nacos config center.
- **No card data.** Payments use Stripe's hosted checkout; the platform only creates payment intents and verifies signed webhooks, keeping it out of PCI scope.
- **Stateless auth.** JWT verified at the gateway; services trust forwarded identity.

> Note: the previous version of this README exposed a live database host and password. Those credentials must be **rotated**, as they remain in the git history.
