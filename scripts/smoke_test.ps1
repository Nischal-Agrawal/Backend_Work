# ==============================================================================
# Personal Finance Manager API - Integration / Smoke Test Suite (PowerShell)
# ==============================================================================
param(
    [string]$BaseUrl = "http://localhost:8080/api"
)

$ErrorActionPreference = "Continue"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " Personal Finance Manager API Integration Test Suite" -ForegroundColor Cyan
Write-Host " Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$totalTests = 0
$passedTests = 0
$failedTests = 0

function Run-ApiTest {
    param(
        [string]$TestName,
        [int]$ExpectedStatus,
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [ref]$WebSession
    )

    $script:totalTests++
    $actualStatus = 0
    $respBody = ""

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }
        if ($WebSession.Value) {
            $params["WebSession"] = $WebSession.Value
        } else {
            $newSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
            $WebSession.Value = $newSession
            $params["WebSession"] = $newSession
        }
        if ($Body) {
            $params["Body"] = $Body
            $params["ContentType"] = "application/json"
        }

        $response = Invoke-WebRequest @params
        $actualStatus = [int]$response.StatusCode
        $respBody = $response.Content
    }
    catch {
        if ($_.Exception.Response) {
            $actualStatus = [int]$_.Exception.Response.StatusCode
        } else {
            $actualStatus = 500
        }
    }

    if ($actualStatus -eq $ExpectedStatus) {
        Write-Host " [PASS] Test ${script:totalTests}: $TestName (Status: $actualStatus)" -ForegroundColor Green
        $script:passedTests++
    } else {
        Write-Host " [FAIL] Test ${script:totalTests}: $TestName (Expected: $ExpectedStatus, Got: $actualStatus)" -ForegroundColor Red
        $script:failedTests++
    }
}

$sessionA = [ref]$null
$sessionB = [ref]$null
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$userA = "user_a_${ts}@example.com"
$userB = "user_b_${ts}@example.com"
$pass = "SecurePass123"

# 1. Health check
Run-ApiTest "Health Check" 200 "GET" "$BaseUrl/health" $null $sessionA

# 2. Unauthenticated access rejected
Run-ApiTest "Unauthenticated access rejected" 401 "GET" "$BaseUrl/transactions" $null $sessionA

# 3. Register User A
$regA = @{ username = $userA; password = $pass; fullName = "User A"; phoneNumber = "+1234567890" } | ConvertTo-Json
Run-ApiTest "Register User A" 201 "POST" "$BaseUrl/auth/register" $regA $sessionA

# 4. Duplicate Registration rejected
Run-ApiTest "Duplicate Registration rejected" 409 "POST" "$BaseUrl/auth/register" $regA $sessionA

# 5. Login User A
$loginA = @{ username = $userA; password = $pass } | ConvertTo-Json
Run-ApiTest "Login User A" 200 "POST" "$BaseUrl/auth/login" $loginA $sessionA

# 6. List Categories
Run-ApiTest "List Categories" 200 "GET" "$BaseUrl/categories" $null $sessionA

# 7. Create Custom Category
$cat = @{ name = "FreelanceIncome"; type = "INCOME" } | ConvertTo-Json
Run-ApiTest "Create Custom Category" 201 "POST" "$BaseUrl/categories" $cat $sessionA

# 8. Duplicate Category rejected
Run-ApiTest "Duplicate Category rejected" 409 "POST" "$BaseUrl/categories" $cat $sessionA

# 9. Create Income Transaction
$today = (Get-Date).ToString("yyyy-MM-dd")
$txInc = @{ amount = 50000.00; date = $today; category = "Salary"; description = "Salary" } | ConvertTo-Json
Run-ApiTest "Create Income Transaction" 201 "POST" "$BaseUrl/transactions" $txInc $sessionA

# 10. Create Expense Transaction
$txExp = @{ amount = 1200.00; date = $today; category = "Food"; description = "Food" } | ConvertTo-Json
Run-ApiTest "Create Expense Transaction" 201 "POST" "$BaseUrl/transactions" $txExp $sessionA

# 11. Reject Future Transaction
$txFuture = @{ amount = 100.00; date = "2099-01-01"; category = "Food"; description = "Future" } | ConvertTo-Json
Run-ApiTest "Reject Future Transaction" 400 "POST" "$BaseUrl/transactions" $txFuture $sessionA

# 12. List Transactions
Run-ApiTest "List Transactions" 200 "GET" "$BaseUrl/transactions" $null $sessionA

# 13. Create Goal
$goal = @{ goalName = "Emergency Fund"; targetAmount = 100000.00; targetDate = "2099-01-01"; startDate = $today } | ConvertTo-Json
Run-ApiTest "Create Savings Goal" 201 "POST" "$BaseUrl/goals" $goal $sessionA

# 14. List Goals
Run-ApiTest "List Savings Goals" 200 "GET" "$BaseUrl/goals" $null $sessionA

# 15. Monthly Report
$year = (Get-Date).Year
$month = (Get-Date).Month
Run-ApiTest "Get Monthly Report" 200 "GET" "$BaseUrl/reports/monthly/$year/$month" $null $sessionA

# 16. Yearly Report
Run-ApiTest "Get Yearly Report" 200 "GET" "$BaseUrl/reports/yearly/$year" $null $sessionA

# 17. Logout User A
Run-ApiTest "Logout User A" 200 "POST" "$BaseUrl/auth/logout" $null $sessionA

# 18. Access after logout rejected
Run-ApiTest "Access with invalidated session rejected" 401 "GET" "$BaseUrl/transactions" $null $sessionA

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"
Write-Host "Total Tests Executed: $totalTests"
Write-Host "Tests Passed: $passedTests"
Write-Host "Tests Failed: $failedTests"
$rate = [Math]::Round(($passedTests / $totalTests) * 100)
Write-Host "Success Rate: $rate%"
if ($failedTests -eq 0) {
    Write-Host "ALL TESTS PASSED!" -ForegroundColor Green
    Write-Host "The Personal Finance Manager API is working correctly." -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host "SOME TESTS FAILED!" -ForegroundColor Red
    Write-Host "==================================================" -ForegroundColor Cyan
    exit 1
}
