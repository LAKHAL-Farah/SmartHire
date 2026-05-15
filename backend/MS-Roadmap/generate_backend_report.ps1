Set-Location "$PSScriptRoot\.."
$report = "MS-Roadmap\backend_java_inventory_report.txt"
$root = "MS-Roadmap\src\main\java"
$files = Get-ChildItem $root -Recurse -File -Filter *.java | Sort-Object FullName

function Get-ClassType($path) {
  if ($path -match "\\Entities\\") { return "Entity" }
  if ($path -match "\\Enums\\") { return "Enum" }
  if ($path -match "\\Repositories\\") { return "Repository" }
  if ($path -match "\\ServicesImpl\\") { return "Service Interface" }
  if ($path -match "\\Services\\") { return "Service Impl" }
  if ($path -match "\\Controllers\\") { return "Controller" }
  if ($path -match "\\DTO\\") { return "DTO" }
  if ($path -match "\\config\\") { return "Config" }
  return "Other"
}

function Get-Package($content) {
  $p = $content | Where-Object { $_ -match '^\s*package\s+.+;' } | Select-Object -First 1
  if ($p) { return ($p -replace '^\s*package\s+','' -replace ';','').Trim() }
  return ""
}

function Get-FieldLines($content) {
  $fields = @()
  foreach ($l in $content) {
    $line = $l.Trim()
    if ($line -match '^private\s+[^\(\)]+;\s*$') { $fields += $line }
  }
  return $fields
}

function Get-MethodSignatures($content) {
  $methods = @()
  foreach ($l in $content) {
    $line = $l.Trim()
    if ($line -match '^(public|protected|private|default)\s+[^=]*\([^;]*\)\s*(\{|;)$') {
      $methods += ($line -replace '\s*\{$',';')
    }
  }
  return $methods
}

function Join-PathParts($a,$b) {
  if ([string]::IsNullOrWhiteSpace($a)) { return $b }
  if ([string]::IsNullOrWhiteSpace($b)) { return $a }
  $left = $a.TrimEnd('/')
  $right = $b.TrimStart('/')
  return "$left/$right"
}

$stubMap = @{}
$endpointRows = New-Object System.Collections.Generic.List[object]
$entityLines = New-Object System.Collections.Generic.List[string]

foreach ($f in $files | Where-Object { $_.FullName -match '\\Services\\.*ServiceImpl\.java$' }) {
  $c = Get-Content $f.FullName
  $className = $f.BaseName
  for ($i=0; $i -lt $c.Count; $i++) {
    $line = $c[$i].Trim()
    if ($line -match '^public\s+[^=]*\(([^)]*)\)\s*\{\s*$') {
      $sig = $line
      $methodName = ([regex]::Match($sig,'([A-Za-z0-9_]+)\s*\(').Groups[1].Value)
      $brace = 1
      $body = @()
      for ($j=$i+1; $j -lt $c.Count; $j++) {
        $body += $c[$j]
        $brace += ([regex]::Matches($c[$j],'\{')).Count
        $brace -= ([regex]::Matches($c[$j],'\}')).Count
        if ($brace -eq 0) { break }
      }
      $bt = ($body -join "`n")
      $reason = $null
      if ($bt -match 'UnsupportedOperationException') { $reason = 'throws UnsupportedOperationException' }
      elseif ($bt -match 'TODO|stub') { $reason = 'contains TODO/stub logic' }
      elseif ($bt -match 'return\s+null\s*;') { $reason = 'returns null' }
      elseif (($bt -replace '[\s\{\}]','').Length -eq 0) { $reason = 'body is empty' }
      if ($reason) { $stubMap["$className.$methodName"] = $reason }
    }
  }
}

$sb = New-Object System.Text.StringBuilder
$null = $sb.AppendLine("MS-ROADMAP JAVA INVENTORY")
$null = $sb.AppendLine("TOTAL JAVA FILES: $($files.Count)")
$null = $sb.AppendLine("")

