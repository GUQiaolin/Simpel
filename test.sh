#!/bin/sh
# Compile the model and tests, run the unit tests, then run the example runs and compare them with the stored
# reference output (including the water balance at every step).
set -e
cd "$(dirname "$0")"
rm -rf bin && mkdir -p bin
javac -encoding UTF-8 -d bin src/thud/simpel/*.java test/thud/simpel/*.java
for t in bin/thud/simpel/*Test.class; do
	name=$(basename "$t" .class)
	[ "$name" = "RegressionTest" ] && continue
	java -cp bin thud.simpel.$name
done
check() { # run folder, reference file
	java -cp bin thud.simpel.SimpelModel "runs/$1" > /dev/null
	output=$(find "runs/$1" -maxdepth 1 -type d -name 'output_[0-9]*' -print | sort | tail -n 1)
	java -cp bin thud.simpel.RegressionTest "$output/SIMPLE_bucket_model.csv" "test/reference/$2" 1e-9
}
check daily_haude daily_example.csv
check hourly_pm hourly_pm_example.csv
check daily_pm daily_pm_example.csv
