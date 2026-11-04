package thud.simpel;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * One-at-a-time sensitivity of the water fluxes: each parameter is tested at its lower limit, midpoint to its
 * default, default, midpoint to its upper limit, and upper limit (all other parameters at their base value).
 * Each test run is the run.txt of the input with one override (e.g. "soil.field_capacity = 30") added.
 *
 * Usage: java thud.simpel.SensitivityRun <run.txt, or the folder that contains it>
 *
 * Writes output_<yyyyMMdd_HHmmss>/sensitivity_oat.csv next to run.txt: for each run the sums over the run of
 * the potential ET (ETP_Input, ETP_dry and its parts), the actual ET terms, seepage and runoff, the final soil
 * storage, and the change against the base run in %. Prints a ranking by the largest change of ETaTotal,
 * Seepage and Runoff_total, and flags a mean potential ET outside 0 - 10 mm/day. Parameters without effect for
 * the run (PM parameters with another ET method, canopy parameters without leaves, litter drying without a
 * litter layer) are left out.
 */
public class SensitivityRun
{
	static final String[] LEVELS = { "minimum", "midpoint_low", "default", "midpoint_high", "maximum" };
	static final String[] OUTPUTS = { "ETP_Input", "ETP_dry", "ETP_dry_lu", "ETP_dry_gap", "ETaTotal", "I-Leaf", "I-Litter",
			"ETa", "Seepage", "Surface_runoff", "Runoff_total" };
	static final String FINAL_STORAGE = "Storage";
	/** Sets both LAI limits of the land use (a constant LAI); the tested value is max_lai. */
	static final String LAI = "land_use.lai";
	/** Litter drying factor used when a litter capacity above 0 is tested on a land use without litter layer. */
	static final double DEFAULT_LITTER_REDUCTION = 3.;

	/** A parameter of run.txt (key) or of a parameter table (table.column, set as an override in run.txt). */
	static class Parameter
	{
		final String name;
		final ToDoubleFunction<ModelParameters> base;
		final Predicate<ModelParameters> used;
		final double min;
		final double max;

		Parameter(String name, ToDoubleFunction<ModelParameters> base, Predicate<ModelParameters> used, double min, double max)
		{
			this.name = name;
			this.base = base;
			this.used = used;
			this.min = min;
			this.max = max;
		}
	}

	static boolean pm(ModelParameters p) { return p.etMethod.equals(ModelParameters.PM); }
	static boolean canopy(ModelParameters p) { return p.landUseParameters.maximumLai() > 0.; }

