# GDPR-Friendly Key Value Store

[![CI](https://github.com/jessicant/GDPR-KV/actions/workflows/ci.yml/badge.svg)](https://github.com/jessicant/GDPR-KV/actions/workflows/ci.yml)

A subject-centric layer on DynamoDB that adds GDPR-oriented behaviors: right to erasure, strict retention, and a tamper-evident audit trail.

## Features

- **Subject-Centric Mapping** – enumerate all records for a data subject.
- **Right to Erasure** – immediate read suppression (tombstone) plus background purge.
- **Retention Enforcement** – deletes occur when retention expires (not best-effort TTL).
- **Audit Trail** – append-only, hash-chained events for create, read, update, delete, and purge.
- **Efficient Purging** – GSI `records_by_purge_due` uses `purge_bucket` (UTC hour shard) and `purge_due_at` to avoid scans or hot partitions.

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
Export LocalStack credentials once per shell (they can be anything, `test` is conventional):
```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```
PowerShell:
```powershell
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
```

Then run the seed script (creates the demo subject and policy `DEMO_PURPOSE`):
```bash
./scripts/seed_demo.sh
```
Windows Powershell:
```powershell
./scripts/seed_demo.ps1
```

> Tip: If you already have credentials or use `awslocal`, the scripts honor whatever is in your environment.

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

1. Start the Spring Boot API locally (default port 8080):
   ```bash
   ./gradlew bootRun
   ```
   Leave this running in a terminal.

2. With LocalStack and the app running, issue the request:

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

### Documentation
The full design (deatiled flows, API shapes, and low-level sweeper design lives here): [Design Document](./doc/design.md)
