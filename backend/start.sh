#!/usr/bin/env bash
# Start command when Render's Root Directory is set to "backend":
#   bash start.sh
set -euo pipefail
exec uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}" --no-access-log
