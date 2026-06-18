#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT_DIR/gms-server"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-beidou}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
EXPECTED_MYSQL_DATADIR="${EXPECTED_MYSQL_DATADIR:-/var/lib/mysql/}"
ALLOW_EMPTY_DB="${ALLOW_EMPTY_DB:-0}"

WEB_PORT="${WEB_PORT:-8686}"
LOGIN_PORT="${LOGIN_PORT:-8484}"
CHANNEL_PORTS="${CHANNEL_PORTS:-7575 7576 7577}"

SERVER_LOG="${SERVER_LOG:-$SERVER_DIR/logs/wsl-server.log}"
SERVER_PID_FILE="${SERVER_PID_FILE:-/tmp/beidou-wsl-server.pid}"

ACTION="start"
ASSUME_YES=0

usage() {
    cat <<EOF
Usage: $0 [start|stop|status|mysql-start] [--yes]

start       Start WSL MySQL from /var/lib/mysql, verify existing beidou DB, then start server.
stop        Stop only the server started by this script. MySQL is left running.
status      Show WSL MySQL, DB, server process, and port status.
mysql-start Start only WSL MySQL and verify it is the original /var/lib/mysql instance.

Safety:
  - Never initializes a MySQL datadir.
  - Never creates the beidou database.
  - Never starts or stops Windows MySQL services.
  - Refuses a MySQL instance whose @@datadir is not $EXPECTED_MYSQL_DATADIR.
  - Prompts before starting if a pending migration can clear hp_challenge_* data.
EOF
}

log() {
    printf '[beidou-wsl] %s\n' "$*"
}

fail() {
    printf '[beidou-wsl] ERROR: %s\n' "$*" >&2
    exit 1
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        start|stop|status|mysql-start)
            ACTION="$1"
            ;;
        -y|--yes)
            ASSUME_YES=1
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "Unknown argument: $1"
            ;;
    esac
    shift
done

mysql_cmd() {
    MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=tcp \
        -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" "$@"
}

mysql_scalar() {
    mysql_cmd --batch --skip-column-names "$@"
}

mysql_ping() {
    MYSQL_PWD="$MYSQL_PASSWORD" mysqladmin --protocol=tcp \
        -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" ping >/dev/null 2>&1
}

port_listening() {
    local port="$1"
    ss -ltn | grep -Eq "[:.]${port}[[:space:]]"
}

start_wsl_mysql() {
    if mysql_ping; then
        log "MySQL already responds on $MYSQL_HOST:$MYSQL_PORT"
    else
        log "Starting WSL MySQL service with existing datadir only..."
        if command -v sudo >/dev/null 2>&1; then
            sudo -n service mysql start
        else
            service mysql start
        fi
        for _ in $(seq 1 30); do
            if mysql_ping; then
                break
            fi
            sleep 1
        done
        mysql_ping || fail "MySQL did not become ready on $MYSQL_HOST:$MYSQL_PORT"
    fi

    local datadir
    datadir="$(mysql_scalar -e "SELECT @@datadir;")"
    if [ "$datadir" != "$EXPECTED_MYSQL_DATADIR" ]; then
        fail "Refusing MySQL datadir '$datadir'. Expected '$EXPECTED_MYSQL_DATADIR'."
    fi
    log "MySQL datadir verified: $datadir"
}

table_exists() {
    local table="$1"
    local exists
    exists="$(mysql_scalar -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='${table}';")"
    [ "$exists" = "1" ]
}

db_exists() {
    local exists
    exists="$(mysql_scalar -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${MYSQL_DB}';")"
    [ "$exists" = "1" ]
}

db_count() {
    local table="$1"
    if table_exists "$table"; then
        mysql_scalar "$MYSQL_DB" -e "SELECT COUNT(*) FROM ${table};"
    else
        printf 'missing'
    fi
}

verify_existing_db() {
    db_exists || fail "Database '$MYSQL_DB' does not exist. Refusing to create or use an empty DB."

    local accounts_count characters_count
    accounts_count="$(db_count accounts)"
    characters_count="$(db_count characters)"
    log "DB '$MYSQL_DB' exists. accounts=$accounts_count, characters=$characters_count"

    if [ "$ALLOW_EMPTY_DB" != "1" ]; then
        if [ "$accounts_count" = "missing" ] || [ "$characters_count" = "missing" ]; then
            fail "accounts/characters table is missing. This does not look like the original DB."
        fi
        if [ "$accounts_count" = "0" ] && [ "$characters_count" = "0" ]; then
            fail "accounts and characters are both empty. Set ALLOW_EMPTY_DB=1 only for a deliberate fresh DB."
        fi
    fi
}

