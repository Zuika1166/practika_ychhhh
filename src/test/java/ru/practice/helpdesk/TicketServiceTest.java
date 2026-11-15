package ru.practice.helpdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TicketServiceTest {
  private final TicketRepository repository = mock(TicketRepository.class);
  private final Instant now = Instant.parse("2025-01-15T12:00:00Z");
  private TicketService service;

  private Ticket ticket(Status status) {
    return new Ticket(
        1,
        "Test",
        "",
        1,
        Priority.HIGH,
        status,
        now.minusSeconds(60),
        now.plusSeconds(60),
        status.isOpen() ? null : now);
  }

  @BeforeEach
  void setup() {
    service = new TicketService(repository, Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void createsNewTicketWithInitialHistory() {
    var request = new CreateTicket("Test", "", 1L, Priority.HIGH, null);
    when(repository.categoryExists(1)).thenReturn(true);
    when(repository.insert(request, now)).thenReturn(ticket(Status.NEW));
    assertEquals(Status.NEW, service.create(request).status());
    verify(repository).recordHistory(1, null, Status.NEW, now);
  }

  @Test
  void rejectsMissingCategoryWithoutInsertion() {
    var error =
        assertThrows(
            ApiException.class,
            () -> service.create(new CreateTicket("Test", "", 999L, Priority.LOW, null)));
    assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void missingTicketIs404() {
    assertEquals(
        HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.get(999)).getStatus());
  }

  @Test
  void resolvedCannotReopenAndDoesNotWrite() {
    when(repository.find(1, true)).thenReturn(ticket(Status.RESOLVED));
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(ApiException.class, () -> service.changeStatus(1, Status.IN_PROGRESS))
            .getStatus());
    verify(repository, never()).changeStatus(anyLong(), any(), any());
    verify(repository, never()).recordHistory(anyLong(), any(), any(), any());
  }

  @Test
  void cancelledCannotBeEdited() {
    when(repository.find(1, true)).thenReturn(ticket(Status.CANCELLED));
    assertThrows(
        ApiException.class, () -> service.edit(1, new EditTicket("Changed", Priority.LOW, null)));
    verify(repository, never()).edit(anyLong(), any());
  }

  @Test
  void resolvedCannotBeEdited() {
    when(repository.find(1, true)).thenReturn(ticket(Status.RESOLVED));
    assertThrows(
        ApiException.class, () -> service.edit(1, new EditTicket("Changed", Priority.LOW, null)));
  }

  @Test
  void startsWorkWithoutClosingTimeAndRecordsPreviousStatus() {
    when(repository.find(1, true)).thenReturn(ticket(Status.NEW));
    when(repository.changeStatus(1, Status.IN_PROGRESS, null))
        .thenReturn(ticket(Status.IN_PROGRESS));
    assertEquals(Status.IN_PROGRESS, service.changeStatus(1, Status.IN_PROGRESS).status());
    verify(repository).recordHistory(1, Status.NEW, Status.IN_PROGRESS, now);
  }

  @Test
  void resolutionUsesInjectedClock() {
    when(repository.find(1, true)).thenReturn(ticket(Status.IN_PROGRESS));
    service.changeStatus(1, Status.RESOLVED);
    verify(repository).changeStatus(1, Status.RESOLVED, now);
    verify(repository).recordHistory(1, Status.IN_PROGRESS, Status.RESOLVED, now);
  }

  @Test
  void cancellationStoresClosedAt() {
    when(repository.find(1, true)).thenReturn(ticket(Status.NEW));
    service.changeStatus(1, Status.CANCELLED);
    verify(repository).changeStatus(1, Status.CANCELLED, now);
  }

  @Test
  void editsOpenTicket() {
    var request = new EditTicket("Changed", Priority.LOW, null);
    when(repository.find(1, true)).thenReturn(ticket(Status.IN_PROGRESS));
    service.edit(1, request);
    verify(repository).edit(1, request);
  }

  @Test
  void rejectsInvalidSearchPeriodBeforeQuery() {
    assertThrows(ApiException.class, () -> service.search(null, null, null, null, now, now, 0, 20));
    verify(repository, never())
        .search(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
  }
}
