-- inventory-service 초기 테이블 생성
-- Entity 기준으로 작성 (ddl-auto: update 사용 중이므로 참고용 스키마)

CREATE TABLE IF NOT EXISTS products (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)  NOT NULL,
    price           DECIMAL(12,2) NOT NULL,
    stock_quantity  INT           NOT NULL DEFAULT 0,
    is_displayed    TINYINT(1)    NOT NULL DEFAULT 1,
    is_deleted      TINYINT(1)    NOT NULL DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS manufactures (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,                            -- orders.id 참조 (FK 제약 없음 - MSA)
    status      VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',         -- ACCEPTED / COMPLETED / DELIVERED / DISCARDED
    is_deleted  TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 샘플 상품 데이터 (푸드트럭 메뉴)
INSERT INTO products (name, price, stock_quantity, is_displayed) VALUES
    ('짜장면',   8000.00,  50, 1),
    ('짬뽕',     9000.00,  50, 1),
    ('볶음밥',   8000.00,  30, 1),
    ('탕수육',  18000.00,  20, 1),
    ('군만두',   6000.00, 100, 1),
    ('한정판 세트', 35000.00, 1, 1);  -- 재고 1개 — 분산 락 테스트용