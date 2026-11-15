# Вопросы на защите и подсказки к ответам

Ответы относятся к этому проекту. Перед защитой найти названные методы в исходниках и понять их последовательность.

| Вопрос | Короткий ответ | Где показать |
| --- | --- | --- |
| Какую проблему решает приложение? | Обращения хранятся централизованно, статус меняется по правилам, история сохраняется, сроки и статистика доступны через API. | docs/specification.md |
| Что такое REST API? | HTTP-интерфейс к ресурсам: URL определяет ресурс, метод — действие, JSON — данные, код ответа — результат. | Swagger, TicketController |
| Где интерфейс пользователя? | Проверка REST API выполняется через Swagger UI. Отдельный пользовательский веб-интерфейс в обязательный объём ТЗ не входит. | docs/specification.md |
| Почему при создании 201? | Создан новый ресурс. Сервер возвращает карточку и Location с её адресом. | TicketController.create |
| Чем отличаются 400, 404 и 409? | 400 — неверные данные или параметры, 404 — заявка не найдена, 409 — операция конфликтует с состоянием заявки. | ApiErrorHandler, TicketService |
| Почему повторное открытие даёт 409? | RESOLVED и CANCELLED терминальные. Требование запрещает открывать и редактировать такие заявки. | Status.allows, TicketService.changeStatus |
| Что обеспечивает транзакция? | Изменение статуса и вставка истории фиксируются вместе. Если вставка истории завершается ошибкой, изменение статуса откатывается. | @Transactional, historyFailureRollsBackStatusAndClosingTime |
| Зачем SELECT FOR UPDATE? | Блокирует строку заявки до конца транзакции и последовательно выполняет конкурирующие изменения одной заявки. | TicketRepository.find, concurrentDuplicateTransitionWritesExactlyOneHistoryEntry |
| Какая технология доступа к БД? | Spring JdbcTemplate и параметризованные SQL-запросы. В этом проекте используется JDBC. | TicketRepository |
| Как предотвращается SQL-инъекция в фильтрах? | Пользовательские значения передаются параметрами через ?, а фрагменты SQL выбираются из заранее заданного кода. | TicketRepository.search |
| Какие таблицы связаны? | categories один-ко-многим tickets; tickets один-ко-многим status_history. Связи защищены внешними ключами. | V1__schema.sql, ER в спецификации |
| Для чего Flyway? | Применяет версионированные изменения схемы при запуске и хранит результат в flyway_schema_history. | db/migration, логи старта |
| Что означает [from,to)? | Заявки с createdAt=from включены, с createdAt=to исключены. Соседние периоды не дублируют одну граничную запись. | findCreatedBetween, summaryUsesHalfOpenCreatedPeriodForEveryMetric |
| Отчёт показывает состояние на конец периода? | Он выбирает заявки по дате создания в периоде и считает их текущие статусы. Это не исторический снимок на дату to. | docs/api.md, ReportService |
| Когда заявка просрочена? | Она открыта, срок задан и dueAt строго раньше now. Ровно в момент dueAt просрочки ещё нет. | Ticket.isOverdue, deadlineEqualityIsNotOverdueRegression |
| Как считается среднее? | Только для RESOLVED, как среднее closedAt-createdAt в секундах с дробной частью. CANCELLED не участвуют. | ReportService.summary |
| Почему среднее null? | В выбранных данных нет завершённых заявок. Нулевое время и отсутствие значения имеют разный смысл. | SummaryReport.averageResolutionSeconds: Double? |
| Зачем Clock? | Сервис получает время через зависимость. В тестах время фиксируется или управляется, поэтому проверки не зависят от календарной даты. | HelpdeskApplication.clock, MutableClock в интеграционных тестах |
| Как Java вызывает Kotlin? | Java-контроллер получает Spring bean ReportService через конструктор и вызывает его JVM-метод summary. | ReportController, ReportService |
| Как Kotlin получает данные Java? | Зависит от Java-интерфейса ReportSource, реализованного Java TicketRepository. | ReportSource, TicketRepository |
| Какие особенности Kotlin реально использованы? | data class, nullable Double?, безопасные вызовы ?.let, mapNotNull, groupingBy, eachCount, associateWith, average. | ReportService.kt |
| Какие тесты написаны на Kotlin? | Восемь тестов отчёта и взаимодействия языков; один вызывает Java TicketService и проверяет 409 для закрытой заявки. | ReportServiceTest.kt |
| Чем модульные тесты отличаются от интеграционных? | Модульные изолируют правило, например через Mockito. Интеграционные проверяют HTTP, Spring, SQL, PostgreSQL и миграции совместно. | TicketServiceTest, ApiIntegrationTest |
| Зачем Testcontainers? | Поднимает настоящую PostgreSQL для тестов. Проверяются PostgreSQL-специфичный SQL, ограничения и миграции. | PostgreSQLContainer в ApiIntegrationTest |
| Как тесты независимы друг от друга? | Перед каждым REST-тестом очищаются заявки и история, сбрасываются идентификаторы и тестовые часы; время задаётся через Clock. | ApiIntegrationTest.reset |
| Как проверяется неизменность после запрета? | Сравнивается вся карточка и вся история до и после двух запрещённых операций. | e2eForbiddenOperationLeavesAllDataUnchanged |
| Какой дефект исправлен? | toMillis() терял доли миллисекунды: 0,0005 с превращалось в 0. Расчёт использует seconds и nano; добавлен регрессионный тест. | docs/defects.md, preservesSubMillisecondResolutionRegression |
| Что означает покрытие? | Показывает выполненные тестами участки кода. В зафиксированном отчёте покрыто 219 из 224 строк, 97,77%; качество также подтверждается сценариями и проверками результата. | JaCoCo, test-plan.md |
| Зачем многоэтапный Dockerfile? | Сборка выполняется в JDK-образе, итоговый runtime содержит JRE и JAR; инструменты сборки в него не копируются. | Dockerfile |
| Где данные после пересоздания контейнера? | В именованном томе PostgreSQL, который существует отдельно от контейнера. | compose.yaml, verify-storage.py |
| Чем образ отличается от контейнера? | Образ содержит файлы и настройки запуска. Контейнер — запущенный экземпляр образа. Постоянные данные БД вынесены в том. | Dockerfile, docker compose ps |
| Что делает health check? | Проверяет readiness приложения; readiness учитывает доступ к БД. Compose ожидает готовности БД и приложения. | application.yaml, Dockerfile, compose.yaml |
| Что делают этапы CI/CD? | verify проверяет и тестирует; publish собирает и отправляет образ с тегом SHA; deploy запускает его на ВМ и проверяет через readiness и smoke. | ci-cd.yaml, успешный Actions run |
| Почему при падении тестов нет доставки? | publish зависит от verify, deploy от publish через needs. Для PR публикация и доставка отключены условием. | .github/workflows/ci-cd.yaml |
| Что такое self-hosted runner? | Агент GitHub Actions на моей Kali Linux. Job deploy выбирает его по метке helpdesk-kali. | GitHub Settings → Actions → Runners |
| Где секреты? | Локальный пароль в игнорируемом .env, в GitHub — secret DB_PASSWORD environment training. Токен registry берётся из GITHUB_TOKEN. | .env.example, workflow; пароль не показывать |
| Чем переменная отличается от секрета? | STAND_DIR — обычный путь настройки. DB_PASSWORD — чувствительное значение, передаваемое через Secrets. | environment training |
| Как проверено восстановление? | pg_dump восстановлен в отдельную базу; содержимое трёх таблиц полностью сравнено с оригиналом. | verify-storage.py, submission.md |
| Чем rollback отличается от restore? | Rollback возвращает образ приложения, сохраняя БД. Restore восстанавливает данные из резервной копии в отдельную БД. | rollback.sh, restore.sh |
| Почему откат совместим с БД? | Между этими версиями схема не менялась. Для будущих релизов нужны миграции, сохраняющие необходимые прежнему коду таблицы и поля. | db/migration, release practice-2 |
| Что происходит при неготовности новой версии? | deploy.sh пытается вернуть ранее работающий образ и завершает доставку с ошибкой. | scripts/deploy.sh, evidence/automatic-rollback.json |
| Что было с 429 в pipeline? | Maven Central временно ограничил запросы зависимостей. Проверки прошли, публикация и доставка сначала остановились; повтор упавшего этапа успешно завершил pipeline. | Actions run второй версии |
| Какие ограничения у решения? | Один учебный сервис; нет аутентификации, уведомлений, вложений и назначения исполнителей. Эти функции не входили в обязательный объём. | docs/specification.md, docs/report.md |

## Быстрая диагностика по трём ситуациям

Рабочий каталог:

```bash
cd /opt/helpdesk
```

### База недоступна

```bash
docker compose ps
docker compose logs --tail=100 db app
docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Проверить DB_URL, доступность сервиса db и соответствие пароля существующей базы .env. Пароль в ответах или на общей демонстрации не выводить.

### Приложение не проходит readiness

```bash
docker compose logs --tail=100 app
docker inspect --format '{{json .State.Health}}' "$(docker compose ps -q app)"
curl -i http://127.0.0.1:8080/actuator/health/readiness
```

Проверить ошибку старта, БД, память, порт и состояние health check. Ответ 503 указывает на неготовность, connection refused — на отсутствие доступного слушающего сервиса по этому адресу.

### Ошибка миграции

```bash
docker compose logs --tail=150 app
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

Разобрать сообщение Flyway, сверить файл уже применённой миграции, сохранить backup. Изменения схемы выпускать новой миграцией после проверки на отдельной восстановленной базе.
