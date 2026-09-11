#!/usr/bin/env bash
set -euo pipefail

base_url="http://localhost:8080/api/v1"

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"speedKph":80,"coolantTemperatureC":120,"gear":"D"}' \
  "$base_url/signals"

curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d '{"question":"为什么出现超温告警？请生成回归测试方案","includeTestPlan":true}' \
  "$base_url/diagnoses"
