# 🚚 QR FoodTruck — 팝업 푸드트럭 주문 시스템
> 회원가입 없이 QR 스캔만으로 주문 · 결제 · 조리 상태를 실시간으로 확인하는 푸드트럭 전용 주문 플랫폼
> </br></br>

| [고객 주문 화면 (Mobile 권장)] | [주문 관리 화면 (PC 권장)] |
| :--- | :--- |
| <a href="http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/orderView"><img src="./resources/orderViewQr.jpeg" width="150"></a> | <a href="http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/admin"><img src="./resources/adminQr.jpeg" width="150"></a> |
| **URL:** http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/orderView | **URL:** http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/admin |
---

## 📋 목차

-  [서비스 시나리오](#서비스-시나리오)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [화면 구성](#화면-구성)
- [주요 설계 결정](#주요-설계-결정)
- [프로젝트 구조](#프로젝트-구조)
- [로컬 실행](#로컬-실행)
- [AWS 배포](#aws-배포)
- [API 목록](#api-목록)

---

## 서비스 시나리오

고객 QR 스캔
│
▼
메뉴 조회 (Redis 재고 실시간 반영)
│
▼
장바구니 → Toss 결제
│  결제 시도 시 Redis DECR 선차감
│  결제 실패 시 releaseStock() 보상 트랜잭션
▼
결제 완료 → RabbitMQ 제조 지시 → inventory-service 조리 (7초 후 자동 완료)
│
├── 고객 화면(주문 고객만): SSE "조리중 → 조리완료" 실시간 수신
└── 관리자 화면(모든admin창): SSE 세션 카드 추가 → 수령 버튼
│
▼
관리자 수령 처리 → 고객 화면 종료 
               (10초 후 모든 admin창+고객order창에서 완료 주문정보 삭제)


**재고 부족 시**: Redis DECR 0 미만 → `releaseStock()` 보상 트랜잭션 → 실패 배너 + 재고 최신화

**Orders 상태**: `PENDING → PAID → COOKING → READY → DELIVERED` / `CANCELLED`

> 💡 **동시 주문 · 브로드캐스트 · 세션 생명주기 등 상세 검증 시나리오는 관리자 화면(`/admin`) 우하단 패널을 참고하세요.**

```
---
---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| ORM | Spring Data JPA |
| Database | MySQL 8.0 (서비스별 독립 DB) |
| Cache / 동시성 | Redis 7.2 (DECR 원자적 재고관리) |
| Message Broker | RabbitMQ 3.13 |
| 실시간 통신 | SSE (Server-Sent Events) + Heartbeat 30초 |
| 결제 | Toss Payments v2 |
| 인프라 | AWS ECS Fargate + EC2 + Docker Compose |
| IaC | AWS CloudFormation (6개 스택) |
| CI/CD | GitHub Actions → ECR → ECS 롤링 배포 |

---

## 아키텍처


                        ┌─────────────────────────────────┐
                        │         AWS (ap-northeast-2)     │
                        │                                  │
  고객 / 관리자           │   ALB (foodtruck-alb)            │
  브라우저   ─────────────┼──▶  :80/:443                     │
                        │       │                          │
                        │  ┌────▼────┐    ┌─────────────┐  │
                        │  │ ECS     │    │ ECS Fargate │  │
                        │  │ Fargate │    │ inventory-  │  │
                        │  │ order-  │    │ service     │  │
                        │  │ service │    │ :8081       │  │
                        │  │ :8080   │    └──────┬──────┘  │
                        │  └────┬────┘           │         │
                        │       │                │         │
                        │  ┌────▼────────────────▼──────┐  │
                        │  │     EC2 t3.micro            │  │
                        │  │  docker-compose             │  │
                        │  │                             │  │
                        │  │  MySQL A (orderdb :3316)    │  │
                        │  │  MySQL B (inventorydb :3317)│  │
                        │  │  Redis  (:6379)             │  │
                        │  │  RabbitMQ (:5672 / :15672)  │  │
                        │  └─────────────────────────────┘  │
                        └─────────────────────────────────┘

서비스 간 통신
  order-service → inventory-service : RestClient (메뉴 조회 / 재고 선차감)
  order-service → inventory-service : RabbitMQ manufacture.queue (제조 지시)
  inventory-service → order-service : RabbitMQ cooking.done.queue (조리완료)
  order-service → admin 브라우저    : RabbitMQ admin.notify.exchange Fanout → SSE
```

### 인프라 이원화 전략

| 레이어 | 리소스 | 이유 |
|--------|--------|------|
| 앱 서버 | ECS Fargate | 수평확장, 무중단 롤링 배포 |
| 인프라 | EC2 t3.micro + docker-compose | MySQL·Redis·RabbitMQ 비용 절감 (관리형 서비스 대비) |

---

## 화면 구성

### 고객 주문 화면 (`/orderView`)
- 메뉴 목록 + 재고 수량 표시
- 장바구니 → Toss 결제
- 결제 완료 후 주문 상태 실시간 표시 (조리중 → 조리완료 → 수령완료)
- 재고 부족 실패 시: 상단 고정 배너 + 장바구니 초기화 + 메뉴 재고 최신화

### 관리자 화면 (`/admin`)
- **좌측**: 고객 세션 카드 (접속 고객별 주문 상태, 수령 버튼)
- **중앙**:
    - POS 현장 주문 (준비중)
    - 환불 처리 (판매 LOG 클릭 → 주문번호 자동입력 → Toss 환불 API)
    - 실시간 재고 조회 / 조정 (주문 접수 시 즉시 반영)
- **우측**: 오늘 판매 LOG (DB 조회, 클릭 시 환불 입력란 연동)

---

## 주요 설계 결정

### 익명 세션 관리
- 회원 서비스 없음. `GET /api/session/init` 으로 서버가 uuid 발급
- 클라이언트는 `sessionStorage`에 보관 (탭 단위 격리)
- Redis에 TTL 10분으로 세션 유지, 30초 heartbeat로 갱신
- 페이지 복귀 시 heartbeat로 Redis 유효성 확인 → 만료면 재발급

### 재고 관리 (Redis + MySQL 이중화)
```
Redis  : source of truth (DECR 원자적 연산으로 동시 주문 처리)
MySQL  : 영속 저장 (ManufactureConsumer에서 MQ 수신 후 차감 동기화)

선차감 흐름:
  결제 시도 → Redis DECR → 0 미만이면 재고 복구 + 결제 차단
  결제 성공 → RabbitMQ 제조 지시
  MQ 수신   → MySQL 재고 차감 동기화
```

### SSE 실시간 통신
- 고객 SSE: 첫 상품 담기 시 연결, 수령 완료 후 종료
- 관리자 SSE: Fanout Exchange → ECS Task별 exclusive queue → 수평확장 대응
- Emitter 재연결 경쟁 조건 해결: `old.complete()` 제거 + 콜백 동일성 체크

### RabbitMQ Exchange 구성
| Exchange | 타입 | 용도 |
|----------|------|------|
| `foodtruck.exchange` | Direct | 제조 지시 / 조리완료 |
| `admin.notify.exchange` | Fanout | 관리자 전체 브로드캐스트 |

### 환불 정책
- Toss Payments 취소 API 호출 (부분 / 전액)
- 투입 재고는 폐기 처리 (재고 롤백 없음)
- 서버 검증: 환불 금액 > 결제 금액이면 400 반환

---

## 프로젝트 구조

```
qrFoodtruck/
├── order-service/                   # 주문 · 결제 서버 (:8080)
│   └── src/main/java/com/example/orderService/
│       ├── session/                 # uuid 발급 · heartbeat · Redis 세션 관리
│       ├── order/                   # Orders · OrderItem 엔티티 / Repository
│       ├── payment/                 # Toss 결제 · 환불
│       ├── product/                 # 메뉴 조회 프록시 (→ inventory-service)
│       ├── manufacture/             # RabbitMQ 제조 지시 발행 · 조리완료 수신
│       ├── common/sse/              # SseEmitterManager (고객 실시간 통신)
│       ├── admin/                   # 관리자 SSE · 스냅샷 · 환불 · 재고 · 판매로그
│       └── config/                  # RabbitMQ · RestClient · Toss
│
├── inventory-service/               # 재고 · 제조 서버 (:8081)
│   └── src/main/java/com/example/inventoryService/
│       ├── stock/                   # Stock 엔티티 · Redis 초기화 · 재고 선차감
│       ├── manufacture/             # 제조 지시 수신 · 비동기 조리 · 조리완료 발행
│       ├── admin/                   # 관리자 재고 조회 / 조정 API
│       └── config/                  # RabbitMQ
│
├── init-sql/
│   ├── order/01_schema.sql          # orders · order_items · payments 테이블
│   └── inventory/01_schema.sql      # products · stock · shipments + 샘플 데이터
│
├── cloudformation/
│   ├── 01_vpc.yml                   # VPC · Subnet · IGW · Security Groups
│   ├── 02_infra.yml                 # EC2 + docker-compose (MySQL·Redis·RabbitMQ)
│   ├── 03_ecr.yml                   # ECR 레포지토리
│   ├── 04_ecs.yml                   # ECS Cluster · ALB · Target Groups
│   ├── 05_dns.yml                   # Route53 + ACM (도메인 연결 시 사용)
│   ├── 06_budget.yml                # 예산 경보 · ECS 자동 중지
│   └── deploy.sh                    # CloudFormation 순차 배포 스크립트
│
├── .github/workflows/deploy.yml     # GitHub Actions CI/CD
├── docker-compose.yml               # 로컬 전체 스택 실행
└── build.gradle                     # 멀티모듈 공통 의존성
```

---

## 로컬 실행

### 1. 인프라 서버 기동 (Docker)

```bash
docker-compose up -d order-db inventory-db redis rabbitmq
```

또는 개별 실행:

```bash
# Redis
docker run -d -p 6379:6379 redis:7.2-alpine redis-server --requirepass redis123

# RabbitMQ
docker run -d -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin123 \
  rabbitmq:3.13-management-alpine
```

### 2. 앱 서버 실행

IntelliJ에서 각 서비스 실행 시 환경변수:

```
SPRING_PROFILES_ACTIVE=local
```

`application-local.yml` 필수 설정:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb   # 로컬은 H2 인메모리
  web:
    resources:
      cache:
        period: 0             # 정적 리소스 캐시 비활성
  data:
    redis:
      host: localhost
      password: redis123
  rabbitmq:
    host: localhost
    username: admin
    password: admin123
```

### 3. 접속

| 서비스 | URL |
|--------|-----|
| 고객 주문 화면 | http://localhost:8080/orderView |
| 관리자 화면 | http://localhost:8080/admin |
| RabbitMQ 관리 UI | http://localhost:15672 (admin / admin123) |
| H2 Console | http://localhost:8080/h2-console |

---

## AWS 배포

### 사전 준비

```bash
# AWS CLI 설치 및 인증 설정
aws configure

# GitHub Secrets 등록 필요
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION              # ap-northeast-2
AWS_ACCOUNT_ID
TOSS_CLIENT_KEY
TOSS_SECRET_KEY
NOTIFICATION_EMAIL
NOTIFICATION_PHONE
```

### CloudFormation 스택 배포

```bash
cd cloudformation
chmod +x deploy.sh
./deploy.sh
```

배포 순서 및 각 스택 역할:

| 순서 | 스택 | 내용 |
|------|------|------|
| 1 | `foodtruck-vpc` | VPC · 퍼블릭 서브넷 2개 · IGW · 보안그룹 |
| 2 | `foodtruck-infra` | EC2 t3.micro + docker-compose 자동 설치 |
| 3 | `foodtruck-ecr` | order-service · inventory-service ECR 레포 |
| 4 | `foodtruck-ecs` | ECS Cluster · ALB · Target Group |
| 5 | `foodtruck-budget` | 월 $10 예산 경보 + ECS 자동 중지 |

### CI/CD (GitHub Actions)

`main` 브랜치 push 시 자동 실행:

```
1. Gradle 빌드 (order-service, inventory-service)
2. Docker 이미지 빌드
3. ECR push
4. ECS 롤링 배포 (무중단)
```

### 도메인 연결 (선택)

도메인 구매 후 `05_dns.yml` 실행:

```bash
aws cloudformation deploy \
  --template-file cloudformation/05_dns.yml \
  --stack-name foodtruck-dns \
  --parameter-overrides \
      DomainName=yourdomain.com \
      HostedZoneId=XXXXXXXXXXXXX
```

---

## API 목록

### order-service (:8080)

#### 세션
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/session/init` | uuid 발급 |
| POST | `/api/session/heartbeat?uuid=` | 세션 갱신 |

#### 메뉴 / 주문
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/products` | 메뉴 + 재고 조회 |
| POST | `/api/payment/prepare` | 주문 준비 (Toss 위젯 초기화) |
| GET | `/api/payment/success` | Toss 결제 성공 콜백 |
| GET | `/api/payment/fail` | Toss 결제 실패 콜백 |
| GET | `/api/order/active?uuid=` | 진행중 주문 조회 (복귀 시 SSE 재연결용) |

#### SSE
| Method | Path | 설명 |
|--------|------|------|
| GET | `/sse/connect?uuid=` | 고객 SSE 연결 |
| POST | `/sse/close?uuid=` | 고객 SSE 종료 |
| GET | `/admin/sse/connect?adminKey=` | 관리자 SSE 연결 |

#### 관리자
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/sessions/snapshot` | 진행중 주문 스냅샷 |
| POST | `/api/admin/order/receive` | 수령 처리 |
| GET | `/api/admin/refund/lookup?orderNo=` | 결제 정보 조회 |
| POST | `/api/admin/refund` | Toss 환불 실행 |
| GET | `/api/admin/stock` | 재고 목록 조회 |
| POST | `/api/admin/stock/{productId}` | 재고 조정 |
| GET | `/api/admin/sales/log` | 오늘 판매 로그 |

### inventory-service (:8081)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/products` | 메뉴 + 재고 조회 (Redis) |
| POST | `/api/stock/reserve` | 재고 선차감 (Redis DECR) |
| POST | `/api/stock/release` | 재고 복구 (결제 실패 시) |
| GET | `/api/admin/stock` | 관리자 재고 목록 |
| POST | `/api/admin/stock/{productId}` | 관리자 재고 조정 (Redis + DB 동기화) |

---

## SSE 이벤트 목록

### 고객 수신 이벤트

| 이벤트 | payload | 시점 |
|--------|---------|------|
| `COOKING_START` | `{orderNo, status}` | 결제 완료 직후 |
| `COOKING_DONE` | `{orderNo, status: "READY"}` | 조리 완료 |
| `ORDER_RECEIVED` | `{orderNo}` | 관리자 수령 처리 |

### 관리자 수신 이벤트

| 이벤트 | payload | 시점 |
|--------|---------|------|
| `ADMIN_CONNECTED` | - | SSE 연결 완료 |
| `SESSION_OPENED` | `{uuid}` | 고객 SSE 연결 |
| `SESSION_CLOSED` | `{uuid}` | 고객 SSE 종료 |
| `ORDER_COOKING` | `{uuid, orderNo, desc, amount, stockUpdates}` | 결제 완료 |
| `ORDER_READY` | `{uuid, orderNo}` | 조리 완료 |
| `ORDER_DELIVERED` | `{uuid, orderNo}` | 수령 완료 |

---

## 트러블슈팅 기록

| 문제 | 원인 | 해결 |
|------|------|------|
| SSE 재연결 시 신규 emitter가 즉시 cleanup 됨 | `old.complete()` 비동기 콜백이 새 emitter를 제거 | `complete()` 제거 + 콜백에 동일성 체크 |
| `ORDER_RECEIVED` 이벤트 파싱 오류 | raw String → `JSON.parse()` SyntaxError | `Map.of("orderNo", ...)` 객체로 직렬화 |
| 새로고침 후 admin에서 조리중으로 표시 | `CookingDoneConsumer`에서 `order.cooking()` 오타 | `order.ready()`로 수정 |
| 실시간 재고 갱신 안 됨 | `AdminNotifyConsumer` payload를 `Map<String,String>`으로 캐스팅 | `Map<String,Object>`로 변경 |
| 재고 조정 후 화면 빈칸 | 성공/실패 구분 없이 항상 success Toast | 실패 항목 개별 오류 Toast + 성공 시 `loadStock()` 재조회 |
| 만료 uuid SSE 재연결 차단 | `exists()` false → 연결 거부 | 만료 uuid 재등록 후 허용 (DEBUG 레그) |
| ECS → EC2 DB 연결 실패 | 퍼블릭 IP 사용 | 프라이빗 IP(10.0.1.130)로 변경 |
| 로컬 H2 JPA 초기화 순서 | `defer-datasource-initialization: true` 누락 | application-local.yml에 추가 | 배포 및 CloudFormation IaC