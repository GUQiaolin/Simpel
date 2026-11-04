package thud.simpel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Checks that bad input stops the run with a clear message instead of producing NaN or using a wrong value,
 * and that the run.txt overrides, the land-use components and the ET methods work. Each case copies an example
 * run (run.txt, the parameters folder and the forcing) to a temporary folder, changes one thing and expects
 * an IllegalArgumentException whose message contains a given text, or a run that finishes.
 */
public class InputValidationTest
{
	static int failures = 0;

	static final Path DAILY = Paths.get("runs", "daily_haude");
	static final Path HOURLY_PM = Paths.get("runs", "hourly_pm");

	public static void main(String[] args) throws IOException
	{
		// run.txt
		expectRunError(DAILY, "land_use.root_depth = 0", "must be above 0 cm");
		expectRunError(DAILY, "land_use_fraction = 1.5", "outside 0.0 to 1.0", "land_use_fraction");
		expectRunError(DAILY, "soil.wilting_point = 30", "must be below field_capacity");
		expectRunError(DAILY, "soil.start_of_reduction = 12", "must be above the wilting point");
		expectRunError(DAILY, "init_soil = 30", "must be at most the field capacity", "init_soil");
		expectRunError(DAILY, "soil.infiltration_fraction = 1.5", "outside 0.0 to 1.0");
		expectRunError(DAILY, "et_methd = PM", "did you mean 'et_method'");
		expectRunError(DAILY, "soil.feld_capacity = 30", "did you mean 'field_capacity'");
		expectRunError(DAILY, "soils.field_capacity = 30", "unknown table 'soils'");
		expectRunError(DAILY, "pm.G1 = 10", "only used by et_method = PM");
		expectRunError(HOURLY_PM, "haude.Jan = 0.2", "only used by et_method = HAUDE");
		expectRunError(DAILY, "land_use = meadow", "not in land_use.txt", "land_use");
		expectRunError(DAILY, "soil = clay", "not in soil.txt", "soil");
		expectRunError(DAILY, "hemisphere = South", "must be N or S", "hemisphere");
		expectRunError(DAILY, "init_litter = 0", "twice");
		expectRunError(DAILY, "Root Depth;100;old line", "should be 'key = value'");
		expectRunError(DAILY, "et_method = ET0", "needs an ET0 column", "et_method");
		expectOldLayoutError();

		// land_use.txt: components decide what is needed
		expectRunError(DAILY, "land_use.min_lai = 2.5", "min_lai <= max_lai");
		expectRunError(DAILY, "land_use.litter_cap = 1", "litter_reduction must be given");
		expectRunError(HOURLY_PM, "land_use = grassland", "albedo must be given", "land_use");
		Path noPmRow = copyRun(HOURLY_PM);
		appendLine(noPmRow.resolve("parameters/land_use.txt"),
				"pasture 1.0 3.0 - - - - 100 3.0 15 0 - 0.35 3.7 2.88 0.2 0.98 0.3");
		setRunValue(noPmRow, "land_use = pasture", "land_use");
		expectError(noPmRow, "not in pm.txt", "PM land use with leaves but no pm.txt row");
		expectRuns(DAILY, "land_use = urban", "land_use", "urban (1 cm root depth)");

		// a land use without canopy and litter: the interception values in the row are ignored with a note
		Path sealed = copyRun(DAILY);
		appendLine(sealed.resolve("parameters/land_use.txt"),
				"sealed 0.0 0.0 - - - - 1 5.00 100 0.00 - 0.35 3.7 2.88 - - -");
		appendLine(sealed.resolve("parameters/haude.txt"),
				"sealed 0.10 0.11 0.11 0.15 0.15 0.14 0.13 0.13 0.12 0.11 0.10 0.10");
		setRunValue(sealed, "land_use = sealed", "land_use");
		expectRunsIn(sealed, "sealed land use");
		expectLogContains(sealed, "icept_cap_per_lai, icept_drain_b, icept_drain_max of land use 'sealed' ignored: max_lai = 0");

		// overrides change the result and are written to the log; run.txt is copied to the output
		Path override = copyRun(DAILY);
		appendLine(override.resolve("run.txt"), "soil.field_capacity = 30");
		expectRunsIn(override, "override of field_capacity");
		expectLogContains(override, "Override from run.txt: soil.field_capacity = 30");
		Path base = copyRun(DAILY);
		expectRunsIn(base, "daily example");
		if (columnSum(override, "Storage") == columnSum(base, "Storage")) fail("soil.field_capacity override had no effect");
		if (!Files.isRegularFile(outputDirectory(base).resolve(ModelParameters.FILE_NAME))) fail("run.txt not copied to the output");

		// default soil (luvisol) and default initial state (field capacity)
		Path luvisol = copyRun(DAILY);
		replaceInFile(luvisol.resolve("run.txt"), line -> line.startsWith("soil ") || line.startsWith("init_soil") ? null : line);
		expectRunsIn(luvisol, "default soil");
		expectLogContains(luvisol, "Soil: luvisol");
		expectLogContains(luvisol, "init_soil not given, the soil starts at field capacity (36.0 vol-%)");

		// forcing
		expectForcingError(DAILY, line -> line.startsWith("05.01.1995") ? line.replace("90.1", "140") : line,
				"outside the plausible range");
		expectForcingError(DAILY, line -> line.startsWith("05.01.1995") ? line.replace("90.1", "NA") : line,
				"missing value");
		expectForcingError(DAILY, line -> line.startsWith("05.01.1995") ? line.replace("90.1", "abc") : line,
				"is not a number");
		expectForcingError(DAILY, line -> line.startsWith("06.01.1995") ? null : line, "not evenly spaced");
		// ISO dates give the same result as dd.mm.yyyy
		Path iso = copyRun(DAILY);
		replaceInFile(iso.resolve("forcing.txt"), line -> line.matches("\\d\\d\\.\\d\\d\\.\\d{4}\\s.*")
				? line.substring(6, 10) + "-" + line.substring(3, 5) + "-" + line.substring(0, 2) + line.substring(10) : line);
		expectRunsIn(iso, "ISO dates");
		if (columnSum(iso, "ETaTotal") != columnSum(base, "ETaTotal")) fail("ISO dates changed the result");

		// ET methods
		Path pmWithEt0 = copyRun(HOURLY_PM);
		addEt0Column(pmWithEt0, "0.1");
		expectRunsIn(pmWithEt0, "PM with an ET0 column");
		Path et0 = copyRun(DAILY);
		addEt0Column(et0, "1.0");
		setRunValue(et0, "et_method = ET0", "et_method");
		Files.delete(et0.resolve("parameters/haude.txt"));
		expectRunsIn(et0, "ET0 from the forcing without haude.txt");
		double steps = Files.readAllLines(outputDirectory(et0).resolve(SimpelModel.OUTPUT_FILE), StandardCharsets.UTF_8).stream()
				.skip(1).filter(line -> !line.trim().isEmpty()).count();
		if (Math.abs(columnSum(et0, "ETP_Input") - steps) > 1e-9) fail("ET0: expected ETP_Input = ET0 column (1 mm), got sum "
				+ columnSum(et0, "ETP_Input") + " over " + steps + " steps");

		// PM, bare land use (max_lai 0, no pm.txt row): the soil dries by evaporation from the bare-soil gap
		Path bare = copyRun(HOURLY_PM);
		appendLine(bare.resolve("parameters/land_use.txt"), "bare_soil 0.0 0.0 - - - - 100 3.00 0 0.00 - - - - 0.18 0.98 0.12");
		setRunValue(bare, "land_use = bare_soil", "land_use");
		expectRunsIn(bare, "PM on bare soil");
		double bareEvaporation = columnSum(bare, "ETa");
		if (!(bareEvaporation > 1.)) fail("PM on bare soil: expected soil evaporation from the gap, got ETa sum " + bareEvaporation);

		// The run log also records an error that stops the run
		Path broken = copyRun(DAILY);
		appendLine(broken.resolve("run.txt"), "land_use.root_depth = 0");
		expectError(broken, "must be above 0 cm", "log of a failed run");
		expectLogContains(broken, "Run stopped: java.lang.IllegalArgumentException: Root depth must be above 0 cm");
		expectLogContains(broken, "Run file:");

		if (failures > 0)
		{
			System.out.println("InputValidationTest FAILED: " + failures);
			System.exit(1);
		}
		System.out.println("InputValidationTest OK");
	}

