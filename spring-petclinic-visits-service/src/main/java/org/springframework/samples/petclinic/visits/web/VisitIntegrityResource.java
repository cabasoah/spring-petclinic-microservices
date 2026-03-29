package org.springframework.samples.petclinic.visits.web;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VisitIntegrityResource {

    private static final String DISPLAY_SQL =
        "SELECT status, COUNT(*) FROM visits WHERE created_at > NOW() - INTERVAL '10 minutes' GROUP BY status;";

    private final VisitRepository visitRepository;

    VisitIntegrityResource(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @GetMapping("/debug/visits/integrity")
    VisitIntegrityReport readIntegrity(@RequestParam(defaultValue = "10") int minutes) {
        Date cutoff = Date.from(Instant.now().minus(minutes, ChronoUnit.MINUTES));
        List<StatusCountRow> rows = visitRepository.countStatusesSince(cutoff).stream()
            .map(result -> new StatusCountRow(result.getStatus(), result.getTotal()))
            .toList();
        long total = rows.stream().mapToLong(StatusCountRow::count).sum();

        return new VisitIntegrityReport(DISPLAY_SQL, minutes, rows, total);
    }

    record VisitIntegrityReport(
        String executedSql,
        int minutesWindow,
        List<StatusCountRow> rows,
        long total
    ) {
    }

    record StatusCountRow(
        String status,
        long count
    ) {
    }
}
