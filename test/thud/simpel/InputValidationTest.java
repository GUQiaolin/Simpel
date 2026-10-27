package thud.simpel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Checks that bad input stops the run with a clear message instead of producing NaN or using a wrong value.
 * Each case copies the daily example, breaks one thing and expects an IllegalArgumentException whose message
 * contains a given text.
 */
public class InputValidationTest
{
	static int failures = 0;

	public static void main(String[] args) throws IOException
	{
		Path example = Paths.get("example");

		expectSoilError(example, "Root Depth;100", "Root Depth;0", "must be above 0 cm");
		expectSoilError(example, "Land Use Fraction;0.75", "Land Use Fraction;1.5", "outside 0.0 to 1.0");
		expectSoilError(example, "Permanent Wilting Point %;12", "Permanent Wilting Point %;30", "must be below");
		expectSoilError(example, "Start of Reduction %;20", "Start of Reduction %;12", "must be above the wilting point");
		expectSoilError(example, "Init-Value Soil %;20", "Init-Value Soil %;30", "must be at most 'Field Capacity %'");
		expectSoilAppendError(example, "albedo;0.2;typo of Albedo", "did you mean 'Albedo'");
		expectSoilAppendError(example, "Infiltration fraction;1.5;too large", "outside 0.0 to 1.0");
		expectSoilAppendError(example, "Root Depth;50;duplicate", "twice");
		expectSoilAppendError(example, "Minimum LAI;1;old key", "moved to Land_Use_Parameters.txt");
		expectSoilAppendError(example, "OHM a1;0.32;old key", "storage heat term (OHM) was removed");
		expectSoilError(example, "Land Use Fraction;0.75", "Vegetation Fraction;0.75", "renamed to 'Land Use Fraction'");
		expectSoilError(example, "Hemisphere;N", "Hemisphere;South", "must be N or S");
		expectSoilError(example, "Land use;grassland", "Land use;meadow", "not in Land_Use_Parameters.txt");
		expectTableError(example, "grassland     1.00    2.00", "grassland     2.50    2.00", "MinLAI <= MaxLAI");
		expectTableError(example, "urban         0.10    0.50    5.00       50            0.00       1 ",
				"urban         0.10    0.50    5.00       50            0.00       0 ", "Root depth must be above 0", "urban");
		expectRuns(example, "urban");
		expectRunsWithExtraLandUse(example, "sealed        0.0     0.0     5.00       100           0.00       1          -             -           -                -", "sealed");

		expectForcingError(example, line -> line.startsWith("05.01.1995") ? line.replace("90.1", "140") : line,
				"outside the plausible range");
		expectForcingError(example, line -> line.startsWith("05.01.1995") ? line.replace("90.1", "NA") : line,
				"missing value");
		expectForcingError(example, line -> line.startsWith("05.01.1995") ? line.replace("90.1", "abc") : line,
				"is not a number");
		expectForcingError(example, line -> line.startsWith("06.01.1995") ? null : line, "not evenly spaced");

		// PM run whose forcing also has an ET0 column: ET0 is ignored instead of stopping the run
		Path pmWithEt0 = copyExample(Paths.get("example_hourly"));
		replaceInFile(pmWithEt0.resolve("Global_Input.txt"), line -> line.trim().isEmpty() ? line : line + (line.startsWith("Date") ? "\tET0" : "\t0.1"));
		expectRunsIn(pmWithEt0, "PM with an ET0 column");
		// ET0 from file does not need Haude_Coefficients.txt
		Path et0WithoutHaude = copyExample(example);
		replaceInFile(et0WithoutHaude.resolve("Global_Input.txt"), line -> line.trim().isEmpty() ? line : line + (line.startsWith("Date") ? "\tET0" : "\t1.0"));
		Files.delete(et0WithoutHaude.resolve("Haude_Coefficients.txt"));
		expectRunsIn(et0WithoutHaude, "ET0 from file without Haude_Coefficients.txt");

		// PM, bare land use (MaxLAI 0): the soil now dries by evaporation from the bare-soil gap, not only by seepage
		Path bare = copyExample(Paths.get("example_hourly"));
		Files.write(bare.resolve("Land_Use_Parameters.txt"), ("bare_soil 0.0 0.0 3.00 0 0.00 - - - - -\n").getBytes(StandardCharsets.UTF_8),
				java.nio.file.StandardOpenOption.APPEND);
		replaceInFile(bare.resolve("Soil_physics.txt"), line -> line.startsWith("Land use;") ? "Land use;bare_soil;" : line);
		expectRunsIn(bare, "PM on bare soil");
		double bareEvaporation = columnSum(bare, "ETa");
		if (!(bareEvaporation > 1.)) fail("PM on bare soil: expected soil evaporation from the gap, got ETa sum " + bareEvaporation);

		// The run log also records an error that stops the run
		Path broken = copyExample(example);
		replaceInFile(broken.resolve("Soil_physics.txt"), line -> line.startsWith("Root Depth;100") ? "Root Depth;0;" : line);
		expectError(broken, "must be above 0 cm", "log of a failed run");
		expectLogContains(broken, "Run stopped: java.lang.IllegalArgumentException: Root depth must be above 0 cm");
		expectLogContains(broken, "Input directory:");

		if (failures > 0)
		{
			System.out.println("InputValidationTest FAILED: " + failures);
			System.exit(1);
		}
		System.out.println("InputValidationTest OK");
	}

