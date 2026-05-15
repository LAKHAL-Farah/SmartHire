#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Integration test script for Face Recognition Service
    
.DESCRIPTION
    Test all endpoints of the Face Recognition Service using curl

.EXAMPLE
    .\test_endpoints.ps1
    .\test_endpoints.ps1 -ServiceUrl "http://localhost:5000"

.NOTES
    Requires curl and PowerShell 5.1+
#>

param(
    [string]$ServiceUrl = "http://localhost:5000",
    [int]$Timeout = 30,
    [switch]$Verbose
)

# Colors for output
$Colors = @{
    Green  = "`e[92m"
    Red    = "`e[91m"
    Yellow = "`e[93m"
    Cyan   = "`e[96m"
    Blue   = "`e[94m"
    Bold   = "`e[1m"
    Reset  = "`e[0m"
}

function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "Blue"
    )
    Write-Host "$($Colors[$Color])$Message$($Colors.Reset)"
}

function Write-Success {
    Write-ColorOutput "✓ $args" "Green"
}

function Write-Error {
    Write-ColorOutput "✗ $args" "Red"
}

function Write-Info {
    Write-ColorOutput "ℹ $args" "Cyan"
}

function Write-Header {
    Write-Host ""
    Write-ColorOutput "$($Colors.Bold)$('='*70)" "Blue"
    Write-ColorOutput "$args".PadRight(70).Substring(0, 70).PadLeft((70 + $args.Length) / 2).PadRight(70) "Blue"
    Write-ColorOutput "$($Colors.Bold)$('='*70)" "Blue"
    Write-Host ""
}

function Write-Section {
    Write-ColorOutput "$($Colors.Bold)$args" "Yellow"
    Write-ColorOutput "-" * $args.Length "Yellow"
    Write-Host ""
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Body,
        [string]$ContentType = "application/json"
    )
    
    $url = "$ServiceUrl$Endpoint"
    
    try {
        $params = @{
            Uri             = $url
            Method          = $Method
            ContentType     = $ContentType
            TimeoutSec      = $Timeout
            UseBasicParsing = $true
        }
        
        if ($Body) {
            $params['Body'] = ($Body | ConvertTo-Json -Depth 10)
        }
        
        $response = Invoke-WebRequest @params
        return @{
            StatusCode = $response.StatusCode
            Content    = $response.Content | ConvertFrom-Json
            Success    = $response.StatusCode -eq 200 -or $response.StatusCode -eq 201
        }
    }
    catch {
        if ($Verbose) {
            Write-Host "Error: $_"
        }
        return @{
            StatusCode = $_.Exception.Response.StatusCode
            Content    = $null
            Success    = $false
            Error      = $_.Exception.Message
        }
    }
}

function Test-Health {
    Write-Section "Testing /health endpoint"
    
    $result = Invoke-ApiRequest -Method "GET" -Endpoint "/health"
    
    if ($result.Success) {
        Write-Success "Health check passed (HTTP $($result.StatusCode))"
        $data = $result.Content
        Write-Host "  Status: $($data.status)"
        Write-Host "  Service: $($data.service)"
        Write-Host "  Version: $($data.version)"
        if ($data.statistics) {
            Write-Host "  Embeddings: $($data.statistics.total_embeddings)"
            Write-Host "  Verifications: $($data.statistics.total_verifications)"
        }
        return $true
    }
    else {
        Write-Error "Health check failed: $($result.Error)"
        return $false
    }
}

function Test-Info {
    Write-Section "Testing /info endpoint"
    
    $result = Invoke-ApiRequest -Method "GET" -Endpoint "/info"
    
    if ($result.Success) {
        Write-Success "Service info retrieved (HTTP $($result.StatusCode))"
        Write-Host "  Service: $($result.Content.service_name)"
        Write-Host "  Version: $($result.Content.version)"
        Write-Host "  API Version: $($result.Content.api_version)"
        return $true
    }
    else {
        Write-Error "Failed to get info: $($result.Error)"
        return $false
    }
}

function Test-Stats {
    Write-Section "Testing /stats endpoint"
    
    $result = Invoke-ApiRequest -Method "GET" -Endpoint "/stats"
    
    if ($result.Success) {
        Write-Success "Statistics retrieved (HTTP $($result.StatusCode))"
        $data = $result.Content
        Write-Host "  Total Embeddings: $($data.total_embeddings)"
        Write-Host "  Total Verifications: $($data.total_verifications)"
        Write-Host "  Successful: $($data.successful_verifications)"
        Write-Host "  Failed: $($data.failed_verifications)"
        Write-Host "  Success Rate: $($data.success_rate)%"
        return $true
    }
    else {
        Write-Error "Failed to get stats: $($result.Error)"
        return $false
    }
}

function Test-CheckService {
    Write-Info "Checking service availability..."
    
    try {
        $testResult = Invoke-WebRequest -Uri "$ServiceUrl/health" `
            -Method "GET" `
            -TimeoutSec 5 `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($testResult.StatusCode -eq 200) {
            Write-Success "Service is accessible!"
            return $true
        }
        else {
            Write-Error "Service returned status code: $($testResult.StatusCode)"
            return $false
        }
    }
    catch {
        Write-Error "Cannot connect to service at $ServiceUrl"
        Write-Info "Make sure the service is running!"
        Write-Info "Run: docker-compose up"
        Write-Info "Or: python face_recognition_service.py"
        return $false
    }
}

# ============================================================
# Main Script
# ============================================================

Write-Header "Face Recognition Service - Test Suite"
Write-Info "Service URL: $ServiceUrl"
Write-Info "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# Check service availability
if (-not (Test-CheckService)) {
    exit 1
}

Write-Host ""
Write-Header "Running Tests"

# Run tests
$allPassed = $true

try {
    if (-not (Test-Health)) { $allPassed = $false }
    if (-not (Test-Info)) { $allPassed = $false }
    if (-not (Test-Stats)) { $allPassed = $false }
}
catch {
    Write-Error "Test execution error: $_"
    $allPassed = $false
}

# Summary
Write-Header "Test Summary"

if ($allPassed) {
    Write-Success "All tests completed!"
    Write-ColorOutput "Service is healthy and responding correctly" "Green"
}
else {
    Write-Error "Some tests failed"
    Write-Info "Check the output above for details"
}

Write-Host ""
Write-ColorOutput "Test suite execution completed" "Cyan"
