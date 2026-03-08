-- order-service 초기 테이블 생성
-- Entity 기준으로 작성 (ddl-auto: update 사용 중이므로 참고용 스키마)

CREATE TABLE IF NOT EXISTS orders (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id  VARCHAR(36)   NOT NULL,                          -- Redis 세션 UUID
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACCEPTED',       -- ACCEPTED / PAID / PARTIAL_REFUND / FULL_REFUND
    total_price  DECIMAL(12,2) NOT NULL,
    paid_price   DECIMAL(12,2) NOT NULL,
    is_deleted   TINYINT(1)    NOT NULL DEFAULT 0,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(255)  NOT NULL,                          -- 주문 시점 스냅샷
    unit_price   DECIMAL(12,2) NOT NULL,
    quantity     INT           NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACCEPTED',       -- ACCEPTED / PAID / FULL_REFUND
    is_deleted   TINYINT(1)    NOT NULL DEFAULT 0,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE IF NOT EXISTS payments (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_no       VARCHAR(36)   NOT NULL UNIQUE,                 -- Toss PG 노출용 UUID
    order_id       BIGINT        NOT NULL,                        -- orders.id 참조 (FK 제약 없음 - MSA)
    total_price    DECIMAL(12,2) NOT NULL,
    paid_price     DECIMAL(12,2) NOT NULL,
    payment_type   VARCHAR(30)   NOT NULL,                        -- CARD / ACCOUNT_TRANSFER
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',      -- PENDING / COMPLETED / PARTIAL_REFUND / FULL_REFUND
    is_deleted     TINYINT(1)    NOT NULL DEFAULT 0,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS payment_details (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id  BIGINT        NOT NULL,
    tx_code     VARCHAR(255)  NOT NULL,                           -- PG사 승인번호
    amount      DECIMAL(12,2) NOT NULL,                          -- 양수: 결제, 음수: 환불
    is_deleted  TINYINT(1)    NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_detail FOREIGN KEY (payment_id) REFERENCES payments(id)
);