package ru.practice.helpdesk;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
  private static final Instant BASE = Instant.parse("2025-01-15T12:00:00Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  static class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(BASE);

    void set(Instant instant) {
      now.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;

  @BeforeEach
  void reset() {
    jdbc.execute("TRUNCATE status_history, tickets RESTART IDENTITY");
    clock.set(BASE);
  }

  private JsonNode create(String title, Priority priority, Instant due) {
    ResponseEntity<JsonNode> response =
        http.postForEntity(
            "/api/tickets", new CreateTicket(title, "Описание", 1L, priority, due), JsonNode.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getHeaders().getLocation());
    return response.getBody();
  }

  private ResponseEntity<JsonNode> status(long id, Status status) {
    return http.exchange(
        "/api/tickets/" + id + "/status",
        HttpMethod.PATCH,
        new HttpEntity<>(new ChangeStatus(status)),
        JsonNode.class);
  }

  private JsonNode get(long id) {
    return http.getForObject("/api/tickets/" + id, JsonNode.class);
  }

  private JsonNode history(long id) {
    return http.getForObject("/api/tickets/" + id + "/history", JsonNode.class);
  }

  @Test
  void appliesRealPostgresMigrationsAndLoadsCategories() {
    assertTrue(jdbc.queryForObject("SELECT version()", String.class).contains("PostgreSQL 17.6"));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
    assertEquals(3, http.getForObject("/api/categories", JsonNode.class).size());
  }

  @Test
  void createsWithInitialHistoryAndReturnsSameRecord() {
    var created = create("  Printer  ", Priority.HIGH, BASE.plusSeconds(60));
    long id = created.get("id").asLong();
    assertEquals("Printer", get(id).get("title").asText());
    assertEquals("NEW", created.get("status").asText());
    assertTrue(created.get("closedAt").isNull());
    assertEquals(BASE.toString(), created.get("createdAt").asText());
    var history = history(id);
    assertEquals(1, history.size());
    assertTrue(history.get(0).get("fromStatus").isNull());
    assertEquals("NEW", history.get(0).get("toStatus").asText());
  }

  @Test
  void validatesBodiesUnknownEnumsAndPagination() {
    var missing = http.postForEntity("/api/tickets", Map.of("title", " "), JsonNode.class);
    assertEquals(400, missing.getStatusCode().value());
    assertEquals("VALIDATION_ERROR", missing.getBody().get("code").asText());
    assertTrue(missing.getBody().get("violations").has("categoryId"));
    assertEquals(
        400,
        http.postForEntity(
                "/api/tickets",
                Map.of("title", "x", "categoryId", 1, "priority", "URGENT"),
                JsonNode.class)
            .getStatusCode()
            .value());
    assertEquals(
        400, http.getForEntity("/api/tickets?size=101", JsonNode.class).getStatusCode().value());
    assertEquals(
        400, http.getForEntity("/api/tickets?page=-1", JsonNode.class).getStatusCode().value());
    assertEquals(
        400,
        http.postForEntity(
                "/api/tickets",
                new CreateTicket("x", "", 999L, Priority.HIGH, null),
                JsonNode.class)
            .getStatusCode()
            .value());
  }

  @Test
  void returnsUniform404ForMissingTicketAndHistory() {
    for (String path : new String[] {"/api/tickets/999", "/api/tickets/999/history"}) {
      var response = http.getForEntity(path, JsonNode.class);
      assertEquals(404, response.getStatusCode().value());
      assertEquals(path, response.getBody().get("path").asText());
      assertEquals("TICKET_NOT_FOUND", response.getBody().get("code").asText());
    }
    assertEquals(404, status(999, Status.IN_PROGRESS).getStatusCode().value());
  }

  @Test
  void editsOpenTicketAndClearsDeadline() {
    long id = create("Edit", Priority.LOW, BASE.plusSeconds(60)).get("id").asLong();
    var response =
        http.exchange(
            "/api/tickets/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(new EditTicket("Changed", Priority.HIGH, null)),
            JsonNode.class);
    assertEquals(200, response.getStatusCode().value());
    assertEquals("Changed", get(id).get("description").asText());
    assertTrue(get(id).get("dueAt").isNull());
    assertEquals(1, history(id).size());
  }

  @Test
  void filtersWithStablePaginationAndCorrectDeadlineBoundary() {
    long overdue = create("Overdue", Priority.HIGH, BASE.minusSeconds(1)).get("id").asLong();
    create("At deadline", Priority.HIGH, BASE);
    create("No deadline", Priority.LOW, null);
    var filtered =
        http.getForObject(
            "/api/tickets?status=NEW&priority=HIGH&categoryId=1&overdue=true&size=1",
            JsonNode.class);
    assertEquals(1, filtered.get("totalElements").asInt());
    assertEquals(overdue, filtered.get("content").get(0).get("id").asLong());
    var notOverdue = http.getForObject("/api/tickets?overdue=false", JsonNode.class);
    assertEquals(2, notOverdue.get("totalElements").asInt());
    var first = http.getForObject("/api/tickets?size=1&page=0", JsonNode.class);
    var second = http.getForObject("/api/tickets?size=1&page=1", JsonNode.class);
    assertEquals(3, first.get("totalPages").asInt());
    assertNotEquals(first.get("content").get(0).get("id"), second.get("content").get(0).get("id"));
    assertEquals(
        0, http.getForObject("/api/tickets?page=10", JsonNode.class).get("content").size());
  }

  @Test
  void summaryUsesHalfOpenCreatedPeriodForEveryMetric() {
    create("From included", Priority.HIGH, BASE.minusSeconds(1));
    clock.set(BASE.plusSeconds(10));
    long resolved = create("Resolved", Priority.LOW, null).get("id").asLong();
    status(resolved, Status.IN_PROGRESS);
    clock.set(BASE.plusSeconds(40));
    status(resolved, Status.RESOLVED);
    long cancelled = create("Cancelled", Priority.MEDIUM, BASE).get("id").asLong();
    status(cancelled, Status.CANCELLED);
    clock.set(BASE.plusSeconds(60));
    create("To excluded", Priority.HIGH, BASE);
    var report =
        http.getForObject(
            "/api/reports/summary?from=" + BASE + "&to=" + BASE.plusSeconds(60), JsonNode.class);
    assertEquals(3, report.get("createdCount").asInt());
    assertEquals(1, report.get("byStatus").get("NEW").asInt());
    assertEquals(1, report.get("byStatus").get("RESOLVED").asInt());
    assertEquals(1, report.get("byStatus").get("CANCELLED").asInt());
    assertEquals(1, report.get("byPriority").get("HIGH").asInt());
    assertEquals(1, report.get("overdueOpenCount").asInt());
    assertEquals(30.0, report.get("averageResolutionSeconds").asDouble());
    var search =
        http.getForObject(
            "/api/tickets?from=" + BASE + "&to=" + BASE.plusSeconds(60), JsonNode.class);
    assertEquals(3, search.get("totalElements").asInt());
  }

  @Test
  void summaryHandlesEmptyAndNoResolvedSelections() {
    String path = "/api/reports/summary?from=" + BASE + "&to=" + BASE.plusSeconds(60);
    var empty = http.getForObject(path, JsonNode.class);
    assertEquals(0, empty.get("createdCount").asInt());
    assertTrue(empty.get("averageResolutionSeconds").isNull());
    create("Open", Priority.LOW, null);
    assertTrue(http.getForObject(path, JsonNode.class).get("averageResolutionSeconds").isNull());
  }

  @Test
  void rejectsInvalidPeriodsAndMissingReportArguments() {
    for (String path :
        new String[] {
          "/api/reports/summary?from=" + BASE + "&to=" + BASE,
          "/api/reports/summary?from=" + BASE.plusSeconds(60) + "&to=" + BASE,
          "/api/reports/summary?from=bad&to=" + BASE,
          "/api/reports/summary",
          "/api/tickets?from=" + BASE + "&to=" + BASE
        }) {
      assertEquals(400, http.getForEntity(path, JsonNode.class).getStatusCode().value(), path);
    }
  }

  @Test
  void cancellationIsAllowedFromBothOpenStatuses() {
    long first = create("Cancel new", Priority.LOW, null).get("id").asLong();
    assertEquals(200, status(first, Status.CANCELLED).getStatusCode().value());
    long second = create("Cancel work", Priority.LOW, null).get("id").asLong();
    status(second, Status.IN_PROGRESS);
    assertEquals(200, status(second, Status.CANCELLED).getStatusCode().value());
    assertEquals(BASE.toString(), get(second).get("closedAt").asText());
    assertEquals(409, status(first, Status.NEW).getStatusCode().value());
    var response =
        http.exchange(
            "/api/tickets/" + first,
            HttpMethod.PUT,
            new HttpEntity<>(new EditTicket("Forbidden", Priority.HIGH, null)),
            JsonNode.class);
    assertEquals(409, response.getStatusCode().value());
  }

  @Test
  void historyFailureRollsBackStatusAndClosingTime() {
    long id = create("Rollback", Priority.LOW, null).get("id").asLong();
    jdbc.execute(
        "ALTER TABLE status_history ADD CONSTRAINT test_history_failure CHECK (to_status <> 'CANCELLED')");
    try {
      assertEquals(500, status(id, Status.CANCELLED).getStatusCode().value());
      assertEquals("NEW", get(id).get("status").asText());
      assertTrue(get(id).get("closedAt").isNull());
      assertEquals(1, history(id).size());
    } finally {
      jdbc.execute("ALTER TABLE status_history DROP CONSTRAINT test_history_failure");
    }
  }

  @Test
  void concurrentDuplicateTransitionWritesExactlyOneHistoryEntry() throws Exception {
    long id = create("Concurrent", Priority.HIGH, null).get("id").asLong();
    try (var executor = Executors.newFixedThreadPool(2)) {
      CountDownLatch start = new CountDownLatch(1);
      Callable<Integer> operation =
          () -> {
            start.await();
            return status(id, Status.IN_PROGRESS).getStatusCode().value();
          };
      var first = executor.submit(operation);
      var second = executor.submit(operation);
      start.countDown();
      var codes =
          java.util.stream.Stream.of(
                  first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
              .sorted()
              .toList();
      assertEquals(java.util.List.of(200, 409), codes);
    }
    assertEquals(2, history(id).size());
  }

  @Test
  void e2eFullLifecycleProducesReportAndOrderedHistory() {
    long id = create("Lifecycle", Priority.MEDIUM, BASE.plusSeconds(3600)).get("id").asLong();
    clock.set(BASE.plusSeconds(60));
    assertEquals(200, status(id, Status.IN_PROGRESS).getStatusCode().value());
    clock.set(BASE.plusSeconds(180));
    assertEquals(200, status(id, Status.RESOLVED).getStatusCode().value());
    var history = history(id);
    assertEquals(3, history.size());
    assertEquals("NEW", history.get(1).get("fromStatus").asText());
    assertEquals("IN_PROGRESS", history.get(2).get("fromStatus").asText());
    assertEquals(BASE.plusSeconds(180).toString(), get(id).get("closedAt").asText());
    var report =
        http.getForObject(
            "/api/reports/summary?from=" + BASE + "&to=" + BASE.plusSeconds(3600), JsonNode.class);
    assertEquals(1, report.get("createdCount").asInt());
    assertEquals(180.0, report.get("averageResolutionSeconds").asDouble());
  }

  @Test
  void e2eForbiddenOperationLeavesAllDataUnchanged() {
    long id = create("Immutable", Priority.LOW, null).get("id").asLong();
    status(id, Status.IN_PROGRESS);
    status(id, Status.RESOLVED);
    var beforeTicket = get(id);
    var beforeHistory = history(id);
    assertEquals(409, status(id, Status.IN_PROGRESS).getStatusCode().value());
    var edit =
        http.exchange(
            "/api/tickets/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(new EditTicket("Forbidden", Priority.HIGH, BASE)),
            JsonNode.class);
    assertEquals(409, edit.getStatusCode().value());
    assertEquals(beforeTicket, get(id));
    assertEquals(beforeHistory, history(id));
  }

  @Test
  void exposesOpenApiAndDatabaseReadiness() {
    assertEquals(200, http.getForEntity("/v3/api-docs", String.class).getStatusCode().value());
    assertEquals(
        "UP",
        http.getForObject("/actuator/health/readiness", JsonNode.class).get("status").asText());
  }
}
