package com.example.gdprkv.service;

import com.example.gdprkv.access.AuditEventAccess;
import com.example.gdprkv.models.AuditEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class InMemoryAuditEventAccess implements AuditEventAccess {

    private final ConcurrentHashMap<String, AuditEvent> events = new ConcurrentHashMap<>();

    @Override
    public void put(AuditEvent event) {
        String key = event.getSubjectId() + "#" + event.getTsUlid();
        events.put(key, event);
    }

    @Override
    public Optional<AuditEvent> findLatest(String subjectId) {
        return events.values().stream()
                .filter(e -> e.getSubjectId().equals(subjectId))
                .max(Comparator.comparing(AuditEvent::getTsUlid));
    }

    @Override
    public List<AuditEvent> findEventsOlderThan(long cutoffTimestamp) {
        return events.values().stream()
                .filter(e -> e.getTimestamp() < cutoffTimestamp)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(AuditEvent event) {
        String key = event.getSubjectId() + "#" + event.getTsUlid();
        events.remove(key);
    }

    public List<AuditEvent> getAllEvents() {
        return new ArrayList<>(events.values());
    }
}
