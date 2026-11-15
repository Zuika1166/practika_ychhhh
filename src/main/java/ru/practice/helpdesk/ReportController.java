package ru.practice.helpdesk;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  @Operation(summary = "Статистика по заявкам, созданным в периоде [from, to)")
  public SummaryReport summary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return service.summary(from, to);
  }
}
