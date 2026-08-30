@echo off
REM ============================================================
REM  ToyShop — k6 Performance Test Runner (Windows)
REM
REM  Prerequisites:
REM    - k6 installed: https://k6.io/docs/get-started/installation/
REM    - All backend services running (see RUN_LOCALLY.md)
REM
REM  Usage:
REM    run-perf-tests.bat                  (runs all tests)
REM    run-perf-tests.bat smoke            (smoke only)
REM    run-perf-tests.bat user             (user service only)
REM    run-perf-tests.bat products         (product service only)
REM    run-perf-tests.bat orders           (order service only)
REM    run-perf-tests.bat payments         (payment service only)
REM    run-perf-tests.bat e2e              (end-to-end checkout)
REM    run-perf-tests.bat e2e stress       (e2e with stress scenario)
REM    run-perf-tests.bat e2e soak         (e2e soak test - 10 min)
REM ============================================================

setlocal enabledelayedexpansion

set BASE_URL=%BASE_URL%
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8080

set REPORT_DIR=reports
if not exist "%REPORT_DIR%" mkdir "%REPORT_DIR%"

set TEST=%1
set SCENARIO=%2
if "%SCENARIO%"=="" set SCENARIO=load

echo.
echo ============================================================
echo  ToyShop k6 Performance Tests
echo  Target: %BASE_URL%
echo  Reports: %CD%\%REPORT_DIR%\
echo ============================================================
echo.

REM Check k6 is installed
k6 version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] k6 is not installed or not in PATH.
    echo         Install from: https://k6.io/docs/get-started/installation/
    exit /b 1
)

set PASS=0
set FAIL=0

if "%TEST%"=="" goto :run_all
if /i "%TEST%"=="smoke"    goto :run_smoke
if /i "%TEST%"=="user"     goto :run_user
if /i "%TEST%"=="products" goto :run_products
if /i "%TEST%"=="orders"   goto :run_orders
if /i "%TEST%"=="payments" goto :run_payments
if /i "%TEST%"=="e2e"      goto :run_e2e
echo [ERROR] Unknown test: %TEST%
exit /b 1

:run_all
call :run_test "User Service Smoke"    "user-service\src\test\k6\user-service-perf-test.js"
call :run_test "User Service Load"     "user-service\src\test\k6\performance-test.js"
call :run_test "Product Service Load"  "product-service\src\test\k6\product-service-perf-test.js"
call :run_test "Order Service Load"    "order-service\src\test\k6\order-service-perf-test.js"
call :run_test "Payment Service Load"  "payment-service\src\test\k6\payment-service-perf-test.js"
call :run_test "E2E Checkout (load)"   "user-service\src\test\k6\scenarios\checkout-e2e.js" "load"
goto :summary

:run_smoke
call :run_test "User Service Smoke"  "user-service\src\test\k6\user-service-perf-test.js"
call :run_test "E2E Checkout Smoke"  "user-service\src\test\k6\scenarios\checkout-e2e.js" "smoke"
goto :summary

:run_user
call :run_test "User Service Smoke"  "user-service\src\test\k6\user-service-perf-test.js"
call :run_test "User Service Load"   "user-service\src\test\k6\performance-test.js"
goto :summary

:run_products
call :run_test "Product Service Load" "product-service\src\test\k6\product-service-perf-test.js"
goto :summary

:run_orders
call :run_test "Order Service Load" "order-service\src\test\k6\order-service-perf-test.js"
goto :summary

:run_payments
call :run_test "Payment Service Load" "payment-service\src\test\k6\payment-service-perf-test.js"
goto :summary

:run_e2e
call :run_test "E2E Checkout (%SCENARIO%)" "user-service\src\test\k6\scenarios\checkout-e2e.js" "%SCENARIO%"
goto :summary

REM ── Helper: run a single test ──────────────────────────────────────────
:run_test
set TEST_NAME=%~1
set TEST_FILE=%~2
set TEST_SCENARIO=%~3

echo.
echo [RUN] %TEST_NAME%
echo       File: %TEST_FILE%

if "%TEST_SCENARIO%"=="" (
    k6 run --env BASE_URL=%BASE_URL% --env REPORT_DIR=%REPORT_DIR% "%TEST_FILE%"
) else (
    k6 run --env BASE_URL=%BASE_URL% --env REPORT_DIR=%REPORT_DIR% --env SCENARIO=%TEST_SCENARIO% "%TEST_FILE%"
)

if errorlevel 1 (
    echo [FAIL] %TEST_NAME%
    set /a FAIL+=1
) else (
    echo [PASS] %TEST_NAME%
    set /a PASS+=1
)
goto :eof

:summary
echo.
echo ============================================================
echo  Results: %PASS% passed, %FAIL% failed
echo  Reports: %CD%\%REPORT_DIR%\
echo ============================================================
echo.
echo  Open reports in your browser:
for %%f in (%REPORT_DIR%\*-report.html) do echo    %%f
echo.

if %FAIL% gtr 0 exit /b 1
exit /b 0
