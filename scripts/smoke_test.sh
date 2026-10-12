#!/usr/bin/env bash
# ==============================================================================
# Personal Finance Manager API - Integration / Smoke Test Suite
# (Project-authored automated end-to-end verification script)
# ==============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
COOKIE_JAR="$(mktemp /tmp/pfm_cookies.XXXXXX 2>/dev/null || echo "pfm_cookies.txt")"
COOKIE_JAR_B="$(mktemp /tmp/pfm_cookies_b.XXXXXX 2>/dev/null || echo "pfm_cookies_b.txt")"

trap 'rm -f "$COOKIE_JAR" "$COOKIE_JAR_B"' EXIT

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

run_test() {
    local test_name="$1"
    local expected_status="$2"
    local response_file="$3"
    local actual_status="$4"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if [ "$actual_status" -eq "$expected_status" ]; then
        echo " [PASS] Test $TOTAL_TESTS: $test_name (Status: $actual_status)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo " [FAIL] Test $TOTAL_TESTS: $test_name (Expected: $expected_status, Got: $actual_status)"
        echo "        Response: $(cat "$response_file" 2>/dev/null || echo 'No body')"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

echo "=================================================="
echo " Personal Finance Manager API Integration Test Suite"
echo " Base URL: $BASE_URL"
echo "=================================================="

TMP_RES="$(mktemp /tmp/res.XXXXXX 2>/dev/null || echo "res.tmp")"
trap 'rm -f "$COOKIE_JAR" "$COOKIE_JAR_B" "$TMP_RES"' EXIT

TIMESTAMP="$(date +%s)"
USER_A_EMAIL="testuser_${TIMESTAMP}@example.com"
USER_B_EMAIL="testuser_b_${TIMESTAMP}@example.com"
PASSWORD="SecurePassword123"

# 1. Health check
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" "$BASE_URL/health" || echo "000")
run_test "Public Health Check (GET /api/health)" 200 "$TMP_RES" "$STATUS"

# 2. Unauthenticated access rejected
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" "$BASE_URL/transactions" || echo "000")
run_test "Unauthenticated access rejected (GET /api/transactions -> 401)" 401 "$TMP_RES" "$STATUS"

# 3. User A Registration
REG_JSON="{\"username\":\"$USER_A_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Test User A\",\"phoneNumber\":\"+1234567890\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" -d "$REG_JSON")
run_test "User A Registration (POST /api/auth/register -> 201)" 201 "$TMP_RES" "$STATUS"

# 4. User Registration Duplicate Rejection
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" -d "$REG_JSON")
run_test "Duplicate Username Rejected (POST /api/auth/register -> 409)" 409 "$TMP_RES" "$STATUS"

# 5. Invalid Registration Format
INVALID_REG="{\"username\":\"notanemail\",\"password\":\"123\",\"fullName\":\"\",\"phoneNumber\":\"abc\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" -d "$INVALID_REG")
run_test "Invalid Registration Format Rejected (POST /api/auth/register -> 400)" 400 "$TMP_RES" "$STATUS"

# 6. Login Invalid Credentials
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" -d "{\"username\":\"$USER_A_EMAIL\",\"password\":\"WrongPassword\"}")
run_test "Invalid Password Login Rejected (POST /api/auth/login -> 401)" 401 "$TMP_RES" "$STATUS"

# 7. User A Login Success
LOGIN_JSON="{\"username\":\"$USER_A_EMAIL\",\"password\":\"$PASSWORD\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -c "$COOKIE_JAR" -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" -d "$LOGIN_JSON")
run_test "User A Login Success & Cookie Issued (POST /api/auth/login -> 200)" 200 "$TMP_RES" "$STATUS"

# 8. List Categories
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/categories")
run_test "List Categories (GET /api/categories -> 200)" 200 "$TMP_RES" "$STATUS"

# 9. Create Custom Category
CAT_JSON="{\"name\":\"FreelanceIncome\",\"type\":\"INCOME\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/categories" \
    -H "Content-Type: application/json" -d "$CAT_JSON")
