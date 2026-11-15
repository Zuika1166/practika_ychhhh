package ru.practice.helpdesk;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EditTicket(
    @NotNull @Size(max = 10000) String description, @NotNull Priority priority, Instant dueAt) {}
