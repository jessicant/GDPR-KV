package com.example.gdprkv.http;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gdprkv.models.Subject;
import com.example.gdprkv.requests.PutSubjectServiceRequest;
import com.example.gdprkv.service.AuditLogService;
import com.example.gdprkv.service.GdprKvException;
import com.example.gdprkv.service.SubjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = SubjectController.class)
class SubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubjectService subjectService;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    @DisplayName("PUT subject creates subject metadata")
    void putSubject() throws Exception {
        when(subjectService.putSubject(any())).thenReturn(Subject.builder()
                .subjectId("demo")
                .createdAt(1700000000000L)
                .residency("US")
                .requestId("test-req-123")
                .build());

        mockMvc.perform(MockMvcRequestBuilders.put("/subjects/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residency\":\"US\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.subject_id", equalTo("demo")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.residency", equalTo("US")));

        ArgumentCaptor<PutSubjectServiceRequest> captor = ArgumentCaptor.forClass(PutSubjectServiceRequest.class);
        verify(subjectService).putSubject(captor.capture());
        PutSubjectServiceRequest request = captor.getValue();
        assertEquals("demo", request.subjectId());
        assertEquals("US", request.residency());
    }

    @Test
    @DisplayName("PUT subject returns 409 when subject exists")
    void putSubjectConflict() throws Exception {
        when(subjectService.putSubject(any())).thenThrow(GdprKvException.subjectAlreadyExists("demo"));

        mockMvc.perform(MockMvcRequestBuilders.put("/subjects/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residency\":\"US\"}"))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code", equalTo("SUBJECT_ALREADY_EXISTS")));
    }
}
