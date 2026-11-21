-- PostgreSQL 初始化脚本
-- 运行前请先把数据库/用户建好（docker-compose 里已建好，DB 名 ds / 用户 ds / 密码 ds123456）
-- psql -h 127.0.0.1 -U ds -d ds -f init.sql

SET client_min_messages = NOTICE;
CREATE SCHEMA IF NOT EXISTS ds;
SET search_path TO ds, public;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64) NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64),
    role        VARCHAR(16) NOT NULL DEFAULT 'USER',
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE t_user IS '用户';

-- 商品表
CREATE TABLE IF NOT EXISTS t_product (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(64),
    price       NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock       INTEGER NOT NULL DEFAULT 0,
    description VARCHAR(512),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE t_product IS '商品';
CREATE INDEX IF NOT EXISTS idx_product_category ON t_product(category);

-- 初始数据
INSERT INTO t_user(username, password, nickname, role)
VALUES ('admin',
        '$2a$10$7xKQyQ5XQqB4qLQ3qJqXueZ6z6zZ6z6z6z6z6z6z6z6z6z6z6z6zG',
        '管理员', 'ADMIN'),
       ('user1',
        '$2a$10$7xKQyQ5XQqB4qLQ3qJqXueZ6z6zZ6z6z6z6z6z6z6z6z6z6z6zG',
        '用户一', 'USER')
ON CONFLICT DO NOTHING;

INSERT INTO t_product(name, category, price, stock, description)
VALUES ('示例商品 A', '演示', 99.00, 100, '用于联调的占位商品'),
       ('示例商品 B', '演示', 199.00, 50, '另一个占位商品'),
       ('示例商品 C', '演示', 299.00, 10, '库存偏低')
ON CONFLICT DO NOTHING;