run_test "Create Custom Category (POST /api/categories -> 201)" 201 "$TMP_RES" "$STATUS"

# 10. Reject Duplicate Category
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/categories" \
    -H "Content-Type: application/json" -d "$CAT_JSON")
run_test "Duplicate Category Name Rejected (POST /api/categories -> 409)" 409 "$TMP_RES" "$STATUS"

# 11. Reject Duplicate Category Matching Default (Salary)
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/categories" \
    -H "Content-Type: application/json" -d "{\"name\":\"Salary\",\"type\":\"INCOME\"}")
run_test "Duplicate Default Category Name Rejected (POST /api/categories -> 409)" 409 "$TMP_RES" "$STATUS"

# 12. Create Income Transaction
CURRENT_DATE="$(date +%Y-%m-%d)"
TX_INC_JSON="{\"amount\":50000.00,\"date\":\"$CURRENT_DATE\",\"category\":\"Salary\",\"description\":\"Salary credit\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/transactions" \
    -H "Content-Type: application/json" -d "$TX_INC_JSON")
run_test "Create Income Transaction (POST /api/transactions -> 201)" 201 "$TMP_RES" "$STATUS"
TX_INC_ID=$(grep -o '"id":[0-9]*' "$TMP_RES" | head -n1 | cut -d: -f2 || echo "")

# 13. Create Expense Transaction
TX_EXP_JSON="{\"amount\":1200.00,\"date\":\"$CURRENT_DATE\",\"category\":\"Food\",\"description\":\"Groceries\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/transactions" \
    -H "Content-Type: application/json" -d "$TX_EXP_JSON")
run_test "Create Expense Transaction (POST /api/transactions -> 201)" 201 "$TMP_RES" "$STATUS"
TX_EXP_ID=$(grep -o '"id":[0-9]*' "$TMP_RES" | head -n1 | cut -d: -f2 || echo "")

# 14. Reject Future Date Transaction
FUTURE_DATE="2099-12-31"
FUTURE_TX="{\"amount\":100.00,\"date\":\"$FUTURE_DATE\",\"category\":\"Food\",\"description\":\"Future food\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/transactions" \
    -H "Content-Type: application/json" -d "$FUTURE_TX")
run_test "Future Date Transaction Rejected (POST /api/transactions -> 400)" 400 "$TMP_RES" "$STATUS"

# 15. Reject Non-positive Amount
INVALID_AMT_TX="{\"amount\":-50.00,\"date\":\"$CURRENT_DATE\",\"category\":\"Food\",\"description\":\"Negative\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/transactions" \
    -H "Content-Type: application/json" -d "$INVALID_AMT_TX")
run_test "Negative Amount Transaction Rejected (POST /api/transactions -> 400)" 400 "$TMP_RES" "$STATUS"

# 16. Reject Invalid Category
INVALID_CAT_TX="{\"amount\":100.00,\"date\":\"$CURRENT_DATE\",\"category\":\"NoSuchCategory\",\"description\":\"Test\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/transactions" \
    -H "Content-Type: application/json" -d "$INVALID_CAT_TX")
run_test "Invalid Category Transaction Rejected (POST /api/transactions -> 400)" 400 "$TMP_RES" "$STATUS"

# 17. Get All Transactions
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/transactions")
run_test "Get All Transactions (GET /api/transactions -> 200)" 200 "$TMP_RES" "$STATUS"

# 18. Filter Transactions by Category
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/transactions?category=Salary")
run_test "Filter Transactions by Category (GET /api/transactions?category=Salary -> 200)" 200 "$TMP_RES" "$STATUS"

# 19. Filter Transactions by Type
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/transactions?type=EXPENSE")
run_test "Filter Transactions by Type (GET /api/transactions?type=EXPENSE -> 200)" 200 "$TMP_RES" "$STATUS"

# 20. Update Transaction
UPDATE_TX="{\"amount\":60000.00,\"description\":\"Updated salary bonus\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X PUT "$BASE_URL/transactions/$TX_INC_ID" \
    -H "Content-Type: application/json" -d "$UPDATE_TX")
