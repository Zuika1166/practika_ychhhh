# Сценарий защиты на 10–15 минут

1. 1–2 минуты: проблема и US-04/F-04 в specification.md. Показать Status.allows, TicketService.changeStatus и enforcesEveryTransition. Объяснить транзакцию и SELECT FOR UPDATE.
2. 2–3 минуты: открыть Swagger, получить категории, создать заявку, выполнить NEW→IN_PROGRESS→RESOLVED, открыть историю.
3. 2 минуты: вызвать summary с периодом, содержащим созданную заявку. Показать Java ReportController → Kotlin ReportService → Java ReportSource/TicketRepository. Объяснить data class, nullable average и операции коллекций.
4. 1 минута: попробовать RESOLVED→IN_PROGRESS и PUT закрытой заявки. Показать 409, прежние данные и прежнюю историю. Можно выполнить `python3 scripts/smoke.py`.
5. 2 минуты: открыть реальный запуск Actions, показать verify, artifacts, тег SHA в GHCR и job deploy на runner Kali. На ВМ выполнить `docker compose ps` и запрос readiness. Записать URL запуска в docs/submission.md.
6. 1–2 минуты: `python3 scripts/verify-storage.py` на учебной базе. Показать дамп, отдельную восстановленную базу и совпадение таблиц.
7. 1–2 минуты: после двух разных развёртываний вызвать `bash scripts/rollback.sh`. Показать `/actuator/info`, readiness и smoke. Описать диагностику из README.

Не удалять рабочий том перед демонстрацией. Сквозные проверки создают реальные учебные заявки. Для независимой демонстрации можно использовать отдельную копию проекта с другим именем Compose и портом.

## Проверенные результаты стенда

Оба pipeline и возврат первого образа на Kali Linux подтверждены. Актуальные ссылки и идентификаторы: docs/submission.md. Тесты: 56 PASS. Сохранность, backup/restore и rollback: PASS. Перед защитой получить последнюю документацию через git pull в клонированном репозитории; стенд находится в /opt/helpdesk. Фактические трудозатраты внести по собственному журналу.
