# Сервис обработки заявок

REST API для регистрации и обработки обращений технической поддержки. Java реализует операции с заявками и историю статусов; Kotlin — сводную статистику. Приложение развёртывается одним сервисом с PostgreSQL.

## Технологии

Java 21, Spring Boot 3.5.7, Kotlin 2.1.21, PostgreSQL 17.6, Flyway, Gradle Wrapper 8.14.3, JUnit 5, Mockito, Testcontainers, JaCoCo, OpenAPI, Docker Compose и GitHub Actions. Полный перечень: [docs/versions.md](docs/versions.md).

## Запуск

Требования: Docker Engine 25+, Docker Compose v2, Git, Python 3 и доступ в интернет для первоначальной сборки. Для сборки вне контейнера нужен JDK 21. Рекомендуемые ресурсы ВМ: 4 vCPU, 6 ГБ RAM, 25 ГБ диска.

Установка инструментов на Kali Linux:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose openjdk-21-jdk curl python3 openssl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

После изменения групп повторно войти в сеанс. Проверить `docker info` и `docker compose version`. Если Compose v2 доступен как `docker-compose`, подключить его как CLI-плагин:

```bash
sudo install -d /usr/local/lib/docker/cli-plugins
sudo ln -sf /usr/bin/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
```

Получение исходников и запуск:

```bash
git clone https://github.com/Zuika1166/practika_ychhhh.git
cd practika_ychhhh
bash scripts/init-env.sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
python3 scripts/smoke.py
```

Скрипт init-env.sh создаёт .env со случайным паролем PostgreSQL. Существующий файл сохраняется. .env.example содержит имена переменных без секретов. Миграции применяются при старте; данные хранятся в именованном томе postgres_data.

- Swagger UI: http://127.0.0.1:8080/swagger-ui/index.html
- OpenAPI: http://127.0.0.1:8080/v3/api-docs
- Readiness: http://127.0.0.1:8080/actuator/health/readiness
- Версия приложения: http://127.0.0.1:8080/actuator/info

Порт по умолчанию доступен на loopback. APP_BIND и APP_PORT задаются в .env. BASE_URL задаёт адрес для проверочных Python-скриптов. При запуске в VirtualBox адрес 127.0.0.1 относится к гостевой ОС; для доступа с хоста требуется настройка проброса порта и соответствующий APP_BIND.

Остановка и повторный запуск с сохранением БД:

```bash
docker compose down
docker compose up -d --no-build --wait --wait-timeout 180
```

Параметр `down -v` удаляет том БД. Изменение DB_PASSWORD в .env не меняет пароль уже созданной базы.

## API и демонстрационные данные

Контракт и примеры запросов: [docs/api.md](docs/api.md). [OpenAPI JSON](api/openapi.json) и [коллекция Postman](api/helpdesk.postman_collection.json) входят в репозиторий.

Категории: GET /api/categories. Создание: POST /api/tickets. Изменение статуса: PATCH /api/tickets/{id}/status. История: GET /api/tickets/{id}/history. Поиск и пагинация: GET /api/tickets. Статистика: GET /api/reports/summary?from=...&to=....

Разрешены NEW→IN_PROGRESS→RESOLVED и отмена из NEW/IN_PROGRESS. Терминальные заявки нельзя редактировать и открывать повторно. Все показатели отчёта используют выборку созданных заявок [from,to); среднее время решения считается только для RESOLVED в секундах.

```bash
python3 scripts/seed-demo.py
```

Генератор создаёт демонстрационные заявки через API. Повторный запуск добавляет новые записи. Коллекция Postman выполняется по порядку; id созданной заявки сохраняется автоматически.

## Сборка и тестирование

