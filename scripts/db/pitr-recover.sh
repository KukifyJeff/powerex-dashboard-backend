#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <full-backup.sql.gz> <binlog-file> <start-position> <stop-position> [--execute]"
  exit 1
fi

BACKUP_FILE="$1"
BINLOG_FILE="$2"
START_POSITION="$3"
STOP_POSITION="$4"
EXECUTE_MODE="${5:-}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-power_trading}"
DB_USER="${DB_USER:-admin}"
BINLOG_DIR="${BINLOG_DIR:-/var/lib/mysql}"

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD is required"
  exit 1
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "Backup file not found: ${BACKUP_FILE}"
  exit 1
fi

BINLOG_PATH="${BINLOG_DIR}/${BINLOG_FILE}"

if [[ ! -f "${BINLOG_PATH}" ]]; then
  echo "Binlog files not found under ${BINLOG_DIR}"
  echo "Expected: ${BINLOG_PATH}"
  exit 1
fi

RECOVERY_SQL="$(mktemp)"

echo "[1/3] Restoring full backup into ${DB_NAME} ..."
if [[ "${EXECUTE_MODE}" == "--execute" ]]; then
  zcat "${BACKUP_FILE}" | mysql \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USER}" \
    --password="${DB_PASSWORD}" \
    "${DB_NAME}"
else
  echo "(dry-run) Would restore full backup with zcat | mysql"
fi

echo "[2/3] Building binlog replay SQL ..."
mysqlbinlog \
  --start-position="${START_POSITION}" \
  --stop-position="${STOP_POSITION}" \
  "${BINLOG_PATH}" > "${RECOVERY_SQL}"

echo "[3/3] Replaying binlog ..."
if [[ "${EXECUTE_MODE}" == "--execute" ]]; then
  mysql \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USER}" \
    --password="${DB_PASSWORD}" \
    "${DB_NAME}" < "${RECOVERY_SQL}"
  echo "PITR completed."
else
  echo "(dry-run) Recovery SQL generated at: ${RECOVERY_SQL}"
  echo "Run again with --execute to apply."
fi
