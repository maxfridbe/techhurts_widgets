#!/usr/bin/env bash
# Set the version the next build gets.
#
#   ./setversion.sh               show the last and next version
#   ./setversion.sh 26.0917.020   next build (local make.sh or CI) is 26.0917.020
#   ./setversion.sh 20            same, using today's date
#
# version.properties stores the last build number for the day, so this writes
# the requested number minus one. The ### counter resets daily, so the date
# must be today. CI builds one past the committed counter (or today's newest
# release, if higher): commit and push version.properties before running it.
set -euo pipefail
cd "$(dirname "$0")"

today=$(date +%y%m%d)
fmt() { printf '%s.%s.%03d' "${1:0:2}" "${1:2:4}" "$2"; }

versionDate=""; versionBuild=0
[ -f version.properties ] && . ./version.properties
last=$((10#$versionBuild))
if [ "$versionDate" = "$today" ]; then next=$((last + 1)); else next=1; fi

if [ $# -eq 0 ]; then
  [ -n "$versionDate" ] && echo "last build: $(fmt "$versionDate" "$last")"
  echo "next build: $(fmt "$today" "$next")"
  exit 0
fi

arg=$1
if [[ "$arg" =~ ^([0-9]{2})\.([0-9]{4})\.([0-9]{1,3})$ ]]; then
  date="${BASH_REMATCH[1]}${BASH_REMATCH[2]}"; build=$((10#${BASH_REMATCH[3]}))
  if [ "$date" != "$today" ]; then
    echo "error: the date must be today ($(fmt "$today" 0 | cut -d. -f1-2)); the ### counter resets daily" >&2
    exit 1
  fi
elif [[ "$arg" =~ ^[0-9]{1,3}$ ]]; then
  build=$((10#$arg))
else
  echo "usage: $0 [YY.MMDD.### | ###]" >&2
  exit 1
fi
if [ "$build" -lt 1 ] || [ "$build" -gt 999 ]; then
  echo "error: ### must be 001-999" >&2; exit 1
fi
if [ "$versionDate" = "$today" ] && [ "$build" -le "$last" ]; then
  echo "warning: $(fmt "$today" "$build") is not above the last build $(fmt "$today" "$last"); it won't install over it" >&2
fi

printf 'versionDate=%s\nversionBuild=%03d\n' "$today" "$((build - 1))" > version.properties
echo "next build: $(fmt "$today" "$build")"
