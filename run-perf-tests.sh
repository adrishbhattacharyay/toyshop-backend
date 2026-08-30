#!/usr/bin/env bash
# ============================================================
#  ToyShop — k6 Performance Test Runner (Linux / macOS)
#
#  Prerequisites:
#    - k6 installed: https://k6.io/docs/get-started/installation/
#    - All backend services running (see RUN_LOCALLY.md)
#
#  Usage:
#    ./run-perf-tests.sh                  (runs all tests)
#    ./run-perf-tests.sh smoke            (smoke only)
#    ./run-perf-tests.sh user             (user service only)
#    ./run-perf-tests.sh products         (product service only)
#    ./run-perf-tests.sh orders           (order service only)
#    ./run-perf-tests.sh payments         (payment service only)
#    ./run-perf-tests.sh e2e              (end-to-end checkout, load)
#    ./run-perf-tests.sh e2e stress       (e2e stress scenario)
#    ./run-perf-tests.sh e2e soak         (e2e soak test - 10 min)
# ============================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REPORT_DIR="${REPORT_DIR:-reports}"
TEST="${1:-all}"
SCENARIO="${2:-load}"

PASS=0
FAIL=0

# Colours
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo ""
echo "============================================================"
echo " ToyShop k6 Performance Tests"
echo " Target:  $BASE_URL"
echo " Reports: $(pwd)/$REPORT_DIR/"
echo "============================================================"
echo ""

# Check k6 is installed
if ! command -v k6 &>/dev/null; then
  echo -e "${RED}[ERROR]${NC} k6 is not installed or not in PATH."
  echo "        Install from: https://k6.io/docs/get-started/installation/"
  exit 1
fi

mkdir -p "$REPORT_DIR"

# ── Helper ──────────────────────────────────────────────────────────────────
run_test() {
  local name="$1"
  local file="$2"
  local scenario="${3:-}"

  echo ""
  echo -e "${YELLOW}[RUN]${NC} $name"
  echo "      File: $file"

  local extra_env=""
  [[ -n "$scenario" ]] && extra_env="--env SCENARIO=$scenario"

  if k6 run \
      --env BASE_URL="$BASE_URL" \
      --env REPORT_DIR="$REPORT_DIR" \
      $extra_env \
      "$file"; then
    echo -e "${GREEN}[PASS]${NC} $name"
    ((PASS++)) || true
  else
    echo -e "${RED}[FAIL]${NC} $name"
    ((FAIL++)) || true
  fi
}

# ── Test selection ───────────────────────────────────────────────────────────
case "$TEST" in
  all)
    run_test "User Service Smoke"    "user-service/src/test/k6/user-service-perf-test.js"
    run_test "User Service Load"     "user-service/src/test/k6/performance-test.js"
    run_test "Product Service Load"  "product-service/src/test/k6/product-service-perf-test.js"
    run_test "Order Service Load"    "order-service/src/test/k6/order-service-perf-test.js"
    run_test "Payment Service Load"  "payment-service/src/test/k6/payment-service-perf-test.js"
    run_test "E2E Checkout (load)"   "user-service/src/test/k6/scenarios/checkout-e2e.js" "load"
    ;;
  smoke)
    run_test "User Service Smoke"  "user-service/src/test/k6/user-service-perf-test.js"
    run_test "E2E Checkout Smoke"  "user-service/src/test/k6/scenarios/checkout-e2e.js" "smoke"
    ;;
  user)
    run_test "User Service Smoke"  "user-service/src/test/k6/user-service-perf-test.js"
    run_test "User Service Load"   "user-service/src/test/k6/performance-test.js"
    ;;
  products)
    run_test "Product Service Load" "product-service/src/test/k6/product-service-perf-test.js"
    ;;
  orders)
    run_test "Order Service Load" "order-service/src/test/k6/order-service-perf-test.js"
    ;;
  payments)
    run_test "Payment Service Load" "payment-service/src/test/k6/payment-service-perf-test.js"
    ;;
  e2e)
    run_test "E2E Checkout ($SCENARIO)" "user-service/src/test/k6/scenarios/checkout-e2e.js" "$SCENARIO"
    ;;
  *)
    echo -e "${RED}[ERROR]${NC} Unknown test: $TEST"
    exit 1
    ;;
esac

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo -e " Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
echo " Reports: $(pwd)/$REPORT_DIR/"
echo "============================================================"
echo ""
echo " Open reports in your browser:"
for f in "$REPORT_DIR"/*-report.html; do
  [[ -f "$f" ]] && echo "   file://$(pwd)/$f"
done
echo ""

[[ $FAIL -gt 0 ]] && exit 1
exit 0
