package ru.practice.helpdesk;

import java.time.Instant;

public record Ticket(
    long id,
    String title,
    String description,
    long categoryId,
    Priority priority,
    Status status,
    Instant createdAt,
    Instant dueAt,
    Instant closedAt) {
  public boolean isOverdue(Instant now) {
    return status.isOpen() && dueAt != null && dueAt.isBefore(now);
  }
}
