package com.example.gdprkv.http;

import com.example.gdprkv.models.Record;
import com.example.gdprkv.service.PolicyDrivenRecordService;
import com.example.gdprkv.service.RecordWriteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordController {

    private final PolicyDrivenRecordService recordService;

    public RecordController(PolicyDrivenRecordService recordService) {
        this.recordService = recordService;
    }

    @PutMapping("/subjects/{subjectId}/records/{recordKey}")
    public ResponseEntity<RecordResponse> putRecord(
            @PathVariable String subjectId,
            @PathVariable String recordKey,
            @Valid @RequestBody PutRecordRequest request
    ) {

        RecordWriteRequest writeRequest = new RecordWriteRequest(
                subjectId,
                recordKey,
                request.purpose(),
                request.value()
        );

        Record record = recordService.putRecord(writeRequest);
        // use the id the service generated for logging/correlation
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, record.getVersion().toString())
                .body(map(record));
    }

    private RecordResponse map(Record record) {
        JsonNode value = record.getValue();
        return new RecordResponse(
                record.getSubjectId(),
                record.getRecordKey(),
                record.getPurpose(),
                value,
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getRetentionDays()
        );
    }
}
