package com.example.gdprkv.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gdprkv.access.AuditEventAccess;
import com.example.gdprkv.access.DynamoAuditEventAccess;
import com.example.gdprkv.access.DynamoPolicyAccess;
import com.example.gdprkv.access.DynamoRecordAccess;
import com.example.gdprkv.access.PolicyAccess;
import com.example.gdprkv.access.RecordAccess;
import com.example.gdprkv.models.AuditEvent;
import com.example.gdprkv.models.Policy;
import com.example.gdprkv.models.Record;
import com.example.gdprkv.service.AuditLogService;
import com.example.gdprkv.service.PolicyDrivenRecordService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Full-stack integration test that boots a DynamoDB-compatible endpoint via Testcontainers
 * (LocalStack) and exercises the {@link RecordController}. This ensures the real data access
 * implementations, audit log service, and controller wiring function end-to-end without
 * relying on mocks.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordControllerDynamoIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.6");

    @Container
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.DYNAMODB);

    private DynamoDbClient dynamo;
    private DynamoDbEnhancedClient enhancedClient;
    private PolicyAccess policyAccess;
    private RecordAccess recordAccess;
    private AuditEventAccess auditAccess;
    private AuditLogService auditLogService;
    private PolicyDrivenRecordService recordService;
    private RecordController controller;
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final Clock clock = Clock.fixed(Instant.parse("2024-10-02T08:00:00Z"), ZoneOffset.UTC);
    private Policy seededPolicy;

    @BeforeAll
    void init() {
        AwsBasicCredentials creds = AwsBasicCredentials.create(
                LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey());
        dynamo = DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
        enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamo).build();
        ensureTables();

        policyAccess = new DynamoPolicyAccess(enhancedClient);
        recordAccess = new DynamoRecordAccess(enhancedClient);
        auditAccess = new DynamoAuditEventAccess(enhancedClient);
        auditLogService = new AuditLogService(auditAccess, clock);
        recordService = new PolicyDrivenRecordService(policyAccess, recordAccess, clock);
        controller = new RecordController(recordService, auditLogService);
    }

    @BeforeEach
    void truncateTables() throws Exception {
        dynamo.deleteItem(b -> b.tableName("records")
                .key(java.util.Map.of(
                        "subject_id", attrS("sub_1"),
                        "record_key", attrS("pref:email"))));

        enhancedClient.table("audit_events", TableSchema.fromBean(AuditEvent.class))
                .scan().items()
                .forEach(item -> enhancedClient.table("audit_events", TableSchema.fromBean(AuditEvent.class)).deleteItem(item));

        seededPolicy = mapper.convertValue(readFixture("fixtures/policy.json"), Policy.class);
        enhancedClient.table("policies", TableSchema.fromBean(Policy.class)).putItem(seededPolicy);
    }

    @Test
    @DisplayName("PUT record persists data and emits audit events")
    void putRecordIntegration() throws Exception {
        PutRecordFixture fixture = mapper.convertValue(readFixture("fixtures/put_record_request.json"), PutRecordFixture.class);

        PutRecordRequest request = new PutRecordRequest(fixture.purpose(), fixture.value());

        ResponseEntity<RecordResponse> response = controller.putRecord(fixture.subjectId(), fixture.recordKey(), request);

        assertEquals(200, response.getStatusCode().value());
        RecordResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(fixture.subjectId(), body.subjectId());
        assertEquals(fixture.recordKey(), body.recordKey());
        assertEquals(fixture.purpose(), body.purpose());
        assertEquals(seededPolicy.getRetentionDays(), body.retentionDays());

        Optional<Record> persisted = recordAccess.findBySubjectIdAndRecordKey(fixture.subjectId(), fixture.recordKey());
        assertTrue(persisted.isPresent());
        assertEquals("demo@example.com", persisted.get().getValue().get("email").asText());

        List<AuditEvent> events = enhancedClient.table("audit_events", TableSchema.fromBean(AuditEvent.class))
                .scan().items().stream().toList();

        assertEquals(2, events.size());
        var eventTypes = events.stream().map(AuditEvent::getEventType).toList();
        assertTrue(eventTypes.contains(AuditEvent.EventType.PUT_REQUESTED));
        assertTrue(eventTypes.contains(AuditEvent.EventType.PUT_NEW_ITEM_SUCCESS));
        events.forEach(evt -> assertFalse(evt.getHash() == null || evt.getHash().isBlank()));
    }

    private void ensureTables() {
        createSubjectTable();
        createPoliciesTable();
        createRecordsTable();
        createAuditEventsTable();
    }

    private void createSubjectTable() {
        try {
            dynamo.describeTable(b -> b.tableName("subjects"));
        } catch (ResourceNotFoundException ex) {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName("subjects")
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("subject_id")
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("subject_id")
                            .keyType(KeyType.HASH)
                            .build())
                    .billingMode("PAY_PER_REQUEST")
                    .build());
        }
    }

    private void createPoliciesTable() {
        try {
            dynamo.describeTable(b -> b.tableName("policies"));
        } catch (ResourceNotFoundException ex) {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName("policies")
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("purpose")
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("purpose")
                            .keyType(KeyType.HASH)
                            .build())
                    .billingMode("PAY_PER_REQUEST")
                    .build());
        }
    }

    private void createRecordsTable() {
        try {
            dynamo.describeTable(b -> b.tableName("records"));
        } catch (ResourceNotFoundException ex) {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName("records")
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("subject_id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("record_key").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("purge_bucket").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("purge_due_at").attributeType(ScalarAttributeType.N).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("subject_id").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("record_key").keyType(KeyType.RANGE).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName("records_by_purge_due")
                            .keySchema(
                                    KeySchemaElement.builder().attributeName("purge_bucket").keyType(KeyType.HASH).build(),
                                    KeySchemaElement.builder().attributeName("purge_due_at").keyType(KeyType.RANGE).build())
                            .projection(b -> b.projectionType("KEYS_ONLY"))
                            .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
                            .build())
                    .billingMode("PAY_PER_REQUEST")
                    .build());
        }
    }

    private void createAuditEventsTable() {
        try {
            dynamo.describeTable(b -> b.tableName("audit_events"));
        } catch (ResourceNotFoundException ex) {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName("audit_events")
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("subject_id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("ts_ulid").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("subject_id").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("ts_ulid").keyType(KeyType.RANGE).build())
                    .billingMode("PAY_PER_REQUEST")
                    .build());
        }
    }

    private AttributeValue attrS(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private Map<String, Object> readFixture(String resourcePath) throws Exception {
        Path path = Path.of("src/test/resources", resourcePath);
        String json = Files.readString(path);
        return mapper.readValue(json, new TypeReference<>() {});
    }

    private record PutRecordFixture(
            @JsonProperty("subject_id") String subjectId,
            @JsonProperty("record_key") String recordKey,
            @JsonProperty("purpose") String purpose,
            @JsonProperty("value") JsonNode value
    ) {}
}
