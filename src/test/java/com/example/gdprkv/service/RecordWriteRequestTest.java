package com.example.gdprkv.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordWriteRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("subjectId validation rejects null/blank")
    void subjectIdValidation() {
        assertThrows(NullPointerException.class, () -> new RecordWriteRequest(
                null, "key", "PURPOSE", MAPPER.createObjectNode(), false, null, "req"));
        assertThrows(IllegalArgumentException.class, () -> new RecordWriteRequest(
                "  ", "key", "PURPOSE", MAPPER.createObjectNode(), false, null, "req"));
    }

    @Test
    @DisplayName("recordKey validation rejects null/blank")
    void recordKeyValidation() {
        assertThrows(NullPointerException.class, () -> new RecordWriteRequest(
                "sub", null, "PURPOSE", MAPPER.createObjectNode(), false, null, "req"));
        assertThrows(IllegalArgumentException.class, () -> new RecordWriteRequest(
                "sub", " ", "PURPOSE", MAPPER.createObjectNode(), false, null, "req"));
    }

    @Test
    @DisplayName("purpose validation rejects null/blank")
    void purposeValidation() {
        assertThrows(NullPointerException.class, () -> new RecordWriteRequest(
                "sub", "key", null, MAPPER.createObjectNode(), false, null, "req"));
        assertThrows(IllegalArgumentException.class, () -> new RecordWriteRequest(
                "sub", "key", " ", MAPPER.createObjectNode(), false, null, "req"));
    }

    @Test
    @DisplayName("requestId validation rejects null/blank")
    void requestIdValidation() {
        assertThrows(NullPointerException.class, () -> new RecordWriteRequest(
                "sub", "key", "PURPOSE", MAPPER.createObjectNode(), false, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RecordWriteRequest(
                "sub", "key", "PURPOSE", MAPPER.createObjectNode(), false, null, "  "));
    }

    @Test
    @DisplayName("valid request passes validation")
    void happyPath() {
        assertDoesNotThrow(() -> new RecordWriteRequest(
                "sub", "key", "PURPOSE", MAPPER.createObjectNode(), false, null, "req"));
    }
}
