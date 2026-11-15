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

GitHub подтвердил success для verify, publish и deploy. На учебном стенде подтверждены healthy приложения и PostgreSQL, ответ /actuator/info с указанным образом, smoke PASS и успешные 56 тестов на JDK 21.0.13.

## Сохранность данных и восстановление на учебном стенде

Проверка scripts/verify-storage.py завершилась PASS. После пересоздания контейнеров заявка id=4 сохранилась. Резервная копия backups/helpdesk-20261115T194643Z.dump восстановлена в отдельную базу restore_20261115194643. Подтверждены restartPreservedData=true и allTablesMatch=true; в момент копирования было 4 заявки и 8 записей истории.

## Демонстрация второй версии и отката

Вторая версия добавляет info.application.release=practice-2. Бизнес-правила и схема PostgreSQL не меняются. Версию можно проверить через /actuator/info, затем вернуть первый образ и повторить smoke.

| Поле | Значение |
| --- | --- |
| Pull request второй версии | https://github.com/Zuika1166/practika_ychhhh/pull/2 |
| Успешный Actions run второй версии | https://github.com/Zuika1166/practika_ychhhh/actions/runs/43756431655 |
| SHA второй версии | 60a2ce4259cedce23de43eae00e409c6b3b4e04b |
| Образ второй версии | ghcr.io/zuika1166/practika_ychhhh:60a2ce4259cedce23de43eae00e409c6b3b4e04b |
| Digest manifest первого образа в GHCR | sha256:27ce77f539f7766a7237b22a7a9e5042e15e8fe381da956a13b6dc0af16cece6 |
| Digest manifest второго образа в GHCR | sha256:108dc47bf75ec137a998f13fbb16b97258096ae48d15a5614fd67d4282b744dc |
| Дата демонстрации отката на ВМ | 15 ноября 2026 года |
| Образ после отката | ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2 |

Digest manifest получены из logs job publish GitHub Actions. Это идентификаторы manifest в registry. Локальные RepoDigests доступны через docker image inspect.

Полный pipeline второй версии завершился success. Первая попытка публикации получила HTTP 429 от Maven Central; повтор упавших этапов прошёл успешно. Схема PostgreSQL не изменялась.

Откат подтверждён выводом терминала учебного стенда. Скрипт rollback.sh вернул первый образ; /actuator/info и .deploy/current-image указывают на SHA 9eb5ae4585c0965e3387c79b7f47836b89136ac2, метка practice-2 отсутствует. Заявка id=4 с заголовком «Проверка сохранности», статусом NEW и временем создания 2026-11-15T19:46:24.030370Z сохранилась. Smoke внутри отката завершился PASS и создал заявку id=8; отдельный повтор smoke завершился PASS и создал заявку id=9. Приложение и PostgreSQL остались healthy.

Структурированный протокол: evidence/kali-verification.json. Команды возврата образа приведены в README.

План трудозатрат: docs/backlog.md. Фактические затраты времени учитываются в журнале практики.
