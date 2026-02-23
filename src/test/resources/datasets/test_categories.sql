-- Create Users
INSERT INTO users (id, full_name, email, password, is_activated, currency_code, created_at, updated_at)
VALUES
    (1, 'Active User', 'test@gmail.com', '$2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC', true, 'USD', NOW(), NOW()),
    (2, 'Inactive User', 'inactive@gmail.com', '$2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC', false, 'EUR', NOW(), NOW()),
    (3, 'Admin User', 'admin@mtracker.com', '$2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC', true, 'UAH', NOW(), NOW());

-- Global Categories (user_id IS NULL)
INSERT INTO categories (id, name, type, status, user_id, created_at, updated_at)
VALUES (1, 'Salary', 'INCOME', 'ACTIVE', NULL, NOW(), NOW()),
       (2, 'Rent', 'EXPENSE', 'ACTIVE', NULL, NOW(), NOW());

-- User-Specific Categories
INSERT INTO categories (id, name, type, status, user_id, created_at, updated_at)
VALUES (3, 'Side Project', 'INCOME', 'ACTIVE', 1, NOW(), NOW()), -- Owned by user@test.com
       (4, 'Secret Hobby', 'EXPENSE', 'ACTIVE', 2, NOW(), NOW()); -- Owned by other@test.com