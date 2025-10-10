#!/usr/bin/env bash
set -euo pipefail

REGION="us-west-2"
ENDPOINT="http://localhost:4566"
SUBJECT_ID="demo_subject_001"
RECORD_KEY="pref:email"
PURPOSE="DEMO_PURPOSE"
REQUEST_ID="seed-demo"
RETENTION_DAYS="${RETENTION_DAYS:-1}"

: "${AWS_ACCESS_KEY_ID:=test}"
: "${AWS_SECRET_ACCESS_KEY:=test}"
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY

usage() {
  cat <<'USAGE'
Usage: seed_demo.sh [--region REGION] [--endpoint URL]

Seeds LocalStack with a demo subject, policy, and purge-ready record.
  --region, -r    AWS region (default: us-west-2)
  --endpoint, -e  DynamoDB endpoint URL (default: http://localhost:4566)
  --help,   -h    Show this help message
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region|-r)
      REGION="$2"; shift 2 ;;
    --endpoint|--endpoint-url|-e)
      ENDPOINT="$2"; shift 2 ;;
    --help|-h)
      usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1 ;;
  esac
done

aws_local() {
  aws --endpoint-url "$ENDPOINT" --region "$REGION" "$@"
}

eval "$(python3 <<'PY'
import datetime, os, time
MILLIS_PER_DAY = 86400000
retention_days = int(os.environ.get('RETENTION_DAYS', '1'))
now_ms = int(time.time() * 1000)
tombstoned_at = now_ms - ((retention_days + 1) * MILLIS_PER_DAY)
purge_due_at = tombstoned_at + retention_days * MILLIS_PER_DAY
bucket = datetime.datetime.fromtimestamp(purge_due_at/1000, tz=datetime.timezone.utc).strftime('h#%Y%m%dT%H')
print(f"TOMBSTONE_MILLIS={tombstoned_at}")
print(f"PURGE_DUE_AT={purge_due_at}")
print(f"PURGE_BUCKET='{bucket}'")
PY
)"

created_at="$TOMBSTONE_MILLIS"
updated_at="$TOMBSTONE_MILLIS"
purge_due_at="$PURGE_DUE_AT"
purge_bucket="$PURGE_BUCKET"

printf 'Seeding demo data into %s (region %s)\n' "$ENDPOINT" "$REGION"

subject_item=$(cat <<JSON
{
  "subject_id": {"S": "$SUBJECT_ID"},
  "created_at": {"N": "$created_at"},
  "version": {"N": "1"}
}
JSON
)

policy_item=$(cat <<JSON
{
  "purpose": {"S": "$PURPOSE"},
  "retention_days": {"N": "$RETENTION_DAYS"},
  "description": {"S": "Demo retention policy"},
  "last_updated_at": {"N": "$created_at"}
}
JSON
)

record_item=$(cat <<JSON
{
  "subject_id": {"S": "$SUBJECT_ID"},
  "record_key": {"S": "$RECORD_KEY"},
  "purpose": {"S": "$PURPOSE"},
  "value": {"S": "{\\"email\\":\\"demo@example.com\\"}"},
  "created_at": {"N": "$created_at"},
  "updated_at": {"N": "$updated_at"},
  "version": {"N": "1"},
  "tombstoned": {"BOOL": true},
  "tombstoned_at": {"N": "$TOMBSTONE_MILLIS"},
  "retention_days": {"N": "$RETENTION_DAYS"},
  "purge_due_at": {"N": "$purge_due_at"},
  "purge_bucket": {"S": "$purge_bucket"},
  "request_id": {"S": "$REQUEST_ID"}
}
JSON
)

aws_local dynamodb put-item --table-name subjects --item "$subject_item"
aws_local dynamodb put-item --table-name policies --item "$policy_item"
aws_local dynamodb put-item --table-name records --item "$record_item"

printf 'Seed complete. Purge bucket: %s\n' "$purge_bucket"
