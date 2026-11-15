package ru.practice.helpdesk;

import java.time.Instant;

public record HistoryEntry(
    long id, long ticketId, Status fromStatus, Status toStatus, Instant changedAt) {}
