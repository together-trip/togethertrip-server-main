#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="/Users/jujaewan/1_Projects/togethertrip"
MAIN_DIR="${ROOT_DIR}/togethertrip-server-main"
GATEWAY_COMPOSE="${ROOT_DIR}/togethertrip-server-gateway/docker-compose.yml"
RESULT_DIR="${ROOT_DIR}/docs/k6-results/dml"
K6_RUN_ID="$(date '+%Y%m%d-%H%M%S')"
RUN_LOG="${RESULT_DIR}/${K6_RUN_ID}-dml-run.log"
PID_FILE="${RESULT_DIR}/run-dml-load-test.pid"

BASE_URL="${BASE_URL:-http://localhost:8080}"
VUS="${VUS:-1}"
HOLD="${HOLD:-30s}"
RAMP_UP="${RAMP_UP:-10s}"
RAMP_DOWN="${RAMP_DOWN:-10s}"
SLEEP="${SLEEP:-1}"
STOP_EXISTING="${STOP_EXISTING:-true}"
OWNER_KAKAO_ACCESS_TOKEN="${OWNER_KAKAO_ACCESS_TOKEN:-local-test:verified:hana}"
SENDER_KAKAO_ACCESS_TOKEN="${SENDER_KAKAO_ACCESS_TOKEN:-local-test:verified:minseo}"
RECEIVER_KAKAO_ACCESS_TOKEN="${RECEIVER_KAKAO_ACCESS_TOKEN:-local-test:verified:joon}"

stop_existing_k6() {
  local pids
  local target_pids=""
  local pid

  pids="$(pgrep -f "k6 run.*main-dml-flow.js" || true)"
  for pid in ${pids}; do
    if [[ "${pid}" != "$$" ]]; then
      target_pids="${target_pids} ${pid}"
    fi
  done

  if [[ -z "${target_pids// /}" ]]; then
    echo "No existing DML k6 process found."
    return
  fi

  echo "Stopping existing DML k6 process:"
  ps -o pid=,command= -p ${target_pids} || true
  kill -TERM ${target_pids} 2>/dev/null || true
  sleep 3
  for pid in ${target_pids}; do
    if kill -0 "${pid}" 2>/dev/null; then
      kill -KILL "${pid}" 2>/dev/null || true
    fi
  done
}

stop_existing_runner() {
  local pid

  if [[ ! -f "${PID_FILE}" ]]; then
    echo "No existing DML runner pid file found."
    return
  fi

  pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
  if [[ -z "${pid}" || "${pid}" == "$$" ]]; then
    rm -f "${PID_FILE}"
    return
  fi

  if ! kill -0 "${pid}" 2>/dev/null; then
    echo "Existing DML runner process is not running. Removing stale pid file."
    rm -f "${PID_FILE}"
    return
  fi

  echo "Stopping existing DML runner process:"
  ps -o pid=,command= -p "${pid}" || true
  kill -TERM "${pid}" 2>/dev/null || true
  sleep 3
  if kill -0 "${pid}" 2>/dev/null; then
    kill -KILL "${pid}" 2>/dev/null || true
  fi
  rm -f "${PID_FILE}"
}

mkdir -p "${RESULT_DIR}"
cd "${MAIN_DIR}"

exec > >(tee -a "${RUN_LOG}") 2>&1

echo "========================================"
echo "TogetherTrip k6 DML load test"
echo "========================================"
echo "run id: ${K6_RUN_ID}"
echo "result dir: ${RESULT_DIR}"
echo "BASE_URL: ${BASE_URL}"
echo "VUS: ${VUS}"
echo "RAMP_UP: ${RAMP_UP}"
echo "HOLD: ${HOLD}"
echo "RAMP_DOWN: ${RAMP_DOWN}"
echo "SLEEP: ${SLEEP}"
echo "STOP_EXISTING: ${STOP_EXISTING}"
echo "started at: $(date '+%Y-%m-%d %H:%M:%S')"
echo

if [[ "${STOP_EXISTING}" == "true" ]]; then
  echo "[1/4] Stop existing DML load-test processes"
  stop_existing_k6
  stop_existing_runner
else
  echo "[1/4] Skip stopping existing DML load-test processes"
fi

echo "$$" > "${PID_FILE}"
trap 'rm -f "${PID_FILE}"' EXIT

echo
echo "[2/4] Cleanup previous DML load-test data"
docker compose -f "${GATEWAY_COMPOSE}" \
  cp performance/seed/cleanup-dml-load-test-data.sql postgres:/tmp/cleanup-dml-load-test-data.sql

docker compose -f "${GATEWAY_COMPOSE}" \
  cp performance/seed/validate-dml-load-test-data.sql postgres:/tmp/validate-dml-load-test-data.sql

docker compose -f "${GATEWAY_COMPOSE}" \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/cleanup-dml-load-test-data.sql \
  | tee "${RESULT_DIR}/${K6_RUN_ID}-dml-cleanup-before.log"

echo
echo "[3/4] Run DML k6. This step stays running until the load test is finished."
echo "k6 output: ${RESULT_DIR}/${K6_RUN_ID}-dml-terminal.log"
echo "k6 summary: ${RESULT_DIR}/${K6_RUN_ID}-dml-summary.json"
echo

set +e
BASE_URL="${BASE_URL}" \
OWNER_KAKAO_ACCESS_TOKEN="${OWNER_KAKAO_ACCESS_TOKEN}" \
SENDER_KAKAO_ACCESS_TOKEN="${SENDER_KAKAO_ACCESS_TOKEN}" \
RECEIVER_KAKAO_ACCESS_TOKEN="${RECEIVER_KAKAO_ACCESS_TOKEN}" \
VUS="${VUS}" \
RAMP_UP="${RAMP_UP}" \
HOLD="${HOLD}" \
RAMP_DOWN="${RAMP_DOWN}" \
SLEEP="${SLEEP}" \
k6 run \
  --summary-export "${RESULT_DIR}/${K6_RUN_ID}-dml-summary.json" \
  performance/k6/main-dml-flow.js \
  2>&1 | tee "${RESULT_DIR}/${K6_RUN_ID}-dml-terminal.log"
K6_EXIT_CODE="${PIPESTATUS[0]}"
set -e

echo
echo "[4/4] Validate DML load-test data"
docker compose -f "${GATEWAY_COMPOSE}" \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/validate-dml-load-test-data.sql \
  | tee "${RESULT_DIR}/${K6_RUN_ID}-dml-validate-after.log"

echo
echo "========================================"
echo "k6 DML load test finished"
echo "========================================"
echo "finished at: $(date '+%Y-%m-%d %H:%M:%S')"
echo "k6 exit code: ${K6_EXIT_CODE}"
echo
echo "result files:"
echo "${RUN_LOG}"
echo "${RESULT_DIR}/${K6_RUN_ID}-dml-terminal.log"
echo "${RESULT_DIR}/${K6_RUN_ID}-dml-summary.json"
echo "${RESULT_DIR}/${K6_RUN_ID}-dml-cleanup-before.log"
echo "${RESULT_DIR}/${K6_RUN_ID}-dml-validate-after.log"

exit "${K6_EXIT_CODE}"
