package ru.practice.helpdesk;

import java.util.List;

public record TicketPage(
    List<Ticket> content, int page, int size, long totalElements, long totalPages) {}
