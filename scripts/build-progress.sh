#!/bin/bash
# Portable Gradle progress reporter: streams completed tasks + elapsed timer.
# Usage: build-progress.sh <logfile> <gradle args...>
LOG="$1"; shift
rm -f "$LOG"
START=$(date +%s)
# Cap RAM tightly (Gradle ~1.1G + Kotlin ~512M = ~1.8G peak) and allow
# 4 CPU cores so 2 workers can run tasks in parallel.
taskset -c 0-3 nice -n 19 ionice -c3 gradle "$@" --console=plain >"$LOG" 2>&1 &
PID=$!
LAST=""
COUNT=0
while kill -0 $PID 2>/dev/null; do
    CUR=$(grep -E "^> Task " "$LOG" 2>/dev/null | tail -1 | sed 's/> Task //')
    if [ "$CUR" != "$LAST" ] && [ -n "$CUR" ]; then
        LAST="$CUR"
        COUNT=$((COUNT+1))
    fi
    NOW=$(date +%s)
    ELAPSED=$((NOW-START))
    printf "\r[%02d:%02d] tasks done: %d | current: %s                             " \
        $((ELAPSED/60)) $((ELAPSED%60)) "$COUNT" "${CUR:-starting...}"
    sleep 3
done
wait $PID
CODE=$?
NOW=$(date +%s)
ELAPSED=$((NOW-START))
TOTAL=$(grep -cE "^> Task " "$LOG" 2>/dev/null)
echo ""
echo "finished in ${ELAPSED}s | tasks: ${TOTAL} | exit: ${CODE}"
tail -5 "$LOG"
exit $CODE
