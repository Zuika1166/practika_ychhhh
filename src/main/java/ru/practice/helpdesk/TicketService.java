package ru.practice.helpdesk;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketService {
  private final TicketRepository repository;
  private final Clock clock;

  public TicketService(TicketRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Ticket create(CreateTicket request) {
    if (!repository.categoryExists(request.categoryId())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Категория не найдена");
    }
    Instant now = clock.instant();
    Ticket ticket = repository.insert(request, now);
    repository.recordHistory(ticket.id(), null, Status.NEW, now);
    return ticket;
  }

  public Ticket get(long id) {
    return requireTicket(id, false);
  }

  private Ticket requireTicket(long id, boolean lock) {
    Ticket ticket = repository.find(id, lock);
    if (ticket == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Заявка не найдена");
    return ticket;
  }

  @Transactional
  public Ticket edit(long id, EditTicket request) {
    Ticket ticket = requireTicket(id, true);
    if (!ticket.status().isOpen()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "TICKET_CLOSED",
          "Закрытую или отменённую заявку нельзя редактировать");
    }
    return repository.edit(id, request);
  }

  @Transactional
  public Ticket changeStatus(long id, Status target) {
    Ticket ticket = requireTicket(id, true);
    if (!ticket.status().allows(target)) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Переход статуса запрещён");
    }
    Instant now = clock.instant();
    Ticket updated = repository.changeStatus(id, target, target.isOpen() ? null : now);
    repository.recordHistory(id, ticket.status(), target, now);
    return updated;
  }

  public List<HistoryEntry> history(long id) {
    requireTicket(id, false);
    return repository.history(id);
  }

  public List<Category> categories() {
    return repository.categories();
  }

  public TicketPage search(
      Status status,
      Priority priority,
      Long categoryId,
      Boolean overdue,
      Instant from,
      Instant to,
      int page,
      int size) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "Начало периода должно быть раньше конца");
    }
    return repository.search(
        status, priority, categoryId, overdue, from, to, clock.instant(), page, size);
  }
}
