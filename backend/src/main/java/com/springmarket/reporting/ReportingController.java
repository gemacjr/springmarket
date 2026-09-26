package com.springmarket.reporting;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportingController {
    private final JobLauncher jobLauncher;
    private final Job dailySalesReportJob;
    private final Clock clock;

    ReportingController(
            JobLauncher jobLauncher,
            @Qualifier("dailySalesReportJob") Job dailySalesReportJob,
            Clock clock) {
        this.jobLauncher = jobLauncher;
        this.dailySalesReportJob = dailySalesReportJob;
        this.clock = clock;
    }

    @PostMapping("/daily-sales")
    ResponseEntity<ReportJobResponse> launchDailySalesReport(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date)
            throws Exception {
        LocalDate reportDate = date == null ? LocalDate.now(clock).minusDays(1) : date;
        JobExecution execution = jobLauncher.run(
                dailySalesReportJob,
                new JobParametersBuilder()
                        .addString("reportDate", reportDate.toString())
                        .addString("requestId", UUID.randomUUID().toString())
                        .toJobParameters());

        return ResponseEntity.ok(new ReportJobResponse(
                execution.getId(), execution.getStatus().name(), reportDate));
    }

    record ReportJobResponse(Long executionId, String status, LocalDate reportDate) {}
}
