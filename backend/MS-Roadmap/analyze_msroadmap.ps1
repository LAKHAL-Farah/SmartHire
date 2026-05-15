$ErrorActionPreference = 'Stop'

Set-Location $PSScriptRoot

$srcRoot = Join-Path $PSScriptRoot 'src/main/java/tn/esprit/msroadmap'
$resourcesRoot = Join-Path $PSScriptRoot 'src/main/resources'
$reportPath = Join-Path $PSScriptRoot 'msroadmap_full_analysis_report.md'

if (-not (Test-Path $srcRoot)) {
  throw "Source root not found: $srcRoot"
}

function Get-RelPath([string]$path) {
  return $path.Substring($PSScriptRoot.Length + 1).Replace('\\','/').Replace('\','/')
}

function Classify-File([string]$path) {
  if ($path -match '/Controllers/') { return 'Controller' }
  if ($path -match '/ServicesImpl/') { return 'Service Interface' }
  if ($path -match '/Services/') { return 'Service Implementation' }
  if ($path -match '/Repositories/') { return 'Repository' }
  if ($path -match '/Entities/') { return 'Entity' }
  if ($path -match '/DTO/request/') { return 'DTO Request' }
  if ($path -match '/DTO/response/') { return 'DTO Response' }
  if ($path -match '/DTO/external/') { return 'DTO External' }
  if ($path -match '/Enums/') { return 'Enum' }
  if ($path -match '/Mapper/') { return 'Mapper' }
  if ($path -match '/config/') { return 'Config' }
  if ($path -match '/ai/') { return 'AI' }
  if ($path -match '/Exception/') { return 'Exception' }
  return 'Other'
}

function Strip-Comments([string]$text) {
  $t = [regex]::Replace($text, '/\*.*?\*/', '', [System.Text.RegularExpressions.RegexOptions]::Singleline)
  $t = [regex]::Replace($t, '//.*$', '', [System.Text.RegularExpressions.RegexOptions]::Multiline)
  return $t
}

function Get-ClassName([string]$text, [string]$fallback) {
  $m = [regex]::Match($text, '\b(class|interface|enum|record)\s+([A-Za-z0-9_]+)')
  if ($m.Success) { return $m.Groups[2].Value }
  return $fallback
}

function Parse-FieldLines([string[]]$lines) {
  $out = New-Object System.Collections.Generic.List[object]
  $pendingAnnotations = New-Object System.Collections.Generic.List[string]
  for ($i=0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i].Trim()
    if ($line -eq '') { continue }
    if ($line.StartsWith('@')) {
      $pendingAnnotations.Add($line) | Out-Null
      continue
    }
    if ($line -match '^(private|protected|public)\s+([A-Za-z0-9_<>,\[\]?\.]+)\s+([A-Za-z0-9_]+)\s*(=.*)?;\s*$') {
      $type = $matches[2]
      $name = $matches[3]
      $ann = @($pendingAnnotations)
      $pendingAnnotations.Clear()
      $relation = ''
      if ($ann -match '@OneToMany') { $relation = 'OneToMany' }
      elseif ($ann -match '@ManyToOne') { $relation = 'ManyToOne' }
      elseif ($ann -match '@OneToOne') { $relation = 'OneToOne' }
      elseif ($ann -match '@ManyToMany') { $relation = 'ManyToMany' }
      $out.Add([PSCustomObject]@{
        Type = $type
        Name = $name
        Relation = $relation
        Annotations = ($ann -join ' ')
      }) | Out-Null
    } else {
      $pendingAnnotations.Clear()
    }
  }
  return $out
}

