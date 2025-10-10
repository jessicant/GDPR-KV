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

Then run the seed script:
```bash
./scripts/seed_demo.sh
```
Windows Powershell:
```powershell
./scripts/seed_demo.ps1
```

> Tip: If you already have credentials or use `awslocal`, the scripts honour whatever is in your environment.

This inserts:
- One subject (`demo_subject_001`)
- One policy for purpose `DEMO_PURPOSE`
- One tombstoned record whose `purge_due_at` is already in the past

The script prints the computed `purge_bucket` so you can confirm the sweeper will target it immediately.

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
