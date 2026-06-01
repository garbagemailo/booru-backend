# Booru Backend

Бэкэнд, вдохновлённый Danbooru, для Android‑клиента.

## Стек

- Kotlin 2.2  
- Ktor 3.4  
- Exposed 1.2  
- PostgreSQL 16  
- Flyway  
- HikariCP  

## Структура проекта

- `src/main/kotlin/ru/hwaarn/booru/Application.kt` — bootstrap приложения  
- `config/` — конфигурация и провайдер JWT  
- `db/` — настройка datasource и таблицы Exposed  
- `repository/` — слой персистентности  
- `service/` — бизнес‑логика аутентификации/загрузки  
- `routes/` — маршруты REST API  
- `src/main/resources/db/migration/` — миграции Flyway  
- `src/main/resources/openapi/documentation.yaml` — описание API  

## Запуск с Docker Compose

```bash
cp .env.example .env
# измените JWT_SECRET перед первым запуском
docker compose up --build
```

Бэкэнд будет доступен по адресам:

- `http://localhost:8080/health`  
- `http://localhost:8080/swagger`  
- `http://localhost:8080/openapi`  
- `http://localhost:8080/media/...`  

## Локальный запуск без Docker

Требования:

- JDK 21  
- PostgreSQL 16+  
- Gradle 8+

Переменные окружения:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/booru
export DATABASE_USER=booru
export DATABASE_PASSWORD=booru
export JWT_SECRET=change_me_now
export PUBLIC_BASE_URL=http://localhost:8080
export UPLOAD_ROOT=./data/uploads
export AUTO_APPROVE_MEMBERS=false
```

Запуск:

```bash
gradle run
```

## Основной API

### Аутентификация

- `POST /api/v1/auth/register`  
- `POST /api/v1/auth/login`  
- `POST /api/v1/auth/refresh`  
- `POST /api/v1/auth/logout`  

### Посты

- `GET /api/v1/posts?tags=cat rating:safe order:score`  
- `GET /api/v1/posts/{id}`  
- `GET /api/v1/posts/count?tags=...`  
- `POST /api/v1/posts/upload` (multipart, auth)  
- `PATCH /api/v1/posts/{id}` (auth)  
- `POST /api/v1/posts/{id}/favorite` (auth)  
- `DELETE /api/v1/posts/{id}/favorite` (auth)  
- `POST /api/v1/posts/{id}/vote` (auth)  
- `DELETE /api/v1/posts/{id}/vote` (auth)  
- `GET /api/v1/posts/{id}/versions` (auth)  

### Теги

- `GET /api/v1/tags`  
- `POST /api/v1/tags/aliases` (moderator+)  
- `POST /api/v1/tags/implications` (moderator+)  

### Комментарии

- `GET /api/v1/comments?postId={id}`  
- `POST /api/v1/comments` (auth)  
- `PATCH /api/v1/comments/{id}` (auth)  
- `DELETE /api/v1/comments/{id}` (auth)  

### Пулы

- `GET /api/v1/pools`  
- `GET /api/v1/pools/{id}`  
- `POST /api/v1/pools` (auth)  
- `PATCH /api/v1/pools/{id}` (auth)  
- `DELETE /api/v1/pools/{id}` (moderator+)  

### Wiki

- `GET /api/v1/wiki_pages`  
- `GET /api/v1/wiki_pages/{id}`  
- `GET /api/v1/wiki_pages/{id}/versions`  
- `POST /api/v1/wiki_pages` (auth)  
- `PATCH /api/v1/wiki_pages/{id}` (auth)  

### Артисты

- `GET /api/v1/artists`  
- `GET /api/v1/artists/{id}`  
- `POST /api/v1/artists` (auth)  
- `PATCH /api/v1/artists/{id}` (auth)  
- `DELETE /api/v1/artists/{id}` (moderator+)  

### Модерация

- `GET /api/v1/moderation/queue` (janitor+)  
- `GET /api/v1/moderation/flags` (janitor+)  
- `GET /api/v1/moderation/appeals` (janitor+)  
- `POST /api/v1/moderation/flags` (auth)  
- `POST /api/v1/moderation/appeals` (auth)  
- `POST /api/v1/moderation/posts/{id}/approve` (janitor+)  
- `POST /api/v1/moderation/posts/{id}/reject` (moderator+)  
- `POST /api/v1/moderation/posts/{id}/delete` (moderator+)  
- `POST /api/v1/moderation/posts/{id}/restore` (moderator+)  

## Тестирование

В проект добавлены smoke‑ и unit‑тесты для хеширования, парсера поисковых запросов и вспомогательных функций хранилища.  

Локальный запуск:

```bash
gradle test
```
