-- inventory-service 시드 데이터
-- data.sql 또는 @Sql 어노테이션으로 테스트 시 실행

INSERT INTO products (name, price, stock_quantity, is_displayed, is_deleted, created_at, updated_at)
VALUES
    ('짜장면', 8000.00, 5, true, false, NOW(), NOW()),
    ('짬뽕',  10000.00, 5, true, false, NOW(), NOW());