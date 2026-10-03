#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
jar="target/sourcedesk-0.1.0.jar"
if [[ ! -f "$jar" ]]; then
  echo "Build first with: mvn test package"
  exit 1
fi
exec java -jar "$jar"
