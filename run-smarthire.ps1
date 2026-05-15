param(
  [switch]$SkipConfigServer,
  [switch]$SkipDiscovery,
  [switch]$SkipGateway,
  [switch]$SkipUser,
  [switch]$SkipProfile,
  [switch]$SkipJob,
  [switch]$SkipInterview,
  [switch]$SkipRoadmap = $false,
  [switch]$SkipAssessment = $false,
  [switch]$SkipAIAdvice,
  [switch]$SkipFaceRecognition,
  [switch]$SkipFrontend,
  [switch]$NoHealthChecks,
  [int]$TimeoutSec = 180
)

$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
  param([string]$StartPath)

  $current = Resolve-Path $StartPath
  while ($true) {
    $backend = Join-Path $current "backend"
    $frontend = Join-Path $current "frontend"

    if ((Test-Path $backend) -and (Test-Path $frontend)) {
      return $current
    }

    $parent = Split-Path -Parent $current
    if ($parent -eq $current) {
      throw "Unable to locate repo root (expected backend/ and frontend/)."
    }

    $current = $parent
  }
}

function Start-ServiceProcess {
  param(
    [string]$Name,
    [string]$WorkingDirectory,
    [string[]]$CommandLines
  )

  $command = $CommandLines -join "; "
  Write-Host "Starting $Name..."

  Start-Process -FilePath "pwsh" `
    -ArgumentList "-NoExit", "-Command", $command `
    -WorkingDirectory $WorkingDirectory | Out-Null
}

function Wait-Port {
  param(
    [string]$HostName = "localhost",
    [int]$Port,
    [int]$TimeoutSeconds = 120
  )

  $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
  while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    $client = $null
    try {
      $client = New-Object System.Net.Sockets.TcpClient
      $iar = $client.BeginConnect($HostName, $Port, $null, $null)
      if ($iar.AsyncWaitHandle.WaitOne(1000)) {
        $client.EndConnect($iar)
        $client.Close()
        return $true
      }
    } catch {
      # swallow and retry
    } finally {
      if ($client) { $client.Close() }
    }
  }

  return $false
}

$repoRoot = Resolve-RepoRoot -StartPath $PSScriptRoot
$backendRoot = Join-Path $repoRoot "backend"
$frontendRoot = Join-Path $repoRoot "frontend"

$configServerDir = Join-Path $backendRoot "ConfigServer"
$discoveryDir = Join-Path $backendRoot "Discovery"
$gatewayDir = Join-Path $backendRoot "Gateway"
$msUserDir = Join-Path $backendRoot "MS-User"
$msProfileDir = Join-Path $backendRoot "MS-Profile"
$msJobDir = Join-Path $backendRoot "MS_JOB"
$msInterviewDir = Join-Path $backendRoot "MS-Interview"
$msRoadmapDir = Join-Path $backendRoot "MS-Roadmap"
$msAssessmentDir = Join-Path $backendRoot "MS-Assessment"
$aiAdviceDir = Join-Path $backendRoot "AI-Advice"
$faceRecognitionDir = Join-Path $backendRoot "FaceRecognition-Service"

Write-Host "Repo root: $repoRoot"

if (-not (Wait-Port -Port 3307 -TimeoutSeconds 1)) {
  Write-Warning "MySQL not reachable on port 3307. MS-User/MS-Profile/MS-Job may fail to start."
}

if (-not $SkipConfigServer -and (Test-Path $configServerDir)) {
  $configRepoPath = (Resolve-Path (Join-Path $configServerDir "config-repo")).Path
  $configRepoUri = "file:" + $configRepoPath.Replace("\\", "/")

  Start-ServiceProcess -Name "Config Server" -WorkingDirectory $configServerDir -CommandLines @(
    "`$env:SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS = '$configRepoUri'",
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8888 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "Config Server did not open port 8888 within timeout."
    }
  }
}

if (-not $SkipDiscovery -and (Test-Path $discoveryDir)) {
  Start-ServiceProcess -Name "Discovery (Eureka)" -WorkingDirectory $discoveryDir -CommandLines @(
    "`$env:SERVER_PORT = '8761'",
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8761 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "Discovery did not open port 8761 within timeout."
    }
  }
}

if (-not $SkipGateway -and (Test-Path $gatewayDir)) {
  Start-ServiceProcess -Name "API Gateway" -WorkingDirectory $gatewayDir -CommandLines @(
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8887 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "Gateway did not open port 8887 within timeout."
    }
  }
}

if (-not $SkipUser -and (Test-Path $msUserDir)) {
  Start-ServiceProcess -Name "MS-User" -WorkingDirectory $msUserDir -CommandLines @(
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8082 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "MS-User did not open port 8082 within timeout."
    }
  }
}

if (-not $SkipProfile -and (Test-Path $msProfileDir)) {
  Start-ServiceProcess -Name "MS-Profile" -WorkingDirectory $msProfileDir -CommandLines @(
    "`$env:EUREKA_ENABLED = 'true'",
    "`$env:EUREKA_REGISTER = 'true'",
    "`$env:EUREKA_FETCH = 'true'",
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8092 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "MS-Profile did not open port 8092 within timeout."
    }
  }
}

