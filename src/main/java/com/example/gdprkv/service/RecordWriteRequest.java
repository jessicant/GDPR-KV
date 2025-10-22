package com.example.gdprkv.service;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

public record RecordWriteRequest(
        String subjectId,
        String recordKey,
        String purpose,
        JsonNode value,
        boolean tombstoned,
        Long tombstonedAt,
        String requestId
) {
    public RecordWriteRequest {
        Objects.requireNonNull(subjectId, "subjectId");
        if (subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must be non-blank");
        }

        Objects.requireNonNull(recordKey, "recordKey");
        if (recordKey.isBlank()) {
            throw new IllegalArgumentException("recordKey must be non-blank");
        }

        Objects.requireNonNull(purpose, "purpose");
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must be non-blank");
        }

        Objects.requireNonNull(requestId, "requestId");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must be non-blank");
        }
    }
}
