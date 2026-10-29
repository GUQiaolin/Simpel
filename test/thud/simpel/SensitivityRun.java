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
import java.util.stream.Stream;

/**
 * One-at-a-time sensitivity of the water fluxes: each parameter is tested at its lower limit, midpoint to its
 * default, default, midpoint to its upper limit, and upper limit (all other parameters at their base value).
 *
 * Usage: java thud.simpel.SensitivityRun <input directory>
 *
 * Writes <input directory>/output_<yyyyMMdd_HHmmss>/sensitivity_oat.csv: for each run the sums over the run of
 * the potential ET (ETP_Input, ETP_dry and its parts), the actual ET terms, seepage and runoff, the final soil
 * storage, and the change against the base run in %. Prints a ranking by the largest change of ETaTotal,
 * Seepage and Runoff_total, and flags a mean potential ET outside 0 - 10 mm/day.
 */
public class SensitivityRun
{
	static final String[] LEVELS = { "minimum", "midpoint_low", "default", "midpoint_high", "maximum" };
	static final String[] OUTPUTS = { "ETP_Input", "ETP_dry", "ETP_dry_lu", "ETP_dry_gap", "ETaTotal", "I-Leaf", "I-Litter",
			"ETa", "Seepage", "Surface_runoff", "Runoff_total" };
	static final String FINAL_STORAGE = "Storage";

	/** A parameter in Soil_physics.txt (key) or in the land-use row of Land_Use_Parameters.txt (column). */
	static class Parameter
	{
		final String name;
		final boolean landUseColumn;
		final double defaultValue; // used when the key is absent from Soil_physics.txt
		final double min;
		final double max;

		Parameter(String name, boolean landUseColumn, double defaultValue, double min, double max)
		{
			this.name = name;
			this.landUseColumn = landUseColumn;
			this.defaultValue = defaultValue;
			this.min = min;
			this.max = max;
		}
	}

	static List<Parameter> parameters()
	{
		PenmanMonteithEvaporation pm = new PenmanMonteithEvaporation();
		List<Parameter> list = new ArrayList<Parameter>();
		// soil
		list.add(new Parameter("Root Depth", false, Double.NaN, 1., 10000.));
		list.add(new Parameter("Field Capacity %", false, Double.NaN, 0., 100.));
		list.add(new Parameter("Permanent Wilting Point %", false, Double.NaN, 0., 100.));
		list.add(new Parameter("Init-Value Soil %", false, Double.NaN, 0., 100.));
		list.add(new Parameter("Glugla coeff.", false, Double.NaN, 0., 1e6));
		list.add(new Parameter("Infiltration fraction", false, SimpelModel.INFILTRATION_FRACTION_PER_DAY, 0., 1.));
		// land use and canopy
		list.add(new Parameter("Land Use Fraction", false, Double.NaN, 0., 1.));
		list.add(new Parameter("Layer Thickness", false, Double.NaN, 0., 10.));
		list.add(new Parameter("Max. Drainage Rate", false, Double.NaN, 0., 1000.));
		list.add(new Parameter("Drainage Coeff. b", false, Double.NaN, 0., 100.));
		list.add(new Parameter("Init-Value Litter", false, Double.NaN, 0., 1000.));
		list.add(new Parameter("Litter Reduction factor", false, Double.NaN, 0.001, 1000.));
		list.add(new Parameter("LAI (MinLAI and MaxLAI)", true, Double.NaN, 0., 20.));
		list.add(new Parameter("DirectRunoff", true, Double.NaN, 0., 100.));
		list.add(new Parameter("LitterCap", true, Double.NaN, 0., 1000.));
		list.add(new Parameter("DegreeDay", true, Double.NaN, 0., 20.));
		// Penman-Monteith
		list.add(new Parameter("Albedo", false, pm.albedo, 0., 1.));
		list.add(new Parameter("Surface emissivity", false, pm.surfaceEmissivity, 0.5, 1.));
		list.add(new Parameter("Vegetation height", false, pm.vegetationHeight, 0.001, 100.));
		list.add(new Parameter("Measurement height", false, pm.measurementHeight, 0.1, 500.));
		list.add(new Parameter("Elevation", false, 0., -500., 9000.));
		list.add(new Parameter("Soil surface conductance", false, pm.soilSurfaceConductance, 0., 50.));
		list.add(new Parameter("G1", false, pm.maxConductanceG1, 0., 1e3));
		list.add(new Parameter("G2", false, pm.kdownShapeG2, 0., 1e5));
		list.add(new Parameter("G3", false, pm.humiditySlopeG3, 0., 1e3));
		list.add(new Parameter("G4", false, pm.humidityThresholdG4, 0., 1e3));
		list.add(new Parameter("G5", false, pm.optimumTemperatureG5, -50., 50.));
		list.add(new Parameter("G6", false, pm.soilMoistureShapeG6, 0., 1e3));
		list.add(new Parameter("TH", false, pm.upperTemperatureTH, -50., 70.));
		list.add(new Parameter("TL", false, pm.lowerTemperatureTL, -50., 70.));
		list.add(new Parameter("Kmax", false, pm.maxKdown, 1., 2000.));
		return list;
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length != 1)
		{
			System.err.println("Usage: java thud.simpel.SensitivityRun <input directory>");
			System.exit(1);
		}
		Path input = Paths.get(args[0]);
		LocalDateTime start = LocalDateTime.now();

