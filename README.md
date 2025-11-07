# GDPR-Friendly Key Value Store

[![CI](https://github.com/jessicant/GDPR-KV/actions/workflows/ci.yml/badge.svg)](https://github.com/jessicant/GDPR-KV/actions/workflows/ci.yml)

A subject-centric layer on DynamoDB that adds GDPR-oriented behaviors: right to erasure, strict retention, and a tamper-evident audit trail.

## Features

- **Subject-Centric Mapping** – enumerate all records for a data subject via `findAllBySubjectId()`.
- **Right to Erasure** – immediate read suppression (tombstone) plus background purge.
- **Retention Enforcement** – records deleted when retention expires; scheduled job deletes audit events older than configured retention period (default: 2 years).
- **Audit Trail** – append-only, hash-chained events for create, read, update, delete, and purge; retrieve complete audit history via `findAllBySubjectId()`.
- **Efficient Purging** – GSI `records_by_purge_due` uses `purge_bucket` (UTC hour shard) and `purge_due_at` to avoid scans or hot partitions.
- **Request Tracking** – all operations tagged with `X-Request-Id` header for end-to-end traceability.
- **GDPR Compliance** – minimal personal data in audit logs (no residency), configurable retention policies.

## Architecture

Tables:
- `subjects` – subject metadata (existence, residency, erasure flags).
- `policies` – purpose → retention mapping.
- `records` – per-subject items; includes `tombstoned`, `purge_due_at`, `purge_bucket`.
- `audit_events` – append-only, tamper-evident audit log.

Index:
- `records_by_purge_due` (GSI):
    - Partition key = `purge_bucket`
    - Sort key = `purge_due_at`
    - Sparse (only set for tombstoned records)

---

## Local Development

### Prerequisites
- Docker Desktop
- AWS CLI (and optionally [`awslocal`](https://github.com/localstack/awscli-local))

### Start LocalStack
```bash
docker compose up -d
```

### Set AWS Credentials
Export LocalStack credentials (they can be anything, `test` is conventional):
```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```
Windows PowerShell:
```powershell
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
```

> **Note:** These credentials are required for the AWS CLI to work, even though LocalStack doesn't validate them. Set them once per shell session.

### Initialize Tables
Run the provided script:
```bash
./scripts/init_tables.sh
```
Windows PowerShell:
```
./scripts/init_tables.ps1
```

This creates the following DynamoDB tables in LocalStack:
- `subjects`
- `policies`
- `records` (with GSI `records_by_purge_due`)
- `audit_events`

### Seed Demo Data
Run the seed script (creates the demo subject and policy `DEMO_PURPOSE`):
```bash
./scripts/seed_demo.sh
```
Windows PowerShell:
```powershell
./scripts/seed_demo.ps1
```

### Start the Application
Start the Spring Boot API locally (default port 8080):
```bash
./gradlew bootRun
```

Leave this running in a terminal. Once you see `Started GdprKvApplication`, the API is ready to accept requests.

### Create a Subject (via API)
Subjects must exist before records can be written for them. The demo seed already creates
`demo_subject_001`, but you can create your own subjects through the API:

```bash
curl -X PUT \
  -H "Content-Type: application/json" \
  http://localhost:8080/subjects/customer_123 \
  -d '{
    "residency": "US"
  }'
```

The request body is optional—omitting it creates a subject with default metadata. Reissuing the PUT
for an existing subject returns a `409 SUBJECT_ALREADY_EXISTS` error; subjects are immutable once
created.

### Put a Record (via API)
Once the subject exists (either via the seed script or the endpoint above) you can write a record
through the API. The service will return `404 SUBJECT_NOT_FOUND` if you attempt to write to a
non-existent subject.

With LocalStack and the app running, issue the request:

```bash
curl -X PUT \
  -H "Content-Type: application/json" \
  http://localhost:8080/subjects/demo_subject_001/records/pref:email \
  -d '{
    "purpose": "DEMO_PURPOSE",
    "value": { "email": "demo@example.com" }
  }'
```

Sample response:
```json
{
  "subject_id": "demo_subject_001",
  "record_key": "pref:email",
  "purpose": "DEMO_PURPOSE",
  "value": { "email": "demo@example.com" },
  "version": 2,
  "created_at": 1761258574353,
  "updated_at": 1761431775441,
  "retention_days": 1
}
```

The server generates an `X-Request-Id` (for audit and traceability) and returns it in the response headers; the JSON payload contains only the record fields.

### Retrieve All Data for a Subject
API endpoints for retrieving all records and audit events for a subject (GDPR subject access requests) are planned for a future update.

### Configure Audit Log Retention
Audit logs are retained for 2 years by default. To enable automatic deletion:

1. Edit `src/main/resources/application.yml`:
   ```yaml
   audit:
     retention:
       enabled: true  # Enable scheduled deletion job
       schedule: "0 0 2 * * *"  # Daily at 2am (cron format)
       retention-days: 730  # 2 years
   ```

2. The scheduled job will run automatically and delete audit events older than the retention period.

### Verify Results
Check that the record is gone:
```bash
awslocal dynamodb get-item \
  --table-name records \
  --key '{"subject_id":{"S":"demo_subject_001"},"record_key":{"S":"pref:email"}}'
```

Query the audit log to confirm purge events:
```bash
awslocal dynamodb query \
  --table-name audit_events \
  --key-condition-expression "subject_id=:s" \
  --expression-attribute-values '{":s":{"S":"demo_subject_001"}}' \
  --scan-index-forward false
```

## Planned Features

The following API endpoints are planned for future updates:

- **Subject Access Requests** – `GET /subjects/{subjectId}/records` to retrieve all records for a subject
- **Audit Trail Retrieval** – `GET /subjects/{subjectId}/audit-events` to retrieve complete audit history for a subject
- **Record Deletion** – `DELETE /subjects/{subjectId}/records/{recordKey}` to tombstone and schedule purge of a record
- **Subject Erasure** – `DELETE /subjects/{subjectId}` to mark subject for erasure and trigger deletion of all records

### Documentation
The full design (detailed flows, API shapes, and low-level sweeper design lives here): [Design Document](./doc/design.md)
