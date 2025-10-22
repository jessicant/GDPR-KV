package com.example.gdprkv.access;

import com.example.gdprkv.models.Record;
import java.util.Optional;

public interface RecordAccess {
    Optional<Record> findBySubjectIdAndRecordKey(String subjectId, String recordKey);

    Record save(Record record);
}
