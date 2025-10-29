package com.example.gdprkv.service;

import com.example.gdprkv.access.SubjectAccess;
import com.example.gdprkv.models.Subject;
import com.example.gdprkv.requests.PutSubjectServiceRequest;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Manages subject metadata, providing a create-once workflow plus lightweight existence checks
 * for downstream services.
 */
@Service
public class SubjectService {

    private final SubjectAccess subjectAccess;
    private final Clock clock;

    public SubjectService(SubjectAccess subjectAccess, Clock clock) {
        this.subjectAccess = subjectAccess;
        this.clock = clock;
    }

    public Subject putSubject(PutSubjectServiceRequest request) {
        Objects.requireNonNull(request, "request");

        long now = clock.millis();
        Subject subject = Subject.builder()
                .subjectId(request.subjectId())
                .createdAt(now)
                .residency(request.residency())
                .build();

        try {
            return subjectAccess.save(subject);
        } catch (ConditionalCheckFailedException ex) {
            throw GdprKvException.subjectAlreadyExists(request.subjectId());
        }
    }

    public boolean exists(String subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return subjectAccess.findBySubjectId(subjectId).isPresent();
    }
}
