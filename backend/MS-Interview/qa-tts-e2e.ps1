$ErrorActionPreference = 'Continue'

$base = 'http://localhost:8081/interview-service'
$api = "$base/api/v1"
$moduleRoot = 'c:\Users\ASUS\Documents\PI - cloud\SmartHire\MS-Interview'
$tools = Join-Path $moduleRoot 'tools\kokoro'
$propsPath = Join-Path $moduleRoot 'src\main\resources\application.properties'

$report = [ordered]@{}

function Resolve-PathLike([string]$v, [string]$root) {
  if ([string]::IsNullOrWhiteSpace($v)) { return $null }
  if ([System.IO.Path]::IsPathRooted($v)) { return [System.IO.Path]::GetFullPath($v) }
  return [System.IO.Path]::GetFullPath((Join-Path $root $v))
}

# Config
$props = Get-Content $propsPath
$scriptVal = (($props | Where-Object { $_ -match '^kokoro\.script\.path=' } | Select-Object -First 1) -replace '^kokoro\.script\.path=', '').Trim()
$audioVal = (($props | Where-Object { $_ -match '^kokoro\.audio\.dir=' } | Select-Object -First 1) -replace '^kokoro\.audio\.dir=', '').Trim()
$scriptPath = Resolve-PathLike $scriptVal $moduleRoot
$audioDir = Resolve-PathLike $audioVal $moduleRoot
if (-not (Test-Path $audioDir)) { New-Item -ItemType Directory -Path $audioDir -Force | Out-Null }

$report.Config = [ordered]@{
  scriptPath = $scriptPath
  audioDir = $audioDir
}

# Infra probes
try {
  $springRoot = Invoke-WebRequest -Uri "$base/" -Method GET -TimeoutSec 8
  $report.SpringRoot = [ordered]@{ status = $springRoot.StatusCode }
}
catch {
  $status = $null
  try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
  $report.SpringRoot = [ordered]@{ status = $status; error = $_.Exception.Message }
}

try {
  $health = Invoke-WebRequest -Uri "$base/actuator/health" -Method GET -TimeoutSec 8
  $report.SpringHealth = [ordered]@{ status = $health.StatusCode; body = $health.Content }
}
catch {
  $status = $null
  try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
  $report.SpringHealth = [ordered]@{ status = $status; error = $_.Exception.Message }
}

try {
  $frontend = Invoke-WebRequest -Uri 'http://localhost:4200' -Method GET -TimeoutSec 8
  $report.Angular = [ordered]@{ status = $frontend.StatusCode }
}
catch {
  $status = $null
  try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
  $report.Angular = [ordered]@{ status = $status; error = $_.Exception.Message }
}

$report.Files = [ordered]@{
  scriptExists = (Test-Path $scriptPath)
  model_v0_19_exists = (Test-Path (Join-Path $tools 'kokoro-v0_19.onnx'))
  model_v1_0_exists = (Test-Path (Join-Path $tools 'kokoro-v1.0.onnx'))
  voices_v1_0_exists = (Test-Path (Join-Path $tools 'voices-v1.0.bin'))
}

$probe = Join-Path $audioDir '._qa_write_probe.tmp'
try {
  'ok' | Set-Content -Path $probe -Encoding UTF8
  $writeOk = $true
}
catch {
  $writeOk = $false
  $report.AudioDirWriteError = $_.Exception.Message
}
finally {
  if (Test-Path $probe) { Remove-Item $probe -Force }
}
$report.AudioDir = [ordered]@{
  exists = (Test-Path $audioDir)
  writable = $writeOk
}

# Phase 2 direct Kokoro
$direct1 = Join-Path $audioDir 'kokoro_direct_test.wav'
$direct2 = Join-Path $audioDir 'kokoro_question_test.wav'
Remove-Item $direct1, $direct2 -Force -ErrorAction SilentlyContinue

