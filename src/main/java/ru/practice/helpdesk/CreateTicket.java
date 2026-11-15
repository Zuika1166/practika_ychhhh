package ru.practice.helpdesk;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateTicket(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 10000) String description,
    @NotNull @Positive Long categoryId,
    @NotNull Priority priority,
    Instant dueAt) {}
