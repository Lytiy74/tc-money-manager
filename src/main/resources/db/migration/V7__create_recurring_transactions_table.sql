CREATE TABLE recurring_transactions
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT         NOT NULL,
    account_id          BIGINT         NOT NULL,
    category_id         BIGINT         NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    type                VARCHAR(16)    NOT NULL,
    start_date          DATE           NOT NULL,
    description         VARCHAR(255),
    next_execution_date DATE           NOT NULL,
    interval_unit       VARCHAR(16)    NOT NULL,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recurring_transaction_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_recurring_transaction_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_recurring_transaction_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