	/** Adds (or, with replacedKey, replaces) a line of run.txt and expects an error containing the text. */
	static void expectRunError(Path run, String line, String expected, String... replacedKey) throws IOException
	{
		Path dir = copyRun(run);
		if (replacedKey.length > 0) setRunValue(dir, line, replacedKey[0]);
		else appendLine(dir.resolve("run.txt"), line);
		expectError(dir, expected, "run.txt line '" + line + "'");
	}

	/** Replaces the line of a key in run.txt (or adds it) and expects a run without NaN. */
	static void expectRuns(Path run, String line, String key, String description) throws IOException
	{
		Path dir = copyRun(run);
		setRunValue(dir, line, key);
		expectRunsIn(dir, description);
		for (String output : Files.readAllLines(outputDirectory(dir).resolve(SimpelModel.OUTPUT_FILE), StandardCharsets.UTF_8))
		{
			if (output.contains("NaN"))
			{
				fail(description + ": NaN in output");
				return;
			}
		}
	}

	static void expectOldLayoutError() throws IOException
	{
		Path dir = Files.createTempDirectory("simpel-validation");
		Files.write(dir.resolve("Soil_physics.txt"), "Field Capacity %;25;\n".getBytes(StandardCharsets.UTF_8));
		try
		{
			new SimpelModel().setRunFile(dir.toString());
			fail("old input layout: expected an error");
		}
		catch (IllegalArgumentException e)
		{
			if (!e.getMessage().contains("old input layout")) fail("old input layout: got " + e.getMessage());
		}
	}

