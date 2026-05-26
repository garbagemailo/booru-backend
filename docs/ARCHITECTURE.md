# Архитектура backend-а

## Стек

- Ktor Server
- PostgreSQL
- Exposed
- Flyway
- JWT access/refresh auth
- Docker Compose

## Слои

- `routes` — HTTP API и авторизационные проверки.
- `service` — бизнес-логика auth/upload.
- `repository` — доступ к БД.
- `db` — конфигурация datasource и таблицы Exposed.
- `model` — сериализуемые DTO/API-модели.
- `util` — поиск, хранение файлов, hashing.

## Danbooru-inspired функции

- посты и загрузки;
- теги, aliases, implications;
- favorites и votes;
- comments и notes;
- pools;
- wiki pages + versions;
- artists;
- moderation queue, flags, appeals;
- user bans и audit log.

## Что важно подчеркнуть на защите

1. Сервер полностью отдельный от мобильного клиента.
2. Авторизация сделана через JWT.
3. Данные лежат в удаленной PostgreSQL.
4. Схема БД накатывается миграциями Flyway.
5. Первый зарегистрированный пользователь становится ADMIN для bootstrap.
