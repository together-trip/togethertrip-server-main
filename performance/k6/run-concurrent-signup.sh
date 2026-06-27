#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/Users/jujaewan/1_Projects/togethertrip}"
MAIN_DIR="${MAIN_DIR:-${ROOT_DIR}/togethertrip-server-main}"
GATEWAY_COMPOSE="${GATEWAY_COMPOSE:-${ROOT_DIR}/togethertrip-server-gateway/docker-compose.yml}"
RESULT_DIR="${RESULT_DIR:-${ROOT_DIR}/docs/k6-results/concurrent-signup}"
RUN_ID="${RUN_ID:-$(date '+%Y%m%d-%H%M%S')}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
CONFIRM_CODE="${CONFIRM_CODE:-123456}"
SAME_SESSION_VUS="${SAME_SESSION_VUS:-20}"
SAME_PHONE_VUS="${SAME_PHONE_VUS:-10}"
MAX_DURATION="${MAX_DURATION:-5s}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-60}"

mkdir -p "${RESULT_DIR}"
cd "${MAIN_DIR}"

RUN_LOG="${RESULT_DIR}/${RUN_ID}-concurrent-signup-run.log"

exec > >(tee -a "${RUN_LOG}") 2>&1

echo "========================================"
echo "TogetherTrip issue #45 concurrent signup k6"
echo "========================================"
echo "RUN_ID: ${RUN_ID}"
echo "BASE_URL: ${BASE_URL}"
echo "CONFIRM_CODE: ${CONFIRM_CODE}"
echo "SAME_SESSION_VUS: ${SAME_SESSION_VUS}"
echo "SAME_PHONE_VUS: ${SAME_PHONE_VUS}"
echo "MAX_DURATION: ${MAX_DURATION}"
echo "started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo
echo "Prerequisite: gateway docker compose is running and main is reachable through ${BASE_URL}."
echo "Prerequisite: main has auth.local-test.enabled=true so /api/local-test/** is available."
echo

K6_EXIT_CODE=0

wait_for_gateway_main() {
  local started_at
  local status
  local body_file

  started_at="$(date +%s)"
  body_file="${RESULT_DIR}/${RUN_ID}-readiness-body.json"

  echo "[ready] Wait for main through gateway ${BASE_URL}"
  while true; do
    status="$(
      curl -s \
        -o "${body_file}" \
        -w '%{http_code}' \
        -X POST "${BASE_URL}/api/auth/oauth/kakao" \
        -H 'Content-Type: application/json' \
        -d "{\"accessToken\":\"local-test:issue45-${RUN_ID}-readiness\"}" \
        || true
    )"

    if [[ "${status}" == "200" ]]; then
      echo "[ready] main is reachable through gateway"
      return
    fi

    if (( "$(date +%s)" - started_at >= READY_TIMEOUT_SECONDS )); then
      echo "[ready] timed out after ${READY_TIMEOUT_SECONDS}s; last status=${status}"
      cat "${body_file}" || true
      return 1
    fi

    sleep 2
  done
}

run_scenario() {
  local scenario="$1"
  local vus="$2"
  local terminal_log="${RESULT_DIR}/${RUN_ID}-${scenario}-terminal.log"
  local summary_json="${RESULT_DIR}/${RUN_ID}-${scenario}-summary.json"

  echo
  echo "[k6] Run scenario=${scenario}, vus=${vus}"
  set +e
  BASE_URL="${BASE_URL}" \
  RUN_ID="${RUN_ID}" \
  SCENARIO="${scenario}" \
  VUS="${vus}" \
  CONFIRM_CODE="${CONFIRM_CODE}" \
  MAX_DURATION="${MAX_DURATION}" \
  k6 run \
    --summary-export "${summary_json}" \
    performance/k6/concurrent-signup.js \
    2>&1 | tee "${terminal_log}"
  local exit_code="${PIPESTATUS[0]}"
  set -e

  echo "[k6] scenario=${scenario} exit_code=${exit_code}"
  if [[ "${exit_code}" -ne 0 ]]; then
    K6_EXIT_CODE="${exit_code}"
  fi
}

wait_for_gateway_main

run_scenario "same-session" "${SAME_SESSION_VUS}"
run_scenario "same-phone" "${SAME_PHONE_VUS}"

echo
echo "[db] Validate created rows through gateway compose postgres"
docker compose -f "${GATEWAY_COMPOSE}" \
  cp performance/seed/validate-concurrent-signup.sql postgres:/tmp/validate-concurrent-signup.sql

docker compose -f "${GATEWAY_COMPOSE}" \
  exec -T postgres psql -U together_trip -d together_trip \
  -v ON_ERROR_STOP=1 \
  -v run_id="${RUN_ID}" \
  -f /tmp/validate-concurrent-signup.sql \
  | tee "${RESULT_DIR}/${RUN_ID}-db-validation.log"

echo
echo "========================================"
echo "issue #45 concurrent signup k6 finished"
echo "========================================"
echo "finished at: $(date '+%Y-%m-%d %H:%M:%S')"
echo "k6 exit code: ${K6_EXIT_CODE}"
echo "result files:"
echo "${RUN_LOG}"
echo "${RESULT_DIR}/${RUN_ID}-same-session-terminal.log"
echo "${RESULT_DIR}/${RUN_ID}-same-session-summary.json"
echo "${RESULT_DIR}/${RUN_ID}-same-phone-terminal.log"
echo "${RESULT_DIR}/${RUN_ID}-same-phone-summary.json"
echo "${RESULT_DIR}/${RUN_ID}-db-validation.log"

exit "${K6_EXIT_CODE}"
