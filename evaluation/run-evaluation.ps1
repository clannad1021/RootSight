param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$ScenarioFile = "$PSScriptRoot/scenarios.example.json",
    [string]$OutputFile = "$PSScriptRoot/evaluation-report.json"
)

$requestBody = Get-Content -LiteralPath $ScenarioFile -Raw -Encoding UTF8
$response = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/evaluations" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($requestBody))

$response | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $OutputFile -Encoding UTF8
Write-Host "Evaluation report written to $OutputFile"
