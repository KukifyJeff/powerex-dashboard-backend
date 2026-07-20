#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [backup-tag]"
  exit 1
fi

BACKUP_TAG="${1:-$(date +%Y%m%d-%H%M%S)}"
OUTPUT_DIR="${BACKUP_OUTPUT_DIR:-./backups/db}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-power_trading}"
DB_USER="${DB_USER:-admin}"

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD is required"
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

BACKUP_FILE="${OUTPUT_DIR}/${DB_NAME}-full-${BACKUP_TAG}.sql.gz"
META_FILE="${OUTPUT_DIR}/${DB_NAME}-full-${BACKUP_TAG}.meta"

echo "[1/3] Dumping ${DB_NAME} ..."
mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USER}" \
  --password="${DB_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  --hex-blob \
  "${DB_NAME}" | gzip > "${BACKUP_FILE}"

echo "[2/3] Capturing master status ..."
MASTER_STATUS="$(mysql \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USER}" \
  --password="${DB_PASSWORD}" \
  --batch \
  --skip-column-names \
  -e "SHOW MASTER STATUS" || true)"

if [[ -z "${MASTER_STATUS}" ]]; then
  echo "SHOW MASTER STATUS returned empty result; ensure binary logging is enabled."
  exit 1
fi

BINLOG_FILE="$(echo "${MASTER_STATUS}" | awk '{print $1}')"
BINLOG_POS="$(echo "${MASTER_STATUS}" | awk '{print $2}')"
GTID_SET="$(echo "${MASTER_STATUS}" | awk '{print $5}')"

echo "[3/3] Writing metadata ..."
cat > "${META_FILE}" <<EOF
backup_tag=${BACKUP_TAG}
backup_file=${BACKUP_FILE}
db_name=${DB_NAME}
created_at=$(date +"%Y-%m-%d %H:%M:%S")
binlog_file=${BINLOG_FILE}
binlog_position=${BINLOG_POS}
gtid_set=${GTID_SET}
EOF

echo "Backup created:"
echo "  SQL:  ${BACKUP_FILE}"
echo "  META: ${META_FILE}"
