param(
  [string]$Region   = "us-west-2",
  [string]$Endpoint = "http://localhost:4566"
)

$ErrorActionPreference = "Stop"

function awsLocal {
  param([Parameter(ValueFromRemainingArguments = $true)]$Args)
  aws --endpoint-url $Endpoint --region $Region @Args
}

function Write-TempJson {
  param([hashtable]$Obj)
  $json = $Obj | ConvertTo-Json -Depth 10 -Compress
  $tmp  = New-TemporaryFile
  [System.IO.File]::WriteAllText(
    $tmp.FullName,
    $json,
    (New-Object System.Text.UTF8Encoding($false)) # UTF-8, no BOM
  )
  return $tmp
}

Write-Host "Seeding demo data against $Endpoint (region $Region)"

# --------------------------------------------------------------------
# Demo values
# --------------------------------------------------------------------
$SubjectId    = "demo-subject-0001"
$Purpose      = "demo"          # Keep separate from your default 'analytics/support/auth'
$RetentionDays= 1               # Small so old tombstones are clearly due for purge
$CreatedIso   = "2020-01-01T00:00:00Z"  # Old date to ensure past-due
$UpdatedIso   = $CreatedIso
$DueEpoch     = 0               # Jan 1, 1970 => definitely due
$SortKey      = "record#$CreatedIso#v1"

# --------------------------------------------------------------------
# 1) Subject (subjects: HASH = subject_id)
# --------------------------------------------------------------------
$subjectItem = @{
  subject_id = @{ S = $SubjectId }
  created_at = @{ S = $CreatedIso }
}
$tmp = Write-TempJson -Obj $subjectItem
try {
  awsLocal dynamodb put-item --table-name subjects --item ("file://{0}" -f $tmp.FullName) 1>$null
  Write-Host "Seeded subject: $SubjectId"
} finally {
  Remove-Item $tmp.FullName -ErrorAction SilentlyContinue
}

# --------------------------------------------------------------------
# 2) Single Policy (policies: HASH = purpose)
# --------------------------------------------------------------------
$policyItem = @{
  purpose        = @{ S = $Purpose }
  retention_days = @{ N = "$RetentionDays" }
  created_at     = @{ S = $CreatedIso }
}
$tmp = Write-TempJson -Obj $policyItem
try {
  awsLocal dynamodb put-item --table-name policies --item ("file://{0}" -f $tmp.FullName) 1>$null
  Write-Host "Seeded policy: $Purpose (retention_days=$RetentionDays)"
} finally {
  Remove-Item $tmp.FullName -ErrorAction SilentlyContinue
}

# --------------------------------------------------------------------
# 3) Tombstoned record already due for purge (records: HASH = subject_id, RANGE = sort_key)
#    Fields kept minimal; add more as your app expects.
# --------------------------------------------------------------------
$recordItem = @{
  subject_id          = @{ S = $SubjectId }
  sort_key            = @{ S = $SortKey }
  purpose             = @{ S = $Purpose }
  version             = @{ N = "1" }
  tombstoned          = @{ BOOL = $true }
  created_at          = @{ S = $CreatedIso }
  updated_at          = @{ S = $UpdatedIso }
  due_for_purge_epoch = @{ N = "$DueEpoch" }
  data                = @{ M = @{ note = @{ S = "example payload removed" } } } # optional
}
$tmp = Write-TempJson -Obj $recordItem
try {
  awsLocal dynamodb put-item --table-name records --item ("file://{0}" -f $tmp.FullName) 1>$null
  Write-Host "Seeded tombstoned record for $SubjectId (due_for_purge=$DueEpoch)"
} finally {
  Remove-Item $tmp.FullName -ErrorAction SilentlyContinue
}

Write-Host "Demo seeding complete."