if (-not $SkipJob -and (Test-Path $msJobDir)) {
  Start-ServiceProcess -Name "MS-Job" -WorkingDirectory $msJobDir -CommandLines @(
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8085 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "MS-Job did not open port 8085 within timeout."
    }
  }
}

if (-not $SkipInterview -and (Test-Path $msInterviewDir)) {
  $msInterviewToolsSetup = Join-Path $msInterviewDir "tools/setup-tools.ps1"
  if (Test-Path $msInterviewToolsSetup) {
    try {
      Write-Host "Setting up MS-Interview tools (Kokoro, Stress, Whisper assets)..."
      & pwsh -NoProfile -ExecutionPolicy Bypass -File $msInterviewToolsSetup
    } catch {
      Write-Warning "MS-Interview tools setup reported an error: $($_.Exception.Message)"
    }
  } else {
    Write-Warning "MS-Interview tools setup script not found: $msInterviewToolsSetup"
  }

  # Start MS-Interview auxiliary tools (Judge0, Kokoro, Stress) if the helper script exists.
  $msInterviewStackScript = Join-Path $msInterviewDir "run-ms-interview-stack.ps1"
  if (Test-Path $msInterviewStackScript) {
    try {
      Write-Host "Starting MS-Interview tools (Judge0, Kokoro, Stress)..."
      & pwsh -NoProfile -ExecutionPolicy Bypass -File $msInterviewStackScript -Action start -SkipBackend
    } catch {
      Write-Warning "Failed to start MS-Interview tools via run-ms-interview-stack.ps1: $($_.Exception.Message)"
    }
  } else {
    Write-Host "MS-Interview tools script not found; skipping tool startup. Expected at: $msInterviewStackScript"
  }

  Start-ServiceProcess -Name "MS-Interview" -WorkingDirectory $msInterviewDir -CommandLines @(
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8081 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "MS-Interview did not open port 8081 within timeout."
    }
  }
}

if (-not $SkipRoadmap -and (Test-Path $msRoadmapDir)) {
  Start-ServiceProcess -Name "MS-Roadmap" -WorkingDirectory $msRoadmapDir -CommandLines @(
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8083 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "MS-Roadmap did not open port 8083 within timeout."
    }
  }
}

if (-not $SkipAssessment -and (Test-Path $msAssessmentDir)) {
  Start-ServiceProcess -Name "MS-Assessment" -WorkingDirectory $msAssessmentDir -CommandLines @(
    "& '.\\mvnw.cmd' spring-boot:run"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 8084 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "MS-Assessment did not open port 8084 within timeout."
    }
  }
}

if (-not $SkipAIAdvice -and (Test-Path $aiAdviceDir)) {
  $pythonCheck = try { python --version 2>&1 | Select-Object -First 1 } catch { $null }
  if (-not $pythonCheck) {
    Write-Warning "Python not found in PATH. AI-Advice service will not start."
  } else {
    Start-ServiceProcess -Name "AI-Advice Service" -WorkingDirectory $aiAdviceDir -CommandLines @(
      ". .\venv\Scripts\Activate.ps1",
      "python app.py"
    )

    if (-not $NoHealthChecks) {
      if (-not (Wait-Port -Port 5001 -TimeoutSeconds $TimeoutSec)) {
        Write-Warning "AI-Advice Service did not open port 5001 within timeout."
      }
    }
  }
}

if (-not $SkipFaceRecognition -and (Test-Path $faceRecognitionDir)) {
  $pythonCheck = try { python --version 2>&1 | Select-Object -First 1 } catch { $null }
  if (-not $pythonCheck) {
    Write-Warning "Python not found in PATH. Face Recognition service will not start. Install Python 3.8+ and add to PATH."
  } elseif (Wait-Port -Port 5050 -TimeoutSeconds 1) {
    Write-Host "Face Recognition Service already listening on port 5050. Skipping new start."
  } else {
    Start-ServiceProcess -Name "Face Recognition Service" -WorkingDirectory $faceRecognitionDir -CommandLines @(
      "`$env:PORT = '5050'",
      "`$env:CORS_ORIGINS = 'http://localhost:4200,http://127.0.0.1:4200,http://localhost:8887,http://127.0.0.1:8887'",
      ". .\venv\Scripts\Activate.ps1",
      "python face_recognition_service.py"
    )

    if (-not $NoHealthChecks) {
      if (-not (Wait-Port -Port 5050 -TimeoutSeconds $TimeoutSec)) {
        Write-Warning "Face Recognition Service did not open port 5050 within timeout."
      }
    }
  }
}

if (-not $SkipFrontend -and (Test-Path $frontendRoot)) {
  if (-not (Test-Path (Join-Path $frontendRoot "node_modules"))) {
    Write-Warning "Frontend node_modules is missing. Run 'npm install' in the frontend folder."
  }

  Start-ServiceProcess -Name "Frontend" -WorkingDirectory $frontendRoot -CommandLines @(
    "npm start"
  )

  if (-not $NoHealthChecks) {
    if (-not (Wait-Port -Port 4200 -TimeoutSeconds $TimeoutSec)) {
      Write-Warning "Frontend did not open port 4200 within timeout."
    }
  }
}

Write-Host "Launch complete."
Write-Host "If a service failed to start, check its terminal logs."
