package com.example.gdprkv.service;

import com.example.gdprkv.access.AuditEventAccess;
import com.example.gdprkv.models.AuditEvent;
import com.example.gdprkv.models.Record;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String ZERO_HASH = "0".repeat(64);

    private final AuditEventAccess auditEventAccess;
    private final Clock clock;

    public void recordPutRequested(String subjectId,
                                   String recordKey,
                                   String purpose,
                                   String requestId) {
        appendEvent(subjectId, recordKey, purpose, requestId, AuditEvent.EventType.PUT_REQUESTED, null);
    }

    public void recordPutSuccess(Record record) {
        AuditEvent.EventType type = (record.getVersion() != null && record.getVersion() > 1)
                ? AuditEvent.EventType.PUT_UPDATE_ITEM_SUCCESS
                : AuditEvent.EventType.PUT_NEW_ITEM_SUCCESS;
        appendEvent(
                record.getSubjectId(),
                record.getRecordKey(),
                record.getPurpose(),
                record.getRequestId(),
                type,
                Map.of("version", record.getVersion())
        );
    }

    public void recordPutFailure(String subjectId,
                                 String recordKey,
                                 String purpose,
                                 String requestId,
                                 String errorMessage) {
        appendEvent(subjectId, recordKey, purpose, requestId,
                AuditEvent.EventType.PUT_FAILED,
                errorMessage == null ? null : Map.of("error", errorMessage));
    }

    /**
     * Appends an audit event, maintaining the per-subject hash chain by
     * reusing the most recent hash (or a zero value if this is the first event).
     */
    private void appendEvent(String subjectId,
                             String recordKey,
                             String purpose,
                             String requestId,
                             AuditEvent.EventType type,
                             Map<String, Object> details) {
        long now = clock.millis();
        String prevHash = auditEventAccess.findLatest(subjectId)
                .map(AuditEvent::getHash)
                .orElse(ZERO_HASH);

        AuditEvent event = AuditEvent.builder()
                .subjectId(subjectId)
                .tsUlid(generateTimestampUlid(now))
                .eventType(type)
                .requestId(requestId)
                .timestamp(now)
                .prevHash(prevHash)
                .itemKey(recordKey)
                .purpose(purpose)
                .details(details)
                .build();

        auditEventAccess.put(event);
    }

    /**
     * Generates a ULID-like string combining the millisecond timestamp and
     * a random component so events retain a natural sort order per subject.
     */
    private String generateTimestampUlid(long timestamp) {
        String random = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return timestamp + "_" + random;
    }
}