	static List<Parameter> parameters()
	{
		Predicate<ModelParameters> always = p -> true;
		Predicate<ModelParameters> pmOnly = SensitivityRun::pm;
		Predicate<ModelParameters> pmCanopy = p -> pm(p) && canopy(p);
		List<Parameter> list = new ArrayList<Parameter>();
		// soil
		list.add(new Parameter("land_use.root_depth", ModelParameters::rootDepth, always, 1., 10000.));
		list.add(new Parameter("soil.field_capacity", p -> p.fieldCapacityPercent, always, 0., 100.));
		list.add(new Parameter("soil.wilting_point", p -> p.wiltingPointPercent, always, 0., 100.));
		list.add(new Parameter("soil.start_of_reduction", p -> p.startOfReductionPercent, p -> !pm(p), 0., 100.));
		list.add(new Parameter("init_soil", p -> p.initialSoilPercent, always, 0., 100.));
		list.add(new Parameter("soil.glugla_c", p -> p.gluglaCoefficient, always, 0., 1e6));
		list.add(new Parameter("soil.infiltration_fraction", p -> p.infiltrationFraction, always, 0., 1.));
		list.add(new Parameter("soil.soil_surface_conductance", p -> p.soil.soilSurfaceConductance, pmOnly, 0., 50.));
		// land use and canopy
		list.add(new Parameter("land_use_fraction", p -> p.landUseFraction, always, 0., 1.));
		list.add(new Parameter("land_use.icept_cap_per_lai", p -> p.landUseParameters.layerThickness, SensitivityRun::canopy, 0., 10.));
		list.add(new Parameter("land_use.icept_drain_max", p -> p.landUseParameters.maxDrainageRate, SensitivityRun::canopy, 0., 1000.));
		list.add(new Parameter("land_use.icept_drain_b", p -> p.landUseParameters.drainageCoefficientB, SensitivityRun::canopy, 0., 100.));
		list.add(new Parameter("init_litter", p -> p.initialLitter, always, 0., 1000.));
		list.add(new Parameter("land_use.litter_reduction", p -> p.landUseParameters.litterReductionFactor,
				p -> p.landUseParameters.litterCapacity > 0., 0.001, 1000.));
		list.add(new Parameter(LAI, p -> p.landUseParameters.maximumLai(), always, 0., 20.));
		list.add(new Parameter("land_use.direct_runoff", p -> p.landUseParameters.directRunoffFactor, always, 0., 100.));
		list.add(new Parameter("land_use.litter_cap", p -> p.landUseParameters.litterCapacity, always, 0., 1000.));
		list.add(new Parameter("land_use.degree_day", p -> p.landUseParameters.degreeDay, always, 0., 20.));
		// Penman-Monteith
		list.add(new Parameter("land_use.albedo", p -> p.landUseParameters.albedo, pmOnly, 0., 1.));
		list.add(new Parameter("land_use.emissivity", p -> p.landUseParameters.emissivity, pmOnly, 0.5, 1.));
		list.add(new Parameter("land_use.veg_height", p -> p.landUseParameters.vegetationHeight, pmOnly, 0.001, 100.));
		list.add(new Parameter("measurement_height", p -> p.measurementHeight, pmOnly, 0.1, 500.));
		list.add(new Parameter("elevation", p -> p.elevation, pmOnly, -500., 9000.));
		list.add(new Parameter("pm.G1", p -> p.penmanMonteith().maxConductanceG1, pmCanopy, 0., 1e3));
		list.add(new Parameter("pm.G2", p -> p.penmanMonteith().kdownShapeG2, pmCanopy, 0., 1e5));
		list.add(new Parameter("pm.G3", p -> p.penmanMonteith().humiditySlopeG3, pmCanopy, 0., 1e3));
		list.add(new Parameter("pm.G4", p -> p.penmanMonteith().humidityThresholdG4, pmCanopy, 0., 1e3));
		list.add(new Parameter("pm.G5", p -> p.penmanMonteith().optimumTemperatureG5, pmCanopy, -50., 50.));
		list.add(new Parameter("pm.G6", p -> p.penmanMonteith().soilMoistureShapeG6, pmCanopy, 0., 1e3));
		list.add(new Parameter("pm.TH", p -> p.penmanMonteith().upperTemperatureTH, pmCanopy, -50., 70.));
		list.add(new Parameter("pm.TL", p -> p.penmanMonteith().lowerTemperatureTL, pmCanopy, -50., 70.));
		list.add(new Parameter("pm.Kmax", p -> p.penmanMonteith().maxKdown, pmCanopy, 1., 2000.));
		return list;
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length != 1)
		{
			System.err.println("Usage: java thud.simpel.SensitivityRun <run.txt, or the folder that contains it>");
			System.exit(1);
		}
		Path runFile = runFile(Paths.get(args[0]));
		LocalDateTime start = LocalDateTime.now();

		ModelParameters parameters = ModelParameters.read(runFile);
		System.out.println("One-at-a-time sensitivity of " + runFile + " (soil " + parameters.soilName + ", land use "
				+ parameters.landUse + ", ET method " + parameters.etMethod + "), five levels per parameter");

		Map<String, Double> base = run(runFile, null, Double.NaN, parameters);
		double days = base.get("days");
		double meanEtp = base.get("ETP_Input") / days;
		System.out.println(String.format(Locale.ROOT, "Base run: %.0f days, mean ETP_Input %.2f mm/day, mean ETP_dry %.2f mm/day, "
				+ "precipitation %.1f mm", days, meanEtp, base.get("ETP_dry") / days, base.get("Precipitation")));
		checkEtp("base", meanEtp);
		if (base.get("Precipitation") == 0.)
		{
			System.out.println("Note: no precipitation in this forcing, so interception, litter, infiltration and runoff "
					+ "parameters cannot show an effect.");
		}

