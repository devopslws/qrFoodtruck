-- order-service 초기 테이블 생성 (현재 엔티티 기준)

CREATE TABLE IF NOT EXISTS orders (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_no    VARCHAR(255) NOT NULL UNIQUE,
    session_uuid VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / PAID / COOKING / READY / CANCELLED / DELIVERED
    total_price INT          NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INT          NOT NULL,
    unit_price   INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE IF NOT EXISTS payments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL UNIQUE,
    payment_key VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',  -- COMPLETED / FAILED / CANCELLED
    paid_amount INT          NOT NULL,
    paid_at     DATETIME,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE IF NOT EXISTS payment_details (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id  BIGINT         NOT NULL,
    tx_code     VARCHAR(255)   NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    is_deleted  TINYINT(1)     NOT NULL DEFAULT 0,
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_detail_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);