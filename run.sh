#!/bin/sh
# Compile and run SIMPEL on the example. Uses javac/java from the PATH (or JAVA_HOME if set).
set -e
cd "$(dirname "$0")"
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
mkdir -p bin
${JAVA_BIN}javac -d bin src/thud/simpel/*.java
directory=example/
#readETFromFile=readET
readETFromFile=dontReadET
${JAVA_BIN}java -cp bin thud.simpel.SimpelModel $directory $readETFromFile
