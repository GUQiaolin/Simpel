#!/bin/sh
# One-at-a-time sensitivity of water fluxes at five levels for every parameter.
# Usage: ./sensitivity.sh [run folder or run.txt]   (default: runs/hourly_pm_winter, i.e. ET method PM)
# Writes output_<yyyyMMdd_HHmmss>/sensitivity_oat.csv next to run.txt and prints a ranking.
set -e
cd "$(dirname "$0")"
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
mkdir -p bin
${JAVA_BIN}javac -encoding UTF-8 -d bin src/thud/simpel/*.java test/thud/simpel/SensitivityRun.java
directory=${1:-runs/hourly_pm_winter}
[ $# -gt 0 ] && shift
${JAVA_BIN}java -cp bin thud.simpel.SensitivityRun "$directory" "$@"
