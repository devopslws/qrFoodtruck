# 🚚 QR FoodTruck — 팝업 푸드트럭 주문 시스템
> 회원가입 없이 QR 스캔만으로 주문 · 결제 · 조리 상태를 실시간으로 확인하는 푸드트럭 전용 주문 플랫폼
> </br></br>

| [고객 주문 화면 (Mobile 권장)] | [주문 관리 화면 (PC 권장)] |
| :--- | :--- |
| <a href="http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/orderView"><img src="./resources/orderViewQr.jpeg" width="150"></a> | <a href="http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/admin"><img src="./resources/adminQr.jpeg" width="150"></a> |
| **URL:** http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/orderView | **URL:** http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/admin |
---

## 📋 목차

*주문 관리 화면에 readMe보다 가독성을 개선한 상세 안내가 있습니다

> 💡 **상세 설계 해설** </br>
> 주문 관리 화면에 readMe보다 가독성을 개선한 상세 안내가 있습니다(스키마, 배포환경, CI/CD 파이프라인, 검증 시나리오)은 </br>상단 주문관리 화면 링크의 좌측 **개발 일지** 메뉴에서 확인하세요.
 
- [서비스 시나리오](#서비스-시나리오)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [화면 구성](#화면-구성)
- [주요 설계 결정](#주요-설계-결정)
- [프로젝트 구조](#프로젝트-구조)
- [로컬 실행](#로컬-실행)
- [AWS 배포](#aws-배포)
- [API 목록](#api-목록)

---

## 한눈에 보는 플로우

```
고객 QR 스캔
  │
  ▼
메뉴 조회 (Redis 재고 실시간 반영)
  │
  ▼
장바구니 → Toss 결제
  │  결제 시도 시  Redis DECR 선차감
  │  결제 실패 시  releaseStock()  보상 트랜잭션
  ▼
결제 완료 → RabbitMQ 제조 지시 → inventory-service 조리 (7초 후 자동 완료)
  │
  ├── 고객 화면(주문 고객만): SSE "조리중 → 조리완료" 실시간 수신
  └── 관리자 화면(모든 admin창): SSE 세션 카드 추가 → 수령 버튼
  │
  ▼
관리자 수령 처리 → 고객 화면 종료 (10초 후 모든 admin창+고객 order창에서 완료정보 삭제)
```

재고 부족 시: Redis DECR 0 미만 → `releaseStock()` 보상 트랜잭션 → 실패 배너 + 재고 최신화

Orders 상태: `PENDING → PAID → COOKING → READY → DELIVERED` / `CANCELLED`

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 17 · Spring Boot 3.3 · JPA |
| 동시성 | Redis 7.2 DECR 원자적 재고관리 |
| 비동기 | RabbitMQ 3.13 (Direct + Fanout Exchange) |
| 실시간 | SSE (Server-Sent Events) + 30초 Heartbeat |
| 결제 | Toss Payments v2 |
| 인프라 | AWS ECS Fargate + EC2 + Docker Compose |
| IaC | CloudFormation 5개 스택 |
| CI/CD | GitHub Actions → ECR → ECS 롤링 배포 |

---

## 아키텍처

```
인터넷
 └── ALB :80
      ├── ECS Fargate  order-service     :8080  (주문 · 결제 · SSE)
      └── ECS Fargate  inventory-service :8081  (재고 · 제조)

EC2 t3.micro  (docker-compose)
 ├── MySQL A  orderdb     :3316
 ├── MySQL B  inventorydb :3317
 ├── Redis                :6379  ← 재고 캐시 + 세션 UUID
 └── RabbitMQ             :5672  ← 서비스 간 메시지 브로커
```

**서비스 간 통신**
- `order → inventory` : RestClient (메뉴 조회 / 재고 선차감)
- `order → inventory` : RabbitMQ `manufacture.queue` (제조 지시)
- `inventory → order` : RabbitMQ `cooking.done.queue` (조리완료)
- `order → admin 브라우저` : RabbitMQ Fanout Exchange → SSE

---

## 주요 설계 포인트

**익명 세션** — 회원 서비스 없음. `GET /api/session/init`으로 UUID 발급 → `sessionStorage` 보관 → Redis TTL 10분 + Heartbeat 갱신

**재고 이중화** — Redis가 source of truth (DECR 원자 연산). 결제 성공 후 MQ 수신 시 MySQL 동기화

**관리자 수평확장** — `admin.notify.exchange` Fanout → ECS Task별 exclusive queue. Task가 늘어도 모든 admin 화면에 동시 브로드캐스트

**환불** — Toss 취소 API (부분/전액). 투입 재고는 폐기 처리 (롤백 없음)

---

## 로컬 실행

```bash
# 1. 인프라 기동
docker-compose up -d order-db inventory-db redis rabbitmq

# 2. 각 서비스 실행 (IntelliJ)
SPRING_PROFILES_ACTIVE=local
```

| 서비스 | URL |
|--------|-----|
| 고객 주문 | http://localhost:8080/orderView |
| 관리자 | http://localhost:8080/admin |
| RabbitMQ UI | http://localhost:15672 (admin / admin123) |
| H2 Console | http://localhost:8080/h2-console |

---

## AWS 배포

```bash
# CloudFormation 순차 배포
cd cloudformation && chmod +x deploy.sh && ./deploy.sh
```

| 스택 | 내용 |
|------|------|
| `foodtruck-vpc` | VPC · 서브넷 · IGW · 보안그룹 |
| `foodtruck-infra` | EC2 + docker-compose 자동 설치 |
| `foodtruck-ecr` | ECR 레포지토리 ×2 |
| `foodtruck-ecs` | ECS Cluster · ALB · Target Group |
| `foodtruck-budget` | 월 $10 예산 경보 + ECS 자동 중지 |

`main` 브랜치 push 시 GitHub Actions가 빌드 → ECR push → ECS 롤링 배포 자동 실행

---

## GitHub Secrets

```
AWS_ACCESS_KEY_ID · AWS_SECRET_ACCESS_KEY · AWS_REGION · AWS_ACCOUNT_ID
TOSS_CLIENT_KEY · TOSS_SECRET_KEY · NOTIFICATION_EMAIL · NOTIFICATION_PHONE
```

---
