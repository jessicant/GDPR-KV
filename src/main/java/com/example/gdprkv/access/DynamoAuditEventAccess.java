package com.example.gdprkv.access;

import com.example.gdprkv.models.AuditEvent;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Component
public class DynamoAuditEventAccess implements AuditEventAccess {

    private final DynamoDbTable<AuditEvent> table;

    public DynamoAuditEventAccess(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("audit_events", TableSchema.fromBean(AuditEvent.class));
    }

    @Override
    public void put(AuditEvent event) {
        table.putItem(event);
    }

    @Override
    public Optional<AuditEvent> findLatest(String subjectId) {
        // Query the partition in reverse chronological order so the first item is the most recent.
        return table.query(r -> r.queryConditional(QueryConditional.keyEqualTo(buildKey(subjectId)))
                        .limit(1)
                        .scanIndexForward(false))
                .items()
                .stream()
                .findFirst();
    }

    private Key buildKey(String subjectId) {
        return Key.builder().partitionValue(subjectId).build();
    }
}
