package com.example.gdprkv.http;

import com.example.gdprkv.models.Subject;
import com.example.gdprkv.requests.PutSubjectHttpRequest;
import com.example.gdprkv.requests.PutSubjectServiceRequest;
import com.example.gdprkv.service.SubjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point for managing subject metadata. Subjects are immutable after creation;
 * attempting to recreate an existing subject yields a conflict response.
 */
@RestController
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PutMapping("/subjects/{subjectId}")
    public ResponseEntity<SubjectResponse> putSubject(
            @PathVariable String subjectId,
            @RequestBody(required = false) PutSubjectHttpRequest request
    ) {
        String residency = request != null ? request.residency() : null;
        Subject subject = subjectService.putSubject(new PutSubjectServiceRequest(subjectId, residency));
        return ResponseEntity.ok(map(subject));
    }

    private SubjectResponse map(Subject subject) {
        return new SubjectResponse(
                subject.getSubjectId(),
                subject.getCreatedAt(),
                subject.getResidency(),
                subject.getErasureInProgress(),
                subject.getErasureRequestedAt()
        );
    }
}
