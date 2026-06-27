#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="/Users/jujaewan/1_Projects/togethertrip"
MAIN_DIR="${ROOT_DIR}/togethertrip-server-main"
GATEWAY_COMPOSE="${ROOT_DIR}/togethertrip-server-gateway/docker-compose.yml"
RESULT_DIR="${ROOT_DIR}/docs/k6-results/read-settlement"
K6_RUN_ID="$(date '+%Y%m%d-%H%M%S')"
RUN_LOG="${RESULT_DIR}/${K6_RUN_ID}-run.log"
PID_FILE="${RESULT_DIR}/run-load-test.pid"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TRIP_TITLE="${TRIP_TITLE:-LOADTEST_정산_대량_여행}"
VUS="${VUS:-10}"
HOLD="${HOLD:-1m}"
RAMP_UP="${RAMP_UP:-30s}"
RAMP_DOWN="${RAMP_DOWN:-30s}"
SLEEP="${SLEEP:-1}"
SLOW_REQUEST_MS="${SLOW_REQUEST_MS:-200}"
STOP_EXISTING="${STOP_EXISTING:-true}"

stop_existing_processes() {
  local pattern="$1"
  local label="$2"
  local pids
  local target_pids=""
  local alive_pids=""
  local pid

  pids="$(pgrep -f "${pattern}" || true)"

  for pid in ${pids}; do
    if [[ "${pid}" != "$$" ]]; then
      target_pids="${target_pids} ${pid}"
    fi
  done

  if [[ -z "${target_pids// /}" ]]; then
    echo "No existing ${label} process found."
    return
  fi

  echo "Stopping existing ${label} process:"
  ps -o pid=,command= -p ${target_pids} || true

  kill -TERM ${target_pids} 2>/dev/null || true
  sleep 3

  for pid in ${target_pids}; do
    if kill -0 "${pid}" 2>/dev/null; then
      alive_pids="${alive_pids} ${pid}"
    fi
  done

  if [[ -n "${alive_pids// /}" ]]; then
    echo "Force stopping remaining ${label} process:"
    ps -o pid=,command= -p ${alive_pids} || true
    kill -KILL ${alive_pids} 2>/dev/null || true
  fi
}

stop_existing_pid_file_process() {
  local label="$1"
  local pid

  if [[ ! -f "${PID_FILE}" ]]; then
    echo "No existing ${label} pid file found."
    return
  fi

  pid="$(cat "${PID_FILE}" 2>/dev/null || true)"

  if [[ -z "${pid}" ]]; then
    echo "Existing ${label} pid file is empty. Removing it."
    rm -f "${PID_FILE}"
    return
  fi

  if [[ "${pid}" == "$$" ]]; then
    echo "Existing ${label} pid file points to current process. Keeping current process."
    return
  fi

  if ! kill -0 "${pid}" 2>/dev/null; then
    echo "Existing ${label} process is not running. Removing stale pid file."
    rm -f "${PID_FILE}"
    return
  fi

  echo "Stopping existing ${label} process from pid file:"
  ps -o pid=,command= -p "${pid}" || true

  kill -TERM "${pid}" 2>/dev/null || true
  sleep 3

  if kill -0 "${pid}" 2>/dev/null; then
    echo "Force stopping remaining ${label} process:"
    ps -o pid=,command= -p "${pid}" || true
    kill -KILL "${pid}" 2>/dev/null || true
  fi

  rm -f "${PID_FILE}"
}

mkdir -p "${RESULT_DIR}"
cd "${MAIN_DIR}"

exec > >(tee -a "${RUN_LOG}") 2>&1

