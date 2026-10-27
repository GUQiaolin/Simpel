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
daily_output=$(find example -maxdepth 1 -type d -name 'output_[0-9]*' -print | sort | tail -n 1)
java -cp bin thud.simpel.RegressionTest "$daily_output/SIMPLE_bucket_model.csv" test/reference/daily_example.csv 1e-9
java -cp bin thud.simpel.SimpelModel example_hourly/ > /dev/null
hourly_output=$(find example_hourly -maxdepth 1 -type d -name 'output_[0-9]*' -print | sort | tail -n 1)
java -cp bin thud.simpel.RegressionTest "$hourly_output/SIMPLE_bucket_model.csv" test/reference/hourly_pm_example.csv 1e-9
java -cp bin thud.simpel.SimpelModel example_daily_pm/ > /dev/null
daily_pm_output=$(find example_daily_pm -maxdepth 1 -type d -name 'output_[0-9]*' -print | sort | tail -n 1)
java -cp bin thud.simpel.RegressionTest "$daily_pm_output/SIMPLE_bucket_model.csv" test/reference/daily_pm_example.csv 1e-9
