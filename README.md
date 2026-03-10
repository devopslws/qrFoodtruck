# 🚚 QR FoodTruck — 팝업 푸드트럭 주문 시스템

> 회원가입 없이 QR 스캔만으로 주문 · 결제 · 조리 상태를 실시간으로 확인하는 푸드트럭 전용 주문 플랫폼
> </br></br>
> [고객 주문 화면: mobile 권장]</br>
> http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/orderView
> > ![서비스 바로가기 QR](.\resources\orderViewQr.jpeg)
> 
> > [주문관리 화면: pc 권장]</br>
http://foodtruck-alb-2021833890.ap-northeast-2.elb.amazonaws.com/admin
> ![서비스 바로가기 QR](.\resources\adminQr.jpeg)

---

## 📌 프로젝트 개요

팝업스토어 푸드트럭에서 사용할 주문 시스템입니다.  
고객은 테이블에 비치된 QR코드를 스캔해 메뉴를 확인하고 바로 결제까지 진행할 수 있으며,  
판매자는 별도 관리 화면에서 주문 접수 및 조리 상태를 칸반 방식으로 관리합니다.

---

## 🖥️ 화면 구성

| 화면 | 대상 | 주요 기능 |
|------|------|-----------|
| 주문 화면 | 고객 (QR 접근) | 메뉴 조회 · 장바구니 · 결제 · 조리 상태 실시간 확인 |
| 관리 화면 | 판매자 | 주문 목록 조회 · 칸반 조리 상태 관리 · 재고 입고 |

---

## 🏗️ 시스템 아키텍처

```
[고객 브라우저]
     │ QR 접속 / SSE 구독
     ▼
[Server A — 주문 · 결제]          [Server B — 재고 · 제조]
 - 주문 접수 및 결제 처리           - 상품 재고 관리
 - Toss PG 연동                    - 조리 상태 관리 (7초 자동 완성)
 - Redis 재고 Read-Only 조회        - Redis 재고 Write 주체
 - SSE 이벤트 Push                 - RabbitMQ 이벤트 발행
     │                                      │
     └──────────── RabbitMQ ────────────────┘
                   (비동기 메시지)
     │
  [Redis] — 공유 캐시 (재고 실시간 관리)
  [MySQL A] — 주문 · 결제 DB
  [MySQL B] — 상품 · 제조 DB
```

---

## 🔄 주문 플로우

```
1. 고객 QR 접속 → 익명 세션 UUID 발급 (Redis)
2. 메뉴 조회 → Server A가 Redis에서 재고 직접 조회
3. 주문 요청 → 재고 확인 후 주문 승인
4. 결제창 이동 → orderNo(UUID) 생성 후 Toss PG에 제출
5. 결제 완료 → Server A가 Redis 최종 재고 확인
6. 재고 있음 → Toss 최종 승인 → TX 전문 저장
7. RabbitMQ → Server B에 제조 지시
8. Server B → 7초 후 조리 완료
9. SSE → 고객에게 조리 완료 알림 Push
10. 판매자가 전달 완료 버튼 클릭 → 10초 후 세션 종료
```

---

## ⚙️ 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| ORM | Spring Data JPA + QueryDSL |
| Database | MySQL 8.0 (서비스별 독립 DB) |
| Cache / 분산락 | Redis 7.2 |
| Message Broker | RabbitMQ 3.13 |
| 실시간 통신 | SSE (Server-Sent Events) |
| 결제 | Toss Payments PG |
| 인프라 | AWS EC2 + Docker Compose |
| IaC | AWS CloudFormation (예정) |

---

## 🗂️ 프로젝트 구조

```
qrFoodtruck/                     ← mono repo
├── order-service/               ← Server A: 주문 · 결제
│   └── src/main/java/com/example/orderService/
│       └── domain/
│           ├── order/entity/    ← Orders, OrderItem
│           └── payment/entity/  ← Payment, PaymentDetail
│
├── inventory-service/           ← Server B: 재고 · 제조
│   └── src/main/java/com/example/inventoryService/
│       └── domain/
│           ├── product/entity/  ← Product (재고 통합)
│           └── manufacture/entity/ ← Manufacture
│
├── init-sql/
│   ├── order/                   ← order-service DB 초기화 SQL
│   └── inventory/               ← inventory-service DB 초기화 SQL (시드 포함)
│
├── docker-compose.yml
└── build.gradle                 ← 루트 공통 설정
```

---

## 🗄️ 데이터 스키마 요약

**Server A (orderdb)**

| 테이블 | 설명 |
|--------|------|
| `orders` | 주문 헤더. 고객 세션 UUID · 총액 · 상태 |
| `order_items` | 주문 상세. 상품명 · 가격 스냅샷 보존 |
| `payments` | 결제. `order_no`(UUID)를 PG사 식별자로 사용 |
| `payment_details` | 결제 이력. 환불은 음수 금액으로 기록 |

**Server B (inventorydb)**

| 테이블 | 설명 |
|--------|------|
| `products` | 상품 · 재고 통합 관리. `is_displayed`로 품절 전시 제어 |
| `manufactures` | 제조 상태 관리. `ACCEPTED → COMPLETED → DELIVERED / DISCARDED` |

> 모든 테이블은 `is_deleted` · `created_at` · `updated_at` 포함 (Soft Delete 적용)

---

## 🔑 설계 포인트

**익명 세션**  
회원 서비스 없이 QR 접속 시 서버가 UUID를 발급, Redis에 저장. 상품 수령 완료 후 10초 뒤 자동 만료.

**재고 관리 전략**  
Redis를 재고의 1차 저장소로 사용, DB와 항상 동기화 유지. Server A는 Read-Only, Server B만 Write 권한 보유.

**결제 보안**  
내부 PK 대신 `order_no`(UUID)를 PG사에 노출해 내부 식별자를 외부로부터 보호.

**서비스 간 통신**  
동기 호출 없이 RabbitMQ 메시지로만 서비스 간 이벤트 전달. 재고 입고 시에도 RabbitMQ → SSE 경로로 접속 고객 화면 자동 갱신.

---

## 🚀 로컬 실행

```bash
git clone https://github.com/devopslws/qrFoodtruck.git
cd qrFoodtruck

docker compose up --build
```

| 서비스 | 주소 |
|--------|------|
| order-service | http://localhost:8080 |
| inventory-service | http://localhost:8081 |
| RabbitMQ Management | http://localhost:15672 (admin / admin123) |
| MySQL order-db | localhost:3316 |
| MySQL inventory-db | localhost:3317 |

---

## 📋 현재 진행 상태

- [x] 프로젝트 구조 설계 (mono repo MSA)
- [x] docker-compose 인프라 구성
- [x] DB 스키마 설계 및 JPA Entity 작성
- [ ] Repository / Service 레이어 구현
- [ ] Toss PG 연동
- [ ] Redis 재고 동기화 전략 구현
- [ ] RabbitMQ 메시지 발행 / 소비 구현
- [ ] SSE 실시간 알림 구현
- [ ] 주문 · 관리 화면 구현
- [ ] AWS 배포 및 CloudFormation IaC