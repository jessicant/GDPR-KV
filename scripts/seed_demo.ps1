#!/usr/bin/env pwsh
param(
    [string]$Region = "us-west-2",
    [string]$Endpoint = "http://localhost:4566"
)

$subjectId = "demo_subject_001"
$recordKey = "pref:email"
$purpose = "DEMO_PURPOSE"
$requestId = "seed-demo"
$retentionDays = 1

$millisPerDay = 86400000
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$tombstonedAt = $now - (($retentionDays + 1) * $millisPerDay)
$purgeDueAt = $tombstonedAt + ($retentionDays * $millisPerDay)
$purgeBucket = [DateTimeOffset]::FromUnixTimeMilliseconds($purgeDueAt).ToString("'h#'yyyyMMdd'T'HH")

Write-Host "Seeding demo data into $Endpoint (region $Region)"

aws --endpoint-url $Endpoint --region $Region dynamodb put-item --table-name subjects --item @""{
  "subject_id": {"S": "$subjectId"},
  "created_at": {"N": "$tombstonedAt"},
  "version": {"N": "1"}
}""@

aws --endpoint-url $Endpoint --region $Region dynamodb put-item --table-name policies --item @""{
  "purpose": {"S": "$purpose"},
  "retention_days": {"N": "$retentionDays"},
  "description": {"S": "Demo retention policy"},
  "last_updated_at": {"N": "$tombstonedAt"}
}""@

aws --endpoint-url $Endpoint --region $Region dynamodb put-item --table-name records --item @""{
  "subject_id": {"S": "$subjectId"},
  "record_key": {"S": "$recordKey"},
  "purpose": {"S": "$purpose"},
  "value": {"S": "{\"email\":\"demo@example.com\"}"},
  "created_at": {"N": "$tombstonedAt"},
  "updated_at": {"N": "$tombstonedAt"},
  "version": {"N": "1"},
  "tombstoned": {"BOOL": true},
  "tombstoned_at": {"N": "$tombstonedAt"},
  "retention_days": {"N": "$retentionDays"},
  "purge_due_at": {"N": "$purgeDueAt"},
  "purge_bucket": {"S": "$purgeBucket"},
  "request_id": {"S": "$requestId"}
}""@

Write-Host "Seed complete. Purge bucket: $purgeBucket"