foreach ($f in $files) {
  $rel = $f.FullName.Replace((Resolve-Path '.').Path + '\\','')
  $c = Get-Content $f.FullName
  $pkg = Get-Package $c
  $ctype = Get-ClassType $f.FullName

  $null = $sb.AppendLine("=== $rel ===")
  $null = $sb.AppendLine("PACKAGE: $pkg")
  $null = $sb.AppendLine("CLASS TYPE: $ctype")
  $null = $sb.AppendLine("KEY CONTENT:")

  if ($ctype -eq 'Entity' -or $ctype -eq 'DTO') {
    $fields = Get-FieldLines $c
    if ($fields.Count -eq 0) {
      $recordLine = $c | Where-Object { $_ -match '\brecord\b' } | Select-Object -First 1
      if ($recordLine) { $null = $sb.AppendLine("- $($recordLine.Trim())") }
      else { $null = $sb.AppendLine("- (no fields detected)") }
    } else {
      foreach ($fl in $fields) { $null = $sb.AppendLine("- $fl") }
    }
  } elseif ($ctype -eq 'Enum') {
    $enumVals = $c | Where-Object { $_.Trim() -match '^[A-Z0-9_]+\s*(,|;)$' } | ForEach-Object { $_.Trim().TrimEnd(',',';') }
    if ($enumVals.Count -eq 0) { $null = $sb.AppendLine("- (enum values not detected)") }
    else { foreach ($e in $enumVals) { $null = $sb.AppendLine("- $e") } }
  } elseif ($ctype -eq 'Repository' -or $ctype -eq 'Service Interface' -or $ctype -eq 'Service Impl' -or $ctype -eq 'Other' -or $ctype -eq 'Config') {
    $methods = Get-MethodSignatures $c
    if ($methods.Count -eq 0) { $null = $sb.AppendLine("- (no methods detected)") }
    else { foreach ($m in $methods) { $null = $sb.AppendLine("- $m") } }
  }

  if ($ctype -eq 'Controller') {
    $base = ""
    $className = $f.BaseName
    foreach ($line in $c) {
      $t = $line.Trim()
      if ($t -match '^@RequestMapping\("([^"]*)"\)') { $base = $matches[1] }
    }

    for ($i=0; $i -lt $c.Count; $i++) {
      $ln = $c[$i].Trim()
      $method = $null
      $sub = ""
      if ($ln -match '^@GetMapping(?:\("([^"]*)"\))?') { $method='GET'; $sub = $matches[1] }
      elseif ($ln -match '^@PostMapping(?:\("([^"]*)"\))?') { $method='POST'; $sub = $matches[1] }
      elseif ($ln -match '^@PutMapping(?:\("([^"]*)"\))?') { $method='PUT'; $sub = $matches[1] }
      elseif ($ln -match '^@DeleteMapping(?:\("([^"]*)"\))?') { $method='DELETE'; $sub = $matches[1] }
      elseif ($ln -match '^@PatchMapping(?:\("([^"]*)"\))?') { $method='PATCH'; $sub = $matches[1] }
      if ($method) {
        $path = Join-PathParts $base $sub
        if ([string]::IsNullOrWhiteSpace($path)) { $path='/' }
        $msig = ''
        for ($j=$i+1; $j -lt [Math]::Min($i+8,$c.Count); $j++) {
          $cand = $c[$j].Trim()
          if ($cand -match '^(public|protected|private)\s+[^\(]+\(') { $msig = $cand; break }
        }
        $svcMethod = ''
        for ($k=$i+1; $k -lt [Math]::Min($i+30,$c.Count); $k++) {
          $cand2 = $c[$k]
          if ($cand2 -match '\.([A-Za-z0-9_]+)\s*\(' -and $cand2 -notmatch 'ResponseEntity') { $svcMethod = $matches[1]; break }
        }
        $status = 'IMPLEMENTED'
        if ($svcMethod) {
          $stubHit = $stubMap.Keys | Where-Object { $_ -like "*.$svcMethod" } | Select-Object -First 1
          if ($stubHit) { $status = "PARTIAL/STUB" }
        }
        $serviceMethodResolved = '(not-resolved)'
        if ($svcMethod -ne '') {
          $serviceMethodResolved = $svcMethod
        }
        $endpointRows.Add([PSCustomObject]@{Method=$method; Path=$path; Controller=$className; ServiceMethod=$serviceMethodResolved; Status=$status}) | Out-Null
        $null = $sb.AppendLine("- ENDPOINT: $method $path -> $($msig)")
      }
    }
    if (($endpointRows | Where-Object { $_.Controller -eq $className }).Count -eq 0) {
      $null = $sb.AppendLine("- (no mapped endpoints detected)")
    }
  }

  if ($ctype -eq 'Entity') {
    $entityName = $f.BaseName
    $entityLines.Add("${entityName}:") | Out-Null
    $fields = Get-FieldLines $c
    foreach ($fl in $fields) {
      $clean = $fl -replace '^private\s+','' -replace ';$',''
      if ($clean -match '^([^\s]+)\s+([^=\s]+)\s*=\s*(.+)$') {
        $entityLines.Add("  - $($matches[2]): $($matches[1]) = $($matches[3])") | Out-Null
      } elseif ($clean -match '^([^\s]+)\s+([^\s]+)$') {
        $entityLines.Add("  - $($matches[2]): $($matches[1]) = (none)") | Out-Null
      }
    }
  }

  $null = $sb.AppendLine("=== END ===")
  $null = $sb.AppendLine("")
}

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("BACKEND ENDPOINTS INVENTORY")
$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("METHOD | PATH | CONTROLLER | SERVICE METHOD | STATUS")
foreach ($ep in $endpointRows) {
  $null = $sb.AppendLine("$($ep.Method) | $($ep.Path) | $($ep.Controller) | $($ep.ServiceMethod) | $($ep.Status)")
}
$null = $sb.AppendLine("")

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("MISSING ENDPOINTS VS PDF SPEC")
$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("METHOD | PATH | PURPOSE")
$null = $sb.AppendLine("GET | /api/roadmaps/{roadmapId}/milestones | Fetch roadmap milestones for visual timeline")
$null = $sb.AppendLine("GET | /api/roadmaps/{roadmapId}/notifications | List roadmap notifications by roadmap")
$null = $sb.AppendLine("POST | /api/roadmaps/{roadmapId}/nodes/{nodeId}/start | Mark node as in-progress")
$null = $sb.AppendLine("GET | /api/roadmaps/{roadmapId}/progress/summary | Dashboard progress summary card")
$null = $sb.AppendLine("GET | /api/roadmaps/{roadmapId}/pace/latest | Latest pace snapshot for dashboard")
$null = $sb.AppendLine("POST | /api/roadmaps/{roadmapId}/projects/suggestions/generate | Trigger AI generation of project suggestions")
$null = $sb.AppendLine("POST | /api/roadmaps/{roadmapId}/resources/sync | Trigger external learning resource sync")
$null = $sb.AppendLine("")

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("VISUAL ROADMAP ENDPOINTS")
$null = $sb.AppendLine("════════════════════════════════")
$has1 = ($endpointRows | Where-Object { $_.Method -eq 'POST' -and $_.Path -eq '/api/roadmaps/visual/generate' }).Count -gt 0
$has2 = ($endpointRows | Where-Object { $_.Method -eq 'GET' -and $_.Path -eq '/api/roadmaps/visual/{roadmapId}/graph' }).Count -gt 0
$has3 = ($endpointRows | Where-Object { $_.Method -eq 'PUT' -and $_.Path -eq '/api/roadmaps/visual/nodes/{nodeId}/complete' }).Count -gt 0
$has4 = ($endpointRows | Where-Object { $_.Method -eq 'POST' -and $_.Path -eq '/api/roadmaps/visual/{roadmapId}/replan' }).Count -gt 0
$v1 = ' '
if ($has1) { $v1 = 'x' }
$v2 = ' '
if ($has2) { $v2 = 'x' }
$v3 = ' '
if ($has3) { $v3 = 'x' }
$v4 = ' '
if ($has4) { $v4 = 'x' }
$null = $sb.AppendLine("[$v1] POST /api/roadmaps/visual/generate")
$null = $sb.AppendLine("[$v2] GET  /api/roadmaps/visual/{id}/graph")
$null = $sb.AppendLine("[$v3] PUT  /api/roadmaps/visual/nodes/{nodeId}/complete")
$null = $sb.AppendLine("[$v4] POST /api/roadmaps/visual/{id}/replan")
$null = $sb.AppendLine("")

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("ENTITIES THAT EXIST")
$null = $sb.AppendLine("════════════════════════════════")
foreach ($l in $entityLines) { $null = $sb.AppendLine($l) }
$null = $sb.AppendLine("")

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("SERVICE METHODS THAT ARE STUBS")
$null = $sb.AppendLine("════════════════════════════════")
if ($stubMap.Count -eq 0) { $null = $sb.AppendLine("(none detected)") }
else {
  foreach ($k in ($stubMap.Keys | Sort-Object)) { $null = $sb.AppendLine("$k -> $($stubMap[$k])") }
}
$null = $sb.AppendLine("")

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("WHAT IS READY TO CONNECT TO FRONTEND")
$null = $sb.AppendLine("════════════════════════════════")
foreach ($ep in ($endpointRows | Where-Object { $_.Status -eq 'IMPLEMENTED' })) {
  $null = $sb.AppendLine("- $($ep.Method) $($ep.Path) -> controller returns service response")
}
$null = $sb.AppendLine("")

