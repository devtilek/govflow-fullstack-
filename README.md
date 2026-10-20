# GovFlow

![CI](https://github.com/devtilek/govflow-fullstack-/actions/workflows/ci.yml/badge.svg)

Учебный fullstack-проект для работы с обращениями: создание заявки, поиск,
фильтрация и последовательное изменение статуса.
Не связан с государственными органами.

**Стек:** Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, H2 / PostgreSQL,
React 18, TypeScript и Vite.

## Быстрый запуск без Docker

Нужны Java 17+, Maven 3.9+ и Node.js 22.

В первом терминале из корня репозитория:

```bash
cd backend
mvn spring-boot:run
```

Во втором:

```bash
cd frontend
npm ci
npm run dev
```

Откройте http://localhost:5173. Vite перенаправляет запросы `/api` на
http://localhost:8080. Backend по умолчанию слушает только локальный интерфейс.
H2 хранит данные в `backend/data/`: обращения сохраняются после перезапуска.
Первый запуск Flyway создаёт схему; Hibernate проверяет её соответствие сущностям.

Если backend не запущен, интерфейс показывает ошибку и кнопку повторной загрузки.

## Запуск с PostgreSQL

```bash
cp .env.example .env
# Установите свой POSTGRES_PASSWORD в .env
docker compose up --build -d
cd frontend
npm ci
npm run dev
```

В PowerShell вместо `cp` можно использовать `Copy-Item .env.example .env`.
PostgreSQL доступен только внутри Docker-сети; данные находятся в томе `pgdata`.
Остановить сервер и базу без удаления данных: `docker compose down`.
После изменений Java-кода повторите `docker compose up --build -d`.

## REST API

| Метод | Путь | Результат |
| --- | --- | --- |
| GET | /api/requests | Массив обращений, новые первыми |
| GET | /api/requests?status=NEW | Фильтр по статусу |
| GET | /api/requests/page?page=0&size=20&q=light&status=NEW | Поиск и постраничный список |
| GET | /api/requests/stats | Общие счётчики по статусам |
| GET | /api/requests/{id} | Одно обращение или 404 |
| POST | /api/requests | Создание обращения, HTTP 201 |
| PATCH | /api/requests/{id}/status | Изменение статуса |

Создание (Bash; в PowerShell используйте `curl.exe` и подходящее экранирование):

```bash
curl -i http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' \
  -d '{"title":"Освещение улицы","description":"Не работает фонарь возле дома 12"}'
```

Изменение статуса (замените 1 на ID созданного обращения):

```bash
curl -X PATCH http://localhost:8080/api/requests/1/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}'
```

Затем можно установить `RESOLVED`. Допустимы только переходы
`NEW → IN_PROGRESS → RESOLVED`; повтор текущего статуса идемпотентен.
Пропуск этапов и возврат назад дают HTTP 409.
Несуществующий ID даёт 404, неверный JSON и ошибки полей — 400.

Название обязательно и не длиннее 120 символов; описание обязательно и не
длиннее 2000. Строки из пробелов отклоняются. ID, начальный статус и дата
создания назначаются сервером. Неизвестные поля JSON отклоняются.

Формат ошибки:

```json
{"status":400,"message":"Check the request fields","fields":{"title":"must not be blank"}}
```

## Поиск и страницы

Интерфейс загружает по 20 обращений; можно выбрать 10 или 50.
Поиск выполняется сервером по названию и описанию через 300 мс после ввода.
Изменение поиска, статуса или размера страницы возвращает на первую страницу.
Карточки показывают общие счётчики по всей базе независимо от поиска.
После создания формы и фильтры сбрасываются; после смены статуса список и
счётчики перечитываются. Если последняя страница опустела, открывается предыдущая.

Параметры `GET /api/requests/page`:

| Параметр | По умолчанию | Ограничение |
| --- | --- | --- |
| page | 0 | Целое число от 0 |
| size | 20 | От 1 до 100 |
| q | Пустая строка | До 200 символов после удаления пробелов по краям |
| status | Все статусы | NEW, IN_PROGRESS или RESOLVED |

Поиск не учитывает регистр. Символы `%` и `_` ищутся буквально.
Сортировка: сначала новые по `createdAt`, при одинаковой дате — по ID убыванию.
Ответ включает `items`, `page`, `size`, `totalElements`, `totalPages`.
Для пустого результата `totalPages` равно 0; страница за пределами списка
возвращает пустой `items` с общим количеством результатов.

`GET /api/requests/stats` возвращает `total`, `newCount`, `inProgress`, `resolved`.
Счётчики рассчитываются одним агрегирующим запросом, без загрузки всех сущностей.
Список и статистика запрашиваются отдельно, поэтому при одновременных изменениях
другим клиентом они могут кратковременно отличаться; кнопка «Обновить» перечитает оба.

Старый `GET /api/requests` оставлен для совместимости. Он возвращает полный
массив без ограничения размера; интерфейс использует новый `/page`.

## Проверки

```bash
cd backend
mvn -B -ntp verify
```

```bash
cd frontend
npm ci
npm test
npm run build
```

GitHub Actions запускает тесты API на H2 и PostgreSQL 16, собирает JAR
и проверяет frontend-тесты, TypeScript и production-сборку интерфейса.
Тесты покрывают жизненный цикл, сохранение, фильтры, граничные длины,
пустые поля, некорректный JSON, статусы, ID и недопустимые переходы.

## Структура

- `backend/src/main/java/kz/govflow/` — controller, DTO, service, entity, repository, обработчик ошибок.
- `backend/src/main/resources/db/migration/` — версионируемая схема.
- `backend/src/test/` — интеграционные и доменные тесты.
- `frontend/src/api.ts` — типизированный HTTP-клиент.
- `frontend/src/App.tsx` — интерфейс обращений.
- `docker-compose.yml` — PostgreSQL и Java API.

## Настройки backend

| Переменная | По умолчанию |
| --- | --- |
| SERVER_ADDRESS | 127.0.0.1 |
| PORT | 8080 |
| DATABASE_URL | Файловая H2 в ./data/govflow |
| DATABASE_USER | sa |
| DATABASE_PASSWORD | Пустая строка |

`DATABASE_URL` должен быть JDBC URL, например
`jdbc:postgresql://localhost:5432/govflow`.
Для тестов на внешней базе используются `TEST_DATABASE_URL`,
`TEST_DATABASE_USER` и `TEST_DATABASE_PASSWORD`.
**Тесты удаляют все обращения: указывайте только отдельную тестовую базу.**

## Границы проекта

Это локальный демонстрационный MVP без авторизации и ролей.
Любой пользователь, имеющий доступ к серверу, может создавать обращения
и менять статусы. Устаревший маршрут полного списка пока доступен.
Для публичного размещения нужны авторизация, разграничение доступа,
лимиты запросов, отключение устаревшего полного списка и настройка HTTPS.
При конкурентном изменении одной записи optimistic locking возвращает 409;
клиенту нужно обновить список и повторить действие.
