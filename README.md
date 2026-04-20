# TC Money Manager

Backend API for personal finance management.

## Stack

- Java 21
- Spring Boot
- Maven
- MySQL
- Flyway
- MinIO
- Mailpit

## Project structure

- `auth` - authentication, registration, tokens, password management
- `user` - user profile and account data
- `account` - user accounts
- `category` - transaction categories
- `transaction` - transactions and receipt images
- `security` - JWT and Spring Security configuration

## Run locally

1. Create a `.env` file with project environment variables.
2. Start local services:

```bash
docker compose up -d
```

3. Run the app with the `dev` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Main env variables

- `MT_MYSQL_URL`
- `MT_MYSQL_USERNAME`
- `MT_MYSQL_PASSWORD`
- `MT_JWT_SECRET_KEY`
- `MT_FRONTEND_URL`
- `MT_AWS_ACCESS_KEY_ID`
- `MT_AWS_SECRET_KEY`
- `MT_AWS_S3_BUCKET_NAME`
- `MT_AWS_REGION`

## Links

- Swagger: `http://localhost:8080/swagger`
- API docs: `http://localhost:8080/api-docs`
- Mailpit UI: `http://localhost:8025`
- MinIO console: `http://localhost:9001`

## Tests
