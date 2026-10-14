package thud.simpel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Compares a SIMPEL output file against a stored reference output and checks
 * that the water balance closes at every time step.
 *
 * Usage: java thud.simpel.RegressionTest <output.csv> <reference.csv> [tolerance]
 *
 * Exits with status 1 if any value differs by more than the tolerance or if the
 * per-step water balance error exceeds the tolerance.
 */
public class RegressionTest
{
	public static void main(String[] args) throws IOException
	{
		String outputFile = args[0];
		String referenceFile = args[1];
		double tolerance = args.length > 2 ? Double.parseDouble(args[2]) : 1e-9;

		List<String> outputLines = readNonEmptyLines(outputFile);
		List<String> referenceLines = readNonEmptyLines(referenceFile);

		int failures = 0;
		failures += compareWithReference(outputLines, referenceLines, tolerance);
		failures += checkStepWaterBalance(outputLines, tolerance);

		if (failures > 0)
		{
			System.out.println("FAILED: " + failures + " problem(s)");
			System.exit(1);
		}
		System.out.println("OK: output matches reference and water balance closes at every step");
	}

	static int compareWithReference(List<String> outputLines, List<String> referenceLines, double tolerance)
	{
		String[] referenceHeader = referenceLines.get(0).split("\t");
		String[] outputHeader = outputLines.get(0).split("\t");
		if (outputLines.size() != referenceLines.size())
		{
			System.out.println("Row count differs: " + outputLines.size() + " vs reference " + referenceLines.size());
			return 1;
		}
		int failures = 0;
		for (int row = 1; row < referenceLines.size(); row++)
		{
			String[] reference = referenceLines.get(row).split("\t");
			String[] output = outputLines.get(row).split("\t");
			for (int col = 1; col < referenceHeader.length; col++)
			{
				int outputCol = indexOf(outputHeader, referenceHeader[col]);
				if (outputCol < 0)
				{
					System.out.println("Missing column in output: " + referenceHeader[col]);
					return failures + 1;
				}
				double expected = Double.parseDouble(reference[col]);
				double actual = Double.parseDouble(output[outputCol]);
				if (Math.abs(expected - actual) > tolerance)
				{
					if (failures < 10)
					{
						System.out.println("Row " + row + " (" + reference[0] + ") column " + referenceHeader[col]
								+ ": expected " + expected + " got " + actual);
					}
					failures++;
				}
			}
		}
		return failures;
	}

	/** Change in all stores must equal precipitation minus evapotranspiration minus runoff (rows after the first). */
	static int checkStepWaterBalance(List<String> outputLines, double tolerance)
	{
		String[] header = outputLines.get(0).split("\t");
		int precipitation = indexOf(header, "Precipitation");
		int snow = indexOf(header, "Snow_Water_Equi");
		int interception = indexOf(header, "I-Bal");
		int litter = indexOf(header, "Content");
		int soil = indexOf(header, "Storage");
		int evapotranspiration = indexOf(header, "ETaTotal");
		int runoff = indexOf(header, "Runoff_total");

		int failures = 0;
		double maxError = 0;
		double previousStores = Double.NaN;
		for (int row = 1; row < outputLines.size(); row++)
		{
			String[] v = outputLines.get(row).split("\t");
			if (v.length < header.length) continue;
			double stores = d(v, snow) + d(v, interception) + d(v, litter) + d(v, soil);
			if (!Double.isNaN(previousStores))
			{
				double error = d(v, precipitation) - d(v, evapotranspiration) - d(v, runoff) - (stores - previousStores);
				maxError = Math.max(maxError, Math.abs(error));
				if (Math.abs(error) > Math.max(tolerance, 1e-9))
				{
					if (failures < 10)
					{
						System.out.println("Water balance error at " + v[0] + ": " + error + " mm");
					}
					failures++;
				}
			}
			previousStores = stores;
		}
		System.out.println("Max per-step water balance error: " + maxError + " mm");
		return failures;
	}

	static List<String> readNonEmptyLines(String file) throws IOException
	{
		List<String> lines = Files.readAllLines(Paths.get(file));
		lines.removeIf(line -> line.trim().isEmpty());
		return lines;
	}

	static double d(String[] values, int col)
	{
		return Double.parseDouble(values[col]);
	}

	static int indexOf(String[] header, String name)
	{
		for (int i = 0; i < header.length; i++)
		{
			if (header[i].trim().equals(name)) return i;
		}
		return -1;
	}
}