function Parse-RecordFields([string]$raw) {
  $out = New-Object System.Collections.Generic.List[object]
  $clean = Strip-Comments $raw
  $m = [regex]::Match($clean, 'record\s+[A-Za-z0-9_]+\s*\((.*?)\)\s*\{', [System.Text.RegularExpressions.RegexOptions]::Singleline)
  if (-not $m.Success) {
    $m = [regex]::Match($clean, 'record\s+[A-Za-z0-9_]+\s*\((.*?)\)\s*;', [System.Text.RegularExpressions.RegexOptions]::Singleline)
  }
  if (-not $m.Success) { return $out }

  $inside = $m.Groups[1].Value

  $parts = New-Object System.Collections.Generic.List[string]
  $buf = New-Object System.Text.StringBuilder
  $angle = 0
  $paren = 0
  for ($idx = 0; $idx -lt $inside.Length; $idx++) {
    $ch = $inside[$idx]
    if ($ch -eq '<') { $angle++ }
    elseif ($ch -eq '>') { if ($angle -gt 0) { $angle-- } }
    elseif ($ch -eq '(') { $paren++ }
    elseif ($ch -eq ')') { if ($paren -gt 0) { $paren-- } }

    if ($ch -eq ',' -and $angle -eq 0 -and $paren -eq 0) {
      $parts.Add($buf.ToString()) | Out-Null
      $null = $buf.Clear()
      continue
    }

    $null = $buf.Append($ch)
  }
  if ($buf.Length -gt 0) {
    $parts.Add($buf.ToString()) | Out-Null
  }

  foreach ($rawPart in $parts) {
    $part = $rawPart.Trim()
    if ([string]::IsNullOrWhiteSpace($part)) { continue }
    $part = [regex]::Replace($part, '@[A-Za-z0-9_]+(?:\([^\)]*\))?\s*', '')
    $part = $part -replace '\bfinal\b\s+', ''
    if ($part -match '([A-Za-z0-9_<>,\[\]?\.\s]+)\s+([A-Za-z0-9_]+)$') {
      $typeNorm = ($matches[1] -replace '\s+', ' ').Trim()
      $out.Add([PSCustomObject]@{
        Type = $typeNorm
        Name = $matches[2]
        Relation = ''
        Annotations = 'record-component'
      }) | Out-Null
    }
  }
  return $out
}

function Parse-Methods([string[]]$lines) {
  $methods = New-Object System.Collections.Generic.List[object]
  for ($i=0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if ($line -match '^\s*(public|protected|private)\s+') {
      $sigParts = New-Object System.Collections.Generic.List[string]
      $sigParts.Add($line.Trim()) | Out-Null
      $openSeen = ($line -match '\(')
      $closeSeen = ($line -match '\)')
      $braceSeen = ($line -match '\{')
      $endSig = $i
      while ((-not $braceSeen) -or (-not $openSeen) -or (-not $closeSeen)) {
        $endSig++
        if ($endSig -ge $lines.Count) { break }
        $next = $lines[$endSig].Trim()
        $sigParts.Add($next) | Out-Null
        if ($next -match '\(') { $openSeen = $true }
        if ($next -match '\)') { $closeSeen = $true }
        if ($next -match '\{') { $braceSeen = $true }
      }
      if (-not $braceSeen) { continue }
      $sig = ($sigParts -join ' ')
      if ($sig -notmatch '\(.*\)\s*\{') { continue }
      if ($sig -match '\b(class|interface|enum|record)\b') { continue }
      $methodNameMatch = [regex]::Match($sig, '([A-Za-z0-9_]+)\s*\(')
      if (-not $methodNameMatch.Success) { continue }
      $methodName = $methodNameMatch.Groups[1].Value
      $returnType = ''
      $returnMatch = [regex]::Match($sig, '^\s*(public|protected|private)\s+(?:static\s+)?(?:final\s+)?([A-Za-z0-9_<>,\[\]?\.]+)\s+[A-Za-z0-9_]+\s*\(')
      if ($returnMatch.Success) { $returnType = $returnMatch.Groups[2].Value }

      $brace = 0
      $bodyLines = New-Object System.Collections.Generic.List[string]
      for ($j=$endSig; $j -lt $lines.Count; $j++) {
        $cur = $lines[$j]
        $brace += ([regex]::Matches($cur, '\{')).Count
        $brace -= ([regex]::Matches($cur, '\}')).Count
        if ($j -gt $endSig) { $bodyLines.Add($cur) | Out-Null }
        if ($brace -eq 0) {
          $i = $j
          break
        }
      }

      $body = ($bodyLines -join "`n")
      $cleanBody = ($body -replace '\s+', ' ').Trim()
      $isStub = $false
      $stubReason = ''
      if ($body -match 'UnsupportedOperationException') { $isStub = $true; $stubReason = 'throws UnsupportedOperationException' }
      elseif ($body -match '\bTODO\b|\bFIXME\b') { $isStub = $true; $stubReason = 'contains TODO/FIXME' }
      elseif ($body -match '\breturn\s+null\s*;') { $isStub = $true; $stubReason = 'returns null' }
      elseif ($cleanBody -eq '' -or $cleanBody -eq '}') { $isStub = $true; $stubReason = 'empty method body' }

      $methods.Add([PSCustomObject]@{
        Name = $methodName
        Signature = $sig
        ReturnType = $returnType
        Body = $body
        IsStub = $isStub
        StubReason = $stubReason
      }) | Out-Null
    }
  }
  return $methods
}

