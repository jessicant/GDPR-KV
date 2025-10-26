package com.example.gdprkv.access;

import java.util.Optional;
import com.example.gdprkv.models.AuditEvent;

/**
 * Storage abstraction for the append-only {@code audit_events} table. Implementations
 * are responsible for persisting events and retrieving the latest hash for a subject so
 * the service layer can maintain the hash chain.
 */
public interface AuditEventAccess {
    void put(AuditEvent event);
    Optional<AuditEvent> findLatest(String subjectId);
}