	static void expectSoilError(Path example, String from, String to, String expected) throws IOException
	{
		if (to == null) return;
		Path dir = copyExample(example);
		replaceInFile(dir.resolve("Soil_physics.txt"), line -> line.startsWith(from) ? line.replace(from, to) : line);
		expectError(dir, expected, from + " -> " + to);
	}

	static void expectTableError(Path example, String from, String to, String expected) throws IOException
	{
		expectTableError(example, from, to, expected, null);
	}

	/** Changes Land_Use_Parameters.txt (and optionally the land use of the run) and expects an error. */
	static void expectTableError(Path example, String from, String to, String expected, String landUse) throws IOException
	{
		Path dir = copyExample(example);
		replaceInFile(dir.resolve("Land_Use_Parameters.txt"), line -> line.startsWith(from) ? line.replace(from, to) : line);
		if (landUse != null)
		{
			replaceInFile(dir.resolve("Soil_physics.txt"), line -> line.startsWith("Land use;") ? "Land use;" + landUse + ";" : line);
		}
		expectError(dir, expected, from + " -> " + to);
	}

	/** The run must finish without NaN for this land use (e.g. urban with its 1 cm root depth). */
	static void expectRuns(Path example, String landUse) throws IOException
	{
		Path dir = copyExample(example);
		replaceInFile(dir.resolve("Soil_physics.txt"), line -> line.startsWith("Land use;") ? "Land use;" + landUse + ";" : line);
		SimpelModel model = new SimpelModel();
		model.setDirectory(dir.toString());
		model.process();
		for (String line : Files.readAllLines(outputDirectory(dir).resolve(SimpelModel.OUTPUT_FILE), StandardCharsets.UTF_8))
		{
			if (line.contains("NaN"))
			{
				fail("land use " + landUse + ": NaN in output line " + line.substring(0, Math.min(40, line.length())));
				return;
			}
		}
	}

	/** Adds a row to Land_Use_Parameters.txt (and a Haude row copied from urban) and expects a run without NaN. */
	static void expectRunsWithExtraLandUse(Path example, String row, String landUse) throws IOException
	{
		Path dir = copyExample(example);
		Files.write(dir.resolve("Land_Use_Parameters.txt"), (row + "\n").getBytes(StandardCharsets.UTF_8),
				java.nio.file.StandardOpenOption.APPEND);
		for (String line : Files.readAllLines(dir.resolve("Haude_Coefficients.txt"), StandardCharsets.UTF_8))
		{
			if (line.startsWith("urban "))
			{
				Files.write(dir.resolve("Haude_Coefficients.txt"), (line.replaceFirst("^urban ", landUse + " ") + "\n")
						.getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);
			}
		}
		replaceInFile(dir.resolve("Soil_physics.txt"), line -> line.startsWith("Land use;") ? "Land use;" + landUse + ";" : line);
		SimpelModel model = new SimpelModel();
		model.setDirectory(dir.toString());
		model.process();
		for (String line : Files.readAllLines(outputDirectory(dir).resolve(SimpelModel.OUTPUT_FILE), StandardCharsets.UTF_8))
		{
			if (line.contains("NaN"))
			{
				fail("land use " + landUse + ": NaN in output");
				return;
			}
		}
	}

