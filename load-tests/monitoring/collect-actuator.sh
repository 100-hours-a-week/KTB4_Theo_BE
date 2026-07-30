#!/usr/bin/env bash

set -uo pipefail

scenario_name="${1:-}"
interval_seconds="${2:-5}"
duration_seconds="${3:-0}"
base_url="${4:-http://localhost:8080}"

if [[ -z "${scenario_name}" ]]; then
  echo "사용법: $0 <scenario-name> [interval-seconds] [duration-seconds] [base-url]" >&2
  exit 1
fi

if ! [[ "${interval_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "interval-seconds는 1 이상의 정수여야 합니다." >&2
  exit 1
fi

if ! [[ "${duration_seconds}" =~ ^[0-9]+$ ]]; then
  echo "duration-seconds는 0 이상의 정수여야 합니다." >&2
  exit 1
fi

for required_command in curl jq; do
  if ! command -v "${required_command}" >/dev/null 2>&1; then
    echo "${required_command} 명령이 필요합니다." >&2
    exit 1
  fi
done

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
result_directory="${script_directory}/results"
result_file="${result_directory}/${scenario_name}-actuator.csv"
endpoint="${base_url%/}/actuator/loadtest-snapshot"

mkdir -p "${result_directory}"

header="timestamp,process_cpu_usage,jvm_heap_used_bytes,jvm_threads_live,gc_pause_count,gc_pause_total_seconds,gc_pause_max_seconds,hikari_active_connections,hikari_pending_connections,unread_request_count,unread_request_total_seconds,unread_request_max_seconds"
echo "${header}" > "${result_file}"

started_at="$(date +%s)"

echo "Actuator 수집을 시작합니다."
echo "- endpoint: ${endpoint}"
echo "- interval: ${interval_seconds}초"
echo "- duration: $([[ "${duration_seconds}" -eq 0 ]] && echo "Ctrl+C까지" || echo "${duration_seconds}초")"
echo "- output: ${result_file}"

while true; do
  iteration_started_at="$(date +%s)"

  if response="$(curl --silent --show-error --fail "${endpoint}")"; then
    if csv_row="$(jq --raw-output '
      [
        .timestamp,
        .processCpuUsage,
        .jvmHeapUsedBytes,
        .jvmThreadsLive,
        .gcPauseCount,
        .gcPauseTotalSeconds,
        .gcPauseMaxSeconds,
        .hikariActiveConnections,
        .hikariPendingConnections,
        .unreadRequestCount,
        .unreadRequestTotalSeconds,
        .unreadRequestMaxSeconds
      ] | @csv
    ' <<< "${response}")"; then
      echo "${csv_row}" >> "${result_file}"
    else
      echo "Actuator 응답을 CSV로 변환하지 못했습니다." >&2
    fi
  else
    echo "Actuator 요청에 실패했습니다: ${endpoint}" >&2
  fi

  now="$(date +%s)"
  elapsed_seconds=$((now - started_at))

  if [[ "${duration_seconds}" -gt 0 && "${elapsed_seconds}" -ge "${duration_seconds}" ]]; then
    break
  fi

  iteration_elapsed_seconds=$((now - iteration_started_at))
  sleep_seconds=$((interval_seconds - iteration_elapsed_seconds))

  if [[ "${sleep_seconds}" -gt 0 ]]; then
    sleep "${sleep_seconds}"
  fi
done

echo "Actuator 수집을 완료했습니다: ${result_file}"
