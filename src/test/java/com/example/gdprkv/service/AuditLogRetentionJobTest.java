package com.example.gdprkv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gdprkv.config.AuditRetentionProperties;
import com.example.gdprkv.models.AuditEvent;
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

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-10-02T08:00:00Z"), ZoneOffset.UTC);

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
        AuditRetentionProperties props = new AuditRetentionProperties();
        props.setRetentionDays(730);
        InMemoryAuditEventAccess auditAccess = new InMemoryAuditEventAccess();
        AuditLogRetentionJob directJob = new AuditLogRetentionJob(CLOCK, props, auditAccess);

        assertDoesNotThrow(directJob::enforceRetentionPolicy,
                "Job should execute without throwing exceptions");
    }

    @Test
    @DisplayName("Job deletes events older than retention period")
    void deletesOldEvents() {
        AuditRetentionProperties props = new AuditRetentionProperties();
        props.setRetentionDays(30);
        InMemoryAuditEventAccess auditAccess = new InMemoryAuditEventAccess();

        long now = CLOCK.millis();
        long oldTimestamp = now - (60L * 86400000L); // 60 days old
        long recentTimestamp = now - (10L * 86400000L); // 10 days old

        AuditEvent oldEvent = AuditEvent.builder()
                .subjectId("sub1")
                .tsUlid(oldTimestamp + "_OLD")
                .eventType(AuditEvent.EventType.PUT_REQUESTED)
                .requestId("req-old")
                .timestamp(oldTimestamp)
                .prevHash("0".repeat(64))
                .build();

        AuditEvent recentEvent = AuditEvent.builder()
                .subjectId("sub1")
                .tsUlid(recentTimestamp + "_RECENT")
                .eventType(AuditEvent.EventType.PUT_REQUESTED)
                .requestId("req-recent")
                .timestamp(recentTimestamp)
                .prevHash("0".repeat(64))
                .build();

        auditAccess.put(oldEvent);
        auditAccess.put(recentEvent);

        AuditLogRetentionJob directJob = new AuditLogRetentionJob(CLOCK, props, auditAccess);
        directJob.enforceRetentionPolicy();

        assertEquals(1, auditAccess.getAllEvents().size(), "Should have 1 event remaining");
        assertEquals(recentEvent.getTsUlid(), auditAccess.getAllEvents().getFirst().getTsUlid(),
                "Recent event should be preserved");
    }

    @Test
    @DisplayName("Job preserves all events when none are older than retention period")
    void preservesRecentEvents() {
        AuditRetentionProperties props = new AuditRetentionProperties();
        props.setRetentionDays(30);
        InMemoryAuditEventAccess auditAccess = new InMemoryAuditEventAccess();

        long now = CLOCK.millis();
        long recentTimestamp1 = now - (10L * 86400000L);
        long recentTimestamp2 = now - (20L * 86400000L);

        AuditEvent event1 = AuditEvent.builder()
                .subjectId("sub1")
                .tsUlid(recentTimestamp1 + "_1")
                .eventType(AuditEvent.EventType.PUT_REQUESTED)
                .requestId("req-1")
                .timestamp(recentTimestamp1)
                .prevHash("0".repeat(64))
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .subjectId("sub1")
                .tsUlid(recentTimestamp2 + "_2")
                .eventType(AuditEvent.EventType.PUT_REQUESTED)
                .requestId("req-2")
                .timestamp(recentTimestamp2)
                .prevHash("0".repeat(64))
                .build();

        auditAccess.put(event1);
        auditAccess.put(event2);

        AuditLogRetentionJob directJob = new AuditLogRetentionJob(CLOCK, props, auditAccess);
        directJob.enforceRetentionPolicy();

        assertEquals(2, auditAccess.getAllEvents().size(), "Should preserve both recent events");
    }

    @Test
    @DisplayName("Job handles empty audit log gracefully")
    void handlesEmptyLog() {
        AuditRetentionProperties props = new AuditRetentionProperties();
        props.setRetentionDays(30);
        InMemoryAuditEventAccess auditAccess = new InMemoryAuditEventAccess();

        AuditLogRetentionJob directJob = new AuditLogRetentionJob(CLOCK, props, auditAccess);

        assertDoesNotThrow(directJob::enforceRetentionPolicy,
                "Job should handle empty audit log without errors");
        assertEquals(0, auditAccess.getAllEvents().size(), "Should remain empty");
    }
}
