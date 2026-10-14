#!/bin/sh
# Compile the model and tests, run the daily example and compare it with the stored reference output.
set -e
cd "$(dirname "$0")"
rm -rf bin && mkdir -p bin
javac -encoding UTF-8 -d bin src/thud/simpel/*.java test/thud/simpel/*.java
for t in bin/thud/simpel/*Test.class; do
	name=$(basename "$t" .class)
	[ "$name" = "RegressionTest" ] && continue
	java -cp bin thud.simpel.$name
done
java -cp bin thud.simpel.SimpelModel example/ > /dev/null
java -cp bin thud.simpel.RegressionTest example/SIMPLE_bucket_model.csv test/reference/daily_example.csv 1e-9
java -cp bin thud.simpel.SimpelModel example_hourly/ > /dev/null
java -cp bin thud.simpel.RegressionTest example_hourly/SIMPLE_bucket_model.csv test/reference/hourly_pm_example.csv 1e-9
java -cp bin thud.simpel.SimpelModel example_daily_pm/ > /dev/null
java -cp bin thud.simpel.RegressionTest example_daily_pm/SIMPLE_bucket_model.csv test/reference/daily_pm_example.csv 1e-9