warn_pending_destructive_migration() {
    local migration="$SERVER_DIR/src/main/resources/db/migration/V1.11.5__reset_hp_challenge_for_life_proof.sql"
    [ -f "$migration" ] || return 0
    table_exists flyway_schema_history || return 0

    local applied
    applied="$(mysql_scalar "$MYSQL_DB" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.11.5' AND success=1;")"
    [ "$applied" = "0" ] || return 0

    log "WARNING: V1.11.5 is not applied yet."
    log "Starting the server will let Flyway run V1.11.5__reset_hp_challenge_for_life_proof.sql."
    log "That migration clears hp_challenge_event_log/progress/reward/state/gm_log."

    if table_exists hp_challenge_state; then
        log "Current hp_challenge_state rows: $(db_count hp_challenge_state)"
    fi
    if table_exists hp_challenge_progress; then
        log "Current hp_challenge_progress rows: $(db_count hp_challenge_progress)"
    fi
    if table_exists hp_challenge_reward_log; then
        log "Current hp_challenge_reward_log rows: $(db_count hp_challenge_reward_log)"
    fi

    if [ "$ASSUME_YES" = "1" ]; then
        log "--yes was passed; continuing."
        return 0
    fi

    printf "Type 'yes' to start server and allow Flyway to run pending migrations: "
    local answer
    read -r answer
    [ "$answer" = "yes" ] || fail "Startup cancelled."
}

ensure_server_ports_free() {
    local port
    for port in "$WEB_PORT" "$LOGIN_PORT" $CHANNEL_PORTS; do
        if port_listening "$port"; then
            fail "Port $port is already listening. Stop the existing server first."
        fi
    done
}

server_running() {
    local pid
    pid="$(server_pid)"
    [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1
}

server_pid() {
    if [ -f "$SERVER_PID_FILE" ]; then
        local pid
        pid="$(cat "$SERVER_PID_FILE" 2>/dev/null || true)"
        if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
            printf '%s\n' "$pid"
            return 0
        fi
    fi
    pgrep -f "org.gms.ServerApplication" | head -n 1
}

server_child_pids() {
    local pid
    pid="$(server_pid)"
    [ -n "$pid" ] || return 0
    pgrep -P "$pid" 2>/dev/null || true
}

start_server() {
    start_wsl_mysql
    verify_existing_db
    warn_pending_destructive_migration
    ensure_server_ports_free

    mkdir -p "$(dirname "$SERVER_LOG")"
    : > "$SERVER_LOG"

    log "Starting server from $SERVER_DIR"
    setsid bash -c 'cd "$1" && exec mvn spring-boot:run' beidou-server "$SERVER_DIR" \
        > "$SERVER_LOG" 2>&1 < /dev/null &
    printf '%s\n' "$!" > "$SERVER_PID_FILE"

    local pid
    pid="$(cat "$SERVER_PID_FILE")"
    log "Maven PID: $pid"
    log "Log file: $SERVER_LOG"

    for _ in $(seq 1 120); do
        if grep -q "启动完成" "$SERVER_LOG" 2>/dev/null \
                && port_listening "$WEB_PORT" \
                && port_listening "$LOGIN_PORT"; then
            sleep 2
            if ! port_listening "$WEB_PORT" || ! port_listening "$LOGIN_PORT"; then
                continue
            fi
            log "Server started."
            log "Web: http://127.0.0.1:$WEB_PORT/"
            log "Login port: $LOGIN_PORT"
            return 0
        fi
        if ! server_running; then
            tail -n 160 "$SERVER_LOG" >&2 || true
            fail "Server process exited during startup."
        fi
        sleep 1
    done

    tail -n 180 "$SERVER_LOG" >&2 || true
    fail "Timed out waiting for server startup."
}

stop_server() {
    if ! server_running; then
        log "No server PID from $SERVER_PID_FILE is running."
        return 0
    fi

    local pid child
    pid="$(server_pid)"
    log "Stopping server PID $pid"
    for child in $(server_child_pids); do
        kill "$child" 2>/dev/null || true
    done
    kill "$pid" 2>/dev/null || true

    for _ in $(seq 1 20); do
        if ! kill -0 "$pid" >/dev/null 2>&1; then
            rm -f "$SERVER_PID_FILE"
            log "Server stopped."
            return 0
        fi
        sleep 1
    done

    for child in $(server_child_pids); do
        kill -9 "$child" 2>/dev/null || true
    done
    kill -9 "$pid" 2>/dev/null || true
    rm -f "$SERVER_PID_FILE"
    log "Server force-stopped."
}

status() {
    if mysql_ping; then
        local datadir
        datadir="$(mysql_scalar -e "SELECT @@datadir;")"
        log "MySQL: alive on $MYSQL_HOST:$MYSQL_PORT, datadir=$datadir"
        if db_exists; then
            log "DB '$MYSQL_DB': accounts=$(db_count accounts), characters=$(db_count characters)"
            if table_exists flyway_schema_history; then
                mysql_cmd "$MYSQL_DB" -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
            fi
        else
            log "DB '$MYSQL_DB': missing"
        fi
    else
        log "MySQL: not responding on $MYSQL_HOST:$MYSQL_PORT"
    fi

    if server_running; then
        log "Server PID: $(server_pid)"
    else
        log "Server: not running from $SERVER_PID_FILE"
    fi

    ss -ltnp | grep -E "(:${MYSQL_PORT}|:${WEB_PORT}|:${LOGIN_PORT}|:7575|:7576|:7577)[[:space:]]" || true
}

case "$ACTION" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    status)
        status
        ;;
    mysql-start)
        start_wsl_mysql
        verify_existing_db
        ;;
esac