function Get-AnnotationValue([string]$annotation, [string]$key) {
  if (-not [string]::IsNullOrWhiteSpace($key)) {
    $pattern = [regex]::Escape($key) + '\s*=\s*"([^"]*)"'
    $m = [regex]::Match($annotation, $pattern)
    if ($m.Success) { return $m.Groups[1].Value }
  }
  $m2 = [regex]::Match($annotation, '\("([^"]*)"\)')
  if ($m2.Success) { return $m2.Groups[1].Value }
  return ''
}

function Join-Url([string]$base, [string]$sub) {
  if ([string]::IsNullOrWhiteSpace($base) -and [string]::IsNullOrWhiteSpace($sub)) { return '/' }
  if ([string]::IsNullOrWhiteSpace($base)) { return ('/' + $sub.TrimStart('/')).Replace('//','/') }
  if ([string]::IsNullOrWhiteSpace($sub)) { return $base }
  return ($base.TrimEnd('/') + '/' + $sub.TrimStart('/')).Replace('//','/')
}

$javaFiles = Get-ChildItem $srcRoot -Recurse -File -Filter *.java | Sort-Object FullName

$fileInventory = New-Object System.Collections.Generic.List[object]
$serviceMethodStatus = @{}
$serviceStubRows = New-Object System.Collections.Generic.List[object]
$controllers = New-Object System.Collections.Generic.List[object]
$endpoints = New-Object System.Collections.Generic.List[object]
$repositories = New-Object System.Collections.Generic.List[object]
$entities = New-Object System.Collections.Generic.List[object]
$dtos = New-Object System.Collections.Generic.List[object]
$enums = New-Object System.Collections.Generic.List[object]

