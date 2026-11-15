# Запуск practika_ychhhh в Kali Linux через Git

Репозиторий: https://github.com/Zuika1166/practika_ychhhh

Все команды выполняются в терминале Kali Linux внутри VirtualBox. Для обычного запуска достаточно разделов 1–5. Разделы 6–9 нужны для полного выполнения требований ТЗ к проверкам, CI/CD и эксплуатации.

## 1. Подготовить ВМ

Выделить 4 vCPU, 6 ГБ оперативной памяти и не менее 25 ГБ диска. Сеть — NAT, доступ в интернет обязателен при первоначальной сборке. Вложенная аппаратная виртуализация Docker-контейнерам не требуется.

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose openjdk-21-jdk curl python3 openssl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

После usermod выйти из сеанса Kali и войти снова. Затем:

```bash
docker info
docker compose version
java -version
```

Нужны Docker Engine 25+, Docker Compose v2, JDK 21. Если `docker compose` отсутствует, но `docker-compose version` показывает v2:

```bash
sudo install -d /usr/local/lib/docker/cli-plugins
sudo ln -sf /usr/bin/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

Если `docker-compose version` показывает v1, нужен пакет Compose v2 из актуального репозитория Kali; подключать бинарный файл v1 как плагин не следует. При отсутствии пакетов проверить репозитории и обновление Kali, не добавляя репозиторий Docker для Ubuntu в Kali.

## 2. Склонировать проект

```bash
cd "$HOME"
git clone https://github.com/Zuika1166/practika_ychhhh.git
cd practika_ychhhh
git switch main
chmod +x gradlew scripts/*.sh
```

Репозиторий публичный, для клонирования пароль и токен не нужны. Команды `docker compose` и `scripts/...` далее выполнять из этого каталога. Код желательно хранить на диске Kali, а не запускать сборку непосредственно из общей папки VirtualBox.

Если проект уже склонирован и локальных изменений нет:

```bash
cd "$HOME/practika_ychhhh"
git pull --ff-only
```

## 3. Создать конфигурацию и запустить

```bash
bash scripts/init-env.sh
docker compose up -d --build --wait --wait-timeout 180
```

Пароль PostgreSQL генерируется в .env локально. Существующий .env сохраняется. Не отправлять этот файл в Git. При первой сборке загружаются JDK-образы, PostgreSQL, Gradle и зависимости Java/Kotlin; загрузка может занять несколько минут. Таймаут 180 секунд относится к ожиданию готовности контейнеров после сборки.

Проверить:

```bash
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
python3 scripts/smoke.py
```

Оба контейнера должны быть healthy, readiness — `{"status":"UP"}`, smoke — result PASS. При ошибке:

```bash
docker compose logs --tail=150 app db
```

## 4. Открыть API и показать работу

В Firefox внутри Kali открыть:

http://127.0.0.1:8080/swagger-ui/index.html

В Swagger выполнить:

1. GET /api/categories — получить categoryId.
2. POST /api/tickets — создать заявку и сохранить её id.
3. PATCH /api/tickets/{id}/status — передать IN_PROGRESS.
4. Тем же методом передать RESOLVED.
5. GET /api/tickets/{id}/history — увидеть три записи.
6. Ещё раз передать IN_PROGRESS — получить 409.
7. GET /api/reports/summary с from=2020-01-01T00:00:00Z и to=2100-01-01T00:00:00Z.

Тело POST:

```json
{"title":"Не работает принтер","description":"Кабинет 104","categoryId":1,"priority":"HIGH","dueAt":"2030-01-20T16:00:00Z"}
```

Тело PATCH:

```json
{"status":"IN_PROGRESS"}
```

Для демонстрационных заявок с разными статусами и просрочкой:

```bash
python3 scripts/seed-demo.py
```

Коллекция Postman: api/helpdesk.postman_collection.json. Запускать по порядку: переменная ticketId заполняется автоматически. Среднее время решения в отчёте выражено в секундах; отсутствие завершённых заявок даёт null.

## 5. Остановить, перезапустить и обновить

Остановить с сохранением БД:

```bash
docker compose down
```

Вновь запустить собранный образ:

```bash
docker compose up -d --no-build --wait --wait-timeout 180
```

Получить новый код и пересобрать:

```bash
git pull --ff-only
docker compose up -d --build --wait --wait-timeout 180
python3 scripts/smoke.py
```

При неизменном APP_IMAGE=helpdesk:local это обновляет локальную сборку. Если ранее использовался scripts/deploy.sh, он записал APP_IMAGE с тегом релиза; для возврата к ручной сборке установить в .env APP_IMAGE=helpdesk:local. Для учебного CD использовать раздел 8.

Не выполнять `docker compose down -v`: ключ -v удаляет данные. Правка DB_PASSWORD в .env не меняет пароль уже созданного PostgreSQL-тома.

## 6. Запустить автотесты и открыть покрытие

```bash
./gradlew --no-daemon spotlessCheck build
```

На amd64 при нескольких установленных версиях Java выбрать JDK 21 для этой сессии:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon spotlessCheck build
```

Для этих команд Docker должен быть доступен обычному пользователю. Testcontainers сам создаёт отдельную PostgreSQL; данные рабочего приложения не используются и не удаляются. Ожидается 56 тестов без ошибок.

Открыть в Firefox файлы:

- build/reports/tests/test/index.html
- build/reports/jacoco/test/html/index.html

Для просмотра результатов проверки, поставленных вместе с проектом: evidence/reports/index.html. Форматирование исправляется командой `./gradlew spotlessApply`.

## 7. Доступ из браузера основной ОС

Если браузер используется внутри Kali, этот раздел не требуется.

В .env установить APP_BIND=0.0.0.0. Затем:

```bash
docker compose up -d --force-recreate --wait --wait-timeout 180 app
```

Выключить ВМ для изменения настроек сети, если VirtualBox этого требует. В VirtualBox: Настройки ВМ → Сеть → NAT → Дополнительно → Проброс портов. Добавить TCP: IP хоста 127.0.0.1, порт хоста 18080, IP гостя пустой, порт гостя 8080.

В браузере основной ОС открыть:

http://127.0.0.1:18080/swagger-ui/index.html

Порт PostgreSQL не пробрасывать. Для доступа только из Kali вернуть APP_BIND=127.0.0.1 и пересоздать приложение.

## 8. Настроить настоящий CI/CD на этой ВМ

Actions: https://github.com/Zuika1166/practika_ychhhh/actions

Проверки и публикация образа выполняются на GitHub-hosted runner. Job deploy ожидает self-hosted runner с меткой helpdesk-kali. Пока runner не зарегистрирован и не включён, job не сможет запустить приложение в вашей ВМ.

### Постоянный каталог стенда

После проверки ручного запуска остановить стек, сохранив том, и перенести конфигурацию в отдельный каталог:

```bash
cd "$HOME/practika_ychhhh"
docker compose down
sudo install -d -o "$USER" -g "$(id -gn)" /opt/helpdesk
install -m 600 .env /opt/helpdesk/.env
```

Имя Compose-проекта остаётся helpdesk: существующий том helpdesk_postgres_data продолжит использоваться. .env переносится для сохранения прежнего пароля. После перехода к CD запускать и обслуживать контейнеры из /opt/helpdesk. Не создавать одновременно второй стек с тем же именем.

### Runner

В GitHub открыть Settings → Actions → Runners → New self-hosted runner → Linux → x64. Выполнить выданные GitHub команды в Kali, например в каталоге "$HOME/actions-runner". GitHub выдаёт актуальную версию пакета и временный регистрационный токен: брать их со страницы репозитория, а не из чужого примера.

При настройке runner добавить метку helpdesk-kali. Запускать от обычного пользователя из группы docker. Для первого запуска выполнить `./run.sh` из каталога runner и оставить терминал открытым. Для постоянной работы можно установить сервис через `sudo ./svc.sh install` и `sudo ./svc.sh start` по инструкции GitHub.

В Settings → Environments создать environment training. В нём задать:

| Тип | Имя | Значение |
| --- | --- | --- |
| Variable | STAND_DIR | /opt/helpdesk |
| Secret | DB_PASSWORD | Тот же пароль, что в /opt/helpdesk/.env |

Пароль скопировать локально из .env в поле GitHub Secret; не отправлять его в сообщения, коммиты и issue. Если база ещё не запускалась, новый пароль можно сгенерировать командой `openssl rand -hex 24`, но пароль существующего тома должен совпадать с фактическим.

Workflow использует встроенный GITHUB_TOKEN для GHCR. При ошибке доступа проверить разрешения GitHub Actions на packages и связь GHCR-пакета с репозиторием. При создании пакета в этом репозитории необходимые job permissions уже указаны в workflow.

### Выполнить доставку

В Actions открыть build-test-deploy → Run workflow → main. Если ранний запуск уже ожидает runner, после его подключения он может продолжиться автоматически. Ожидать verify, затем publish и deploy. Один runner и concurrency выполняют доставки последовательно; старые ненужные запуски можно отменить в интерфейсе GitHub.

Проверить в Kali:

```bash
cd /opt/helpdesk
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:8080/actuator/info
cat .deploy/current-image
```

Образ имеет вид ghcr.io/zuika1166/practika_ychhhh:<полный SHA коммита>. Публикация в GHCR без завершённого deploy не закрывает требование CD. Для PR выполняется verify без публикации и доставки; после слияния в main запускается полный pipeline.

Ссылку на успешный run и идентификатор образа записать в docs/submission.md. Перед отправкой изменения по Git настроить собственные user.name и user.email; создавать PR из отдельной ветки.

## 9. Backup, восстановление и откат

Рабочий каталог: "$HOME/practika_ychhhh" для ручного запуска или /opt/helpdesk после настройки CD. Workflow копирует туда эксплуатационные скрипты.

```bash
bash scripts/backup.sh
bash scripts/backup.sh backups/manual.dump
bash scripts/restore.sh backups/manual.dump helpdesk_restore_check
```

Восстановление выполняется в отдельную новую базу. При повторе использовать новое имя. Команда для полной проверки сохранности после пересоздания контейнеров и совпадения таблиц доступна в исходном репозитории:

```bash
python3 scripts/verify-storage.py
```

Если текущий рабочий каталог /opt/helpdesk, сначала скопировать туда проверочный сценарий из клонированного репозитория:

```bash
cp "$HOME/practika_ychhhh/scripts/verify-storage.py" /opt/helpdesk/scripts/
cd /opt/helpdesk
python3 scripts/verify-storage.py
```

Сценарий кратковременно останавливает приложение; ожидается PASS, restartPreservedData=true и allTablesMatch=true.

Для демонстрации отката нужны два успешных развёртывания разных образов. После следующего изменения и успешного pipeline:

```bash
cd /opt/helpdesk
cat .deploy/current-image
cat .deploy/previous-image
bash scripts/rollback.sh
curl --fail http://127.0.0.1:8080/actuator/info
python3 scripts/smoke.py
```

Явный выбор известного образа: `bash scripts/rollback.sh ghcr.io/zuika1166/practika_ychhhh:<SHA>`. Схема и данные при откате образа сохраняются. Последующие миграции должны оставаться совместимыми с предыдущим приложением.

Три сценария диагностики — недоступная БД, неуспешный health check и ошибка миграции — подробно разобраны в README.
