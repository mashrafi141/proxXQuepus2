#!/usr/bin/env bash
# Render / Railway / any normal web service start command (from repository root):
#   bash start.sh
set -euo pipefail
exec uvicorn backend.app.main:app --host 0.0.0.0 --port "${PORT:-8000}" --no-access-log