run_test "Update Transaction (PUT /api/transactions/{id} -> 200)" 200 "$TMP_RES" "$STATUS"

# 21. Reject Updating Transaction Date (Immutable Date)
UPDATE_DATE_TX="{\"date\":\"2020-01-01\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X PUT "$BASE_URL/transactions/$TX_INC_ID" \
    -H "Content-Type: application/json" -d "$UPDATE_DATE_TX")
run_test "Reject Transaction Date Modification (PUT /api/transactions/{id} -> 400)" 400 "$TMP_RES" "$STATUS"

# 22. Create Savings Goal
GOAL_JSON="{\"goalName\":\"Emergency Fund\",\"targetAmount\":100000.00,\"targetDate\":\"2099-01-01\",\"startDate\":\"$CURRENT_DATE\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/goals" \
    -H "Content-Type: application/json" -d "$GOAL_JSON")
run_test "Create Savings Goal (POST /api/goals -> 201)" 201 "$TMP_RES" "$STATUS"
GOAL_ID=$(grep -o '"id":[0-9]*' "$TMP_RES" | head -n1 | cut -d: -f2 || echo "")

# 23. Reject Past Target Date for Goal
INVALID_GOAL="{\"goalName\":\"Past Goal\",\"targetAmount\":1000.00,\"targetDate\":\"2020-01-01\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/goals" \
    -H "Content-Type: application/json" -d "$INVALID_GOAL")
run_test "Past Target Date Goal Rejected (POST /api/goals -> 400)" 400 "$TMP_RES" "$STATUS"

# 24. Get All Savings Goals
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/goals")
run_test "Get All Savings Goals (GET /api/goals -> 200)" 200 "$TMP_RES" "$STATUS"

# 25. Get Single Goal by ID
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/goals/$GOAL_ID")
run_test "Get Single Savings Goal (GET /api/goals/{id} -> 200)" 200 "$TMP_RES" "$STATUS"

# 26. Update Savings Goal
UPDATE_GOAL="{\"targetAmount\":120000.00,\"targetDate\":\"2099-06-01\"}"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X PUT "$BASE_URL/goals/$GOAL_ID" \
    -H "Content-Type: application/json" -d "$UPDATE_GOAL")
run_test "Update Savings Goal (PUT /api/goals/{id} -> 200)" 200 "$TMP_RES" "$STATUS"

# 27. Monthly Report
YEAR="$(date +%Y)"
MONTH="$(date +%-m)"
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/reports/monthly/$YEAR/$MONTH")
run_test "Get Monthly Financial Report (GET /api/reports/monthly/{year}/{month} -> 200)" 200 "$TMP_RES" "$STATUS"

# 28. Yearly Report
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/reports/yearly/$YEAR")
run_test "Get Yearly Financial Report (GET /api/reports/yearly/{year} -> 200)" 200 "$TMP_RES" "$STATUS"

# 29. Reject Invalid Report Month
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/reports/monthly/$YEAR/13")
run_test "Invalid Month in Report Rejected (GET /api/reports/monthly/{year}/13 -> 400)" 400 "$TMP_RES" "$STATUS"

# 30. Prohibit Deleting Category with Existing Transactions
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X DELETE "$BASE_URL/categories/Salary")
run_test "Prohibit Deleting Default Category (DELETE /api/categories/Salary -> 403)" 403 "$TMP_RES" "$STATUS"

# 31. Prohibit Deleting Default Category Food
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X DELETE "$BASE_URL/categories/Food")
run_test "Prohibit Deleting Default Category Food (DELETE /api/categories/Food -> 403)" 403 "$TMP_RES" "$STATUS"

# 32. Delete Custom Category FreelanceIncome (unused by transactions)
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X DELETE "$BASE_URL/categories/FreelanceIncome")
run_test "Delete Unused Custom Category (DELETE /api/categories/FreelanceIncome -> 200)" 200 "$TMP_RES" "$STATUS"