foreach ($f in $javaFiles) {
  $raw = Get-Content $f.FullName -Raw
  $lines = Get-Content $f.FullName
  $rel = Get-RelPath $f.FullName
  $type = Classify-File $rel
  $className = Get-ClassName $raw $f.BaseName

  $stripped = Strip-Comments $raw
  $loc = (($stripped -split "`n") | Where-Object { $_.Trim() -ne '' }).Count
  $status = 'implemented'
  if ($loc -le 5) { $status = 'empty' }

  $methods = Parse-Methods $lines
  $stubMethods = @($methods | Where-Object { $_.IsStub })

  if ($type -eq 'Service Implementation' -or $type -eq 'Controller') {
    if ($methods.Count -gt 0 -and $stubMethods.Count -eq $methods.Count) { $status = 'stub' }
    elseif ($stubMethods.Count -gt 0) { $status = 'partial' }
  } elseif ($type -eq 'Entity' -or $type -like 'DTO*' -or $type -eq 'Repository' -or $type -eq 'Enum') {
    if ($loc -le 5) { $status = 'empty' } else { $status = 'implemented' }
  }

  $fileInventory.Add([PSCustomObject]@{
    Path = $rel
    FileType = $type
    ClassName = $className
    Status = $status
    MethodCount = $methods.Count
    StubMethodCount = $stubMethods.Count
  }) | Out-Null

  if ($type -eq 'Service Implementation') {
    foreach ($m in $methods) {
      $key = "$className.$($m.Name)"
      $serviceMethodStatus[$key] = if ($m.IsStub) { 'stub' } else { 'implemented' }
      if ($m.IsStub) {
        $serviceStubRows.Add([PSCustomObject]@{
          ServiceClass = $className
          Method = $m.Name
          Signature = $m.Signature
          Reason = $m.StubReason
          File = $rel
        }) | Out-Null
      }
    }
  }

  if ($type -eq 'Repository') {
    $methodRows = New-Object System.Collections.Generic.List[string]
    $ann = ''
    foreach ($ln in $lines) {
      $t = $ln.Trim()
      if ($t.StartsWith('@Query')) { $ann = $t; continue }
      if ($t -match '^[A-Za-z0-9_<>,\[\]?\.]+\s+[A-Za-z0-9_]+\(.*\);$' -or $t -match '^(List|Optional|Page|Slice|Set|boolean|long|int|double|void)[A-Za-z0-9_<>,\[\]?\.\s]*\(.*\);$') {
        if ($ann -ne '') {
          $methodRows.Add("$t    [$ann]") | Out-Null
          $ann = ''
        } else {
          $methodRows.Add($t) | Out-Null
        }
      }
    }
    $repositories.Add([PSCustomObject]@{
      Name = $className
      File = $rel
      Methods = @($methodRows)
    }) | Out-Null
  }

  if ($type -eq 'Entity') {
    $fields = Parse-FieldLines $lines
    if ($fields.Count -eq 0 -and $raw -match '\brecord\b') {
      $fields = Parse-RecordFields $raw
    }
    $relations = @($fields | Where-Object { $_.Relation -ne '' } | ForEach-Object { "$($_.Name): $($_.Relation) -> $($_.Type)" })
    $entities.Add([PSCustomObject]@{
      Name = $className
      File = $rel
      Fields = @($fields)
      Relations = $relations
    }) | Out-Null
  }

  if ($type -like 'DTO*') {
    $fields = Parse-FieldLines $lines
    if ($fields.Count -eq 0 -and $raw -match '\brecord\b') {
      $fields = Parse-RecordFields $raw
    }
    $dtos.Add([PSCustomObject]@{
      Name = $className
      DtoType = $type
      File = $rel
      Fields = @($fields)
    }) | Out-Null
  }

  if ($type -eq 'Enum') {
    $body = [regex]::Match($raw, 'enum\s+[A-Za-z0-9_]+\s*\{(.*?)\}', [System.Text.RegularExpressions.RegexOptions]::Singleline).Groups[1].Value
    $values = @()
    if ($body) {
      $firstPart = $body
      if ($body.Contains(';')) { $firstPart = $body.Split(';')[0] }
      foreach ($v in ($firstPart -split ',')) {
        $vv = ($v.Trim() -replace '\s+',' ')
        if ($vv -match '^[A-Z0-9_]+$') { $values += $vv }
      }
    }
    $enums.Add([PSCustomObject]@{
      Name = $className
      File = $rel
      Values = $values
    }) | Out-Null
  }

  if ($type -eq 'Controller') {
    $basePath = ''
    $controllerMethods = Parse-Methods $lines

    foreach ($ln in $lines) {
      $t = $ln.Trim()
      if ($t -match '^@RequestMapping') {
        $basePath = Get-AnnotationValue $t 'value'
        if ([string]::IsNullOrWhiteSpace($basePath)) {
          $basePath = Get-AnnotationValue $t 'path'
        }
        if ([string]::IsNullOrWhiteSpace($basePath)) {
          $basePath = Get-AnnotationValue $t ''
        }
      }
      if ($t -match '^public\s+class\s+') { break }
    }

    $controllers.Add([PSCustomObject]@{
      Name = $className
      File = $rel
      BasePath = if ($basePath) { $basePath } else { '/' }
    }) | Out-Null

    for ($i=0; $i -lt $lines.Count; $i++) {
      $annLine = $lines[$i].Trim()
      $httpMethod = ''
      if ($annLine -match '^@GetMapping') { $httpMethod = 'GET' }
      elseif ($annLine -match '^@PostMapping') { $httpMethod = 'POST' }
      elseif ($annLine -match '^@PutMapping') { $httpMethod = 'PUT' }
      elseif ($annLine -match '^@DeleteMapping') { $httpMethod = 'DELETE' }
      elseif ($annLine -match '^@PatchMapping') { $httpMethod = 'PATCH' }
      elseif ($annLine -match '^@RequestMapping') {
        if ($annLine -match 'RequestMethod\.GET') { $httpMethod = 'GET' }
        elseif ($annLine -match 'RequestMethod\.POST') { $httpMethod = 'POST' }
        elseif ($annLine -match 'RequestMethod\.PUT') { $httpMethod = 'PUT' }
        elseif ($annLine -match 'RequestMethod\.DELETE') { $httpMethod = 'DELETE' }
        elseif ($annLine -match 'RequestMethod\.PATCH') { $httpMethod = 'PATCH' }
      }

      if ($httpMethod -eq '') { continue }

      $subPath = Get-AnnotationValue $annLine 'value'
      if ([string]::IsNullOrWhiteSpace($subPath)) { $subPath = Get-AnnotationValue $annLine 'path' }
      if ([string]::IsNullOrWhiteSpace($subPath)) { $subPath = Get-AnnotationValue $annLine '' }

      $sig = ''
      $sigStart = -1
      for ($j=$i+1; $j -lt [Math]::Min($i+15, $lines.Count); $j++) {
        if ($lines[$j] -match '^\s*(public|protected|private)\s+') {
          $sigParts = New-Object System.Collections.Generic.List[string]
          $sigParts.Add($lines[$j].Trim()) | Out-Null
          $openSeen = ($lines[$j] -match '\(')
          $closeSeen = ($lines[$j] -match '\)')
          $braceSeen = ($lines[$j] -match '\{')
          $kSig = $j
          while ((-not $braceSeen) -or (-not $openSeen) -or (-not $closeSeen)) {
            $kSig++
            if ($kSig -ge $lines.Count) { break }
            $next = $lines[$kSig].Trim()
            $sigParts.Add($next) | Out-Null
            if ($next -match '\(') { $openSeen = $true }
            if ($next -match '\)') { $closeSeen = $true }
            if ($next -match '\{') { $braceSeen = $true }
          }
          if (-not $braceSeen) { continue }
          $sig = ($sigParts -join ' ')
          if ($sig -notmatch '\(.*\)\s*\{') { continue }
          $sigStart = $j
          break
        }
      }
      if ($sigStart -lt 0) { continue }

      $methodName = ([regex]::Match($sig, '([A-Za-z0-9_]+)\s*\(')).Groups[1].Value
      $returnType = ([regex]::Match($sig, '^\s*(public|protected|private)\s+(?:static\s+)?(?:final\s+)?([A-Za-z0-9_<>,\[\]?\.]+)\s+[A-Za-z0-9_]+\s*\(')).Groups[2].Value
      $paramsRaw = ([regex]::Match($sig, '\((.*)\)')).Groups[1].Value
      $requestBodyType = ''
      $pathParams = @()
      $queryParams = @()

      foreach ($param in ($paramsRaw -split ',')) {
        $p = $param.Trim()
        if ($p -match '@RequestBody\s+([A-Za-z0-9_<>,\[\]?\.]+)\s+[A-Za-z0-9_]+') { $requestBodyType = $matches[1] }
        if ($p -match '@PathVariable(?:\("([A-Za-z0-9_]+)"\))?') {
          if ($matches[1]) { $pathParams += $matches[1] } else {
            $n = ([regex]::Match($p, '([A-Za-z0-9_]+)$')).Groups[1].Value
            if ($n) { $pathParams += $n }
          }
        }
        if ($p -match '@RequestParam(?:\("([A-Za-z0-9_]+)"\))?') {
          if ($matches[1]) { $queryParams += $matches[1] } else {
            $n = ([regex]::Match($p, '([A-Za-z0-9_]+)$')).Groups[1].Value
            if ($n) { $queryParams += $n }
          }
        }
      }

      $brace = 0
      $bodyLines = New-Object System.Collections.Generic.List[string]
      for ($k=$sigStart; $k -lt $lines.Count; $k++) {
        $cur = $lines[$k]
        $brace += ([regex]::Matches($cur, '\{')).Count
        $brace -= ([regex]::Matches($cur, '\}')).Count
        if ($k -gt $sigStart) { $bodyLines.Add($cur) | Out-Null }
        if ($brace -eq 0) {
          break
        }
      }
      $body = ($bodyLines -join "`n")
      $implStatus = 'implemented'
      if ($body -match 'UnsupportedOperationException|\bTODO\b|\bFIXME\b|return\s+null\s*;') {
        $implStatus = 'stub'
      }

      if ($implStatus -eq 'implemented') {
        $calls = [regex]::Matches($body, '([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)\s*\(')
        foreach ($c in $calls) {
          $candidate = $c.Groups[2].Value
          $stubHit = $serviceMethodStatus.Keys | Where-Object { $_ -like "*.$candidate" } | Select-Object -First 1
          if ($stubHit -and $serviceMethodStatus[$stubHit] -eq 'stub') {
            $implStatus = 'partial'
            break
          }
        }
      }

      $effectiveBasePath = if ($basePath) { $basePath } else { '/' }

      $endpoints.Add([PSCustomObject]@{
        Method = $httpMethod
        Controller = $className
        ControllerFile = $rel
        BasePath = $effectiveBasePath
        SubPath = $subPath
        FullPath = Join-Url $effectiveBasePath $subPath
        HandlerMethod = $methodName
        ReturnType = if ($returnType) { $returnType } else { 'unknown' }
        RequestBody = if ($requestBodyType) { $requestBodyType } else { '-' }
        PathParams = if ($pathParams.Count -gt 0) { ($pathParams -join ', ') } else { '-' }
        QueryParams = if ($queryParams.Count -gt 0) { ($queryParams -join ', ') } else { '-' }
        ImplStatus = $implStatus
      }) | Out-Null
    }
  }
}