$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("WHAT NEEDS TO BE BUILT FIRST")
$null = $sb.AppendLine("════════════════════════════════")
$null = $sb.AppendLine("Based on the Angular frontend structure:")
$null = $sb.AppendLine("- /dashboard/roadmap needs these endpoints: graph read/update, node start/complete, pace, milestones")
$null = $sb.AppendLine("- /dashboard/assessment needs these endpoints: skill-gap assessment submit and score retrieval")
$null = $sb.AppendLine("- /dashboard/jobs needs these endpoints: job feed/search/recommendations")
$null = $sb.AppendLine("- /dashboard/profile needs these endpoints: profile read/update, skills update")
$null = $sb.AppendLine("- /dashboard/interview needs these endpoints: mock interview session create, question stream, scoring")
$null = $sb.AppendLine("- /admin/careers needs these endpoints: CRUD for career paths and templates")
$null = $sb.AppendLine("")
$null = $sb.AppendLine("Rank the top 10 things to build next to make the frontend functional:")
$null = $sb.AppendLine("1. Add assessment APIs to produce skill gaps used by visual roadmap generation")
$null = $sb.AppendLine("2. Add roadmap node start endpoint and progress summary endpoint")
$null = $sb.AppendLine("3. Add milestone read endpoints and certificate trigger endpoints for dashboard milestones")
$null = $sb.AppendLine("4. Add roadmap notifications-by-roadmap and mark-read batch endpoints")
$null = $sb.AppendLine("5. Add resource sync endpoint and provider status endpoint")
$null = $sb.AppendLine("6. Add project suggestion generation endpoint and filtering/search endpoint")
$null = $sb.AppendLine("7. Add jobs module APIs (search, recommendations, save/apply tracking)")
$null = $sb.AppendLine("8. Add interview module APIs (session lifecycle and AI evaluation)")
$null = $sb.AppendLine("9. Add profile/skills bridge APIs between MS-User and MS-Roadmap")
$null = $sb.AppendLine("10. Add admin career-path management endpoints and template publishing")

[System.IO.File]::WriteAllText((Join-Path (Resolve-Path '.').Path $report), $sb.ToString())

Write-Output ("REPORT_FILE=" + (Join-Path (Resolve-Path '.').Path $report))
Write-Output ("JAVA_FILE_COUNT=" + $files.Count)
Write-Output ("ENDPOINT_COUNT=" + $endpointRows.Count)
Write-Output ("STUB_METHOD_COUNT=" + $stubMap.Count)
