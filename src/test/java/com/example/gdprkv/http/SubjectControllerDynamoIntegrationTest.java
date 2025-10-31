package com.example.gdprkv.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gdprkv.access.DynamoSubjectAccess;
import com.example.gdprkv.access.SubjectAccess;
import com.example.gdprkv.models.Subject;
import com.example.gdprkv.requests.PutSubjectHttpRequest;
import com.example.gdprkv.service.SubjectService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Full-stack integration test for {@link SubjectController} using LocalStack-backed DynamoDB.
 * Tests the complete flow: HTTP request → Controller → Service → SubjectAccess → DynamoDB.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubjectControllerDynamoIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.6");
    private static final String EXISTING_SUBJECT_ID = "intTest_existing";

    @Container
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.DYNAMODB);

    private DynamoDbClient dynamo;
    private DynamoDbEnhancedClient enhancedClient;
    private SubjectAccess subjectAccess;
    private SubjectService subjectService;
    private SubjectController controller;
    private final Clock clock = Clock.fixed(Instant.parse("2024-10-02T08:00:00Z"), ZoneOffset.UTC);

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
        ensureSubjectsTable();

        subjectAccess = new DynamoSubjectAccess(enhancedClient);
        subjectService = new SubjectService(subjectAccess, clock);
        controller = new SubjectController(subjectService);
    }

    @BeforeEach
    void seedExistingSubject() {
        Subject existingSubject = Subject.builder()
                .subjectId(EXISTING_SUBJECT_ID)
                .createdAt(clock.millis() - 10000L)
                .residency("US")
                .build();

        try {
            subjectAccess.save(existingSubject);
        } catch (Exception ignore) {
            // Subject may already exist from previous test run in LocalStack; that's fine
        }
    }

    @Test
    @DisplayName("PUT subject with random ID creates new subject and persists to DynamoDB")
    void putSubjectHappyPath() {
        String randomSubjectId = "test_" + UUID.randomUUID().toString().substring(0, 8);
        PutSubjectHttpRequest request = new PutSubjectHttpRequest("EU");

        ResponseEntity<SubjectResponse> response = controller.putSubject(randomSubjectId, request);

        assertEquals(200, response.getStatusCode().value());
        SubjectResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(randomSubjectId, body.subjectId());
        assertEquals("EU", body.residency());
        assertEquals(clock.millis(), body.createdAt());

        Optional<Subject> persisted = subjectAccess.findBySubjectId(randomSubjectId);
        assertTrue(persisted.isPresent());
        assertEquals(randomSubjectId, persisted.get().getSubjectId());
        assertEquals("EU", persisted.get().getResidency());
        assertEquals(clock.millis(), persisted.get().getCreatedAt());

        // Sanity check: verify LocalStack actually persisted to DynamoDB
        GetItemResponse dbResponse = dynamo.getItem(GetItemRequest.builder()
                .tableName("subjects")
                .key(java.util.Map.of("subject_id", AttributeValue.builder().s(randomSubjectId).build()))
                .build());
        assertTrue(dbResponse.hasItem());
        assertEquals(randomSubjectId, dbResponse.item().get("subject_id").s());
    }

    @Test
    @DisplayName("PUT subject with existing ID throws GdprKvException and leaves subject unchanged")
    void putSubjectConflict() {
        PutSubjectHttpRequest request = new PutSubjectHttpRequest("EU");

        try {
            controller.putSubject(EXISTING_SUBJECT_ID, request);
            throw new AssertionError("Expected GdprKvException to be thrown");
        } catch (com.example.gdprkv.service.GdprKvException ex) {
            assertEquals(com.example.gdprkv.service.GdprKvException.Code.SUBJECT_ALREADY_EXISTS, ex.getCode());
            assertTrue(ex.getMessage().contains(EXISTING_SUBJECT_ID));
        }

        Optional<Subject> unchanged = subjectAccess.findBySubjectId(EXISTING_SUBJECT_ID);
        assertTrue(unchanged.isPresent());
        assertEquals("US", unchanged.get().getResidency());
        assertEquals(clock.millis() - 10000L, unchanged.get().getCreatedAt());
    }

    @Test
    @DisplayName("PUT subject with null residency creates subject successfully")
    void putSubjectNullResidency() {
        String randomSubjectId = "test_" + UUID.randomUUID().toString().substring(0, 8);
        PutSubjectHttpRequest request = new PutSubjectHttpRequest(null);

        ResponseEntity<SubjectResponse> response = controller.putSubject(randomSubjectId, request);

        assertEquals(200, response.getStatusCode().value());
        SubjectResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(randomSubjectId, body.subjectId());
        assertNull(body.residency());

        Optional<Subject> persisted = subjectAccess.findBySubjectId(randomSubjectId);
        assertTrue(persisted.isPresent());
        assertNull(persisted.get().getResidency());
    }

    private void ensureSubjectsTable() {
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
}
