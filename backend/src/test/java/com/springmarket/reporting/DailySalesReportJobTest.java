package com.springmarket.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DailySalesReportJobTest {
    private static final UUID BUYER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("dailySalesReportJob")
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS daily_sales_reports (
                    report_date DATE PRIMARY KEY,
                    order_count BIGINT NOT NULL,
                    total_amount NUMERIC(19,2) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.update("DELETE FROM daily_sales_reports");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                BUYER_ID,
                "batch-buyer@example.com",
                "not-used",
                true,
                Timestamp.from(Instant.parse("2026-09-25T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-25T00:00:00Z")));

        seedOrder("PAID", "10.00", "2026-09-25T01:00:00Z");
        seedOrder("SHIPPED", "20.50", "2026-09-25T12:00:00Z");
        seedOrder("DELIVERED", "4.50", "2026-09-25T23:59:59Z");
        seedOrder("PENDING", "100.00", "2026-09-25T10:00:00Z");
        seedOrder("PAID", "200.00", "2026-09-24T23:59:59Z");
    }

    @Test
    void writesSummaryForCompletedOrdersOnReportDate() throws Exception {
        JobExecution execution = jobLauncher.run(
                job,
                new JobParametersBuilder()
                        .addString("reportDate", "2026-09-25")
                        .addLong("run.id", System.nanoTime())
                        .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Map<String, Object> report = jdbcTemplate.queryForMap(
                """
                SELECT order_count, total_amount
                FROM daily_sales_reports
                WHERE report_date = DATE '2026-09-25'
                """);
        assertThat(((Number) report.get("order_count")).longValue()).isEqualTo(3);
        assertThat((BigDecimal) report.get("total_amount"))
                .isEqualByComparingTo("35.00");
    }

    @Test
    void atomicallyReplacesExistingReportForDate() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO daily_sales_reports (
                    report_date, order_count, total_amount, created_at
                ) VALUES (DATE '2026-09-25', 99, 999.00, CURRENT_TIMESTAMP)
                """);

        JobExecution execution = jobLauncher.run(
                job,
                new JobParametersBuilder()
                        .addString("reportDate", "2026-09-25")
                        .addLong("run.id", System.nanoTime())
                        .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Map<String, Object> report = jdbcTemplate.queryForMap(
                """
                SELECT order_count, total_amount
                FROM daily_sales_reports
                WHERE report_date = DATE '2026-09-25'
                """);
        assertThat(((Number) report.get("order_count")).longValue()).isEqualTo(3);
        assertThat((BigDecimal) report.get("total_amount"))
                .isEqualByComparingTo("35.00");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM daily_sales_reports", Long.class))
                .isEqualTo(1L);
    }

    private void seedOrder(String status, String totalAmount, String createdAt) {
        Instant timestamp = Instant.parse(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    id, buyer_id, status, total_amount, currency, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                BUYER_ID,
                status,
                new BigDecimal(totalAmount),
                "USD",
                Timestamp.from(timestamp),
                Timestamp.from(timestamp));
    }
}
