package ru.practice.helpdesk;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StatusRulesTest {
  private final Instant now = Instant.parse("2025-01-15T12:00:00Z");

  static Stream<Arguments> transitions() {
    return Stream.of(Status.values())
        .flatMap(
            from ->
                Stream.of(Status.values())
                    .map(
                        to ->
                            Arguments.of(
                                from,
                                to,
                                (from == Status.NEW
                                        && (to == Status.IN_PROGRESS || to == Status.CANCELLED))
                                    || (from == Status.IN_PROGRESS
                                        && (to == Status.RESOLVED || to == Status.CANCELLED)))));
  }

  @ParameterizedTest
  @MethodSource("transitions")
  void enforcesEveryTransition(Status from, Status to, boolean allowed) {
    assertEquals(allowed, from.allows(to));
  }

  private Ticket ticket(Status status, Instant due) {
    return new Ticket(1, "Test", "", 1, Priority.MEDIUM, status, now.minusSeconds(60), due, null);
  }

  @Test
  void deadlineEqualityIsNotOverdueRegression() {
    assertFalse(ticket(Status.NEW, now).isOverdue(now));
  }

  @Test
  void deadlineOneNanosecondEarlierIsOverdue() {
    assertTrue(ticket(Status.IN_PROGRESS, now.minusNanos(1)).isOverdue(now));
  }

  @Test
  void missingDeadlineIsNotOverdue() {
    assertFalse(ticket(Status.NEW, null).isOverdue(now));
  }

  @Test
  void closedAndCancelledAreNeverOverdue() {
    assertFalse(ticket(Status.RESOLVED, now.minusSeconds(1)).isOverdue(now));
    assertFalse(ticket(Status.CANCELLED, now.minusSeconds(1)).isOverdue(now));
  }

  @Test
  void futureDeadlineIsNotOverdue() {
    assertFalse(ticket(Status.NEW, now.plusSeconds(1)).isOverdue(now));
  }

  @Test
  void validatesRequiredTitleCategoryAndPriority() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      assertEquals(3, validator.validate(new CreateTicket(" ", "", null, null, null)).size());
      assertEquals(
          1,
          validator
              .validate(new CreateTicket("x".repeat(201), "", 1L, Priority.HIGH, null))
              .size());
      assertEquals(
          1, validator.validate(new CreateTicket("Title", "", 0L, Priority.HIGH, null)).size());
      assertTrue(
          validator.validate(new CreateTicket("Title", null, 1L, Priority.LOW, null)).isEmpty());
      assertEquals(1, validator.validate(new EditTicket(null, Priority.LOW, null)).size());
    }
  }
}
