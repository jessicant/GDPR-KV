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
```bash
./scripts/seed_demo.sh
```
Windows Powershell:
```bash
./scripts/seed_demo.ps1
```

This inserts:
- One subject
- One policy
- One tombstoned record that is already due for purge
- Run the PurgeSweeper (manual invoke)

Invoke the sweeper Lambda once (NOT READY YET):
```bash
awslocal lambda invoke --function-name purge-sweeper out.json && cat out.json
```

You should see a JSON summary with the number of records deleted.

### Verify Results
Check that the record is gone:
```bash
awslocal dynamodb get-item \
  --table-name records \
  --key '{"subject_id":{"S":"sub_demo"},"record_key":{"S":"pref:email"}}'
```

Query the audit log to confirm purge events:
```bash
awslocal dynamodb query \
  --table-name audit_events \
  --key-condition-expression "subject_id=:s" \
  --expression-attribute-values '{":s":{"S":"sub_demo"}}' \
  --scan-index-forward false
```

### Documentation
The full design (deatiled flows, API shapes, and low-level sweeper design lives here): [Design Document](./doc/design.md)
