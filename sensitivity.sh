#!/bin/sh
# One-at-a-time sensitivity (default 0.5x and 1.5x) of the water fluxes to every parameter.
# Usage: ./sensitivity.sh [input directory] [factor ...]   (default: example_hourly/, i.e. ET method PM)
# Writes <input directory>/output/sensitivity_oat.csv and prints a ranking.
set -e
cd "$(dirname "$0")"
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
mkdir -p bin
${JAVA_BIN}javac -encoding UTF-8 -d bin src/thud/simpel/*.java test/thud/simpel/SensitivityRun.java
directory=${1:-example_hourly/}
[ $# -gt 0 ] && shift
${JAVA_BIN}java -cp bin thud.simpel.SensitivityRun "$directory" "$@"
