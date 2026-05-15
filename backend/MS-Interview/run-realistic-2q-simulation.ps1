$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8081/interview-service'
$api = "$base/api/v1"

$q1Text = 'Tell me about yourself.'
$q2Text = 'Tell me about a challenge you faced and how you overcame it.'

$answer1Text = 'I am a third-year software engineering student at ESPRIT with a strong focus on backend development using Spring Boot and microservices. Over the past two years I have built three full-stack projects including a university project management system used by over 200 students and a real-time notification service using Kafka. Before that I completed an internship where I contributed to a production REST API handling thousands of daily requests. I am applying here specifically because SmartHire is building AI-powered hiring tools - that intersection of machine learning and backend engineering is exactly the direction I want to grow in, and I believe this platform is the right environment to develop that expertise.'

$answer2Text = 'During my second year, I was leading the backend for our capstone project when our CI/CD pipeline completely broke two days before the final presentation. The issue was that a Docker volume misconfiguration was causing the database container to reset on every restart, wiping all seeded data. The rest of the team was focused on the frontend so I took full ownership of diagnosing it. I spent four hours reading Docker Compose documentation, added a named volume to persist the database, and wrote a health check so the API container would wait for the DB to be ready before starting. The presentation ran flawlessly and our professor specifically noted the robustness of the deployment. After that I added a pre-deploy checklist to our team''s workflow so we would never hit the same issue again.'

$lines = New-Object System.Collections.Generic.List[string]

function Add-Line {
    param([string]$Text)
    $lines.Add($Text) | Out-Null
}

