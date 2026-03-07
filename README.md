# MSA 모노레포 프로젝트 구조
호스팅: ? (다 만들고 아주 심플한 조작부 화면 추가하자)
## 디렉토리 레이아웃

```
msa-project/                        ← IntelliJ에서 이 폴더를 열기
├── build.gradle                    ← 공통 의존성 (전체 서비스에 적용)
├── settings.gradle                 ← 서브모듈 등록
├── gradle/
│   └── libs.versions.toml          ← 버전 통합 관리(지금은 안씀)
│
├── docker-compose.yml              ← 인프라 전체 (DB, Redis, RabbitMQ)
├── init-sql/
│   ├── order/
│   │   └── 01_schema.sql
│   └── inventory/
│       └── 01_schema.sql
│
├── order-service/                  ← 서비스 A (주문 & 결제)
│   ├── build.gradle                ← 서비스 전용 의존성
│   └── src/main/
│       ├── java/com/example/orderservice/
│       │   └── OrderServiceApplication.java
│       └── resources/
│           └── application.yml     ← port: 8080, order-db 연결
│
└── inventory-service/              ← 서비스 B (재고 & 출고)
    ├── build.gradle
    └── src/main/
        ├── java/com/example/inventoryservice/
        │   └── InventoryServiceApplication.java
        └── resources/
            └── application.yml     ← port: 8081, inventory-db 연결
```

## 인프라 실행

```bash
# 인프라만 먼저 띄우기 (앱은 IDE에서 직접 실행)
docker compose up -d order-db inventory-db redis rabbitmq

# 상태 확인
docker compose ps

# 전체 종료
docker compose down
```

## 앱 실행 순서

```
1. docker compose up -d (인프라 먼저)
2. docker compose ps → 전부 healthy 확인
3. IntelliJ에서 OrderServiceApplication 실행 (▶)
4. IntelliJ에서 InventoryServiceApplication 실행 (▶)
```

## 접속 정보

| 서비스             | 주소                    |
|------------------|-------------------------|
| Order Service    | http://localhost:8080   |
| Inventory Service| http://localhost:8081   |
| RabbitMQ UI      | http://localhost:15672  |
| Order DB         | localhost:3306          |
| Inventory DB     | localhost:3307          |
| Redis            | localhost:6379          |
