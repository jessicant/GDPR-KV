package com.example.gdprkv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "audit.retention.enabled=true",
    "audit.retention.schedule=0 0 2 * * *",
    "audit.retention.retention-days=730"
})
class AuditLogRetentionJobTest {

    @Autowired
    private ApplicationContext context;

    @Autowired(required = false)
    private AuditLogRetentionJob job;

    @Test
    @DisplayName("Job bean is created when retention is enabled")
    void jobBeanCreated() {
        boolean beanExists = context.containsBean("auditLogRetentionJob");
        assertTrue(beanExists,
                "AuditLogRetentionJob bean should exist when audit.retention.enabled=true");
        assertNotNull(job, "Job should be autowired");
    }

    @Test
    @DisplayName("Job can be invoked without throwing exceptions")
    void jobCanBeInvoked() {
        assertDoesNotThrow(() -> job.enforceRetentionPolicy(),
                "Job should execute without throwing exceptions");
    }

    @Test
    @DisplayName("Job can be constructed directly with dependencies")
    void directConstruction() {
        Clock clock = Clock.fixed(Instant.parse("2024-10-02T08:00:00Z"), ZoneOffset.UTC);
        com.example.gdprkv.config.AuditRetentionProperties props = new com.example.gdprkv.config.AuditRetentionProperties();
        props.setRetentionDays(730);
        AuditLogRetentionJob directJob = new AuditLogRetentionJob(clock, props);

        assertDoesNotThrow(directJob::enforceRetentionPolicy,
                "Job should execute without throwing exceptions");
    }
}
