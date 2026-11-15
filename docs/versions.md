# Версии и сборка

| Компонент | Версия |
| --- | --- |
| Java | 21, контейнер Eclipse Temurin 21.0.9+10 |
| Spring Boot | 3.5.7 |
| Kotlin/JVM и spring plugin | 2.1.21 |
| Gradle Wrapper | 8.14.3, SHA-256 дистрибутива в wrapper properties |
| PostgreSQL | 17.6-alpine |
| Flyway, JDBC driver, JUnit, Mockito | версии из BOM Spring Boot 3.5.7 |
| Testcontainers | 1.21.3 |
| springdoc OpenAPI | 2.8.14 |
| JaCoCo | 0.8.13 |
| Spotless / google-java-format / ktfmt | 7.0.4 / 1.24.0 / 0.54 |
| Docker Compose | v2; проверено с 2.40.3 |
| ОС контейнера приложения | Alpine, образ eclipse-temurin:21.0.9_10-jre-alpine |

Java и Kotlin компилируются в JVM 21 в одном Gradle-проекте. Java-контроллер вызывает Kotlin-сервис; тот читает данные через Java-интерфейс ReportSource. Spring управляет общими транзакциями и внедряет Clock. JPA и H2 не используются: SQL и интеграционные проверки работают на PostgreSQL.

Зависимости разрешаются через Maven Central и Gradle Plugin Portal. Команда `./gradlew dependencies` показывает полный граф. Номера прямых зависимостей и BOM фиксированы; динамические версии и SNAPSHOT отсутствуют. Образы закреплены тегами версий. Для побитовой фиксации образов на учебном стенде можно дополнительно сохранить digest из `docker image inspect`.
