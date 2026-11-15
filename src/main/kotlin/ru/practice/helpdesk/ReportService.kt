package ru.practice.helpdesk

import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class SummaryReport(
    val from: Instant,
    val to: Instant,
    val createdCount: Long,
    val byStatus: Map<Status, Long>,
    val byPriority: Map<Priority, Long>,
    val overdueOpenCount: Long,
    val averageResolutionSeconds: Double?,
)

@Service
class ReportService(private val source: ReportSource, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun summary(from: Instant, to: Instant): SummaryReport {
        require(from < to)
        val tickets = source.findCreatedBetween(from, to)
        val now = clock.instant()
        val statusCounts = tickets.groupingBy { it.status() }.eachCount()
        val priorityCounts = tickets.groupingBy { it.priority() }.eachCount()
        val durations =
            tickets
                .filter { it.status() == Status.RESOLVED }
                .mapNotNull { ticket ->
                    ticket.closedAt()?.let {
                        Duration.between(ticket.createdAt(), it).let { duration ->
                            duration.seconds.toDouble() + duration.nano / 1_000_000_000.0
                        }
                    }
                }
        return SummaryReport(
            from = from,
            to = to,
            createdCount = tickets.size.toLong(),
            byStatus = Status.entries.associateWith { (statusCounts[it] ?: 0).toLong() },
            byPriority = Priority.entries.associateWith { (priorityCounts[it] ?: 0).toLong() },
            overdueOpenCount = tickets.count { it.isOverdue(now) }.toLong(),
            averageResolutionSeconds = durations.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
