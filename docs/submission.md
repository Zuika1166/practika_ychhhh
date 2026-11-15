# Данные стенда для сдачи

## Подтверждённая первая доставка на Kali Linux в VirtualBox

| Поле | Значение |
| --- | --- |
| Репозиторий | https://github.com/Zuika1166/practika_ychhhh |
| Pull request первой доставки | https://github.com/Zuika1166/practika_ychhhh/pull/1 |
| Успешный Actions run | https://github.com/Zuika1166/practika_ychhhh/actions/runs/43745510914 |
| SHA первой доставки | 9eb5ae4585c0965e3387c79b7f47836b89136ac2 |
| Образ первой доставки | ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2 |
| Каталог стенда | /opt/helpdesk |
| Runner | self-hosted Linux x64, метка helpdesk-kali |
| Дата проверки | 15 ноября 2026 года |

GitHub подтвердил success для verify, publish и deploy. На пользовательской ВМ подтверждены healthy приложения и PostgreSQL, ответ /actuator/info с указанным образом, smoke PASS и успешные 56 тестов на JDK 21.0.13.

## Сохранность данных и восстановление на пользовательской ВМ

Проверка scripts/verify-storage.py завершилась PASS. После пересоздания контейнеров заявка id=4 сохранилась. Резервная копия backups/helpdesk-20261115T194643Z.dump восстановлена в отдельную базу restore_20261115194643. Подтверждены restartPreservedData=true и allTablesMatch=true; в момент копирования было 4 заявки и 8 записей истории.

## Демонстрация второй версии и отката

Вторая версия добавляет info.application.release=practice-2. Бизнес-правила и схема PostgreSQL не меняются. Версию можно проверить через /actuator/info, затем вернуть первый образ и повторить smoke.

Ссылку на успешный run второй версии, её полный SHA, результат отката и RepoDigests на ВМ внести после выполнения. Проверка пока не отмечена выполненной. Порядок: docs/rollback-check.md.

Фактические трудозатраты студент указывает по собственному журналу; план 72 академических часа не является подтверждением фактически отработанного времени.
