Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ModelDir = Join-Path $PSScriptRoot "models"
$ModelPath = Join-Path $ModelDir "ggml-base.en.bin"
$ModelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

New-Item -ItemType Directory -Path $ModelDir -Force | Out-Null

if (Test-Path $ModelPath) {
    $sizeMb = [math]::Round((Get-Item $ModelPath).Length / 1MB, 2)
    Write-Host "Model already exists: $ModelPath ($sizeMb MB)"
    exit 0
}

Write-Host "Downloading Whisper model from $ModelUrl"
Invoke-WebRequest -Uri $ModelUrl -OutFile $ModelPath

if (-not (Test-Path $ModelPath) -or (Get-Item $ModelPath).Length -eq 0) {
    throw "Model download failed or file is empty: $ModelPath"
}

$finalSizeMb = [math]::Round((Get-Item $ModelPath).Length / 1MB, 2)
Write-Host "Download complete: $ModelPath ($finalSizeMb MB)"
