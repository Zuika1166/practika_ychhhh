package ru.practice.helpdesk;

import java.time.Instant;
import java.util.List;

public interface ReportSource {
  List<Ticket> findCreatedBetween(Instant from, Instant to);
}
