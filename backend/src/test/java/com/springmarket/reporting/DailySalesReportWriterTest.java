package com.springmarket.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DailySalesReportWriterTest {
    @Test
    void usesPostgresAtomicConflictUpdate() {
        assertThat(DailySalesReportJobConfig.upsertSqlFor("PostgreSQL"))
                .contains("ON CONFLICT (report_date) DO UPDATE");
    }

    @Test
    void usesH2AtomicMergeForTests() {
        assertThat(DailySalesReportJobConfig.upsertSqlFor("H2"))
                .contains("MERGE INTO daily_sales_reports")
                .contains("KEY (report_date)");
    }
}
