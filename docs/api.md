# Контракт REST API

Базовый адрес: http://127.0.0.1:8080. JSON, время ISO-8601 с часовым поясом. Интерактивная схема: /swagger-ui/index.html, машиночитаемая: /v3/api-docs. Сохранённая схема: api/openapi.json. Коллекция: api/helpdesk.postman_collection.json.

| Метод | Путь | Успех | Ошибки и назначение |
| --- | --- | --- | --- |
| POST | /api/tickets | 201 + Location, Ticket | 400: поля/категория |
| GET | /api/tickets/{id} | 200, Ticket | 404: id не найден |
| PUT | /api/tickets/{id} | 200, Ticket | 400, 404, 409: терминальная заявка |
| PATCH | /api/tickets/{id}/status | 200, Ticket | 400, 404, 409: переход |
| GET | /api/tickets | 200, TicketPage | 400: фильтры/пагинация/период |
| GET | /api/tickets/{id}/history | 200, HistoryEntry[] | 404: заявка не найдена |
| GET | /api/categories | 200, Category[] | Справочник только для чтения |
| GET | /api/reports/summary?from=...&to=... | 200, SummaryReport | 400: период обязателен, from < to |
| GET | /actuator/health/readiness | 200 или 503 | Готовность приложения и БД |
| GET | /actuator/info | 200 | Версия образа |

## Создание

```http
POST /api/tickets
Content-Type: application/json

{"title":"Не работает принтер","description":"Кабинет 104","categoryId":1,"priority":"HIGH","dueAt":"2030-01-20T16:00:00Z"}
```

Ответ 201:

```json
{"id":1,"title":"Не работает принтер","description":"Кабинет 104","categoryId":1,"priority":"HIGH","status":"NEW","createdAt":"2030-01-15T10:00:00Z","dueAt":"2030-01-20T16:00:00Z","closedAt":null}
```

GET /api/tickets/1 возвращает ту же структуру. Все примеры времени иллюстративны; сервер задаёт createdAt самостоятельно.

## Редактирование и статус

```http
PUT /api/tickets/1
Content-Type: application/json

{"description":"Заменить кабель","priority":"MEDIUM","dueAt":null}
```

200 возвращает Ticket с новым description, priority и пустым dueAt. title, categoryId и createdAt не меняются. PUT требует description и priority.

```http
PATCH /api/tickets/1/status
Content-Type: application/json

{"status":"IN_PROGRESS"}
```

200 возвращает Ticket со статусом IN_PROGRESS. Следующий разрешённый запрос {"status":"RESOLVED"} заполняет closedAt. Для отмены передать {"status":"CANCELLED"} из NEW или IN_PROGRESS.

## Поиск

```http
GET /api/tickets?status=NEW&priority=HIGH&categoryId=1&overdue=true&page=0&size=20&from=2030-01-01T00:00:00Z&to=2030-02-01T00:00:00Z
```

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Каждый фильтр необязателен. overdue=false включает заявки без срока и терминальные заявки. from включается, to исключается. page — от 0, size — от 1 до 100. Порядок: createdAt DESC, id DESC. Элементы content имеют структуру Ticket.

## Категории и история

```http
GET /api/categories
```

```json
[{"id":1,"name":"Оборудование"},{"id":2,"name":"Программное обеспечение"},{"id":3,"name":"Сеть"}]
```

```http
GET /api/tickets/1/history
```

```json
[{"id":1,"ticketId":1,"fromStatus":null,"toStatus":"NEW","changedAt":"2030-01-15T10:00:00Z"},{"id":2,"ticketId":1,"fromStatus":"NEW","toStatus":"IN_PROGRESS","changedAt":"2030-01-15T10:01:00Z"}]
```

## Сводка Kotlin

```http
GET /api/reports/summary?from=2030-01-01T00:00:00Z&to=2030-02-01T00:00:00Z
```

```json
{"from":"2030-01-01T00:00:00Z","to":"2030-02-01T00:00:00Z","createdCount":3,"byStatus":{"NEW":1,"IN_PROGRESS":0,"RESOLVED":1,"CANCELLED":1},"byPriority":{"LOW":1,"MEDIUM":1,"HIGH":1},"overdueOpenCount":1,"averageResolutionSeconds":180.0}
```

Все показатели считают заявки, созданные в [from,to). Состояние заявки берётся текущее, а не историческое на to. Просрочка определяется на момент запроса. Среднее выражено в секундах с дробной частью; CANCELLED исключаются. Пустая выборка возвращает нули и averageResolutionSeconds=null.

## Ошибки

```json
{"timestamp":"2030-01-15T11:00:00Z","status":409,"code":"INVALID_TRANSITION","message":"Переход статуса запрещён","path":"/api/tickets/1/status","violations":{}}
```

Для Bean Validation code=VALIDATION_ERROR и violations содержит ошибки полей; для нечитаемого JSON, неверного enum или параметра — INVALID_REQUEST. Положительный, но отсутствующий id: TICKET_NOT_FOUND/404. Неположительный id: 400. Неподдерживаемые поля JSON отвергаются, чтобы не создавать ложное впечатление сохранения данных.
