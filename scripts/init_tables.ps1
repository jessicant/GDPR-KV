param(
  [string]$Region   = "us-west-2",
  [string]$Endpoint = "http://localhost:4566"
)

$ErrorActionPreference = "Stop"

function awsLocal {
  param([Parameter(ValueFromRemainingArguments = $true)]$Args)
  aws --endpoint-url $Endpoint --region $Region @Args
}

Write-Host "Using AWS endpoint $Endpoint (region $Region)"

# 1) Wait for DynamoDB to be ready (probe DDB directly; no STS needed)
Write-Host "Waiting for DynamoDB to be ready..."
$max = 40
for ($i=0; $i -lt $max; $i++) {
  try {
    awsLocal dynamodb list-tables 1>$null 2>$null
    break
  } catch {
    Start-Sleep -Seconds 1
    if ($i -eq ($max - 1)) { throw "DynamoDB not responding on $Endpoint" }
  }
}

# 2) Helpers
function Test-TableExists {
  param([string]$TableName)
  $name = awsLocal dynamodb list-tables `
    --query "TableNames[?@=='$TableName'] | [0]" `
    --output text 2>$null
  return ($name -eq $TableName)
}

function Ensure-TableJson {
  param(
    [string]$TableName,
    [array]$AttributeDefinitions, # @(@{AttributeName="..."; AttributeType="S"}, ...)
    [array]$KeySchema             # @(@{AttributeName="..."; KeyType="HASH"}, @{...; KeyType="RANGE"})
  )

  if (Test-TableExists $TableName) {
    Write-Host "$TableName already exists"
    return
  }

  Write-Host "Creating $TableName..."

  $req = [ordered]@{
    TableName            = $TableName
    BillingMode          = "PAY_PER_REQUEST"
    AttributeDefinitions = $AttributeDefinitions
    KeySchema            = $KeySchema
  }

  # Write compact JSON to a temp file to avoid quoting/escaping issues
  $json = $req | ConvertTo-Json -Depth 6 -Compress
  $tmp  = New-TemporaryFile
  # Use ASCII/UTF8 without BOM to avoid BOM issues
  [System.IO.File]::WriteAllText($tmp.FullName, $json, (New-Object System.Text.UTF8Encoding($false)))

  try {
    awsLocal dynamodb create-table --cli-input-json ("file://{0}" -f $tmp.FullName) 1>$null
    awsLocal dynamodb wait table-exists --table-name $TableName
    Write-Host "$TableName created and ACTIVE"
  } finally {
    Remove-Item $tmp.FullName -ErrorAction SilentlyContinue
  }
}

function Upsert-Policy {
  param([string]$Purpose, [int]$Days)
  $item = @{
    purpose        = @{ S = "$Purpose" }
    retention_days = @{ N = "$Days" }
  }
  $json = $item | ConvertTo-Json -Depth 4 -Compress
  $tmp  = New-TemporaryFile
  [System.IO.File]::WriteAllText($tmp.FullName, $json, (New-Object System.Text.UTF8Encoding($false)))
  try {
    awsLocal dynamodb put-item --table-name policies --item ("file://{0}" -f $tmp.FullName) 1>$null
    Write-Host "Seeded policy: $Purpose = $Days days"
  } finally {
    Remove-Item $tmp.FullName -ErrorAction SilentlyContinue
  }
}

# 3) Create tables

# subjects: HASH = subject_id
Ensure-TableJson -TableName "subjects" `
  -AttributeDefinitions @(
    @{ AttributeName = "subject_id"; AttributeType = "S" }
  ) `
  -KeySchema @(
    @{ AttributeName = "subject_id"; KeyType = "HASH" }
  )

# policies: HASH = purpose
Ensure-TableJson -TableName "policies" `
  -AttributeDefinitions @(
    @{ AttributeName = "purpose"; AttributeType = "S" }
  ) `
  -KeySchema @(
    @{ AttributeName = "purpose"; KeyType = "HASH" }
  )

# records: HASH = subject_id, RANGE = sort_key
Ensure-TableJson -TableName "records" `
  -AttributeDefinitions @(
    @{ AttributeName = "subject_id"; AttributeType = "S" },
    @{ AttributeName = "sort_key";   AttributeType = "S" }
  ) `
  -KeySchema @(
    @{ AttributeName = "subject_id"; KeyType = "HASH"  },
    @{ AttributeName = "sort_key";   KeyType = "RANGE" }
  )

# audit_events: HASH = event_id
Ensure-TableJson -TableName "audit_events" `
  -AttributeDefinitions @(
    @{ AttributeName = "event_id"; AttributeType = "S" }
  ) `
  -KeySchema @(
    @{ AttributeName = "event_id"; KeyType = "HASH" }
  )

Write-Host "All done."
