package ru.practice.helpdesk

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ReportServiceTest {
    private val from = Instant.parse("2025-01-01T00:00:00Z")
    private val to = from.plusSeconds(7200)
    private val clock = Clock.fixed(from.plusSeconds(3600), ZoneOffset.UTC)
    private val source = mock(ReportSource::class.java)
    private val service = ReportService(source, clock)

    private fun ticket(
        id: Long,
        status: Status,
        priority: Priority,
        dueAt: Instant?,
        closedAt: Instant?,
    ) = Ticket(id, "Test", "", 1, priority, status, from, dueAt, closedAt)

    @Test
    fun emptySelectionHasZeroCountsAndNullAverage() {
        `when`(source.findCreatedBetween(from, to)).thenReturn(emptyList())
        val report = service.summary(from, to)
        assertEquals(0, report.createdCount)
        assertEquals(Status.entries.toSet(), report.byStatus.keys)
        assertTrue(report.byStatus.values.all { it == 0L })
        assertTrue(report.byPriority.values.all { it == 0L })
        assertEquals(0, report.overdueOpenCount)
        assertNull(report.averageResolutionSeconds)
    }

    @Test
    fun noResolvedTicketsHaveNullAverage() {
        `when`(source.findCreatedBetween(from, to))
            .thenReturn(
                listOf(
                    ticket(1, Status.NEW, Priority.LOW, null, null),
                    ticket(2, Status.CANCELLED, Priority.HIGH, from, from.plusSeconds(60)),
                )
            )
        val report = service.summary(from, to)
        assertNull(report.averageResolutionSeconds)
        assertEquals(0, report.overdueOpenCount)
    }

    @Test
    fun calculatesAllMetricsFromOneSelectionAndIgnoresCancellationDuration() {
        `when`(source.findCreatedBetween(from, to))
            .thenReturn(
                listOf(
                    ticket(1, Status.RESOLVED, Priority.HIGH, from, from.plusMillis(1500)),
                    ticket(2, Status.RESOLVED, Priority.LOW, null, from.plusMillis(4500)),
                    ticket(3, Status.NEW, Priority.HIGH, from.plusSeconds(100), null),
                    ticket(4, Status.IN_PROGRESS, Priority.MEDIUM, clock.instant(), null),
                    ticket(5, Status.CANCELLED, Priority.LOW, from, from.plusSeconds(10000)),
                )
            )
        val report = service.summary(from, to)
        assertEquals(5, report.createdCount)
        assertEquals(2, report.byStatus[Status.RESOLVED])
        assertEquals(2, report.byPriority[Priority.HIGH])
        assertEquals(1, report.overdueOpenCount)
        assertEquals(3.0, report.averageResolutionSeconds)
        verify(source, times(1)).findCreatedBetween(from, to)
        verifyNoMoreInteractions(source)
    }

    @Test
    fun preservesSubMillisecondResolutionRegression() {
        `when`(source.findCreatedBetween(from, to))
            .thenReturn(
                listOf(ticket(1, Status.RESOLVED, Priority.LOW, null, from.plusNanos(500000)))
            )
        assertEquals(0.0005, service.summary(from, to).averageResolutionSeconds)
    }

    @Test
    fun safelyHandlesMissingCloseTimeFromJavaInterface() {
        `when`(source.findCreatedBetween(from, to))
            .thenReturn(listOf(ticket(1, Status.RESOLVED, Priority.LOW, null, null)))
        assertNull(service.summary(from, to).averageResolutionSeconds)
    }

    @Test
    fun rejectsEqualAndReversedPeriods() {
        assertFailsWith<IllegalArgumentException> { service.summary(from, from) }
        assertFailsWith<IllegalArgumentException> { service.summary(to, from) }
        verifyNoInteractions(source)
    }

    @Test
    fun checksJavaTicketDeadlineBoundary() {
        assertFalse(
            ticket(1, Status.NEW, Priority.LOW, clock.instant(), null).isOverdue(clock.instant())
        )
        assertTrue(
            ticket(1, Status.NEW, Priority.LOW, clock.instant().minusNanos(1), null)
                .isOverdue(clock.instant())
        )
    }

    @Test
    fun kotlinInvokesJavaServiceAndEnforcesTerminalStatus() {
        val repository = mock(TicketRepository::class.java)
        val javaService = TicketService(repository, clock)
        `when`(repository.find(1, true))
            .thenReturn(ticket(1, Status.RESOLVED, Priority.LOW, null, from.plusSeconds(30)))
        val error =
            assertFailsWith<ApiException> { javaService.changeStatus(1, Status.IN_PROGRESS) }
        assertEquals(409, error.status.value())
        verify(repository, never()).recordHistory(anyLong(), any(), any(), any())
    }
}
