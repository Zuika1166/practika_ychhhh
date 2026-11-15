package ru.practice.helpdesk;

import jakarta.validation.constraints.NotNull;

public record ChangeStatus(@NotNull Status status) {}