function Wait-Evaluation {
    param(
        [Parameter(Mandatory = $true)][long]$AnswerId,
        [int]$PollSeconds = 3,
        [int]$TimeoutSeconds = 40
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $lastResult = $null

    while ($stopwatch.Elapsed.TotalSeconds -le $TimeoutSeconds) {
        try {
            $eval = Invoke-RestMethod -Method GET -Uri "$api/evaluations/answer/$AnswerId"
            $lastResult = $eval
            if ($null -ne $eval.overallScore) {
                return $eval
            }
        } catch {
            $lastResult = $_.Exception.Message
        }

        Start-Sleep -Seconds $PollSeconds
    }

    return $lastResult
}

# STEP 0 - Find question IDs
Add-Line 'STEP 0 - FIND QUESTION IDS'
$questions = Invoke-RestMethod -Method GET -Uri "$api/questions"
$q1 = $questions | Where-Object { $_.questionText -eq $q1Text } | Select-Object -First 1
$q2 = $questions | Where-Object { $_.questionText -eq $q2Text } | Select-Object -First 1

if ($null -eq $q1 -or $null -eq $q2) {
    throw 'Could not find both required questions by exact questionText.'
}

$Q1_ID = [long]$q1.id
$Q2_ID = [long]$q2.id

Add-Line "Q1_ID = $Q1_ID"
Add-Line "Q2_ID = $Q2_ID"
Add-Line ''

# STEP 1 - Start session
Add-Line 'STEP 1 - START SESSION'
$startBody = @{
    userId = 99
    careerPathId = 1
    roleType = 'ALL'
    mode = 'PRACTICE'
    type = 'BEHAVIORAL'
    questionCount = 2
} | ConvertTo-Json

$session = Invoke-RestMethod -Method POST -Uri "$api/sessions/start" -ContentType 'application/json' -Body $startBody
$SESSION_ID = [long]$session.id

if ([string]$session.status -ne 'IN_PROGRESS') {
    throw "Session status is not IN_PROGRESS. Actual: $($session.status)"
}

Add-Line "SESSION_ID = $SESSION_ID"
Add-Line "Session status = $($session.status)"
Add-Line ''

# STEP 2 - Answer Question 1
Add-Line 'STEP 2 - ANSWER QUESTION 1'
$current1 = Invoke-RestMethod -Method GET -Uri "$api/sessions/$SESSION_ID/questions/current"
$current1Text = [string]$current1.questionText

Add-Line "Current question (step 2) = $current1Text"
if ($current1Text -ne $q1Text) {
    throw "Expected current question to be '$q1Text' but got '$current1Text'"
}

$submit1Body = @{
    sessionId = $SESSION_ID
    questionId = $Q1_ID
    answerText = $answer1Text
    videoUrl = $null
    audioUrl = $null
} | ConvertTo-Json

$a1 = Invoke-RestMethod -Method POST -Uri "$api/answers/submit" -ContentType 'application/json' -Body $submit1Body
$A1_ID = [long]$a1.id

Add-Line "A1_ID = $A1_ID"
Add-Line ''

# STEP 3 - Wait evaluation A1
Add-Line 'STEP 3 - WAIT FOR EVALUATION A1'
$eval1 = Wait-Evaluation -AnswerId $A1_ID -PollSeconds 3 -TimeoutSeconds 40
if ($eval1 -is [string]) {
    throw "Timed out waiting for evaluation A1. Last state: $eval1"
}
if ($null -eq $eval1.overallScore) {
    throw 'Evaluation A1 did not complete with overallScore.'
}

Add-Line "contentScore = $($eval1.contentScore)"
Add-Line "clarityScore = $($eval1.clarityScore)"
Add-Line "technicalScore = $($eval1.technicalScore)"
Add-Line "overallScore = $($eval1.overallScore)"
Add-Line "aiFeedback = $($eval1.aiFeedback)"
Add-Line "followUpGenerated = $($eval1.followUpGenerated)"
Add-Line ''

# STEP 4 - Answer Question 2
Add-Line 'STEP 4 - ANSWER QUESTION 2'
$current2 = Invoke-RestMethod -Method GET -Uri "$api/sessions/$SESSION_ID/questions/current"
$current2Text = [string]$current2.questionText

Add-Line "Current question (step 4) = $current2Text"
if ($current2Text -ne $q2Text) {
    throw "Expected current question to be '$q2Text' but got '$current2Text'"
}

$submit2Body = @{
    sessionId = $SESSION_ID
    questionId = $Q2_ID
    answerText = $answer2Text
    videoUrl = $null
    audioUrl = $null
} | ConvertTo-Json

$a2 = Invoke-RestMethod -Method POST -Uri "$api/answers/submit" -ContentType 'application/json' -Body $submit2Body
$A2_ID = [long]$a2.id

Add-Line "A2_ID = $A2_ID"
Add-Line ''

# STEP 5 - Wait evaluation A2
Add-Line 'STEP 5 - WAIT FOR EVALUATION A2'
$eval2 = Wait-Evaluation -AnswerId $A2_ID -PollSeconds 3 -TimeoutSeconds 40
if ($eval2 -is [string]) {
    throw "Timed out waiting for evaluation A2. Last state: $eval2"
}
if ($null -eq $eval2.overallScore) {
    throw 'Evaluation A2 did not complete with overallScore.'
}

Add-Line "contentScore = $($eval2.contentScore)"
Add-Line "clarityScore = $($eval2.clarityScore)"
Add-Line "technicalScore = $($eval2.technicalScore)"
Add-Line "overallScore = $($eval2.overallScore)"
Add-Line "aiFeedback = $($eval2.aiFeedback)"
Add-Line "followUpGenerated = $($eval2.followUpGenerated)"
Add-Line ''

# STEP 6 - Complete session
Add-Line 'STEP 6 - COMPLETE SESSION'
$completed = Invoke-RestMethod -Method PUT -Uri "$api/sessions/$SESSION_ID/complete"
if ([string]$completed.status -ne 'COMPLETED') {
    throw "Session status is not COMPLETED. Actual: $($completed.status)"
}
Add-Line "Session completed. status = $($completed.status)"
Add-Line ''

# STEP 7 - Generate report
Add-Line 'STEP 7 - GENERATE REPORT'
$report = Invoke-RestMethod -Method POST -Uri "$api/reports/generate/$SESSION_ID"

Add-Line "finalScore = $($report.finalScore)"
Add-Line "contentAvg = $($report.contentAvg)"
Add-Line "strengths = $($report.strengths)"
Add-Line "weaknesses = $($report.weaknesses)"
Add-Line "recommendations = $($report.recommendations)"
Add-Line "recruiterVerdict = $($report.recruiterVerdict)"
Add-Line ''

# STEP 8 - Final readable summary
Add-Line 'STEP 8 - FINAL READABLE SUMMARY'
Add-Line ''
Add-Line '══════════════════════════════════════════'
Add-Line ' SMARTHIRE AI - INTERVIEW SIMULATION RESULT'
Add-Line '══════════════════════════════════════════'
Add-Line ''
Add-Line "SESSION ID   : $SESSION_ID"
Add-Line 'ROLE         : ALL (Behavioral)'
Add-Line 'MODE         : PRACTICE'
Add-Line 'QUESTIONS    : 2'
Add-Line ''
Add-Line '──────────────────────────────────────────'
Add-Line 'Q1: Tell me about yourself.'
Add-Line '──────────────────────────────────────────'
Add-Line "Content Score    : $($eval1.contentScore)/10"
Add-Line "Clarity Score    : $($eval1.clarityScore)/10"
Add-Line "Overall Score    : $($eval1.overallScore)/10"
Add-Line "AI Feedback      : $($eval1.aiFeedback)"
Add-Line "Follow-up asked  : $($eval1.followUpGenerated)"
Add-Line ''
Add-Line '──────────────────────────────────────────'
Add-Line 'Q2: Tell me about a challenge you faced.'
Add-Line '──────────────────────────────────────────'
Add-Line "Content Score    : $($eval2.contentScore)/10"
Add-Line "Clarity Score    : $($eval2.clarityScore)/10"
Add-Line "Overall Score    : $($eval2.overallScore)/10"
Add-Line "AI Feedback      : $($eval2.aiFeedback)"
Add-Line "Follow-up asked  : $($eval2.followUpGenerated)"
Add-Line ''
Add-Line '──────────────────────────────────────────'
Add-Line 'FINAL REPORT'
Add-Line '──────────────────────────────────────────'
Add-Line "Final Score      : $($report.finalScore)/10"
Add-Line "Strengths        : $($report.strengths)"
Add-Line "Weaknesses       : $($report.weaknesses)"
Add-Line "Recommendations  : $($report.recommendations)"
Add-Line "Recruiter Verdict: $($report.recruiterVerdict)"
Add-Line ''
Add-Line '══════════════════════════════════════════'

$outPath = Join-Path (Get-Location) 'simulation-2q-result.txt'
$lines -join [Environment]::NewLine | Set-Content -Path $outPath -Encoding UTF8

Write-Output "Simulation completed. Output file: $outPath"
Write-Output '----- SUMMARY PREVIEW -----'
Write-Output ($lines[-35..-1] -join [Environment]::NewLine)
