package thud.simpel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All inputs of one run except the forcing, read and checked once: run.txt (lines "key = value", '#' starts
 * a comment) and the parameter tables it points to.
 *
 * run.txt names the forcing file and the parameters folder (paths relative to run.txt), chooses the soil, the
 * land use and the ET method, and holds the site settings and the initial state. Any value of the chosen
 * rows of the parameter tables can be overridden in run.txt with "table.column = value", e.g.
 * "soil.field_capacity = 30" or "land_use.max_lai = 3". The overrides are applied before the tables are
 * checked, so they are checked like the table values.
 *
 * The parameters folder holds:
 *   soil.txt       soil properties, one row per soil (see SoilType)
 *   land_use.txt   land use, phenology and surface properties, one row per land use (see LandUse)
 *   pm.txt         surface conductance parameters G1 ... Kmax per land use (only read for et_method = PM)
 *   haude.txt      monthly Haude coefficients per land use (only read for et_method = HAUDE)
 *
 * Unknown keys and columns stop the run (with a suggestion for typos), as do values outside their plausible
 * range and inconsistent values (wilting point below start of reduction (HAUDE, ET0) below field capacity, initial soil
 * water at most field capacity, root depth above zero).
 */
public class ModelParameters
{
	public static final String FILE_NAME = "run.txt";

	// run.txt keys
	public static final String FORCING = "forcing";
	public static final String PARAMETERS = "parameters";
	public static final String SOIL = "soil";
	public static final String LAND_USE = "land_use";
	public static final String ET_METHOD = "et_method";
	public static final String HEMISPHERE = "hemisphere";
	public static final String LAND_USE_FRACTION = "land_use_fraction";
	public static final String INIT_SOIL = "init_soil";
	public static final String INIT_LITTER = "init_litter";
	public static final String ELEVATION = "elevation";
	public static final String MEASUREMENT_HEIGHT = "measurement_height";
	static final List<String> KEYS = Arrays.asList(FORCING, PARAMETERS, SOIL, LAND_USE, ET_METHOD, HEMISPHERE,
			LAND_USE_FRACTION, INIT_SOIL, INIT_LITTER, ELEVATION, MEASUREMENT_HEIGHT);

	// ET methods
	public static final String HAUDE = "HAUDE";
	public static final String PM = "PM";
	public static final String ET0 = "ET0";

	// tables in the parameters folder; their name is also the prefix of an override in run.txt
	public static final String PM_FILE_NAME = "pm.txt";
	static final List<String> PM_COLUMNS = Arrays.asList("G1", "G2", "G3", "G4", "G5", "G6", "TH", "TL", "Kmax");
	static final String SOIL_TABLE = "soil";
	static final String LAND_USE_TABLE = "land_use";
	static final String PM_TABLE = "pm";
	static final String HAUDE_TABLE = "haude";
	static final List<String> TABLES = Arrays.asList(SOIL_TABLE, LAND_USE_TABLE, PM_TABLE, HAUDE_TABLE);

	/** Land uses renamed in land_use.txt, with their new name. */
	static final Map<String, String> RENAMED_LAND_USES = new LinkedHashMap<String, String>();
	static
	{
		RENAMED_LAND_USES.put("beech", "forest_deciduous");
		RENAMED_LAND_USES.put("spruce", "forest_evergreen");
		RENAMED_LAND_USES.put("corn", "crop_maize");
		RENAMED_LAND_USES.put("sugar_beet", "crop_sugarbeet");
		RENAMED_LAND_USES.put("winter_wheat", "crop_winterwheat");
		RENAMED_LAND_USES.put("irrigated_grass", "grassland_irrigated");
	}

	public final Path runFile;
	public final Path forcingFile;
	public final Path parametersDirectory;

	// what is simulated
	public final String soilName;
	public final String landUse;
	public final String etMethod;                  // HAUDE, PM or ET0
	// site
	public final boolean southernHemisphere;       // hemisphere = S shifts the seasonal tables by half a year
	public final double landUseFraction;           // share covered by the land use; the gap (1 - fraction) is bare soil
	public final double elevation;                 // m, PM only, for the air pressure without a PRES column
	public final double measurementHeight;         // m, PM only, height of wind and temperature
	// initial state
	public final double initialSoilPercent;        // vol-%
	public final double initialLitter;             // mm