# AI integration scan
$aiFindings = New-Object System.Collections.Generic.List[string]
foreach ($f in $javaFiles) {
  $rel = Get-RelPath $f.FullName
  $txt = Get-Content $f.FullName -Raw
  if ($txt -match 'WebClient|RestTemplate|nvidia|openai|groq|integrate\.api\.nvidia\.com|http[s]?://|api\.coursera\.org|googleapis|udemy|github\.com') {
    $hits = @()
    if ($txt -match 'WebClient') { $hits += 'WebClient' }
    if ($txt -match 'RestTemplate') { $hits += 'RestTemplate' }
    if ($txt -match 'nvidia|integrate\.api\.nvidia\.com') { $hits += 'NVIDIA API' }
    if ($txt -match 'openai') { $hits += 'OpenAI compatibility' }
    if ($txt -match 'groq') { $hits += 'Groq references' }
    if ($txt -match 'api\.coursera\.org|coursera\.org') { $hits += 'Coursera API/search' }
    if ($txt -match 'udemy\.com|udemy') { $hits += 'Udemy API/search' }
    if ($txt -match 'googleapis|youtube\.com') { $hits += 'YouTube API/search' }
    if ($txt -match 'api\.github\.com|github\.com') { $hits += 'GitHub API/search' }
    if ($txt -match 'http[s]?://') { $hits += 'External URL literal(s)' }
    $aiFindings.Add("- $rel : $((@($hits | Select-Object -Unique)) -join ', ')") | Out-Null
  }
}

