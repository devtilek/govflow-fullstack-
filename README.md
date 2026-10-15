# GovFlow — Fullstack Java + React

Portfolio project for managing service requests, inspired by enterprise workflow systems (not affiliated with any government agency).

## Stack
Java 17, Spring Boot 3, PostgreSQL, React, TypeScript, Vite, Docker Compose.

## Local setup
1. Run `docker compose up -d` for PostgreSQL.
2. Run `cd backend && mvn spring-boot:run` (Java 17 + Maven required).
3. Frontend will be added in the next milestone.

REST endpoints: `GET /api/requests`, `POST /api/requests`, `PATCH /api/requests/{id}/status`.
