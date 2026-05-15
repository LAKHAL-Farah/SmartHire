$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8081/interview-service/api/v1'
$baseline = 15.87
$results = @()

function Wait-Eval {
    param(
        [Parameter(Mandatory = $true)][long]$AnswerId,
        [int]$TimeoutSeconds = 60
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $last = $null

    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $eval = Invoke-RestMethod -Method GET -Uri "$base/evaluations/answer/$AnswerId"
            $last = $eval

            $hasOverall = $null -ne $eval.overallScore
            $hasFeedback = -not [string]::IsNullOrWhiteSpace([string]$eval.aiFeedback)
            $hasFollow = -not [string]::IsNullOrWhiteSpace([string]$eval.followUpGenerated)
            $notInProgress = ([string]$eval.aiFeedback) -notlike 'Evaluation in progress*'
            $notFailed = ([string]$eval.aiFeedback) -notlike 'Evaluation failed*'

            if ($hasOverall -and $hasFeedback -and $hasFollow -and $notInProgress -and $notFailed) {
                return @{
                    ready = $true
                    eval = $eval
                    elapsed = $sw.Elapsed.TotalSeconds
                }
            }
        } catch {
            $last = $_.Exception.Message
        }

        Start-Sleep -Seconds 1
    }

    return @{
        ready = $false
        eval = $last
        elapsed = $sw.Elapsed.TotalSeconds
    }
}

for ($i = 1; $i -le 5; $i++) {
    $startBody = @{
        userId = 1200 + $i
        careerPathId = 1
        roleType = 'ALL'
        mode = 'PRACTICE'
        type = 'BEHAVIORAL'
        questionCount = 2
    } | ConvertTo-Json

    $session = Invoke-RestMethod -Method POST -Uri "$base/sessions/start" -ContentType 'application/json' -Body $startBody
    $sessionId = [long]$session.id

    $current = Invoke-RestMethod -Method GET -Uri "$base/sessions/$sessionId/questions/current"
    $questionId = [long]$current.id

    $submitBody = @{
        sessionId = $sessionId
        questionId = $questionId
        answerText = 'During my internship I faced repeated API latency spikes. I first profiled endpoint timings, then found an N+1 query pattern in the service layer. I fixed it by batching reads and adding an index, then added metrics dashboards and alert thresholds so we could catch regressions early. P95 dropped from about 1.8s to 650ms and error rates stabilized. The main lesson was to validate assumptions with measurements before optimizing.'
    } | ConvertTo-Json

    $submitStart = Get-Date
    $answer = Invoke-RestMethod -Method POST -Uri "$base/answers/submit" -ContentType 'application/json' -Body $submitBody

    $wait = Wait-Eval -AnswerId ([long]$answer.id) -TimeoutSeconds 60
    $evalLatency = [Math]::Round(((Get-Date) - $submitStart).TotalSeconds, 2)

    $score = $null
    $scorePass = $false
    $feedbackPass = $false
    $followPass = $false
    $qualityPass = $false

    if ($wait.ready) {
        $score = [double]$wait.eval.overallScore
        $scorePass = ($score -ge 0 -and $score -le 10)
        $feedbackPass = ([string]$wait.eval.aiFeedback).Trim().Length -ge 20
        $followPass = ([string]$wait.eval.followUpGenerated).Trim().Length -ge 8
        $qualityPass = $scorePass -and $feedbackPass -and $followPass
    }

    $null = Invoke-RestMethod -Method PUT -Uri "$base/sessions/$sessionId/complete"

    $reportStart = Get-Date
    $report = Invoke-RestMethod -Method POST -Uri "$base/reports/generate/$sessionId"
    $reportLatency = [Math]::Round(((Get-Date) - $reportStart).TotalSeconds, 2)

    $reportPass =
        (-not [string]::IsNullOrWhiteSpace([string]$report.strengths)) -and
        (-not [string]::IsNullOrWhiteSpace([string]$report.weaknesses)) -and
        (-not [string]::IsNullOrWhiteSpace([string]$report.recommendations)) -and
        (-not [string]::IsNullOrWhiteSpace([string]$report.recruiterVerdict))

    $runPass = $wait.ready -and $qualityPass -and $reportPass

    $results += [pscustomobject]@{
        run = $i
        sessionId = $sessionId
        answerId = $answer.id
        evalReady = $wait.ready
        evalLatencySec = $evalLatency
        overallScore = $score
        scorePass = $scorePass
        feedbackPass = $feedbackPass
        followUpPass = $followPass
        reportLatencySec = $reportLatency
        reportPass = $reportPass
        runPass = $runPass
    }
}

$latencies = $results | Where-Object { $_.evalReady } | Select-Object -ExpandProperty evalLatencySec
$avg = if ($latencies.Count -gt 0) { [Math]::Round((($latencies | Measure-Object -Average).Average), 2) } else { 0 }
$min = if ($latencies.Count -gt 0) { [Math]::Round((($latencies | Measure-Object -Minimum).Minimum), 2) } else { 0 }
$max = if ($latencies.Count -gt 0) { [Math]::Round((($latencies | Measure-Object -Maximum).Maximum), 2) } else { 0 }
$improvement = if ($avg -gt 0) { [Math]::Round((($baseline - $avg) / $baseline) * 100, 2) } else { 0 }

$latencyTargetPass = ($avg -lt 8.0)
$qualityAllPass = (($results | Where-Object { -not $_.runPass }).Count -eq 0)

[pscustomobject]@{
    baselineSec = $baseline
    avgEvalLatencySec = $avg
    minEvalLatencySec = $min
    maxEvalLatencySec = $max
    improvementPct = $improvement
    latencyTargetPass = $latencyTargetPass
    qualityAllPass = $qualityAllPass
    results = $results
} | ConvertTo-Json -Depth 8
