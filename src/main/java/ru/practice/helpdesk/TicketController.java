package ru.practice.helpdesk;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class TicketController {
  private final TicketService service;

  public TicketController(TicketService service) {
    this.service = service;
  }

  @PostMapping("/tickets")
  @Operation(summary = "Создать заявку")
  public ResponseEntity<Ticket> create(@Valid @RequestBody CreateTicket request) {
    Ticket ticket = service.create(request);
    return ResponseEntity.created(URI.create("/api/tickets/" + ticket.id())).body(ticket);
  }

  @GetMapping("/tickets/{id}")
  @Operation(summary = "Получить заявку")
  public Ticket get(@PathVariable @Positive long id) {
    return service.get(id);
  }

  @PutMapping("/tickets/{id}")
  @Operation(summary = "Изменить описание, приоритет и срок")
  public Ticket edit(@PathVariable @Positive long id, @Valid @RequestBody EditTicket request) {
    return service.edit(id, request);
  }

  @PatchMapping("/tickets/{id}/status")
  @Operation(summary = "Изменить статус")
  public Ticket changeStatus(
      @PathVariable @Positive long id, @Valid @RequestBody ChangeStatus request) {
    return service.changeStatus(id, request.status());
  }

  @GetMapping("/tickets/{id}/history")
  @Operation(summary = "История статусов")
  public List<HistoryEntry> history(@PathVariable @Positive long id) {
    return service.history(id);
  }

  @GetMapping("/categories")
  @Operation(summary = "Справочник категорий")
  public List<Category> categories() {
    return service.categories();
  }

  @GetMapping("/tickets")
  @Operation(summary = "Фильтрация и пагинация заявок")
  public TicketPage search(
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) @Positive Long categoryId,
      @RequestParam(required = false) Boolean overdue,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.search(status, priority, categoryId, overdue, from, to, page, size);
  }
}