# Config files
$pomText = Get-Content (Join-Path $PSScriptRoot 'pom.xml') -Raw
$appPropsPath = Join-Path $resourcesRoot 'application.properties'
$appPropsText = if (Test-Path $appPropsPath) { Get-Content $appPropsPath -Raw } else { '' }
$appYmlPath = Join-Path $resourcesRoot 'application.yml'
$appYmlText = if (Test-Path $appYmlPath) { Get-Content $appYmlPath -Raw } else { '' }

# Missing endpoint heuristic focused on roadmap core features
$existingEndpointKeys = @{}
foreach ($e in $endpoints) {
  $existingEndpointKeys["$($e.Method) $($e.FullPath)"] = $true
}

$coreExpected = @(
  @{ Method='POST'; Path='/api/roadmaps/generate'; Purpose='Generate roadmap from assessment/profile' },
  @{ Method='GET'; Path='/api/roadmaps/{roadmapId}'; Purpose='Get roadmap details' },
  @{ Method='GET'; Path='/api/roadmaps/visual/{roadmapId}/graph'; Purpose='Get visual graph nodes/edges' },
  @{ Method='POST'; Path='/api/roadmaps/visual/{roadmapId}/nodes/{nodeId}/start'; Purpose='Start node progress' },
  @{ Method='PUT'; Path='/api/roadmaps/visual/nodes/{nodeId}/complete'; Purpose='Complete node' },
  @{ Method='GET'; Path='/api/roadmaps/{roadmapId}/progress-summary'; Purpose='Roadmap progress summary' },
  @{ Method='GET'; Path='/api/roadmaps/{roadmapId}/milestones'; Purpose='List milestones for roadmap' },
  @{ Method='GET'; Path='/api/roadmaps/{roadmapId}/notifications'; Purpose='List notifications for roadmap' },
  @{ Method='GET'; Path='/api/certificates/{userId}'; Purpose='List earned micro-certificates' },
  @{ Method='POST'; Path='/api/certificates/{roadmapId}/evaluate'; Purpose='Evaluate certificate eligibility' }
)

