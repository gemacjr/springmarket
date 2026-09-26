package com.springmarket.reporting;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DailySalesReportJobConfig {
    @Bean
    Job dailySalesReportJob(JobRepository jobRepository, Step dailySalesReportStep) {
        return new JobBuilder("dailySalesReportJob", jobRepository)
                .validator(parameters -> {
                    String value = parameters.getString("reportDate");
                    if (value == null) {
                        throw new IllegalArgumentException("reportDate job parameter is required");
                    }
                    LocalDate.parse(value);
                })
                .start(dailySalesReportStep)
                .build();
    }

    @Bean
    Step dailySalesReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcCursorItemReader<SalesAggregate> dailySalesReader,
            ItemProcessor<SalesAggregate, DailySalesReport> dailySalesProcessor,
            ItemWriter<DailySalesReport> dailySalesWriter) {
        return new StepBuilder("dailySalesReportStep", jobRepository)
                .<SalesAggregate, DailySalesReport>chunk(1, transactionManager)
                .reader(dailySalesReader)
                .processor(dailySalesProcessor)
                .writer(dailySalesWriter)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<SalesAggregate> dailySalesReader(
            DataSource dataSource,
            @Value("#{jobParameters['reportDate']}") String reportDateParameter) {
        LocalDate reportDate = LocalDate.parse(reportDateParameter);
        Instant start = reportDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return new JdbcCursorItemReaderBuilder<SalesAggregate>()
                .name("dailySalesReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT COUNT(*) AS order_count,
                               COALESCE(SUM(total_amount), 0) AS total_amount
                        FROM orders
                        WHERE status IN ('PAID', 'SHIPPED', 'DELIVERED')
                          AND created_at >= ?
                          AND created_at < ?
                        """)
                .preparedStatementSetter(statement -> {
                    statement.setTimestamp(1, Timestamp.from(start));
                    statement.setTimestamp(2, Timestamp.from(end));
                })
                .rowMapper((resultSet, rowNum) -> new SalesAggregate(
                        resultSet.getLong("order_count"),
                        resultSet.getBigDecimal("total_amount")))
                .build();
    }

    @Bean
    @StepScope
    ItemProcessor<SalesAggregate, DailySalesReport> dailySalesProcessor(
            @Value("#{jobParameters['reportDate']}") String reportDateParameter) {
        LocalDate reportDate = LocalDate.parse(reportDateParameter);
        return aggregate -> new DailySalesReport(
                reportDate, aggregate.orderCount(), aggregate.totalAmount());
    }

    @Bean
    ItemWriter<DailySalesReport> dailySalesWriter(DataSource dataSource, Clock clock) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return chunk -> {
            for (DailySalesReport report : chunk) {
                Timestamp generatedAt = Timestamp.from(clock.instant());
                int updated = jdbcTemplate.update(
                        """
                        UPDATE daily_sales_reports
                        SET order_count = ?, total_amount = ?, created_at = ?
                        WHERE report_date = ?
                        """,
                        report.orderCount(),
                        report.totalAmount(),
                        generatedAt,
                        Date.valueOf(report.reportDate()));
                if (updated == 0) {
                    jdbcTemplate.update(
                            """
                            INSERT INTO daily_sales_reports (
                                report_date, order_count, total_amount, created_at
                            ) VALUES (?, ?, ?, ?)
                            """,
                            Date.valueOf(report.reportDate()),
                            report.orderCount(),
                            report.totalAmount(),
                            generatedAt);
                }
            }
        };
    }

    record SalesAggregate(long orderCount, BigDecimal totalAmount) {}

    record DailySalesReport(LocalDate reportDate, long orderCount, BigDecimal totalAmount) {}
}
