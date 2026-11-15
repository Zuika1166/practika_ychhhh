# Сервис обработки заявок

Один REST-сервис на Java 21 и Spring Boot с работающим блоком статистики на Kotlin. PostgreSQL, Flyway, Gradle Wrapper, Swagger UI, Docker Compose, JUnit/Mockito/Testcontainers, JaCoCo, GitHub Actions.

## Запуск в VirtualBox с Kali Linux

Рекомендуется выделить ВМ 4 ядра, 6 ГБ RAM и 25 ГБ диска. Достаточен сетевой режим NAT. Аппаратная вложенная виртуализация для обычных Docker-контейнеров не требуется. Команды ниже выполняются в терминале Kali внутри ВМ. Для первого запуска нужен интернет.

Установить инструменты из репозиториев Kali:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose openjdk-21-jdk git curl python3 unzip
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Выйти из сеанса Kali и войти снова, затем проверить:

```bash
docker info
docker compose version
java -version
```

Нужны Docker Engine 25 или новее, Compose v2 и Java 21. Если пакет установил Compose v2 как `docker-compose`, а `docker compose` ещё не виден, проверить `docker-compose version`. Для версии v2 подключить установленный бинарный файл как плагин:

```bash
sudo install -d /usr/local/lib/docker/cli-plugins
sudo ln -sf /usr/bin/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

Скопировать helpdesk-kali.zip в ВМ через общую папку VirtualBox или загрузить из браузера Kali. Распаковать в домашний каталог Kali, чтобы сборка выполнялась на файловой системе гостевой ОС:

```bash
unzip helpdesk-kali.zip -d "$HOME/practice"
cd "$HOME/practice/helpdesk"
chmod +x gradlew scripts/*.sh
bash scripts/init-env.sh
docker compose up -d --build --wait --wait-timeout 180
```

`init-env.sh` создаёт .env с локальным случайным паролем. Существующий .env не перезаписывается. .env.example содержит пустой пароль; его не нужно заполнять перед использованием генератора. Первичная сборка загружает Gradle, зависимости и образы, поэтому занимает несколько минут.

Открыть в Firefox внутри Kali:

- Swagger UI: http://127.0.0.1:8080/swagger-ui/index.html
- Схема OpenAPI: http://127.0.0.1:8080/v3/api-docs
- Готовность с проверкой БД: http://127.0.0.1:8080/actuator/health/readiness

```bash
docker compose ps
python3 scripts/smoke.py
python3 scripts/seed-demo.py
```

Smoke создаёт заявку, проводит её до RESOLVED, проверяет историю и отчёт, затем подтверждает 409 и неизменность данных при запрещённых операциях. seed-demo добавляет примеры с разными статусами и сроками; повторный запуск добавляет новые демонстрационные заявки.

Если браузер нужен на основной ОС ПК, остановить контейнеры, установить в .env `APP_BIND=0.0.0.0`, затем снова выполнить `docker compose up -d --wait`. В VirtualBox открыть настройки ВМ → Сеть → NAT → Дополнительно → Проброс портов: протокол TCP, IP хоста 127.0.0.1, порт хоста 18080, IP гостя пустой, порт гостя 8080. На основной ОС открыть http://127.0.0.1:18080/swagger-ui/index.html. Для возврата к доступу только из Kali установить APP_BIND=127.0.0.1 и пересоздать приложение.

Остановка с сохранением данных:

```bash
docker compose down
```

Повторный запуск собранного образа:

```bash
docker compose up -d --no-build --wait --wait-timeout 180
```

Не добавлять `-v` к down, если данные должны сохраниться: этот параметр удаляет том PostgreSQL.

## Разработка и проверка

```bash
./gradlew spotlessCheck build
```

На машине должны работать Java 21 и Docker. Gradle установленный в системе не нужен. Testcontainers автоматически создаёт PostgreSQL 17.6. Тесты не пропускаются, если Docker недоступен. Java и Kotlin проверяются Spotless; исправление форматирования: `./gradlew spotlessApply`.

Отчёты текущей сборки:

- build/reports/tests/test/index.html — JUnit.
- build/reports/jacoco/test/html/index.html — покрытие Java и Kotlin.
- build/test-results/test — XML для CI.

Проверенные отчёты из комплекта: evidence/reports/index.html. В поставку входят XML JUnit/JaCoCo и статические HTML-таблицы результатов без внешних ресурсов. Сквозные сценарии присутствуют в ApiIntegrationTest и scripts/smoke.py. Тесты используют Clock, создают собственные данные и работают независимо от календарной даты.

Для запуска из JAR сначала поднять только БД с временным локальным портом через compose.dev.yaml:

```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d db
set -a
. ./.env
set +a
export DB_URL=jdbc:postgresql://127.0.0.1:5432/helpdesk
./gradlew bootRun
```

В поставляемой конфигурации DB_NAME=helpdesk; при изменении имени базы скорректировать DB_URL. Перед bootRun остановить контейнер app командой `docker compose stop app`, если он занимает порт 8080. Режим разработки не нужен для обычного запуска.

## Использование API

В Swagger выбрать POST /api/tickets → Try it out. Получить categoryId через GET /api/categories. Пример тела:

```json
{"title":"Не работает принтер","description":"Кабинет 104","categoryId":1,"priority":"HIGH","dueAt":"2030-01-20T16:00:00Z"}
```

Статус: PATCH /api/tickets/{id}/status, сначала `{"status":"IN_PROGRESS"}`, затем `{"status":"RESOLVED"}`. Отмена `{"status":"CANCELLED"}` разрешена из NEW и IN_PROGRESS. Завершённые и отменённые заявки нельзя изменить или повторно открыть.

Отчёт: GET /api/reports/summary?from=2020-01-01T00:00:00Z&to=2100-01-01T00:00:00Z. Для всех метрик берутся заявки, созданные в [from,to); среднее — только для RESOLVED. Единица среднего — секунды с дробной частью. Текущее состояние и просрочка определяются на момент запроса.

Коллекцию api/helpdesk.postman_collection.json импортировать в Postman и выполнить по порядку или через Collection Runner. Переменная ticketId заполняется после создания. Коллекция проверяет коды ответа и неизменность данных после запрета. Файл api/requests.http содержит отдельные примеры запросов; в нём нужно подставить полученный id. Полный контракт — docs/api.md.

## Резервное копирование и восстановление

```bash
bash scripts/backup.sh
bash scripts/backup.sh backups/manual.dump
bash scripts/restore.sh backups/manual.dump helpdesk_restore_check
```

pg_dump создаёт custom-format архив с правами пользователя. При ошибке не остаётся файл, выглядящий как успешная копия. restore создаёт новую отдельную базу и отказывается восстанавливать поверх рабочей. Если имя уже существует, выбрать новое.

Полная проверка тома и восстановления на учебном стенде:

```bash
python3 scripts/verify-storage.py
```

Сценарий создаёт заявку, останавливает и заново создаёт контейнеры без удаления тома, сравнивает карточку и историю, делает dump, восстанавливает в отдельную базу и сравнивает полное содержимое categories, tickets и status_history. Ожидается result=PASS, restartPreservedData=true, allTablesMatch=true. Во время проверки приложение кратковременно недоступно. Отдельную базу можно изучить через `docker compose exec db psql -U helpdesk -d <имя>`.

Для переноса восстановленной базы в эксплуатацию администратор отдельно останавливает приложение, меняет DB_NAME/DB_URL на проверенную базу и запускает сервис. Автоматическая замена рабочей БД в restore не выполняется.

## Диагностика

### База недоступна

```bash
docker compose ps
docker compose logs --tail=100 db
docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose logs --tail=100 app
```

Проверить состояние db, свободное место, имя сервиса db в JDBC URL и согласованность DB_USER/DB_PASSWORD. Пароль существующего тома PostgreSQL не меняется от правки .env. При необходимости сменить пароль SQL-командой через администратора БД и согласовать .env. Данные ради исправления соединения не удалять. После исправления: `docker compose up -d --force-recreate app`.

### Приложение не проходит health check

```bash
docker compose logs --tail=150 app
docker inspect --format '{{json .State.Health}}' "$(docker compose ps -q app)"
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

Проверить отсутствие ошибки старта, доступность БД и достаточную RAM. 503 означает, что приложение ещё не готово; connection refused — что порт не слушается. Если порт 8080 занят, изменить APP_PORT в .env, пересоздать контейнер и передавать BASE_URL при проверках, например `BASE_URL=http://127.0.0.1:8081 python3 scripts/smoke.py`. BASE_URL также используется verify-storage.py и seed-demo.py. Проверки workflow рассчитаны на стандартный порт 8080.

### Миграция завершается ошибкой

```bash
docker compose logs --tail=200 app
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

Проверить сообщение Flyway: доступ, SQL, checksum, конфликт версии. Восстановить оригинал уже применённого файла миграции; исправление схемы оформить новой миграцией. Не запускать flyway clean и не удалять том. Перед ручной коррекцией сделать backup и проверить изменения на восстановленной копии. Не маскировать checksum mismatch автоматическим repair.

## Git и CI/CD на Kali runner

В архиве сохранена локальная история Git с тематическими ветками и merge-коммитами. После копирования настроить собственную подпись для будущих коммитов:

```bash
git config user.name "Ваше имя"
git config user.email "Ваш email"
git log --graph --oneline --all
```

Создать пустой репозиторий GitHub, добавить remote origin с его URL и отправить main и тематические ветки. Для следующего изменения создать ветку, коммит, push и реальный pull request. Шаблон PR включён. История локальных merge-коммитов не заменяет удалённый PR.

Workflow .github/workflows/ci-cd.yaml:

1. Checkout, Java 21, Gradle setup.
2. Spotless для Java/Kotlin, сборка и все тесты.
3. JUnit/JaCoCo artifacts, в том числе при падении тестов.
4. Только для main после успешной проверки: сборка и публикация ghcr.io/<owner>/<repo>:<SHA>.
5. Доставка на runner в ВМ, ожидание readiness, сквозная проверка и публикация протокола.

В GitHub Settings → Actions → Runners → New self-hosted runner выбрать Linux x64 и выполнить выданные GitHub команды внутри Kali от обычного пользователя, имеющего доступ к Docker. При настройке добавить метку helpdesk-kali. ВМ и runner должны работать во время CD; запускать runner как сервис можно по инструкции GitHub. Создать environment training.

Подготовить постоянный каталог стенда:

```bash
sudo mkdir -p /opt/helpdesk
sudo chown "$USER:$(id -gn)" /opt/helpdesk
```

В environment training задать variable STAND_DIR=/opt/helpdesk и secret DB_PASSWORD — новый случайный пароль из шестнадцатеричных символов. Сгенерировать локально: `openssl rand -hex 24`. Не записывать его в репозиторий. Токен GHCR — встроенный secrets.GITHUB_TOKEN, workflow запрашивает нужные permissions. В настройках Actions разрешить запись packages; при существующем GHCR-пакете проверить связь с репозиторием и права доступа.

Не использовать для runner тот же каталог, из которого вручную запущен другой Compose-проект с именем helpdesk. Перед первым CD остановить локальный демонстрационный стек командой `docker compose down` с сохранением тома. Для отдельного чистого учебного стенда задать runner другой Docker-контекст/хост либо явно перенести .env и согласовать пароль существующего тома. Если используется существующий том helpdesk_postgres_data, DB_PASSWORD в secrets должен совпадать с уже установленным паролем этой БД. Созданный CD файл /opt/helpdesk/.env сохраняется между запусками.

Сделать изменение в отдельной ветке, открыть PR, дождаться проверок и слить в main. В Actions должны пройти verify, publish и deploy. Сохранить ссылку запуска и полный образ в docs/submission.md. Публикация без успешного deploy не завершает ТЗ.

Локальный аналог последовательности без публикации в registry:

```bash
bash scripts/local-pipeline.sh
```

Он выполняет проверки, собирает образ с тегом текущего Git SHA, запускает стенд и smoke. Это полезно для подготовки ВМ; внешний GitHub Actions run оформляется отдельно.

## Развёртывание и откат

Из каталога стенда:

```bash
bash scripts/deploy.sh ghcr.io/OWNER/REPOSITORY:FULL_COMMIT_SHA
curl --fail http://127.0.0.1:8080/actuator/info
bash scripts/rollback.sh
```

Перед реальным откатом должны существовать два успешных развёртывания разных версий. deploy сохраняет прежний образ в .deploy/previous-image, текущий — в .deploy/current-image и APP_IMAGE в .env. При ошибке ожидания готовности или smoke скрипт возвращает предыдущий образ и завершает pipeline с ошибкой. Используется flock для защиты от одновременных развёртываний. Явный возврат к известному образу: `bash scripts/rollback.sh <образ>`.

Образы возвращаются, а данные и миграции остаются. Включённые миграции создают базовую схему и категории и совместимы с обеими проверенными сборками. Последующие миграции должны быть добавочными: не удалять/переименовывать столбцы, необходимые предыдущей версии. Старый образ следует сохранять в registry до завершения учебной демонстрации.

## Материалы

- docs/specification.md — требования, истории, критерии, процесс и ER.
- docs/backlog.md — приоритеты и план 72 академических часа.
- docs/api.md, api/ — описание, OpenAPI и коллекция.
- docs/test-plan.md, docs/defects.md, evidence/ — проверки, регрессия и результаты.
- docs/report.md — отчёт и особенности Kotlin.
- docs/defense.md — сценарий защиты на 10–15 минут.
- docs/submission.md — данные реального удалённого pipeline и вашего стенда.

Проверки в комплекте выполнены в Linux-среде подготовки. Доступа к VirtualBox этого ПК, вашему GitHub-аккаунту и runner не было; эти внешние шаги выполняются по инструкции выше и фиксируются в submission.md.
