#!/bin/sh
# One-at-a-time sensitivity of water fluxes at five levels for every parameter.
# Usage: ./sensitivity.sh [input directory]   (default: example_hourly_winter/, i.e. ET method PM)
# Writes <input directory>/output_<yyyyMMdd_HHmmss>/sensitivity_oat.csv and prints a ranking.
set -e
cd "$(dirname "$0")"
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
mkdir -p bin
${JAVA_BIN}javac -encoding UTF-8 -d bin src/thud/simpel/*.java test/thud/simpel/SensitivityRun.java
directory=${1:-example_hourly_winter/}
[ $# -gt 0 ] && shift
${JAVA_BIN}java -cp bin thud.simpel.SensitivityRun "$directory" "$@"