	static void addEt0Column(Path dir, String value) throws IOException
	{
		replaceInFile(dir.resolve("forcing.txt"), line -> line.trim().isEmpty() ? line
				: line + (line.startsWith("Date") ? "\tET0" : "\t" + value));
	}

	static void expectForcingError(Path run, UnaryOperator<String> change, String expected) throws IOException
	{
		Path dir = copyRun(run);
		replaceInFile(dir.resolve("forcing.txt"), change);
		expectError(dir, expected, "changed forcing");
	}

	/** Sum of one output column of the latest run. */
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
		try (Stream<Path> paths = Files.list(dir))
		{
			return paths.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().matches(SimpelModel.OUTPUT_FOLDER + "_\\d{8}_\\d{6}"))
					.max(Comparator.comparing(path -> path.getFileName().toString()))
					.orElseThrow(() -> new IOException("No timestamped output folder in " + dir));
		}
	}

	static SimpelModel model(Path dir)
	{
		SimpelModel model = new SimpelModel();
		model.setRunFile(dir.toString());
		return model;
	}

	/** The run in this directory must finish without an error. */
	static void expectRunsIn(Path dir, String description) throws IOException
	{
		try
		{
			model(dir).process();
			if (!Files.isRegularFile(outputDirectory(dir).resolve("SIMPEL_log.txt")))
			{
				fail(description + ": expected SIMPEL_log.txt in " + outputDirectory(dir));
			}
		}
		catch (RuntimeException e)
		{
			fail(description + ": " + e.getMessage());
		}
	}

	static void expectError(Path dir, String expected, String description)
	{
		try
		{
			model(dir).process();
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

	/**
	 * Copies an example run to a temporary folder: run.txt (with forcing = forcing.txt and parameters = parameters),
	 * a copy of its forcing file and of the parameters folder.
	 */
	static Path copyRun(Path run) throws IOException
	{
		Path dir = Files.createTempDirectory("simpel-validation");
		ModelParameters parameters = ModelParameters.read(run.resolve(ModelParameters.FILE_NAME));
		Files.copy(parameters.forcingFile, dir.resolve("forcing.txt"));
		Files.createDirectories(dir.resolve("parameters"));
		try (Stream<Path> files = Files.list(parameters.parametersDirectory))
		{
			for (Path file : (Iterable<Path>) files::iterator)
			{
				if (file.getFileName().toString().endsWith(".txt")) Files.copy(file, dir.resolve("parameters").resolve(file.getFileName()));
			}
		}
		Files.copy(run.resolve(ModelParameters.FILE_NAME), dir.resolve(ModelParameters.FILE_NAME));
		setRunValue(dir, "forcing = forcing.txt", "forcing");
		setRunValue(dir, "parameters = parameters", "parameters");
		return dir;
	}

	/** Replaces the line of a key in run.txt, or adds the line if the key is not there. */
	static void setRunValue(Path dir, String line, String key) throws IOException
	{
		Path file = dir.resolve(ModelParameters.FILE_NAME);
		boolean[] found = { false };
		replaceInFile(file, existing -> {
			if (existing.matches("\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=.*"))
			{
				found[0] = true;
				return line;
			}
			return existing;
		});
		if (!found[0]) appendLine(file, line);
	}

	static void appendLine(Path file, String line) throws IOException
	{
		Files.write(file, ("\n" + line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
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
