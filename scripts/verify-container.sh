#!/usr/bin/env sh
set -eu

image_name=${1:-user-management-gateway:verify}
container_id=""

cleanup() {
    if [ -n "$container_id" ]; then
        docker rm --force "$container_id" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT INT TERM

docker build --tag "$image_name" .

configured_user=$(docker image inspect --format '{{.Config.User}}' "$image_name")
test "$configured_user" = "10001:10001"

healthcheck=$(docker image inspect --format '{{json .Config.Healthcheck.Test}}' "$image_name")
test "$healthcheck" != "null"

AUTHENTICATION_SERVICE_TOKEN=${AUTHENTICATION_SERVICE_TOKEN:-container-test-auth-service-token-32-bytes}
export AUTHENTICATION_SERVICE_TOKEN
container_id=$(docker run --detach --read-only --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    --env AUTHENTICATION_SERVICE_TOKEN "$image_name")

attempt=0
while [ "$attempt" -lt 45 ]; do
    state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container_id")
    if [ "$state" = "running healthy" ]; then
        break
    fi
    if [ "${state%% *}" != "running" ]; then
        docker logs "$container_id"
        exit 1
    fi
    attempt=$((attempt + 1))
    sleep 1
done

test "$(docker inspect --format '{{.State.Health.Status}}' "$container_id")" = "healthy"
docker exec "$container_id" sh -c 'test "$(id -u)" = 10001 && test "$(id -g)" = 10001'