	/** Sum of one output column. */
	static double columnSum(Path dir, String column) throws IOException
	{
		List<String> lines = Files.readAllLines(outputDirectory(dir).resolve(SimpelModel.OUTPUT_FILE), StandardCharsets.UTF_8);
		int index = java.util.Arrays.asList(lines.get(0).split("\t")).indexOf(column);
		double sum = 0.;
		for (String line : lines.subList(1, lines.size()))
		{
			if (!line.trim().isEmpty()) sum += Double.parseDouble(line.split("\t")[index]);
		}
		return sum;
	}

	/** The run log in the most recent output folder must contain this text. */
	static void expectLogContains(Path dir, String expected) throws IOException
	{
		Path log = outputDirectory(dir).resolve("SIMPEL_log.txt");
		if (Files.exists(log) && new String(Files.readAllBytes(log), StandardCharsets.UTF_8).contains(expected))
		{
			return;
		}
		fail("no run log in " + dir + " contains '" + expected + "'");
	}

	static Path outputDirectory(Path dir) throws IOException
	{
		try (java.util.stream.Stream<Path> paths = Files.list(dir))
		{
			return paths.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().matches(SimpelModel.OUTPUT_FOLDER + "_\\d{8}_\\d{6}"))
					.max(Comparator.comparing(path -> path.getFileName().toString()))
					.orElseThrow(() -> new IOException("No timestamped output folder in " + dir));
		}
	}

	/** The run in this directory must finish without an error. */
	static void expectRunsIn(Path dir, String description)
	{
		SimpelModel model = new SimpelModel();
		model.setDirectory(dir.toString());
		try
		{
			model.process();
			Path output = outputDirectory(dir);
			if (!Files.isRegularFile(output.resolve("SIMPEL_log.txt")))
			{
				fail(description + ": expected SIMPEL_log.txt in " + output);
			}
		}
		catch (RuntimeException e)
		{
			fail(description + ": " + e.getMessage());
		}
		catch (IOException e)
		{
			fail(description + ": " + e.getMessage());
		}
	}

	static void expectSoilAppendError(Path example, String extraLine, String expected) throws IOException
	{
		Path dir = copyExample(example);
		Files.write(dir.resolve("Soil_physics.txt"), ("\n" + extraLine + "\n").getBytes(StandardCharsets.UTF_8),
				java.nio.file.StandardOpenOption.APPEND);
		expectError(dir, expected, "extra line '" + extraLine + "'");
	}

	static void expectForcingError(Path example, UnaryOperator<String> change, String expected) throws IOException
	{
		Path dir = copyExample(example);
		replaceInFile(dir.resolve("Global_Input.txt"), change);
		expectError(dir, expected, "changed forcing");
	}

	static void expectError(Path dir, String expected, String description)
	{
		SimpelModel model = new SimpelModel();
		model.setDirectory(dir.toString());
		try
		{
			model.process();
			fail(description + ": expected an error containing '" + expected + "', but the run finished");
		}
		catch (IllegalArgumentException e)
		{
			if (!e.getMessage().contains(expected))
			{
				fail(description + ": expected '" + expected + "' in the message, got: " + e.getMessage());
			}
		}
	}

	static Path copyExample(Path example) throws IOException
	{
		Path dir = Files.createTempDirectory("simpel-validation");
		try (java.util.stream.Stream<Path> files = Files.list(example))
		{
			for (Path file : (Iterable<Path>) files::iterator)
			{
				if (file.getFileName().toString().endsWith(".txt"))
				{
					Files.copy(file, dir.resolve(file.getFileName()));
				}
			}
		}
		return dir;
	}

	static void replaceInFile(Path file, UnaryOperator<String> change) throws IOException
	{
		List<String> result = new ArrayList<String>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
		{
			String changed = change.apply(line);
			if (changed != null) result.add(changed);
		}
		Files.write(file, result, StandardCharsets.UTF_8);
	}

	static void fail(String message)
	{
		System.out.println("FAIL " + message);
		failures++;
	}
}
