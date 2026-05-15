param(
    [ValidateSet("start", "stop", "status", "restart")]
    [string]$Action = "start",
    [switch]$SkipJudge0,
    [switch]$SkipKokoro,
    [switch]$SkipStress,
    [switch]$SkipBackend,
    [switch]$RestartKokoro,
    [switch]$RestartStress
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Judge0Dir = Join-Path $RootDir "tools/Judge0/judge0-v1.13.1"
$KokoroDir = Join-Path $RootDir "tools/kokoro"
$KokoroVenvPython = Join-Path $KokoroDir "venv/Scripts/python.exe"
$KokoroServerScript = Join-Path $KokoroDir "tts_server.py"
$KokoroRequirements = Join-Path $KokoroDir "requirements.txt"
$KokoroDownloadScript = Join-Path $KokoroDir "download_model.py"
$KokoroModel = Join-Path $KokoroDir "kokoro-v1.0.onnx"
$KokoroVoices = Join-Path $KokoroDir "voices-v1.0.bin"
$StressDir = Join-Path $RootDir "tools/stress-inference-service"
$StressVenvPython = Join-Path $StressDir "venv/Scripts/python.exe"
$StressMainScript = Join-Path $StressDir "main.py"
$StressRequirements = Join-Path $StressDir "requirements.txt"
$StateFile = Join-Path $RootDir "temp/ms-interview-stack-state.json"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message"
}

function Write-WarnLine {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Ok {
    param([string]$Message)
    Write-Host "[OK]   $Message" -ForegroundColor Green
}

function Quote-PS {
    param([string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-PythonCommand {
    if (Get-Command py -ErrorAction SilentlyContinue) {
        return "py"
    }

    if (Get-Command python -ErrorAction SilentlyContinue) {
        return "python"
    }

    throw "Python launcher not found. Install Python 3.11+ and ensure py or python is in PATH."
}

function Test-PortOpen {
    param([int]$Port)

    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        return [bool]$conn
    } catch {
        return $false
    }
}

function Wait-PortOpen {
    param(
        [int]$Port,
        [int]$TimeoutMs = 30000,
        [int]$PollMs = 500
    )

    $deadline = (Get-Date).AddMilliseconds($TimeoutMs)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen -Port $Port) {
            return $true
        }
        Start-Sleep -Milliseconds $PollMs
    }

    return (Test-PortOpen -Port $Port)
}

function Get-PortPid {
    param([int]$Port)

    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) {
            return [int]$conn.OwningProcess
        }
    } catch {}

    return $null
}

function Read-State {
    if (-not (Test-Path $StateFile)) {
        return @{}
    }

    try {
        return (Get-Content -Raw -Path $StateFile | ConvertFrom-Json -AsHashtable)
    } catch {
        Write-WarnLine "State file is invalid; ignoring previous state."
        return @{}
    }
}

function Save-State {
    param([hashtable]$State)

    $stateDir = Split-Path -Parent $StateFile
    New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
    $State | ConvertTo-Json -Depth 10 | Set-Content -Path $StateFile -Encoding UTF8
}

function Ensure-Venv {
    param(
        [string]$ServiceName,
        [string]$ServiceDir,
        [string]$VenvPython,
        [string]$RequirementsFile
    )

    if (Test-Path $VenvPython) {
        return
    }

    $pythonCommand = Get-PythonCommand
    Write-Info "$ServiceName venv not found. Creating it now..."

    Push-Location $ServiceDir
    try {
        if ($pythonCommand -eq "py") {
            & py -3 -m venv venv
        } else {
            & python -m venv venv
        }

        if (-not (Test-Path $VenvPython)) {
            throw "Failed to create venv for $ServiceName at $VenvPython"
        }

        if (Test-Path $RequirementsFile) {
            & $VenvPython -m pip install --upgrade pip
            & $VenvPython -m pip install -r $RequirementsFile
        }
    } finally {
        Pop-Location
    }

    Write-Ok "$ServiceName venv is ready."
}

function Restart-PortListener {
    param(
        [int]$Port,
        [string]$ServiceName
    )

    $procId = Get-PortPid -Port $Port
    if (-not $procId) {
        return
    }

    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        Write-Info "Stopped existing $ServiceName listener on port $Port (pid=$procId)."
        Start-Sleep -Milliseconds 500
    } catch {
        Write-WarnLine "Could not stop existing $ServiceName listener on port $Port (pid=$procId)."
    }
}

