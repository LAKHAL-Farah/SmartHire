<#
Idempotent setup script for MS-Interview tools.
- Creates Python virtualenvs for Kokoro and Stress services if missing
- Installs their requirements
- Runs Kokoro model download script
- Runs Whisper download script

Run from the `backend/MS-Interview/tools` folder or from anywhere (script resolves paths).
Usage: pwsh -NoProfile -ExecutionPolicy Bypass -File .\setup-tools.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Info { param([string]$m) Write-Host "[INFO] $m" }
function Write-Ok   { param([string]$m) Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Warn { param([string]$m) Write-Host "[WARN] $m" -ForegroundColor Yellow }

function Get-PythonCommand {
    if (Get-Command py -ErrorAction SilentlyContinue) { return 'py' }
    if (Get-Command python -ErrorAction SilentlyContinue) { return 'python' }
    throw 'Python launcher not found. Install Python 3.11+ and ensure py or python is in PATH.'
}

function Ensure-VenvAndRequirements {
    param(
        [string]$ServiceName,
        [string]$ServiceDir,
        [string]$VenvPythonRelative = 'venv/Scripts/python.exe',
        [string]$RequirementsFile = 'requirements.txt'
    )

    $venvPython = Join-Path $ServiceDir $VenvPythonRelative

    if (-not (Test-Path $ServiceDir)) {
        Write-Warn "$ServiceName directory not found: $ServiceDir"
        return $false
    }

    $pythonLauncher = Get-PythonCommand

    if (-not (Test-Path $venvPython)) {
        Write-Info "$ServiceName venv not found. Creating venv..."
        Push-Location $ServiceDir
        try {
            if ($pythonLauncher -eq 'py') { & py -3 -m venv venv } else { & python -m venv venv }
        } finally { Pop-Location }

        if (-not (Test-Path $venvPython)) {
            Write-Warn "Failed to create venv for $ServiceName at $venvPython"
            return $false
        }
        Write-Ok "$ServiceName venv created"
    } else {
        Write-Info "$ServiceName venv already exists"
    }

    $reqPath = Join-Path $ServiceDir $RequirementsFile
    if (Test-Path $reqPath) {
        Write-Info "Installing $ServiceName requirements from $reqPath"
        & $venvPython -m pip install --upgrade pip
        & $venvPython -m pip install -r $reqPath
        Write-Ok "$ServiceName dependencies installed"
    } else {
        Write-Warn "$ServiceName requirements file not found at $reqPath"
    }

    return $true
}

try {
    # Kokoro
    $kokoroDir = Join-Path $ScriptRoot 'kokoro'
    $kokoroVenvPython = Join-Path $kokoroDir 'venv/Scripts/python.exe'
    Ensure-VenvAndRequirements -ServiceName 'Kokoro' -ServiceDir $kokoroDir -VenvPythonRelative 'venv/Scripts/python.exe' -RequirementsFile 'requirements.txt' | Out-Null

    # Run kokoro download_model.py if model is missing
    $kokoroModel = Join-Path $kokoroDir 'kokoro-v1.0.onnx'
    $kokoroVoices = Join-Path $kokoroDir 'voices-v1.0.bin'
    $kokoroDownload = Join-Path $kokoroDir 'download_model.py'
    if ((-not (Test-Path $kokoroModel)) -or (-not (Test-Path $kokoroVoices))) {
        if (Test-Path $kokoroDownload) {
            Write-Info 'Downloading Kokoro model assets...'
            & $kokoroVenvPython $kokoroDownload
            Write-Ok 'Kokoro model download attempted'
        } else {
            Write-Warn "Kokoro download script not found at $kokoroDownload"
        }
    } else {
        Write-Info 'Kokoro model assets already present'
    }

    # Stress inference service
    $stressDir = Join-Path $ScriptRoot 'stress-inference-service'
    Ensure-VenvAndRequirements -ServiceName 'StressInference' -ServiceDir $stressDir -VenvPythonRelative 'venv/Scripts/python.exe' -RequirementsFile 'requirements.txt' | Out-Null

    # Whisper binaries/models
    $whisperDir = Join-Path $ScriptRoot 'whisper'
    $whisperDownload = Join-Path $whisperDir 'download-model.ps1'
    if (Test-Path $whisperDownload) {
        Write-Info 'Downloading Whisper model (may take time)...'
        & pwsh -NoProfile -ExecutionPolicy Bypass -File $whisperDownload
        Write-Ok 'Whisper model download attempted'
    } else {
        Write-Warn "Whisper download script not found at $whisperDownload"
    }

    # Report
    Write-Host "`nSetup report:`n"
    $checkPaths = @(
        Join-Path $kokoroDir 'venv/Scripts/python.exe',
        Join-Path $kokoroDir 'kokoro-v1.0.onnx',
        Join-Path $kokoroDir 'voices-v1.0.bin',
        Join-Path $stressDir 'venv/Scripts/python.exe',
        Join-Path $whisperDir 'ggml-base.dll'
    )

    foreach ($p in $checkPaths) {
        $status = if (Test-Path $p) { 'FOUND' } else { 'MISSING' }
        Write-Host ("{0} => {1}" -f $p, $status)
    }

    Write-Ok 'Setup-tools script finished.'
} catch {
    Write-Warn "Setup failed: $($_.Exception.Message)"
    exit 1
}
