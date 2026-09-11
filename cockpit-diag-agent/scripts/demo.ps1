$ErrorActionPreference = 'Stop'
$baseUrl = 'http://localhost:8080/api/v1'

Invoke-RestMethod -Method Post -Uri "$baseUrl/signals" -ContentType 'application/json' -Body (@{
    speedKph = 80
    coolantTemperatureC = 120
    gear = 'D'
} | ConvertTo-Json)

$report = Invoke-RestMethod -Method Post -Uri "$baseUrl/diagnoses" -ContentType 'application/json' -Body (@{
    question = '为什么出现超温告警？请生成回归测试方案'
    includeTestPlan = $true
} | ConvertTo-Json)

$report | ConvertTo-Json -Depth 8