function Stop-IfManaged {
    param(
        [hashtable]$State,
        [string]$Key,
        [string]$DisplayName
    )

    if (-not $State.ContainsKey($Key)) {
        return
    }

    $entry = $State[$Key]
    if (-not $entry -or -not $entry.pid -or -not $entry.managed) {
        return
    }

    try {
        Stop-Process -Id ([int]$entry.pid) -Force -ErrorAction Stop
        Write-Ok "Stopped $DisplayName (pid=$($entry.pid))."
    } catch {
        Write-WarnLine "$DisplayName process pid=$($entry.pid) is not running."
    }
}

function Start-Kokoro {
    param([hashtable]$State)

    if (-not (Test-Path $KokoroDir)) {
        throw "Kokoro directory not found: $KokoroDir"
    }

    if (-not (Test-Path $KokoroServerScript)) {
        throw "Kokoro server script not found: $KokoroServerScript"
    }

    Ensure-Venv -ServiceName "Kokoro" -ServiceDir $KokoroDir -VenvPython $KokoroVenvPython -RequirementsFile $KokoroRequirements

    if ((-not (Test-Path $KokoroModel)) -or (-not (Test-Path $KokoroVoices))) {
        if (-not (Test-Path $KokoroDownloadScript)) {
            throw "Kokoro model download script not found: $KokoroDownloadScript"
        }

        Write-Info "Kokoro model assets are missing. Downloading required model files..."
        & $KokoroVenvPython $KokoroDownloadScript

        if ((-not (Test-Path $KokoroModel)) -or (-not (Test-Path $KokoroVoices))) {
            throw "Kokoro model download completed but required files are still missing."
        }

        Write-Ok "Kokoro model assets are ready."
    }

    if ($RestartKokoro) {
        Restart-PortListener -Port 5000 -ServiceName "kokoro"
    }

    if (Test-PortOpen -Port 5000) {
        $listenerPid = Get-PortPid -Port 5000
        Write-WarnLine "Kokoro already listening on 5000 (pid=$listenerPid)."
        $State.kokoro = [ordered]@{ managed = $false; pid = $listenerPid; startedAt = $null }
        return
    }

    $command = "Set-Location $(Quote-PS $KokoroDir); `$env:CORS_ORIGINS = 'http://localhost:4200,http://127.0.0.1:4200,http://localhost:8887,http://127.0.0.1:8887'; `& $(Quote-PS $KokoroVenvPython) $(Quote-PS $KokoroServerScript)"
    $proc = Start-Process -FilePath "pwsh" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
    $State.kokoro = [ordered]@{ managed = $true; pid = $proc.Id; startedAt = (Get-Date).ToString("o") }

    if (Wait-PortOpen -Port 5000 -TimeoutMs 60000 -PollMs 500) {
        Write-Ok "Kokoro started on http://localhost:5000/tts"
    } else {
        Write-WarnLine "Kokoro process started (pid=$($proc.Id)) but port 5000 did not open in time."
    }
}

function Start-Stress {
    param([hashtable]$State)

    if (-not (Test-Path $StressDir)) {
        throw "Stress service directory not found: $StressDir"
    }

    if (-not (Test-Path $StressMainScript)) {
        throw "Stress service script not found: $StressMainScript"
    }

    Ensure-Venv -ServiceName "Stress" -ServiceDir $StressDir -VenvPython $StressVenvPython -RequirementsFile $StressRequirements

    if ($RestartStress) {
        Restart-PortListener -Port 8000 -ServiceName "stress"
    }

    if (Test-PortOpen -Port 8000) {
        $listenerPid = Get-PortPid -Port 8000
        Write-WarnLine "Stress service already listening on 8000 (pid=$listenerPid)."
        $State.stress = [ordered]@{ managed = $false; pid = $listenerPid; startedAt = $null }
        return
    }

    # Set CORS origins environment variable to allow frontend calls
    $corsOrigins = "http://localhost:4200,http://127.0.0.1:4200,http://localhost:8887,http://127.0.0.1:8887"
    $command = "`$env:CORS_ORIGINS='$corsOrigins'; Set-Location $(Quote-PS $StressDir); `& $(Quote-PS $StressVenvPython) -m uvicorn main:app --host 127.0.0.1 --port 8000 --reload"
    $proc = Start-Process -FilePath "pwsh" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
    $State.stress = [ordered]@{ managed = $true; pid = $proc.Id; startedAt = (Get-Date).ToString("o") }

    if (Wait-PortOpen -Port 8000 -TimeoutMs 90000 -PollMs 500) {
        Write-Ok "Stress service started on http://127.0.0.1:8000/analyze"
    } else {
        Write-WarnLine "Stress process started (pid=$($proc.Id)) but port 8000 did not open in time."
    }
}