		Map<String, String> soil = readSoil(input);
		String landUse = soil.get("Land use");
		System.out.println("One-at-a-time sensitivity of " + input + " (land use " + landUse + ", ET method "
				+ soil.getOrDefault("ET method", "HAUDE") + "), five levels per parameter");

		Map<String, Double> base = run(input, null, Double.NaN, landUse);
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
			double defaultValue = baseValue(parameter, soil, input, landUse);
			double[] bounds = parameterBounds(parameter, soil, input, landUse);
			if (defaultValue < bounds[0] || defaultValue > bounds[1])
			{
				throw new IllegalArgumentException(parameter.name + " default value " + defaultValue
						+ " is outside its valid sensitivity range [" + bounds[0] + ", " + bounds[1] + "]");
			}
			double[] values = sensitivityValues(bounds[0], defaultValue, bounds[1]);
			for (int i = 0; i < values.length; i++)
			{
				double value = values[i];
				Map<String, Double> result = run(input, parameter, value, landUse);
				String note = result.containsKey("note")
						? "Init-Value Soil % lowered to the new field capacity" : "";
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

		Path outputDirectory = SimpelModel.outputDirectory(input, start);
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

	static double baseValue(Parameter parameter, Map<String, String> soil, Path input, String landUse) throws IOException
	{
		if (parameter.landUseColumn)
		{
			Map<String, String> row = landUseRow(input, landUse);
			String column = parameter.name.startsWith("LAI") ? "MaxLAI" : parameter.name;
			return Double.parseDouble(row.get(column));
		}
		if (parameter.name.equals("Root Depth"))
		{
			String rootDepth = landUseRow(input, landUse).getOrDefault("RootDepth", "-");
			if (!rootDepth.equals("-")) return Double.parseDouble(rootDepth);
		}
		String value = soil.get(parameter.name);
		return value == null ? parameter.defaultValue : Double.parseDouble(value);
	}

	static double[] parameterBounds(Parameter parameter, Map<String, String> soil, Path input, String landUse)
			throws IOException
	{
		double min = parameter.min;
		double max = parameter.max;
		if (parameter.name.equals("Field Capacity %"))
		{
			min = Math.max(min, Math.nextUp(Double.parseDouble(soil.get("Permanent Wilting Point %"))));
			String startOfReduction = soil.get("Start of Reduction %");
			if (startOfReduction != null) min = Math.max(min, Double.parseDouble(startOfReduction));
		}
		else if (parameter.name.equals("Permanent Wilting Point %"))
		{
			max = Math.min(max, Math.nextDown(Double.parseDouble(soil.get("Field Capacity %"))));
			String startOfReduction = soil.get("Start of Reduction %");
			if (startOfReduction != null)
			{
				max = Math.min(max, Math.nextDown(Double.parseDouble(startOfReduction)));
			}
		}
		else if (parameter.name.equals("Init-Value Soil %"))
		{
			max = Math.min(max, Double.parseDouble(soil.get("Field Capacity %")));
		}
		else if (parameter.name.equals("Measurement height"))
		{
			double vegetationHeight = Double.parseDouble(soil.getOrDefault("Vegetation height", "0.12"));
			min = Math.max(min, Math.nextUp(0.67 * vegetationHeight));
		}
		else if (parameter.name.equals("Vegetation height"))
		{
			double measurementHeight = Double.parseDouble(soil.getOrDefault("Measurement height", "2.0"));
			max = Math.min(max, Math.nextDown(measurementHeight / 0.67));
		}
		else if (parameter.name.equals("G5"))
		{
			double lowerTemperature = Double.parseDouble(soil.getOrDefault("TL", "0."));
			double upperTemperature = Double.parseDouble(soil.getOrDefault("TH", "40."));
			min = Math.max(min, Math.nextUp(lowerTemperature));
			max = Math.min(max, Math.nextDown(upperTemperature));
		}
		else if (parameter.name.equals("TH"))
		{
			double lowerTemperature = Double.parseDouble(soil.getOrDefault("TL", "0."));
			double optimumTemperature = Double.parseDouble(soil.getOrDefault("G5", "11.0764"));
			min = Math.max(min, Math.nextUp(Math.max(lowerTemperature, optimumTemperature)));
		}
		else if (parameter.name.equals("TL"))
		{
			double upperTemperature = Double.parseDouble(soil.getOrDefault("TH", "40."));
			double optimumTemperature = Double.parseDouble(soil.getOrDefault("G5", "11.0764"));
			max = Math.min(max, Math.nextDown(Math.min(upperTemperature, optimumTemperature)));
		}
		return new double[] { min, max };
	}

	/** Runs the model on a copy of the input with one parameter changed (none for the base run) and sums the outputs. */
	static Map<String, Double> run(Path input, Parameter parameter, double value, String landUse) throws IOException
	{
		Path dir = Files.createTempDirectory("simpel-sensitivity");
		try
		{
			return runInTemporaryDirectory(input, parameter, value, landUse, dir);
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

	private static Map<String, Double> runInTemporaryDirectory(Path input, Parameter parameter, double value,
			String landUse, Path dir) throws IOException
	{
		try (Stream<Path> files = Files.list(input))
		{
			for (Path file : (Iterable<Path>) files::iterator)
			{
				if (file.getFileName().toString().endsWith(".txt")) Files.copy(file, dir.resolve(file.getFileName()));
			}
		}
		boolean initLowered = false;
		if (parameter != null)
		{
			if (parameter.landUseColumn || (parameter.name.equals("Root Depth")
					&& !landUseRow(dir, landUse).getOrDefault("RootDepth", "-").equals("-")))
			{
				setLandUseValue(dir, landUse, parameter, value);
			}
			else
			{
				setSoilValue(dir, parameter.name, value);
				if (parameter.name.equals("Field Capacity %"))
				{
					// the initial water content must not exceed field capacity
					double init = Double.parseDouble(readSoil(dir).get("Init-Value Soil %"));
					if (init > value)
					{
						setSoilValue(dir, "Init-Value Soil %", value);
						initLowered = true;
					}
				}
			}
		}

		SimpelModel model = new SimpelModel();
		model.setDirectory(dir.toString());
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
		if (initLowered) sums.put("note", 1.);
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

	static Map<String, String> readSoil(Path dir) throws IOException
	{
		Map<String, String> values = new LinkedHashMap<String, String>();
		for (String line : Files.readAllLines(dir.resolve("Soil_physics.txt"), StandardCharsets.UTF_8))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			String[] fields = trimmed.split(";");
			if (fields.length >= 2) values.put(fields[0].trim(), fields[1].trim());
		}
		return values;
	}

	static void setSoilValue(Path dir, String key, double value) throws IOException
	{
		Path file = dir.resolve("Soil_physics.txt");
		List<String> lines = new ArrayList<String>();
		boolean found = false;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
		{
			if (line.trim().startsWith(key + ";"))
			{
				line = key + ";" + value + ";sensitivity run";
				found = true;
			}
			lines.add(line);
		}
		if (!found) lines.add(key + ";" + value + ";sensitivity run");
		Files.write(file, lines, StandardCharsets.UTF_8);
	}

	static Map<String, String> landUseRow(Path dir, String landUse) throws IOException
	{
		String[] header = null;
		for (String line : Files.readAllLines(dir.resolve(LandUse.FILE_NAME), StandardCharsets.UTF_8))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			String[] fields = trimmed.split("\\s+");
			if (header == null)
			{
				header = fields;
				continue;
			}
			if (fields[0].equals(landUse))
			{
				Map<String, String> row = new LinkedHashMap<String, String>();
				for (int i = 1; i < header.length; i++) row.put(header[i], fields[i]);
				return row;
			}
		}
		throw new IllegalArgumentException("land use " + landUse + " not found");
	}

	/** Changes one column of the land-use row; this land use uses one shared value for both LAI limits. */
	static void setLandUseValue(Path dir, String landUse, Parameter parameter, double value) throws IOException
	{
		Path file = dir.resolve(LandUse.FILE_NAME);
		Map<String, String> row = landUseRow(dir, landUse);
		if (parameter.name.startsWith("LAI"))
		{
			row.put("MinLAI", String.valueOf(value));
			row.put("MaxLAI", String.valueOf(value));
		}
		else
		{
			row.put(parameter.name.equals("Root Depth") ? "RootDepth" : parameter.name, String.valueOf(value));
		}
		List<String> lines = new ArrayList<String>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
		{
			String trimmed = line.trim();
			if (!trimmed.startsWith("#") && trimmed.split("\\s+")[0].equals(landUse))
			{
				line = landUse + " " + String.join(" ", row.values());
			}
			lines.add(line);
		}
		Files.write(file, lines, StandardCharsets.UTF_8);
	}
}
