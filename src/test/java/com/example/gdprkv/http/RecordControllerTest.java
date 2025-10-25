package com.example.gdprkv.http;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gdprkv.models.Record;
import com.example.gdprkv.service.GdprKvException;
import com.example.gdprkv.service.PolicyDrivenRecordService;
import com.example.gdprkv.service.RecordWriteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = RecordController.class)
@Import(RequestIdFilter.class)
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PolicyDrivenRecordService recordService;

    @Test
    @DisplayName("PUT record returns 200 and response body")
    void putRecordSuccess() throws Exception {
        when(recordService.putRecord(any())).thenAnswer(invocation -> {
            RecordWriteRequest req = invocation.getArgument(0);
            return Record.builder()
                    .subjectId(req.subjectId())
                    .recordKey(req.recordKey())
                    .purpose(req.purpose())
                    .value(objectMapper.readTree("{\"email\":\"demo@example.com\"}"))
                    .createdAt(Instant.parse("2024-09-01T10:00:00Z").toEpochMilli())
                    .updatedAt(Instant.parse("2024-09-01T10:05:00Z").toEpochMilli())
                    .version(2L)
                    .retentionDays(30)
                    .tombstoned(false)
                    .requestId(req.requestId())
                    .build();
        });

        String body = "{" +
                "\"purpose\":\"FULFILLMENT\"," +
                "\"value\":{\"email\":\"demo@example.com\"}" +
                "}";

        mockMvc.perform(MockMvcRequestBuilders.put("/subjects/sub_123/records/pref:email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("ETag", equalTo("2")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.subject_id", equalTo("sub_123")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.record_key", equalTo("pref:email")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.purpose", equalTo("FULFILLMENT")))
                .andReturn();

        ArgumentCaptor<RecordWriteRequest> captor = ArgumentCaptor.forClass(RecordWriteRequest.class);
        verify(recordService, times(1)).putRecord(captor.capture());
        RecordWriteRequest writeRequest = captor.getValue();
        assertEquals("sub_123", writeRequest.subjectId());
        assertEquals("pref:email", writeRequest.recordKey());
        assertEquals("FULFILLMENT", writeRequest.purpose());
        assertFalse(writeRequest.requestId().isBlank());

    }

    @Test
    @DisplayName("PUT record returns 400 when invalid purpose")
    void putRecordInvalidPurpose() throws Exception {
        when(recordService.putRecord(any())).thenThrow(GdprKvException.invalidPurpose("UNKNOWN"));

        String body = "{" +
                "\"purpose\":\"UNKNOWN\"," +
                "\"value\":{}" +
                "}";

        mockMvc.perform(MockMvcRequestBuilders.put("/subjects/sub_999/records/pref:email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code", equalTo("INVALID_PURPOSE")));
    }

    @Test
    @DisplayName("PUT record emits filter-generated request id when none supplied")
    void putRecordGeneratesRequestId() throws Exception {
        ArgumentCaptor<RecordWriteRequest> captor = ArgumentCaptor.forClass(RecordWriteRequest.class);

        when(recordService.putRecord(any())).thenAnswer(invocation -> {
            RecordWriteRequest req = invocation.getArgument(0);
            return Record.builder()
                    .subjectId(req.subjectId())
                    .recordKey(req.recordKey())
                    .purpose(req.purpose())
                    .requestId(req.requestId())
                    .createdAt(1L)
                    .updatedAt(1L)
                    .version(1L)
                    .retentionDays(30)
                    .tombstoned(false)
                    .build();
        });

        String body = "{" +
                "\"purpose\":\"FULFILLMENT\"," +
                "\"value\":{}" +
                "}";

        var result = mockMvc.perform(MockMvcRequestBuilders.put("/subjects/sub_abc/records/pref:sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        verify(recordService).putRecord(captor.capture());
        RecordWriteRequest writeRequest = captor.getValue();
        assertEquals("sub_abc", writeRequest.subjectId());
        assertFalse(writeRequest.requestId().isBlank());

        String headerId = result.getResponse().getHeader("X-Request-Id");
        assertFalse(headerId == null || headerId.isBlank());
    }
}
