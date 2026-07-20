#!/usr/bin/env sh
set -eu

port=${SERVER_PORT:-8083}
wget --quiet --spider "http://127.0.0.1:${port}/actuator/health/readiness"
