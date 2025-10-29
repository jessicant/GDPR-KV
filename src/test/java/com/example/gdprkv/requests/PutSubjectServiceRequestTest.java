package com.example.gdprkv.requests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PutSubjectServiceRequestTest {

    @Test
    @DisplayName("subjectId must be non-blank")
    void subjectIdValidation() {
        assertThrows(NullPointerException.class, () -> new PutSubjectServiceRequest(null, "US"));
        assertThrows(IllegalArgumentException.class, () -> new PutSubjectServiceRequest("  ", "US"));
    }

    @Test
    @DisplayName("allows optional residency")
    void allowsOptionalResidency() {
        assertDoesNotThrow(() -> new PutSubjectServiceRequest("sub", null));
        assertDoesNotThrow(() -> new PutSubjectServiceRequest("sub", "GB"));
    }
}
