#!/bin/sh
# Compile and run SIMPEL. Uses javac/java from the PATH (or JAVA_HOME if set).
# Usage: ./run.sh [run folder or run.txt]   (default: runs/daily_haude)
# The results are written to output_<yyyyMMdd_HHmmss>/ next to run.txt.
set -e
cd "$(dirname "$0")"
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
mkdir -p bin
${JAVA_BIN}javac -encoding UTF-8 -d bin src/thud/simpel/*.java
${JAVA_BIN}java -cp bin thud.simpel.SimpelModel "${1:-runs/daily_haude}"
