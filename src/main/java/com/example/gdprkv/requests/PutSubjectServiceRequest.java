package com.example.gdprkv.requests;

import java.util.Objects;

public record PutSubjectServiceRequest(
        String subjectId,
        String residency
) {
    public PutSubjectServiceRequest {
        Objects.requireNonNull(subjectId, "subjectId");
        if (subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must be non-blank");
        }
    }
}