		StringBuilder csv = new StringBuilder("parameter\tlevel\tvalue\tnote");
		for (String output : OUTPUTS) csv.append('\t').append(output);
		csv.append("\tStorage_end");
		for (String output : OUTPUTS) csv.append('\t').append(output).append("_change_%");
		csv.append("\tStorage_end_change_%\n");
		appendRow(csv, "base", "reference", Double.NaN, "", base, base);

		Map<String, Double> largestChange = new LinkedHashMap<String, Double>();
		for (Parameter parameter : parameters())
		{
			if (!parameter.used.test(parameters))
			{
				System.out.println("Skipped " + parameter.name + " (no effect in this run)");
				continue;
			}
			double defaultValue = parameter.base.applyAsDouble(parameters);
			double[] bounds = parameterBounds(parameter, parameters);
			if (defaultValue < bounds[0] || defaultValue > bounds[1])
			{
				throw new IllegalArgumentException(parameter.name + " default value " + defaultValue
						+ " is outside its valid sensitivity range [" + bounds[0] + ", " + bounds[1] + "]");
			}
			double[] values = sensitivityValues(bounds[0], defaultValue, bounds[1]);
			for (int i = 0; i < values.length; i++)
			{
				double value = values[i];
				Map<String, Double> result = run(runFile, parameter, value, parameters);
				String note = notes(parameter, value, parameters);
				appendRow(csv, parameter.name, LEVELS[i], value, note, result, base);
				checkEtp(parameter.name + " " + LEVELS[i], result.get("ETP_Input") / days);
				double change = 0.;
				for (String output : new String[] { "ETaTotal", "Seepage", "Runoff_total" })
				{
					double percent = percentChange(result.get(output), base.get(output));
					if (!Double.isNaN(percent)) change = Math.max(change, Math.abs(percent));
				}
				largestChange.merge(parameter.name, change, Math::max);
			}
		}

		Path outputDirectory = SimpelModel.outputDirectory(runFile.toAbsolutePath().getParent(), start);
		Path output = outputDirectory.resolve("sensitivity_oat.csv");
		Files.createDirectories(output.getParent());
		Files.write(output, csv.toString().getBytes(StandardCharsets.UTF_8));

