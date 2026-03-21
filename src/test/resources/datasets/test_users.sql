-- Clean table
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE receipt_images;
TRUNCATE TABLE transactions;
TRUNCATE TABLE refresh_tokens;
TRUNCATE TABLE categories;
TRUNCATE TABLE accounts;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- Password for all users is "12345678"
-- Hash: $2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC

INSERT INTO users (id, full_name, email, password, is_activated, currency_code, created_at, updated_at)
VALUES
    (1, 'Active User', 'test@gmail.com', '$2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC', true, 'USD', NOW(), NOW()),
    (2, 'Inactive User', 'inactive@gmail.com', '$2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC', false, 'EUR', NOW(), NOW()),
    (3, 'Admin User', 'admin@mtracker.com', '$2a$12$1b5AfGkfkGpDREqtic/Mh.g.ilnMjh08WF3ihHm3wp/5SJtR7nmjC', true, 'UAH', NOW(), NOW());

INSERT INTO accounts (id, user_id, balance)
VALUES (1, 1, 0.00),
       (2, 2, 0.00),
       (3, 3, 0.00);

UPDATE users
SET default_account_id = CASE id
                             WHEN 1 THEN 1
                             WHEN 2 THEN 2
                             WHEN 3 THEN 3
    END
WHERE id IN (1, 2, 3);

-- Add a valid refresh token for user 1 (Expires in 2030)
INSERT INTO refresh_tokens (id, token, expiry_date, user_id)
VALUES (1, 'existing-refresh-token-uuid', '2030-01-01 00:00:00', 1);