На Linux amd64 выбрать установленный JDK 21 и выполнить:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon spotlessCheck build
```

Gradle запускается через Wrapper. Testcontainers создаёт отдельную PostgreSQL для интеграционных тестов. Рабочая база приложения в этих тестах не используется. Spotless проверяет Java и Kotlin; исправление форматирования: `./gradlew spotlessApply`.

Отчёты текущей сборки: `build/reports/tests/test/index.html`, `build/reports/jacoco/test/html/index.html`, `build/test-results/test`. Зафиксированные результаты: [evidence/reports/index.html](evidence/reports/index.html). Всего 56 тестов, включая 8 на Kotlin; 15 интеграционных методов включают два сквозных сценария.

Для разработки с локальным JVM-процессом compose.dev.yaml публикует PostgreSQL на 127.0.0.1:5432. Перед bootRun освободить порт приложения:

```bash
docker compose stop app
docker compose -f compose.yaml -f compose.dev.yaml up -d db
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://127.0.0.1:5432/$DB_NAME"
./gradlew bootRun
```

## Резервное копирование и восстановление

Из рабочего каталога Compose:

```bash
bash scripts/backup.sh backups/manual.dump
bash scripts/restore.sh backups/manual.dump helpdesk_restore_check
```

pg_dump создаёт custom-format архив. restore создаёт отдельную новую базу и отказывается перезаписывать рабочую. Для повторного восстановления выбрать новое имя базы.

```bash
python3 scripts/verify-storage.py
```

Проверка создаёт заявку, пересоздаёт контейнеры без удаления тома, сверяет заявку и историю, делает backup, восстанавливает в отдельную базу и сравнивает все строки categories, tickets и status_history. Ожидается PASS, restartPreservedData=true и allTablesMatch=true. На время проверки сервис кратковременно останавливается.

## CI/CD и эксплуатация

[GitHub Actions workflow](.github/workflows/ci-cd.yaml) выполняет verify → publish → deploy. verify проверяет форматирование, собирает приложение, запускает тесты и публикует отчёты. После успешного слияния в main publish собирает образ с тегом полного SHA и отправляет его в GHCR; deploy запускает образ на self-hosted runner и проверяет readiness и smoke. PR проходит проверки без публикации и доставки. Ошибка verify блокирует следующие jobs.

Параметры учебного стенда:

| Параметр | Значение |
| --- | --- |
| Runner labels | self-hosted, linux, x64, helpdesk-kali |
| GitHub environment | training |
| Variable STAND_DIR | /opt/helpdesk |
| Secret DB_PASSWORD | Пароль PostgreSQL стенда |
| Registry | ghcr.io/zuika1166/practika_ychhhh |

Runner регистрируется через Settings → Actions → Runners и должен иметь доступ к Docker. Каталог STAND_DIR должен принадлежать пользователю runner. Для новой БД пароль создаётся локально, например `openssl rand -hex 24`; для существующего тома требуется его действующий пароль. Токен GHCR передаётся через GITHUB_TOKEN. .env сохраняется между доставками. Локальный запуск и CD с именем Compose-проекта helpdesk используют один том; при переносе конфигурации между каталогами необходимо сохранить .env.

```bash
sudo install -d -o "$USER" -g "$(id -gn)" /opt/helpdesk
```

Локальная последовательность проверки, сборки и доставки без публикации в registry:

```bash
bash scripts/local-pipeline.sh
```

Фактические pipeline, SHA, digest образов и результаты проверок: [docs/submission.md](docs/submission.md).

## Возврат предыдущего образа

Команды выполняются из каталога стенда. Для приватного образа, отсутствующего локально, требуется авторизация в GHCR.

```bash
cd /opt/helpdesk
bash scripts/deploy.sh ghcr.io/zuika1166/practika_ychhhh:60a2ce4259cedce23de43eae00e409c6b3b4e04b
curl --fail http://127.0.0.1:8080/actuator/info
bash scripts/rollback.sh ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2
curl --fail http://127.0.0.1:8080/actuator/info
python3 scripts/smoke.py
```

Без явного аргумента rollback использует .deploy/previous-image. deploy сохраняет текущий образ в .deploy/current-image и .env. При неуспешном health check или smoke выполняется попытка возврата прежнего образа; доставка завершается ошибкой. flock защищает от одновременного развёртывания.

Откат образа сохраняет данные и схему БД. Миграции должны быть совместимы с предыдущей версией. В двух проверенных релизах схема одинаковая; версия practice-2 добавляет метку релиза в /actuator/info. Восстановление данных из backup выполняется отдельно.

## Диагностика

### База недоступна

```bash
docker compose ps
docker compose logs --tail=100 db app
docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Проверить доступность db, свободное место, DB_URL, пользователя и соответствие пароля существующего тома. После исправления конфигурации пересоздать app: `docker compose up -d --force-recreate app`.

### Приложение не проходит health check

```bash
docker compose logs --tail=150 app
docker inspect --format '{{json .State.Health}}' "$(docker compose ps -q app)"
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

Проверить запуск JVM, доступ к БД, память и порт. Ответ 503 означает неготовность; connection refused — отсутствие доступного сервиса по указанному адресу. При изменении порта задать соответствующий BASE_URL проверочным скриптам; workflow использует стандартный порт 8080.

### Ошибка миграции

```bash
docker compose logs --tail=200 app
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

Проверить SQL, права и checksum. Применённые файлы миграций сохраняются в исходном виде; исправления схемы оформляются новой миграцией. Перед ручной коррекцией сделать backup и проверить изменения на восстановленной копии. Рабочий том сохраняется.

## Материалы проекта

| Материал | Файл |
| --- | --- |
| Спецификация, истории, критерии, процесс, ER-диаграмма | [docs/specification.md](docs/specification.md) |
| Версии | [docs/versions.md](docs/versions.md) |
| Backlog и план трудозатрат | [docs/backlog.md](docs/backlog.md) |
| Контракт API | [docs/api.md](docs/api.md) |
| Тест-план и трассировка | [docs/test-plan.md](docs/test-plan.md) |
| Дефекты и регрессионная проверка | [docs/defects.md](docs/defects.md) |
| Отчёт, Kotlin, результаты и ограничения | [docs/report.md](docs/report.md) |
| Развёртывание и откат на учебном стенде | [docs/submission.md](docs/submission.md) |
| JUnit и JaCoCo | [evidence/reports/index.html](evidence/reports/index.html) |
| Протокол проверки Kali Linux | [evidence/kali-verification.json](evidence/kali-verification.json) |