function Start-Backend {
    param([hashtable]$State)

    if (Test-PortOpen -Port 8081) {
        $listenerPid = Get-PortPid -Port 8081
        Write-WarnLine "MS-Interview backend already listening on 8081 (pid=$listenerPid)."
        $State.backend = [ordered]@{ managed = $false; pid = $listenerPid; startedAt = $null }
        return
    }

    $command = "Set-Location $(Quote-PS $RootDir); `& { .\mvnw spring-boot:run -DskipTests }"
    $proc = Start-Process -FilePath "pwsh" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
    $State.backend = [ordered]@{ managed = $true; pid = $proc.Id; startedAt = (Get-Date).ToString("o") }
    Write-Ok "MS-Interview backend started in a new terminal (pid=$($proc.Id))."
}

function Start-Stack {
    $state = Read-State

    if (-not $SkipJudge0) {
        if (-not (Test-Path $Judge0Dir)) {
            throw "Judge0 directory not found: $Judge0Dir"
        }

        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            throw "Docker CLI not found. Install Docker Desktop and ensure docker is in PATH."
        }

        Write-Info "Starting Judge0 via docker compose..."
        Push-Location $Judge0Dir
        try {
            docker compose up -d | Out-Null
            Write-Ok "Judge0 started on http://localhost:2358"
        } finally {
            Pop-Location
        }
    }

    if (-not $SkipKokoro) {
        Start-Kokoro -State $state
    }

    if (-not $SkipStress) {
        Start-Stress -State $state
    }

    if (-not $SkipBackend) {
        Start-Backend -State $state
    }

    $state.lastAction = "start"
    $state.lastUpdated = (Get-Date).ToString("o")
    Save-State -State $state

    Write-Host ""
    Write-Host "Run this command anytime to inspect status:"
    Write-Host "  .\run-ms-interview-stack.ps1 -Action status"
}

function Stop-Stack {
    $state = Read-State

    if (-not $SkipKokoro) {
        Stop-IfManaged -State $state -Key "kokoro" -DisplayName "Kokoro"
    }

    if (-not $SkipStress) {
        Stop-IfManaged -State $state -Key "stress" -DisplayName "Stress"
    }

    if (-not $SkipBackend) {
        Stop-IfManaged -State $state -Key "backend" -DisplayName "MS-Interview backend"
    }

    if (-not $SkipJudge0) {
        if (Test-Path $Judge0Dir) {
            Write-Info "Stopping Judge0 via docker compose..."
            Push-Location $Judge0Dir
            try {
                docker compose down | Out-Null
                Write-Ok "Judge0 stopped."
            } finally {
                Pop-Location
            }
        }
    }

    Remove-Item -Path $StateFile -Force -ErrorAction SilentlyContinue
}

function Show-Status {
    $judgeUp = Test-PortOpen -Port 2358
    $kokoroUp = Test-PortOpen -Port 5000
    $stressUp = Test-PortOpen -Port 8000
    $backendUp = Test-PortOpen -Port 8081

    Write-Host "MS-Interview stack status"
    Write-Host "-------------------------"
    Write-Host ("Judge0  : {0}" -f ($(if ($judgeUp) { "UP" } else { "DOWN" })))
    Write-Host ("Kokoro  : {0}" -f ($(if ($kokoroUp) { "UP" } else { "DOWN" })))
    Write-Host ("Stress  : {0}" -f ($(if ($stressUp) { "UP" } else { "DOWN" })))
    Write-Host ("Backend : {0}" -f ($(if ($backendUp) { "UP" } else { "DOWN" })))
}

switch ($Action) {
    "start" {
        Start-Stack
    }
    "stop" {
        Stop-Stack
    }
    "status" {
        Show-Status
    }
    "restart" {
        Stop-Stack
        Start-Stack
    }
}
