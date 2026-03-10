-- inventory-service 초기 테이블 생성 (현재 엔티티 기준)

CREATE TABLE IF NOT EXISTS stock (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    price       INT          NOT NULL,
    quantity    INT          NOT NULL DEFAULT 0,
    displayed   TINYINT(1)   NOT NULL DEFAULT 1,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS manufactures (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(255) NOT NULL,
    session_uuid VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'COOKING',  -- COOKING / DONE
    done_at      DATETIME,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- seed 데이터
INSERT INTO stock (name, price, quantity, displayed, deleted, created_at, updated_at) VALUES
    ('짜장면',      8000,  5,  1, 0, NOW(), NOW()),
    ('짬뽕',       10000,  5,  1, 0, NOW(), NOW()),
    ('탕수육',     18000,  0,  1, 1, NOW(), NOW()),
    ('군만두',      6000,  20, 0, 0, NOW(), NOW()),
    ('한정판 세트', 35000,  1,  1, 0, NOW(), NOW());