	public final SoilType soil;
	public final LandUse landUseParameters;
	public final HaudeCoefficients haude;          // null unless et_method = HAUDE
	private final LookupTable pmTable;             // null unless et_method = PM

	// soil values, copied from the soil for the model
	public final double fieldCapacityPercent;
	public final double wiltingPointPercent;
	public final double startOfReductionPercent;  // NaN if not given (allowed for the Penman-Monteith method)
	public final double gluglaCoefficient;
	public final double infiltrationFraction;

	/** Overrides from run.txt ("table.column" -> value), for the log. */
	public final Map<String, String> overrides = new LinkedHashMap<String, String>();
	/** Notes for the run log: defaults used and values that have no effect. */
	public final List<String> notes = new ArrayList<String>();

	private final Map<String, String> values;

	private ModelParameters(Path runFile, Map<String, String> values) throws IOException
	{
		this.runFile = runFile;
		this.values = values;
		Map<String, Map<String, String>> tableOverrides = new LinkedHashMap<String, Map<String, String>>();
		for (String table : TABLES) tableOverrides.put(table, new LinkedHashMap<String, String>());
		for (Map.Entry<String, String> entry : values.entrySet())
		{
			String key = entry.getKey();
			int dot = key.indexOf('.');
			if (dot > 0)
			{
				String table = key.substring(0, dot);
				if (!TABLES.contains(table))
				{
					throw new IllegalArgumentException(FILE_NAME + ": '" + key + "' overrides an unknown table '" + table
							+ "'" + suggestion(table, TABLES) + "; tables: " + TABLES);
				}
				tableOverrides.get(table).put(key.substring(dot + 1), entry.getValue());
				overrides.put(key, entry.getValue());
			}
			else if (!KEYS.contains(key))
			{
				throw new IllegalArgumentException("Unknown key '" + key + "' in " + FILE_NAME + suggestion(key, KEYS)
						+ "; keys: " + KEYS + ", or table.column to override a parameter");
			}
		}

		Path directory = runFile.toAbsolutePath().getParent();
		forcingFile = directory.resolve(requiredText(FORCING)).normalize();
		parametersDirectory = directory.resolve(requiredText(PARAMETERS)).normalize();
		soilName = values.getOrDefault(SOIL, SoilType.DEFAULT);
		landUse = requiredText(LAND_USE);
		etMethod = values.getOrDefault(ET_METHOD, HAUDE).toUpperCase();
		if (!etMethod.equals(HAUDE) && !etMethod.equals(PM) && !etMethod.equals(ET0))
		{
			throw new IllegalArgumentException(FILE_NAME + ": unknown et_method '" + values.get(ET_METHOD) + "', use "
					+ HAUDE + ", " + PM + " or " + ET0 + " (ET0 column of the forcing)");
		}
		boolean penmanMonteith = etMethod.equals(PM);
		String hemisphere = values.getOrDefault(HEMISPHERE, "N").toUpperCase();
		if (!hemisphere.equals("N") && !hemisphere.equals("S"))
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + HEMISPHERE + " must be N or S, got '" + values.get(HEMISPHERE) + "'");
		}
		southernHemisphere = hemisphere.equals("S");
		landUseFraction = optional(LAND_USE_FRACTION, 1., 0., 1.);
		elevation = optional(ELEVATION, 0., -500., 9000.);
		measurementHeight = optional(MEASUREMENT_HEIGHT, 2., 0.1, 500.);
		initialLitter = optional(INIT_LITTER, 0., 0., 1000.);
		if (!penmanMonteith)
		{
			for (String key : new String[] {ELEVATION, MEASUREMENT_HEIGHT})
			{
				if (values.containsKey(key)) notes.add("Note: " + key + " is only used by et_method = PM");
			}
		}

		// soil
		LookupTable soilTable = table(SoilType.FILE_NAME, SoilType.COLUMNS);
		applyOverrides(soilTable, soilName, tableOverrides.get(SOIL_TABLE));
		soil = new SoilType(soilTable, soilName);
		fieldCapacityPercent = soil.fieldCapacityPercent;
		wiltingPointPercent = soil.wiltingPointPercent;
		startOfReductionPercent = soil.startOfReductionPercent;
		gluglaCoefficient = soil.gluglaCoefficient;
		infiltrationFraction = soil.infiltrationFraction;
		if (!penmanMonteith)
		{
			soil.checkStartOfReduction(etMethod);
		}
		if (!Double.isNaN(startOfReductionPercent) && penmanMonteith)
		{
			notes.add("Note: " + SoilType.START_OF_REDUCTION + " is not used by et_method = PM (the surface conductance limits ET as the soil dries)");
		}
		initialSoilPercent = optional(INIT_SOIL, fieldCapacityPercent, 0., 100.);
		if (!values.containsKey(INIT_SOIL))
		{
			notes.add("Note: " + INIT_SOIL + " not given, the soil starts at field capacity (" + fieldCapacityPercent + " vol-%)");
		}
		if (initialSoilPercent > fieldCapacityPercent)
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + INIT_SOIL + " (" + initialSoilPercent
					+ ") must be at most the field capacity of soil '" + soilName + "' (" + fieldCapacityPercent + ")");
		}

		// land use
		LookupTable landUseTable = table(LandUse.FILE_NAME, LandUse.COLUMNS);
		if (!landUseTable.hasRow(landUse) && RENAMED_LAND_USES.containsKey(landUse))
		{
			throw new IllegalArgumentException("Land use '" + landUse + "' has been renamed to '" + RENAMED_LAND_USES.get(landUse)
					+ "'; change land_use in " + FILE_NAME);
		}
		applyOverrides(landUseTable, landUse, tableOverrides.get(LAND_USE_TABLE));
		landUseParameters = new LandUse(landUseTable, landUse, penmanMonteith);
		notes.addAll(landUseParameters.notes);

		// ET method tables
		pmTable = penmanMonteith ? table(PM_FILE_NAME, PM_COLUMNS) : null;
		if (pmTable != null)
		{
			if (pmTable.hasRow(landUse) || !tableOverrides.get(PM_TABLE).isEmpty())
			{
				applyOverrides(pmTable, landUse, tableOverrides.get(PM_TABLE));
			}
			else if (landUseParameters.maximumLai() > 0.)
			{
				throw new IllegalArgumentException("Land use '" + landUse + "' is not in " + PM_FILE_NAME + " (needed for et_method = PM "
						+ "because it has leaves); available: " + pmTable.rows.keySet());
			}
		}
		else
		{
			noEffect(tableOverrides.get(PM_TABLE), PM_TABLE, PM);
		}
		if (etMethod.equals(HAUDE))
		{
			LookupTable haudeTable = table(HaudeCoefficients.FILE_NAME, HaudeCoefficients.MONTHS);
			applyOverrides(haudeTable, landUse, tableOverrides.get(HAUDE_TABLE));
			haude = new HaudeCoefficients(haudeTable, landUse);
		}
		else
		{
			noEffect(tableOverrides.get(HAUDE_TABLE), HAUDE_TABLE, HAUDE);
			haude = null;
		}
	}

	/** Reads run.txt and the parameter tables it points to, and checks them. */
	public static ModelParameters read(Path runFile) throws IOException
	{
		if (!Files.isRegularFile(runFile))
		{
			throw new IllegalArgumentException(runFile + " not found");
		}
		Map<String, String> values = new LinkedHashMap<String, String>();
		List<String> lines = Files.readAllLines(runFile, StandardCharsets.UTF_8);
		for (int n = 0; n < lines.size(); n++)
		{
			String line = lines.get(n);
			int comment = line.indexOf('#');
			if (comment >= 0) line = line.substring(0, comment);
			line = line.trim();
			if (line.isEmpty()) continue;
			int equals = line.indexOf('=');
			if (equals <= 0)
			{
				throw new IllegalArgumentException(FILE_NAME + " line " + (n + 1) + " should be 'key = value': " + line
						+ (line.contains(";") ? " (the old 'key;value;description' lines of Soil_physics.txt are replaced by "
								+ "run.txt and the tables of the parameters folder, see readme.md)" : ""));
			}
			String key = line.substring(0, equals).trim();
			String value = line.substring(equals + 1).trim();
			if (value.isEmpty())
			{
				throw new IllegalArgumentException(FILE_NAME + " line " + (n + 1) + ": no value for '" + key + "'");
			}
			if (values.containsKey(key))
			{
				throw new IllegalArgumentException(FILE_NAME + " has the key '" + key + "' twice");
			}
			values.put(key, value);
		}
		return new ModelParameters(runFile, values);
	}

	private LookupTable table(String fileName, List<String> columns) throws IOException
	{
		return LookupTable.read(parametersDirectory.resolve(fileName), columns);
	}

	private static void applyOverrides(LookupTable table, String row, Map<String, String> tableOverrides)
	{
		for (Map.Entry<String, String> entry : tableOverrides.entrySet())
		{
			table.override(row, entry.getKey(), entry.getValue());
		}
	}

	private static void noEffect(Map<String, String> tableOverrides, String table, String method)
	{
		if (!tableOverrides.isEmpty())
		{
			throw new IllegalArgumentException(FILE_NAME + ": the overrides of " + table + "." + tableOverrides.keySet()
					+ " have no effect, " + table + " parameters are only used by et_method = " + method);
		}
	}

	/** The Penman-Monteith module with the surface of the land use and soil, and the conductance parameters of pm.txt. */
	public PenmanMonteithEvaporation penmanMonteith()
	{
		PenmanMonteithEvaporation pm = new PenmanMonteithEvaporation();
		pm.albedo = landUseParameters.albedo;
		pm.surfaceEmissivity = landUseParameters.emissivity;
		pm.vegetationHeight = landUseParameters.vegetationHeight;
		pm.soilSurfaceConductance = soil.soilSurfaceConductance;
		pm.measurementHeight = measurementHeight;
		if (pm.measurementHeight <= 0.67 * pm.vegetationHeight)
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + MEASUREMENT_HEIGHT + " (" + pm.measurementHeight + " m) must be above "
					+ "the displacement height 0.67 x veg_height (" + 0.67 * pm.vegetationHeight + " m)");
		}
		if (pmTable != null && pmTable.hasRow(landUse))
		{
			pm.maxConductanceG1 = pmValue("G1");
			pm.kdownShapeG2 = pmValue("G2");
			pm.humiditySlopeG3 = pmValue("G3");
			pm.humidityThresholdG4 = pmValue("G4");
			pm.optimumTemperatureG5 = pmValue("G5");
			pm.soilMoistureShapeG6 = pmValue("G6");
			pm.upperTemperatureTH = pmValue("TH");
			pm.lowerTemperatureTL = pmValue("TL");
			pm.maxKdown = pmValue("Kmax");
		}
		return pm;
	}

	private double pmValue(String column)
	{
		return pmTable.required(landUse, column, -Double.MAX_VALUE, Double.MAX_VALUE);
	}

	public boolean has(String key)
	{
		return values.containsKey(key);
	}

	/** Optional number with a default; checked against the range when present. */
	double optional(String key, double defaultValue, double min, double max)
	{
		if (!values.containsKey(key)) return defaultValue;
		double value;
		try
		{
			value = Double.parseDouble(values.get(key));
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + key + " = '" + values.get(key) + "' is not a number");
		}
		if (value < min || value > max)
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + key + " = " + value + " is outside " + min + " to " + max);
		}
		return value;
	}

	String requiredText(String key)
	{
		if (!values.containsKey(key))
		{
			throw new IllegalArgumentException(FILE_NAME + " is missing the key '" + key + "'");
		}
		return values.get(key);
	}

	/** " (did you mean 'x'?)" for a close match among the candidates, otherwise "". */
	static String suggestion(String key, Collection<String> candidates)
	{
		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (String known : candidates)
		{
			int distance = editDistance(key.toLowerCase(), known.toLowerCase());
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = known;
			}
		}
		return bestDistance <= 3 ? " (did you mean '" + best + "'?)" : "";
	}

	static int editDistance(String a, String b)
	{
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) previous[j] = j;
		for (int i = 1; i <= a.length(); i++)
		{
			current[0] = i;
			for (int j = 1; j <= b.length(); j++)
			{
				int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
				current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}

	/** Root depth in cm, from the land use. */
	public double rootDepth() { return landUseParameters.rootDepth; }

	// Derived soil values in mm (vol-% x root depth in cm / 10)
	public double fieldCapacity() { return fieldCapacityPercent * rootDepth() / 10.; }
	public double wiltingPoint() { return wiltingPointPercent * rootDepth() / 10.; }
	public double startOfReduction() { return startOfReductionPercent * rootDepth() / 10.; }
	public double initialSoilWater() { return initialSoilPercent * rootDepth() / 10.; }
	public double soilDepthMm() { return rootDepth() * 10.; }
	public double gluglaLambda() { return gluglaCoefficient / soilDepthMm() / soilDepthMm(); }
}
