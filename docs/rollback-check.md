# Проверка второй версии и возврата первого образа

Runner должен оставаться запущенным. Сначала дождаться успешного pipeline релиза practice-2, включая deploy. Команды выполнять во втором терминале Kali.

```bash
cd /opt/helpdesk
curl --fail http://127.0.0.1:8080/actuator/info
cat .deploy/current-image
cat .deploy/previous-image
```

До отката application.release должен быть practice-2, current-image — новый тег, previous-image — ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2. Повторный запуск pipeline той же версии может заменить previous-image тем же тегом; в этом случае выбрать первый образ явно.

```bash
bash scripts/rollback.sh ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2
curl --fail http://127.0.0.1:8080/actuator/info
cat .deploy/current-image
python3 scripts/smoke.py
docker compose ps
```

После отката version должен указывать на первый образ, поле release=practice-2 должно исчезнуть, контейнеры должны быть healthy и smoke должен вернуть PASS. Данные и Flyway-миграции сохраняются. Изменение practice-2 касается только метаданных приложения, поэтому схема совместима с первым образом.

Для фиксации digest:

```bash
docker image inspect ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2 --format '{{json .RepoDigests}}'
```

Сохранить вывод до и после возврата и внести фактический результат в docs/submission.md. Проверка на пользовательской ВМ считается выполненной только после получения результата.