Push-Location $tools
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$out1 = & python tts.py "Welcome to your SmartHire AI interview. I will be your interviewer today. Let us begin." $direct1 2>&1
$code1 = $LASTEXITCODE
$sw.Stop()
$t1 = [math]::Round($sw.Elapsed.TotalSeconds, 3)
$size1 = if (Test-Path $direct1) { (Get-Item $direct1).Length } else { 0 }

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$out2 = & python tts.py "Tell me about yourself. Walk me through your background, your experience, and why you are interested in this role." $direct2 2>&1
$code2 = $LASTEXITCODE
$sw.Stop()
$t2 = [math]::Round($sw.Elapsed.TotalSeconds, 3)
$size2 = if (Test-Path $direct2) { (Get-Item $direct2).Length } else { 0 }
Pop-Location

$report.KokoroDirectShort = [ordered]@{
  exitCode = $code1
  stdout = ($out1 -join "`n")
  seconds = $t1
  exists = (Test-Path $direct1)
  bytes = $size1
}
$report.KokoroDirectLong = [ordered]@{
  exitCode = $code2
  stdout = ($out2 -join "`n")
  seconds = $t2
  exists = (Test-Path $direct2)
  bytes = $size2
}

# Phase 3 audio endpoint
$manual = Join-Path $audioDir 'tts_manualtest.wav'
Copy-Item $direct1 $manual -Force
try {
  $ga = Invoke-WebRequest -Uri "$base/audio/tts_manualtest.wav" -Method GET -TimeoutSec 20
  $report.AudioManualGet = [ordered]@{
    status = $ga.StatusCode
    contentType = $ga.Headers['Content-Type']
    contentLength = $ga.Headers['Content-Length']
    bodyBytes = $ga.RawContentLength
  }
}
catch {
  $status = $null
  try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
  $report.AudioManualGet = [ordered]@{ status = $status; error = $_.Exception.Message }
}

try { Invoke-WebRequest -Uri "$base/audio/../../secret.txt" -Method GET -TimeoutSec 10 | Out-Null; $report.AudioTraversal = 'unexpected-success' }
catch { try { $report.AudioTraversal = $_.Exception.Response.StatusCode.value__ } catch { $report.AudioTraversal = 'error' } }
try { Invoke-WebRequest -Uri "$base/audio/notprefix_file.wav" -Method GET -TimeoutSec 10 | Out-Null; $report.AudioWrongPrefix = 'unexpected-success' }
catch { try { $report.AudioWrongPrefix = $_.Exception.Response.StatusCode.value__ } catch { $report.AudioWrongPrefix = 'error' } }
try { Invoke-WebRequest -Uri "$base/audio/tts_doesnotexist.wav" -Method GET -TimeoutSec 10 | Out-Null; $report.AudioMissing = 'unexpected-success' }
catch { try { $report.AudioMissing = $_.Exception.Response.StatusCode.value__ } catch { $report.AudioMissing = 'error' } }

# Phase 4/5 pipeline
$startBody = @{ userId = 99; careerPathId = 1; roleType = 'ALL'; mode = 'PRACTICE'; type = 'BEHAVIORAL'; questionCount = 2 } | ConvertTo-Json
$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
  $session = Invoke-RestMethod -Method POST -Uri "$api/sessions/start" -ContentType 'application/json' -Body $startBody
  $sw.Stop()
  $report.SessionStart = [ordered]@{
    ok = $true
    ms = $sw.ElapsedMilliseconds
    id = $session.id
    status = $session.status
    hasTtsAudioUrl = [bool]$session.ttsAudioUrl
    ttsAudioUrl = $session.ttsAudioUrl
  }
}
catch {
  $sw.Stop()
  $report.SessionStart = [ordered]@{
    ok = $false
    ms = $sw.ElapsedMilliseconds
    error = $_.Exception.Message
  }
}

