# Тест-план и трассировка

Среда: Java 21, PostgreSQL 17.6-alpine через Testcontainers, JUnit 5, Mockito, Kotlin test, Spring Boot RANDOM_PORT. Интеграционные проверки не подменяют PostgreSQL на H2 и не пропускаются при отсутствии Docker.

| Требование/история | Риск | Автоматическая проверка |
| --- | --- | --- |
| F-01, US-01 | Потеря/невалидное создание | StatusRulesTest.validatesRequiredTitleCategoryAndPriority; TicketServiceTest.createsNewTicketWithInitialHistory; ApiIntegrationTest.createsWithInitialHistoryAndReturnsSameRecord |
| F-02, US-02 | Неверный 404, схема не создана | missingTicketIs404; returnsUniform404ForMissingTicketAndHistory; appliesRealPostgresMigrationsAndLoadsCategories |
| F-03, US-03 | Изменение закрытой заявки | cancelledCannotBeEdited; resolvedCannotBeEdited; editsOpenTicketAndClearsDeadline |
| F-04, US-04 | Недопустимый переход | enforcesEveryTransition: все 16 пар; cancellationIsAllowedFromBothOpenStatuses |
| F-05, US-05 | Статус без истории | historyFailureRollsBackStatusAndClosingTime: принудительная ошибка CHECK в таблице истории; concurrentDuplicateTransitionWritesExactlyOneHistoryEntry |
| F-06, US-06 | Потеря/дублирование страниц, неверный AND | filtersWithStablePaginationAndCorrectDeadlineBoundary |
| F-07 | Ошибка границы срока/null | deadlineEqualityIsNotOverdueRegression; deadlineOneNanosecondEarlierIsOverdue; missingDeadlineIsNotOverdue; checksJavaTicketDeadlineBoundary |
| F-08, US-07 | Разные выборки в метриках | summaryUsesHalfOpenCreatedPeriodForEveryMetric; calculatesAllMetricsFromOneSelectionAndIgnoresCancellationDuration |
| F-08 | Пустой отчёт, нет RESOLVED | emptySelectionHasZeroCountsAndNullAverage; noResolvedTicketsHaveNullAverage; summaryHandlesEmptyAndNoResolvedSelections |
| F-08 | Неверный период | rejectsEqualAndReversedPeriods; rejectsInvalidPeriodsAndMissingReportArguments |
| F-08, D-01 | Потеря дробной части времени | preservesSubMillisecondResolutionRegression |
| F-09 | Ошибки enum, размера страницы, категории | validatesBodiesUnknownEnumsAndPagination |
| N-05 | Зависимость от календаря/порядка | Clock.fixed для unit; MutableClock для REST; TRUNCATE перед каждым интеграционным тестом |
| US-01,04,05,07 | Полный процесс | e2eFullLifecycleProducesReportAndOrderedHistory |
| US-03,04,05 | Запрещённая операция без побочных эффектов | e2eForbiddenOperationLeavesAllDataUnchanged: сравнение всей карточки и всей истории |
| US-08, N-02 | Потеря тома/нерабочий backup | scripts/verify-storage.py: down/up, pg_dump, restore, полное сравнение трёх таблиц |
| US-09 | Неисправный релиз | workflow needs: verify→publish→deploy; scripts/smoke.py; deploy.sh/rollback.sh |

Модульные проверки включают все пары статусов, валидацию, Clock, Mockito и расчёты Kotlin. Интеграционный класс содержит 15 методов, два из них — полные сквозные сценарии на реальном HTTP и PostgreSQL. Отдельный smoke повторяет оба сценария против контейнерного стенда. Всего после регрессии D-01 — 56 выполняемых тестов JUnit, включая 8 на Kotlin. Отчёты: build/reports/tests/test, build/test-results/test, build/reports/jacoco/test.

Критерий прохождения: ноль ошибок/падений/пропусков; readiness=UP; smoke=PASS; сохранность и восстановление=PASS. При сбое publication/deployment не выполняются. Процент покрытия приведён в evidence/verification.json и docs/report.md, без подмены тестирования одним числом.

Команда: `./gradlew spotlessCheck build`. Для исправления форматирования перед коммитом: `./gradlew spotlessApply`. Для отдельного теста: `./gradlew test --tests '*ReportServiceTest.preservesSubMillisecondResolutionRegression'`.
