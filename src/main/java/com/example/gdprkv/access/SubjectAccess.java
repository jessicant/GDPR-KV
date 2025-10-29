package com.example.gdprkv.access;

import com.example.gdprkv.models.Subject;
import java.util.Optional;

public interface SubjectAccess {

    Optional<Subject> findBySubjectId(String subjectId);

    Subject save(Subject subject);
}
