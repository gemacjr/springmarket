package com.springmarket.reporting;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportingControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobLauncher jobLauncher;

    @Test
    void adminCanLaunchDailySalesReport() throws Exception {
        when(jobLauncher.run(any(), any()))
                .thenReturn(new JobExecution(1L, new JobParameters()));

        mockMvc.perform(post("/api/v1/admin/reports/daily-sales")
                        .param("date", "2026-09-25")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isAccepted());
    }

    @Test
    void nonAdminCannotLaunchDailySalesReport() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reports/daily-sales")
                        .param("date", "2026-09-25")
                        .with(user("buyer").roles("BUYER")))
                .andExpect(status().isForbidden());
    }
}
