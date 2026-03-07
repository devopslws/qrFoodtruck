-- inventory-service 초기 테이블 생성
CREATE TABLE IF NOT EXISTS products (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)  NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS stock (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    product_id  BIGINT   NOT NULL UNIQUE,
    quantity    INT      NOT NULL DEFAULT 0,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS shipments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PREPARING',  -- PREPARING / SHIPPED / DELIVERED
    shipped_at  DATETIME,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 샘플 상품 데이터
INSERT INTO products (name, price) VALUES
    ('무선 키보드',        49900.00),
    ('USB-C 허브',         35000.00),
    ('모니터 받침대',      29900.00),
    ('웹캠 HD',            79000.00),
    ('노트북 파우치',      19900.00),
    ('한정판 마우스패드',   9900.00);  -- 재고 1개 — 분산 락 테스트용

-- 초기 재고 (product_id 순서 일치)
INSERT INTO stock (product_id, quantity) VALUES
    (1, 50),
    (2, 30),
    (3, 20),
    (4, 15),
    (5, 100),
    (6, 1);    -- 동시 결제 시나리오 테스트용
