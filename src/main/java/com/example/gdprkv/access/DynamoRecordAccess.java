package com.example.gdprkv.access;

import com.example.gdprkv.models.Record;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Component
public class DynamoRecordAccess implements RecordAccess {

    private final DynamoDbTable<Record> table;

    public DynamoRecordAccess(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("records", TableSchema.fromBean(Record.class));
    }

    @Override
    public Optional<Record> findBySubjectIdAndRecordKey(String subjectId, String recordKey) {
        return Optional.ofNullable(table.getItem(r -> r.key(Key.builder()
                .partitionValue(subjectId)
                .sortValue(recordKey)
                .build())));
    }

    @Override
    public Record save(Record record) {
        table.putItem(record);
        return record;
    }
}