if ($report.SessionStart.ok) {
  $sessionId = $report.SessionStart.id
  try {
    $q1 = Invoke-RestMethod -Method GET -Uri "$api/sessions/$sessionId/questions/current"
    $report.Question1 = [ordered]@{ id = $q1.id; text = $q1.questionText; ttsAudioUrl = $q1.ttsAudioUrl }
    if ($q1.ttsAudioUrl) {
      $a1 = Invoke-WebRequest -Uri ('http://localhost:8081' + $q1.ttsAudioUrl) -Method GET -TimeoutSec 20
      $report.Question1Audio = [ordered]@{
        status = $a1.StatusCode
        contentType = $a1.Headers['Content-Type']
        contentLength = [int64]$a1.Headers['Content-Length']
        bytes = $a1.RawContentLength
      }
    }
  }
  catch {
    $report.Question1 = [ordered]@{ error = $_.Exception.Message }
  }

  $ttsFiles = Get-ChildItem $audioDir -Filter 'tts_*.wav' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
  $report.FilesAfterStart = @($ttsFiles)

  if ($report.Question1.id) {
    $submitBody = @{ sessionId = $sessionId; questionId = $report.Question1.id; answerText = 'I am a software engineering student specializing in backend development with Spring Boot and microservices. I have built several production-grade APIs and I am passionate about AI-driven products like SmartHire.'; videoUrl = $null; audioUrl = $null } | ConvertTo-Json
    try {
      $ans = Invoke-RestMethod -Method POST -Uri "$api/answers/submit" -ContentType 'application/json' -Body $submitBody
      $report.SubmitAnswer = [ordered]@{ ok = $true; answerId = $ans.id }
    }
    catch {
      $report.SubmitAnswer = [ordered]@{ ok = $false; error = $_.Exception.Message }
    }

    try {
      $q2 = Invoke-RestMethod -Method GET -Uri "$api/sessions/$sessionId/questions/current"
      $report.Question2 = [ordered]@{ id = $q2.id; text = $q2.questionText; ttsAudioUrl = $q2.ttsAudioUrl }
      if ($q2.ttsAudioUrl) {
        $a2 = Invoke-WebRequest -Uri ('http://localhost:8081' + $q2.ttsAudioUrl) -Method GET -TimeoutSec 20
        $report.Question2Audio = [ordered]@{
          status = $a2.StatusCode
          contentType = $a2.Headers['Content-Type']
          contentLength = [int64]$a2.Headers['Content-Length']
          bytes = $a2.RawContentLength
        }
      }
    }
    catch {
      $report.Question2 = [ordered]@{ error = $_.Exception.Message }
    }
  }
}

# Phase 7 perf
$perfOut = Join-Path $audioDir 'tts_perf_test.wav'
Remove-Item $perfOut -Force -ErrorAction SilentlyContinue
Push-Location $tools
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$perfStd = & python tts.py "Where do you see yourself in five years?" $perfOut 2>&1
$perfCode = $LASTEXITCODE
$sw.Stop()
$perfSec = [math]::Round($sw.Elapsed.TotalSeconds, 3)
Pop-Location
$perfBytes = if (Test-Path $perfOut) { (Get-Item $perfOut).Length } else { 0 }
$report.KokoroPerf = [ordered]@{ exitCode = $perfCode; seconds = $perfSec; bytes = $perfBytes; stdout = ($perfStd -join "`n") }

# Phase 8 cleanup
$sample = Get-ChildItem $audioDir -Filter 'tts_*.wav' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($sample) {
  $fname = $sample.Name
  try {
    $del = Invoke-WebRequest -Method DELETE -Uri "$base/audio/$fname" -TimeoutSec 15
    $existsAfter = Test-Path (Join-Path $audioDir $fname)
    $report.CleanupDelete = [ordered]@{ file = $fname; status = $del.StatusCode; existsAfterDelete = $existsAfter }
  }
  catch {
    $report.CleanupDelete = [ordered]@{ file = $fname; error = $_.Exception.Message }
  }
}
$remaining = Get-ChildItem $audioDir -Filter 'tts_*.wav' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
$report.RemainingFiles = [ordered]@{ count = @($remaining).Count; files = @($remaining) }

$report | ConvertTo-Json -Depth 8
