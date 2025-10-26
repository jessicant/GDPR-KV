package com.example.gdprkv.service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.example.gdprkv.access.PolicyAccess;
import com.example.gdprkv.access.RecordAccess;
import com.example.gdprkv.models.Policy;
import com.example.gdprkv.models.Record;
import com.example.gdprkv.requests.PutRecordServiceRequest;
import org.springframework.stereotype.Service;

/**
 * Applies policy constraints when writing records, handling versioning, tombstone metadata,
 * retention calculations, and persistence while delegating storage to the configured access ports.
 */
@Service
public class PolicyDrivenRecordService {

    private final PolicyAccess policyAccess;
    private final RecordAccess recordAccess;
    private final Clock clock;

    public PolicyDrivenRecordService(PolicyAccess policyAccess,
                                     RecordAccess recordAccess,
                                     Clock clock) {
        this.policyAccess = policyAccess;
        this.recordAccess = recordAccess;
        this.clock = clock;
    }

    public Record putRecord(PutRecordServiceRequest request) {
        Objects.requireNonNull(request, "request");

        Policy policy = policyAccess.findByPurpose(request.purpose())
                .orElseThrow(() -> GdprKvException.invalidPurpose(request.purpose()));

        long now = clock.millis();
        Optional<Record> existingRecord = recordAccess
                .findBySubjectIdAndRecordKey(request.subjectId(), request.recordKey());

        long createdAt = existingRecord.map(Record::getCreatedAt).orElse(now);
        long version = existingRecord.map(Record::getVersion).orElse(0L) + 1;

        Record.RecordBuilder builder = existingRecord
                .map(Record::toBuilder)
                .orElseGet(() -> Record.builder()
                        .subjectId(request.subjectId())
                        .recordKey(request.recordKey()));

        builder.purpose(request.purpose())
                .value(request.value())
                .createdAt(createdAt)
                .updatedAt(now)
                .version(version)
                .requestId(request.requestId())
                .retentionDays(policy.getRetentionDays());

        if (request.tombstoned()) {
            long tombstonedAt = request.tombstonedAt() != null ? request.tombstonedAt() : now;
            long purgeDueAt = Record.calculatePurgeDueAt(tombstonedAt, policy.getRetentionDays());
            builder.tombstoned(true)
                    .tombstonedAt(tombstonedAt)
                    .purgeDueAt(purgeDueAt)
                    .purgeBucket(Record.formatPurgeBucket(purgeDueAt));
        } else {
            builder.tombstoned(false)
                    .tombstonedAt(null)
                    .purgeDueAt(null)
                    .purgeBucket(null);
        }

        Record toSave = builder.build();
        recordAccess.save(toSave);
        return toSave;
    }
}