$missingCore = New-Object System.Collections.Generic.List[object]
foreach ($c in $coreExpected) {
  $k = "$($c.Method) $($c.Path)"
  if (-not $existingEndpointKeys.ContainsKey($k)) {
    $missingCore.Add([PSCustomObject]@{
      Method = $c.Method
      Path = $c.Path
      Purpose = $c.Purpose
      Status = 'missing'
    }) | Out-Null
  }
}

# Compile check output
$compileResult = ''
try {
  $compileOutput = & mvn -q -DskipTests test-compile 2>&1
  $compileResult = ($compileOutput | Out-String)
  if ($LASTEXITCODE -ne 0) { $compileResult += "`nEXIT_CODE=$LASTEXITCODE" }
  else { $compileResult += "`nEXIT_CODE=0" }
} catch {
  $compileResult = "Compilation check failed to run: $($_.Exception.Message)"
}

# Report
$sb = New-Object System.Text.StringBuilder
$null = $sb.AppendLine('# MS-Roadmap Full Backend Analysis')
$null = $sb.AppendLine('')
$null = $sb.AppendLine("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$null = $sb.AppendLine("Java files scanned: $($javaFiles.Count)")
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 1) Project Structure Inventory (All Java Files)')
$null = $sb.AppendLine('| File | Type | Class | Status | Methods | Stub Methods |')
$null = $sb.AppendLine('|---|---|---|---|---:|---:|')
foreach ($fi in $fileInventory) {
  $null = $sb.AppendLine("| $($fi.Path) | $($fi.FileType) | $($fi.ClassName) | $($fi.Status) | $($fi.MethodCount) | $($fi.StubMethodCount) |")
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 2) Controllers and Endpoints')
$null = $sb.AppendLine('| Method | Full Path | Controller | Handler | Request Body | Return Type | Path Params | Query Params | Status |')
$null = $sb.AppendLine('|---|---|---|---|---|---|---|---|---|')
foreach ($ep in $endpoints) {
  $statusIcon = if ($ep.ImplStatus -eq 'implemented') { '✅ implemented' } elseif ($ep.ImplStatus -eq 'partial') { '⚠️ stub' } else { '⚠️ stub' }
  $null = $sb.AppendLine("| $($ep.Method) | $($ep.FullPath) | $($ep.Controller) | $($ep.HandlerMethod) | $($ep.RequestBody) | $($ep.ReturnType) | $($ep.PathParams) | $($ep.QueryParams) | $statusIcon |")
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 3) Missing Core Endpoints (Roadmap/Visual/Progress/Certificate Heuristic)')
if ($missingCore.Count -eq 0) {
  $null = $sb.AppendLine('- None detected by heuristic set.')
} else {
  $null = $sb.AppendLine('| Method | Path | Purpose | Status |')
  $null = $sb.AppendLine('|---|---|---|---|')
  foreach ($m in $missingCore) {
    $null = $sb.AppendLine("| $($m.Method) | $($m.Path) | $($m.Purpose) | ❌ missing |")
  }
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 4) Service Interfaces and Implementations')
$svcInterfaces = @($fileInventory | Where-Object { $_.FileType -eq 'Service Interface' })
$svcImpl = @($fileInventory | Where-Object { $_.FileType -eq 'Service Implementation' })
$null = $sb.AppendLine("- Service interfaces: $($svcInterfaces.Count)")
$null = $sb.AppendLine("- Service implementations: $($svcImpl.Count)")
$null = $sb.AppendLine('')
$null = $sb.AppendLine('### Stub Methods')
if ($serviceStubRows.Count -eq 0) {
  $null = $sb.AppendLine('- None detected.')
} else {
  $null = $sb.AppendLine('| Service | Method | Reason | File |')
  $null = $sb.AppendLine('|---|---|---|---|')
  foreach ($s in $serviceStubRows) {
    $null = $sb.AppendLine("| $($s.ServiceClass) | $($s.Method) | $($s.Reason) | $($s.File) |")
  }
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 5) Repositories and Custom Methods')
foreach ($r in $repositories) {
  $null = $sb.AppendLine("### $($r.Name) ($($r.File))")
  if ($r.Methods.Count -eq 0) {
    $null = $sb.AppendLine('- No custom methods (JpaRepository defaults only).')
  } else {
    foreach ($m in $r.Methods) { $null = $sb.AppendLine("- $m") }
  }
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 6) Entities (Fields + Relationships)')
foreach ($e in $entities) {
  $null = $sb.AppendLine("### $($e.Name) ($($e.File))")
  if ($e.Fields.Count -eq 0) {
    $null = $sb.AppendLine('- No fields detected.')
  } else {
    foreach ($f in $e.Fields) {
      if ($f.Relation) {
        $null = $sb.AppendLine("- $($f.Name): $($f.Type) [$($f.Relation)]")
      } else {
        $null = $sb.AppendLine("- $($f.Name): $($f.Type)")
      }
    }
  }
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 7) DTOs (Request/Response/External)')
foreach ($d in $dtos) {
  $null = $sb.AppendLine("### $($d.Name) ($($d.DtoType), $($d.File))")
  if ($d.Fields.Count -eq 0) {
    $null = $sb.AppendLine('- No fields detected.')
  } else {
    foreach ($f in $d.Fields) {
      $null = $sb.AppendLine("- $($f.Name): $($f.Type)")
    }
  }
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 8) Enums')
foreach ($en in $enums) {
  $null = $sb.AppendLine("- $($en.Name): $($en.Values -join ', ')")
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 9) AI Integration')
if ($aiFindings.Count -eq 0) {
  $null = $sb.AppendLine('- No AI integration pattern detected.')
} else {
  foreach ($a in $aiFindings) { $null = $sb.AppendLine($a) }
}
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 10) Configuration Files')
$null = $sb.AppendLine('- pom.xml: loaded')
$null = $sb.AppendLine("- application.properties: $(if ($appPropsText) { 'loaded' } else { 'not found' })")
$null = $sb.AppendLine("- application.yml: $(if ($appYmlText) { 'loaded' } else { 'not found' })")
$null = $sb.AppendLine('')

$null = $sb.AppendLine('## 11) Compilation Check')
$null = $sb.AppendLine('```text')
$null = $sb.AppendLine($compileResult.Trim())
$null = $sb.AppendLine('```')

[System.IO.File]::WriteAllText($reportPath, $sb.ToString())

Write-Output "REPORT_PATH=$reportPath"
Write-Output "JAVA_FILES=$($javaFiles.Count)"
Write-Output "ENDPOINTS=$($endpoints.Count)"
Write-Output "STUB_METHODS=$($serviceStubRows.Count)"
Write-Output "MISSING_CORE=$($missingCore.Count)"