$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081/interview-service/api/v1'
$checks = New-Object System.Collections.ArrayList
$sessionId = $null
$questionId = $null
$answerId = $null

function Add-Check {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Details
    )

    [void]$checks.Add([pscustomobject]@{
        Check = $Name
        Passed = $Passed
        Details = $Details
    })
}

try {
    $hello = Invoke-RestMethod -Method GET -Uri 'http://localhost:8081/interview-service/hello'
    Add-Check '1-App reachable' ($hello -eq 'Hello from Interview Service') ($hello | Out-String)
} catch {
    Add-Check '1-App reachable' $false $_.Exception.Message
}

try {
    $startBody = @{
        userId = 999
        careerPathId = 1
        roleType = 'SE'
        mode = 'PRACTICE'
        type = 'TECHNICAL'
        questionCount = 5
    } | ConvertTo-Json

    $session = Invoke-RestMethod -Method POST -Uri "$base/sessions/start" -ContentType 'application/json' -Body $startBody
    $sessionId = $session.id
    Add-Check '2-Start session JSON' ($null -ne $sessionId) ($session | ConvertTo-Json -Depth 8)
} catch {
    Add-Check '2-Start session JSON' $false $_.Exception.Message
}

try {
    $current = Invoke-RestMethod -Method GET -Uri "$base/sessions/$sessionId/questions/current"
    $questionId = $current.id
    Add-Check '3-Current question fetched' ($null -ne $questionId) ($current | ConvertTo-Json -Depth 8)
} catch {
    Add-Check '3-Current question fetched' $false $_.Exception.Message
}

try {
    $submitBody = @{
        sessionId = $sessionId
        questionId = $questionId
        answerText = 'To improve scalability, I would use stateless services behind a load balancer, cache hot reads with Redis, and rely on asynchronous messaging for slow workflows.'
    } | ConvertTo-Json

    $answer = Invoke-RestMethod -Method POST -Uri "$base/answers/submit" -ContentType 'application/json' -Body $submitBody
    $answerId = $answer.id
    Add-Check '4-Submit answer' ($null -ne $answerId) ($answer | ConvertTo-Json -Depth 8)
} catch {
    Add-Check '4-Submit answer' $false $_.Exception.Message
}

$evalFound = $false
$evalReady = $false
$lastEvalPayload = ''

for ($i = 0; $i -lt 30; $i++) {
    try {
        $tmpEval = Invoke-RestMethod -Method GET -Uri "$base/evaluations/answer/$answerId"
        $lastEvalPayload = $tmpEval | ConvertTo-Json -Depth 8
        $evalFound = $true

        $hasOverall = $null -ne $tmpEval.overallScore
        $hasFeedback = -not [string]::IsNullOrWhiteSpace([string]$tmpEval.aiFeedback)
        $hasFollowUp = -not [string]::IsNullOrWhiteSpace([string]$tmpEval.followUpGenerated)
        $notInProgress = ([string]$tmpEval.aiFeedback) -notlike 'Evaluation in progress*'
        $notFailed = ([string]$tmpEval.aiFeedback) -notlike 'Evaluation failed*'

        if ($hasOverall -and $hasFeedback -and $hasFollowUp -and $notInProgress -and $notFailed) {
            $evalReady = $true
            break
        }
    } catch {
        $lastEvalPayload = $_.Exception.Message
    }

    Start-Sleep -Seconds 1
}

Add-Check '5-Evaluation row appears' $evalFound $lastEvalPayload
Add-Check '6-NVIDIA evaluation complete' $evalReady $lastEvalPayload

try {
    $completed = Invoke-RestMethod -Method PUT -Uri "$base/sessions/$sessionId/complete"
    $isCompleted = ([string]$completed.status -eq 'COMPLETED')
    Add-Check '7-Session completed' $isCompleted ($completed | ConvertTo-Json -Depth 8)
} catch {
    Add-Check '7-Session completed' $false $_.Exception.Message
}

try {
    $report = Invoke-RestMethod -Method POST -Uri "$base/reports/generate/$sessionId"
    $okStrengths = -not [string]::IsNullOrWhiteSpace([string]$report.strengths)
    $okWeaknesses = -not [string]::IsNullOrWhiteSpace([string]$report.weaknesses)
    $okVerdict = -not [string]::IsNullOrWhiteSpace([string]$report.recruiterVerdict)
    $okReport = $okStrengths -and $okWeaknesses -and $okVerdict
    Add-Check '8-Report generated with narratives' $okReport ($report | ConvertTo-Json -Depth 8)
} catch {
    Add-Check '8-Report generated with narratives' $false $_.Exception.Message
}

$passed = ($checks | Where-Object { $_.Passed }).Count
$total = $checks.Count

[pscustomobject]@{
    passed = $passed
    total = $total
    checks = $checks
    sessionId = $sessionId
    questionId = $questionId
    answerId = $answerId
} | ConvertTo-Json -Depth 8
