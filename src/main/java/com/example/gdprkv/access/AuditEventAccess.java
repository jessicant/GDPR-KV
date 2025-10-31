package com.example.gdprkv.access;

import com.example.gdprkv.models.AuditEvent;
import java.util.Optional;

/**
 * Storage abstraction for the append-only {@code audit_events} table. Implementations
 * are responsible for persisting events and retrieving the latest hash for a subject so
 * the service layer can maintain the hash chain.
 */
public interface AuditEventAccess {
    void put(AuditEvent event);
    Optional<AuditEvent> findLatest(String subjectId);
}