echo "========================================"
echo "TogetherTrip k6 load test"
echo "========================================"
echo "run id: ${K6_RUN_ID}"
echo "result dir: ${RESULT_DIR}"
echo "BASE_URL: ${BASE_URL}"
echo "TRIP_TITLE: ${TRIP_TITLE}"
echo "VUS: ${VUS}"
echo "RAMP_UP: ${RAMP_UP}"
echo "HOLD: ${HOLD}"
echo "RAMP_DOWN: ${RAMP_DOWN}"
echo "SLEEP: ${SLEEP}"
echo "SLOW_REQUEST_MS: ${SLOW_REQUEST_MS}"
echo "STOP_EXISTING: ${STOP_EXISTING}"
echo "started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo

if [[ "${STOP_EXISTING}" == "true" ]]; then
  echo "[1/6] Stop existing load-test processes"
  stop_existing_processes "k6 run.*main-read-settlement.js" "k6"
  stop_existing_pid_file_process "run-load-test.sh"
else
  echo "[1/6] Skip stopping existing load-test processes"
fi

echo "$$" > "${PID_FILE}"
trap 'rm -f "${PID_FILE}"' EXIT

echo
echo "[2/6] Copy SQL files into postgres container"
docker compose -f "${GATEWAY_COMPOSE}" \
  cp performance/seed/cleanup-load-test-data.sql postgres:/tmp/cleanup-load-test-data.sql

docker compose -f "${GATEWAY_COMPOSE}" \
  cp performance/seed/load-test-data.sql postgres:/tmp/load-test-data.sql

docker compose -f "${GATEWAY_COMPOSE}" \
  cp performance/seed/validate-load-test-data.sql postgres:/tmp/validate-load-test-data.sql

echo
echo "[3/6] Reset and seed load-test data"
docker compose -f "${GATEWAY_COMPOSE}" \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/load-test-data.sql

echo
echo "[4/6] Validate data before k6"
docker compose -f "${GATEWAY_COMPOSE}" \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/validate-load-test-data.sql \
  | tee "${RESULT_DIR}/${K6_RUN_ID}-validate-before.log"

echo
echo "[5/6] Run k6. This step stays running until the load test is finished."
echo "k6 output: ${RESULT_DIR}/${K6_RUN_ID}-terminal.log"
echo "k6 summary: ${RESULT_DIR}/${K6_RUN_ID}-summary.json"
echo

set +e
BASE_URL="${BASE_URL}" \
TRIP_TITLE="${TRIP_TITLE}" \
VUS="${VUS}" \
RAMP_UP="${RAMP_UP}" \
HOLD="${HOLD}" \
RAMP_DOWN="${RAMP_DOWN}" \
SLEEP="${SLEEP}" \
SLOW_REQUEST_MS="${SLOW_REQUEST_MS}" \
k6 run \
  --summary-export "${RESULT_DIR}/${K6_RUN_ID}-summary.json" \
  performance/k6/main-read-settlement.js \
  2>&1 | tee "${RESULT_DIR}/${K6_RUN_ID}-terminal.log"
K6_EXIT_CODE="${PIPESTATUS[0]}"
set -e

echo
echo "[6/6] Validate data after k6"
docker compose -f "${GATEWAY_COMPOSE}" \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/validate-load-test-data.sql \
  | tee "${RESULT_DIR}/${K6_RUN_ID}-validate-after.log"

echo
echo "========================================"
echo "k6 load test finished"
echo "========================================"
echo "finished at: $(date '+%Y-%m-%d %H:%M:%S')"
echo "k6 exit code: ${K6_EXIT_CODE}"
echo
echo "result files:"
echo "${RUN_LOG}"
echo "${RESULT_DIR}/${K6_RUN_ID}-terminal.log"
echo "${RESULT_DIR}/${K6_RUN_ID}-summary.json"
echo "${RESULT_DIR}/${K6_RUN_ID}-validate-before.log"
echo "${RESULT_DIR}/${K6_RUN_ID}-validate-after.log"
echo

if [[ "${K6_EXIT_CODE}" -ne 0 ]]; then
  echo "k6 finished with a non-zero exit code."
  echo "If checks are 100% and http_req_failed is 0%, this usually means a threshold failed."
fi

exit "${K6_EXIT_CODE}"