# 33. User B Registration & Login (Cross-User Isolation Testing)
REG_B="{\"username\":\"$USER_B_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Test User B\",\"phoneNumber\":\"+9876543210\"}"
curl -s -X POST "$BASE_URL/auth/register" -H "Content-Type: application/json" -d "$REG_B" > /dev/null
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -c "$COOKIE_JAR_B" -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" -d "{\"username\":\"$USER_B_EMAIL\",\"password\":\"$PASSWORD\"}")
run_test "User B Login (POST /api/auth/login -> 200)" 200 "$TMP_RES" "$STATUS"

# 34. User B cannot see User A transactions
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR_B" "$BASE_URL/transactions")
if grep -q '"id":' "$TMP_RES"; then
    run_test "User Isolation: User B sees 0 transactions of User A" 200 "$TMP_RES" 500
else
    run_test "User Isolation: User B sees 0 transactions of User A" 200 "$TMP_RES" "$STATUS"
fi

# 35. User B cannot access User A goal
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR_B" "$BASE_URL/goals/$GOAL_ID")
run_test "User Isolation: User B cannot get User A goal (GET /api/goals/{id} -> 404)" 404 "$TMP_RES" "$STATUS"

# 36. User B cannot update User A goal
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR_B" -X PUT "$BASE_URL/goals/$GOAL_ID" \
    -H "Content-Type: application/json" -d "$UPDATE_GOAL")
run_test "User Isolation: User B cannot update User A goal (PUT /api/goals/{id} -> 404)" 404 "$TMP_RES" "$STATUS"

# 37. User B cannot delete User A goal
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR_B" -X DELETE "$BASE_URL/goals/$GOAL_ID")
run_test "User Isolation: User B cannot delete User A goal (DELETE /api/goals/{id} -> 404)" 404 "$TMP_RES" "$STATUS"

# 38. User B cannot delete User A transaction
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR_B" -X DELETE "$BASE_URL/transactions/$TX_INC_ID")
run_test "User Isolation: User B cannot delete User A transaction (DELETE /api/transactions/{id} -> 404)" 404 "$TMP_RES" "$STATUS"

# 39. Delete Goal for User A
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X DELETE "$BASE_URL/goals/$GOAL_ID")
run_test "Delete Goal for Owner (DELETE /api/goals/{id} -> 200)" 200 "$TMP_RES" "$STATUS"

# 40. Delete Transactions for User A
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X DELETE "$BASE_URL/transactions/$TX_INC_ID")
run_test "Delete Transaction 1 for Owner (DELETE /api/transactions/{id} -> 200)" 200 "$TMP_RES" "$STATUS"

STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X DELETE "$BASE_URL/transactions/$TX_EXP_ID")
run_test "Delete Transaction 2 for Owner (DELETE /api/transactions/{id} -> 200)" 200 "$TMP_RES" "$STATUS"

# 41. User A Logout
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" -X POST "$BASE_URL/auth/logout")
run_test "User A Logout (POST /api/auth/logout -> 200)" 200 "$TMP_RES" "$STATUS"

# 42. Subsequent Request with Logged-Out Cookie Fails
STATUS=$(curl -s -o "$TMP_RES" -w "%{http_code}" -b "$COOKIE_JAR" "$BASE_URL/transactions")
run_test "Access with Invalidated Session Rejected (GET /api/transactions -> 401)" 401 "$TMP_RES" "$STATUS"

# Summary
echo ""
echo "=================================================="
echo "Base URL: $BASE_URL"
echo "Total Tests Executed: $TOTAL_TESTS"
echo "Tests Passed: $PASSED_TESTS"
echo "Tests Failed: $FAILED_TESTS"
SUCCESS_RATE=$(( PASSED_TESTS * 100 / TOTAL_TESTS ))
echo "Success Rate: ${SUCCESS_RATE}%"
if [ "$FAILED_TESTS" -eq 0 ]; then
    echo ">> ALL TESTS PASSED! <<"
    echo "The Personal Finance Manager API is working correctly."
    echo "=================================================="
    exit 0
else
    echo ">> SOME TESTS FAILED! <<"
    echo "=================================================="
    exit 1
fi
