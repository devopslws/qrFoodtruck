-- inventory-service 시드 데이터
-- data.sql 또는 @Sql 어노테이션으로 테스트 시 실행

INSERT INTO stock (name, price, quantity, displayed, deleted, created_at, updated_at) VALUES ('짜장면', 8000, 5, 1, 0, NOW(), NOW());
INSERT INTO stock (name, price, quantity, displayed, deleted, created_at, updated_at) VALUES ('짬뽕', 10000, 5, 1, 0, NOW(), NOW());
INSERT INTO stock (name, price, quantity, displayed, deleted, created_at, updated_at) VALUES ('탕수육', 18000, 0, 1, 0, NOW(), NOW());
INSERT INTO stock (name, price, quantity, displayed, deleted, created_at, updated_at) VALUES ('군만두', 6000, 20, 0, 0, NOW(), NOW());
INSERT INTO stock (name, price, quantity, displayed, deleted, created_at, updated_at) VALUES ('한정판 세트', 35000, 1, 1, 0, NOW(), NOW());