		System.out.println("\nRanking by the largest change of ETaTotal, Seepage or Runoff_total (%):");
		largestChange.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.forEach(e -> System.out.println(String.format(Locale.ROOT, "  %-28s %8.2f", e.getKey(), e.getValue())));
		System.out.println("\nTable written to " + output);
	}

	static double[] sensitivityValues(double min, double defaultValue, double max)
	{
		if (min > defaultValue || defaultValue > max)
		{
			throw new IllegalArgumentException("Default parameter value must be within its valid sensitivity limits");
		}
		return new double[] { min, (min + defaultValue) / 2., defaultValue, (defaultValue + max) / 2., max };
	}

	static void checkEtp(String run, double meanEtpPerDay)
	{
		if (meanEtpPerDay < 0. || meanEtpPerDay > 10.)
		{
			System.out.println(String.format(Locale.ROOT, "  CHECK ETP: %s has a mean ETP_Input of %.2f mm/day (outside 0 - 10)",
					run, meanEtpPerDay));
		}
	}

	static double[] parameterBounds(Parameter parameter, ModelParameters p)
	{
		double min = parameter.min;
		double max = parameter.max;
		// start_of_reduction is only checked against the other soil values where it is used (not with PM)
		double startOfReduction = pm(p) ? Double.NaN : p.startOfReductionPercent;
		if (parameter.name.equals("soil.field_capacity"))
		{
			min = Math.max(min, Math.nextUp(p.wiltingPointPercent));
			if (!Double.isNaN(startOfReduction)) min = Math.max(min, startOfReduction);
		}
		else if (parameter.name.equals("soil.wilting_point"))
		{
			max = Math.min(max, Math.nextDown(p.fieldCapacityPercent));
			if (!Double.isNaN(startOfReduction)) max = Math.min(max, Math.nextDown(startOfReduction));
		}
		else if (parameter.name.equals("soil.start_of_reduction"))
		{
			min = Math.max(min, Math.nextUp(p.wiltingPointPercent));
			max = Math.min(max, p.fieldCapacityPercent);
		}
		else if (parameter.name.equals("init_soil"))
		{
			max = Math.min(max, p.fieldCapacityPercent);
		}
		else if (parameter.name.equals("measurement_height"))
		{
			min = Math.max(min, Math.nextUp(0.67 * p.landUseParameters.vegetationHeight));
		}
		else if (parameter.name.equals("land_use.veg_height"))
		{
			max = Math.min(max, Math.nextDown(p.measurementHeight / 0.67));
		}
		else if (parameter.name.startsWith("pm."))
		{
			PenmanMonteithEvaporation pm = p.penmanMonteith();
			if (parameter.name.equals("pm.G5"))
			{
				min = Math.max(min, Math.nextUp(pm.lowerTemperatureTL));
				max = Math.min(max, Math.nextDown(pm.upperTemperatureTH));
			}
			else if (parameter.name.equals("pm.TH"))
			{
				min = Math.max(min, Math.nextUp(Math.max(pm.lowerTemperatureTL, pm.optimumTemperatureG5)));
			}
			else if (parameter.name.equals("pm.TL"))
			{
				max = Math.min(max, Math.nextDown(Math.min(pm.upperTemperatureTH, pm.optimumTemperatureG5)));
			}
		}
		return new double[] { min, max };
	}

	/** The overrides of one test run: the parameter, plus the adjustments that keep the inputs consistent. */
	static Map<String, String> overrides(Parameter parameter, double value, ModelParameters p)
	{
		Map<String, String> overrides = new LinkedHashMap<String, String>();
		if (parameter.name.equals(LAI))
		{
			overrides.put("land_use.min_lai", String.valueOf(value));
			overrides.put("land_use.max_lai", String.valueOf(value));
		}
		else
		{
			overrides.put(parameter.name, String.valueOf(value));
		}
		// the initial water content must not exceed field capacity
		if (parameter.name.equals("soil.field_capacity") && p.initialSoilPercent > value)
		{
			overrides.put("init_soil", String.valueOf(value));
		}
		// a litter layer needs a drying factor
		if (parameter.name.equals("land_use.litter_cap") && value > 0. && p.landUseParameters.litterCapacity == 0.)
		{
			overrides.put("land_use.litter_reduction", String.valueOf(DEFAULT_LITTER_REDUCTION));
		}
		return overrides;
	}

	static String notes(Parameter parameter, double value, ModelParameters p)
	{
		List<String> notes = new ArrayList<String>();
		for (Map.Entry<String, String> entry : overrides(parameter, value, p).entrySet())
		{
			if (!entry.getKey().equals(parameter.name) && !parameter.name.equals(LAI))
			{
				notes.add(entry.getKey() + " set to " + entry.getValue());
			}
		}
		return String.join("; ", notes);
	}

	static Path runFile(Path path)
	{
		return Files.isDirectory(path) ? path.resolve(ModelParameters.FILE_NAME) : path;
	}

	/** Runs the model with run.txt plus the overrides of one parameter value (none for the base run) and sums the outputs. */
	static Map<String, Double> run(Path runFile, Parameter parameter, double value, ModelParameters p) throws IOException
	{
		Path dir = Files.createTempDirectory("simpel-sensitivity");
		try
		{
			Map<String, String> changes = new LinkedHashMap<String, String>();
			// absolute paths, so that the copy of run.txt finds the inputs
			changes.put(ModelParameters.FORCING, p.forcingFile.toAbsolutePath().toString());
			changes.put(ModelParameters.PARAMETERS, p.parametersDirectory.toAbsolutePath().toString());
			if (parameter != null) changes.putAll(overrides(parameter, value, p));
			writeRunFile(runFile, dir.resolve(ModelParameters.FILE_NAME), changes);
			return runInTemporaryDirectory(dir);
		}
		finally
		{
			try (Stream<Path> paths = Files.walk(dir))
			{
				for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
				{
					Files.delete(path);
				}
			}
		}
	}

	/** Copies run.txt and sets keys: the line of a key is replaced, a key that is not there is added. */
	static void writeRunFile(Path from, Path to, Map<String, String> changes) throws IOException
	{
		Map<String, String> remaining = new LinkedHashMap<String, String>(changes);
		List<String> lines = new ArrayList<String>();
		for (String line : Files.readAllLines(from, StandardCharsets.UTF_8))
		{
			String content = line.contains("#") ? line.substring(0, line.indexOf('#')) : line;
			int equals = content.indexOf('=');
			String key = equals > 0 ? content.substring(0, equals).trim() : null;
			if (key != null && remaining.containsKey(key))
			{
				line = key + " = " + remaining.remove(key) + "   # sensitivity run";
			}
			lines.add(line);
		}
		for (Map.Entry<String, String> entry : remaining.entrySet())
		{
			lines.add(entry.getKey() + " = " + entry.getValue() + "   # sensitivity run");
		}
		Files.write(to, lines, StandardCharsets.UTF_8);
	}

	private static Map<String, Double> runInTemporaryDirectory(Path dir) throws IOException
	{
		SimpelModel model = new SimpelModel();
		model.setRunFile(dir.toString());
		PrintStream console = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		try
		{
			model.process();
		}
		finally
		{
			System.setOut(console);
		}

		Path outputFile;
		try (Stream<Path> outputDirectories = Files.list(dir))
		{
			outputFile = outputDirectories.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().startsWith(SimpelModel.OUTPUT_FOLDER + "_"))
					.map(path -> path.resolve(SimpelModel.OUTPUT_FILE))
					.filter(Files::isRegularFile)
					.findFirst()
					.orElseThrow(() -> new IOException("Model results not found in timestamped output folder for " + dir));
		}
		List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
		List<String> header = Arrays.asList(lines.get(0).split("\t"));
		Map<String, Double> sums = new LinkedHashMap<String, Double>();
		Set<String> days = new LinkedHashSet<String>();
		String[] last = null;
		for (String line : lines.subList(1, lines.size()))
		{
			if (line.trim().isEmpty()) continue;
			String[] v = line.split("\t");
			days.add(v[0].substring(0, 10));
			for (String output : OUTPUTS) sums.merge(output, Double.parseDouble(v[header.indexOf(output)]), Double::sum);
			sums.merge("Precipitation", Double.parseDouble(v[header.indexOf("Precipitation")]), Double::sum);
			last = v;
		}
		sums.put(FINAL_STORAGE, Double.parseDouble(last[header.indexOf(FINAL_STORAGE)]));
		sums.put("days", (double) days.size());
		return sums;
	}

	static void appendRow(StringBuilder csv, String name, String level, double value, String note,
			Map<String, Double> result, Map<String, Double> base)
	{
		csv.append(name).append('\t').append(level).append('\t').append(Double.isNaN(value) ? "" : String.valueOf(value))
				.append('\t').append(note);
		for (String output : OUTPUTS) csv.append('\t').append(String.format(Locale.ROOT, "%.4f", result.get(output)));
		csv.append('\t').append(String.format(Locale.ROOT, "%.4f", result.get(FINAL_STORAGE)));
		for (String output : OUTPUTS) csv.append('\t').append(String.format(Locale.ROOT, "%.3f", percentChange(result.get(output), base.get(output))));
		csv.append('\t').append(String.format(Locale.ROOT, "%.3f", percentChange(result.get(FINAL_STORAGE), base.get(FINAL_STORAGE))));
		csv.append('\n');
	}

	static double percentChange(double value, double base)
	{
		if (base == 0.) return value == 0. ? 0. : Double.NaN;
		return 100. * (value - base) / Math.abs(base);
	}
}
