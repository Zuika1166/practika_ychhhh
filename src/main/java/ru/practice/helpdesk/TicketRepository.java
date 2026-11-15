package ru.practice.helpdesk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TicketRepository implements ReportSource {
  private final JdbcTemplate jdbc;

  public TicketRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static final RowMapper<Ticket> MAPPER =
      (rs, row) ->
          new Ticket(
              rs.getLong("id"),
              rs.getString("title"),
              rs.getString("description"),
              rs.getLong("category_id"),
              Priority.valueOf(rs.getString("priority")),
              Status.valueOf(rs.getString("status")),
              instant(rs, "created_at"),
              instant(rs, "due_at"),
              instant(rs, "closed_at"));

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  public List<Category> categories() {
    return jdbc.query(
        "SELECT * FROM categories ORDER BY id",
        (rs, row) -> new Category(rs.getLong("id"), rs.getString("name")));
  }

  public boolean categoryExists(long id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM categories WHERE id=?)", Boolean.class, id));
  }

  public Ticket insert(CreateTicket request, Instant now) {
    return jdbc.queryForObject(
        """
        INSERT INTO tickets(title, description, category_id, priority, status, created_at, due_at)
        VALUES (?, ?, ?, ?, 'NEW', ?, ?) RETURNING *
        """,
        MAPPER,
        request.title().strip(),
        request.description() == null ? "" : request.description(),
        request.categoryId(),
        request.priority().name(),
        timestamp(now),
        timestamp(request.dueAt()));
  }

  public Ticket find(long id, boolean lock) {
    List<Ticket> result =
        jdbc.query("SELECT * FROM tickets WHERE id=?" + (lock ? " FOR UPDATE" : ""), MAPPER, id);
    return result.isEmpty() ? null : result.getFirst();
  }

  public Ticket edit(long id, EditTicket request) {
    return jdbc.queryForObject(
        "UPDATE tickets SET description=?, priority=?, due_at=? WHERE id=? RETURNING *",
        MAPPER,
        request.description(),
        request.priority().name(),
        timestamp(request.dueAt()),
        id);
  }

  public Ticket changeStatus(long id, Status status, Instant closedAt) {
    return jdbc.queryForObject(
        "UPDATE tickets SET status=?, closed_at=? WHERE id=? RETURNING *",
        MAPPER,
        status.name(),
        timestamp(closedAt),
        id);
  }

  public void recordHistory(long id, Status from, Status to, Instant now) {
    jdbc.update(
        "INSERT INTO status_history(ticket_id, from_status, to_status, changed_at) VALUES (?, ?, ?, ?)",
        id,
        from == null ? null : from.name(),
        to.name(),
        timestamp(now));
  }

  public List<HistoryEntry> history(long id) {
    return jdbc.query(
        "SELECT * FROM status_history WHERE ticket_id=? ORDER BY id",
        (rs, row) -> {
          String from = rs.getString("from_status");
          return new HistoryEntry(
              rs.getLong("id"),
              rs.getLong("ticket_id"),
              from == null ? null : Status.valueOf(from),
              Status.valueOf(rs.getString("to_status")),
              instant(rs, "changed_at"));
        },
        id);
  }

  @Override
  public List<Ticket> findCreatedBetween(Instant from, Instant to) {
    return jdbc.query(
        "SELECT * FROM tickets WHERE created_at>=? AND created_at<? ORDER BY id",
        MAPPER,
        timestamp(from),
        timestamp(to));
  }

  public TicketPage search(
      Status status,
      Priority priority,
      Long categoryId,
      Boolean overdue,
      Instant from,
      Instant to,
      Instant now,
      int page,
      int size) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> params = new ArrayList<>();
    if (status != null) {
      where.append(" AND status=?");
      params.add(status.name());
    }
    if (priority != null) {
      where.append(" AND priority=?");
      params.add(priority.name());
    }
    if (categoryId != null) {
      where.append(" AND category_id=?");
      params.add(categoryId);
    }
    if (from != null) {
      where.append(" AND created_at>=?");
      params.add(timestamp(from));
    }
    if (to != null) {
      where.append(" AND created_at<?");
      params.add(timestamp(to));
    }
    if (overdue != null) {
      where.append(
          overdue
              ? " AND (status IN ('NEW','IN_PROGRESS') AND due_at < ?)"
              : " AND NOT (status IN ('NEW','IN_PROGRESS') AND due_at IS NOT NULL AND due_at < ?)");
      params.add(timestamp(now));
    }
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM tickets" + where, Long.class, params.toArray());
    params.add(size);
    params.add((long) page * size);
    List<Ticket> items =
        jdbc.query(
            "SELECT * FROM tickets" + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            MAPPER,
            params.toArray());
    long count = total == null ? 0 : total;
    return new TicketPage(items, page, size, count, (count + size - 1) / size);
  }
}
