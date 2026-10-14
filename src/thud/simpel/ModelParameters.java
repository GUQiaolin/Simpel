package thud.simpel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Soil, vegetation and method parameters from Soil_physics.txt (lines "key;value;description"), read and
 * checked once.
 *
 * Every key the model knows is listed in KNOWN_KEYS. An unknown key (e.g. a typo such as "albedo" for
 * "Albedo") stops the run instead of silently falling back to a default. Values are checked against
 * plausible ranges and against each other (wilting point below start of reduction below field capacity,
 * root depth above zero), so the model cannot run into a division by zero and produce NaN silently.
 *
 * Land-use dependent values (LAI, degree-day factor, direct runoff, litter capacity) are in
 * Land_Use_Parameters.txt (see LandUse); if one of them is still found here, the run stops and says so.
 * Root Depth is required here and can be overridden per land use (RootDepth column), see applyLandUse.
 */
public class ModelParameters
{
	public static final String FIELD_CAPACITY_PERCENT = "Field Capacity %";
	public static final String PERMANENT_WILTING_POINT = "Permanent Wilting Point %";
	public static final String START_OF_REDUCTION_PERCENT = "Start of Reduction %";
	public static final String ROOT_DEPTH = "Root Depth";
	public static final String INIT_VALUE_SOIL_PERCENT = "Init-Value Soil %";
	public static final String LAND_USE = "Land use";
	public static final String VEGETATION_FRACTION = "Vegetation Fraction";
	public static final String LAYER_THICKNESS = "Layer Thickness";
	public static final String DRAINAGE_COEFF_B = "Drainage Coeff. b";
	public static final String MAX_DRAINAGE_RATE = "Max. Drainage Rate";
	public static final String GLUGLA_C = "Glugla coeff.";
	public static final String INIT_VALUE_LITTER = "Init-Value Litter";
	public static final String LITTER_REDUCTION_FACTOR = "Litter Reduction factor";
	public static final String TIMESTEP = "Timestep";
	public static final String ET_METHOD = "ET method";
	public static final String HEMISPHERE = "Hemisphere";

	/** Optional keys of the PM ET method. */
	public static final List<String> ET_KEYS = Arrays.asList("Albedo", "Surface emissivity", "Vegetation height",
			"Measurement height", "Elevation", "G1", "G2", "G3", "G4", "G5", "G6", "TH", "TL", "Kmax");

	public static final String HAUDE = "HAUDE";
	public static final String PM = "PM";

	static final Set<String> KNOWN_KEYS = new LinkedHashSet<String>(Arrays.asList(FIELD_CAPACITY_PERCENT,
			PERMANENT_WILTING_POINT, START_OF_REDUCTION_PERCENT, ROOT_DEPTH, INIT_VALUE_SOIL_PERCENT, LAND_USE,
			VEGETATION_FRACTION, LAYER_THICKNESS, DRAINAGE_COEFF_B, MAX_DRAINAGE_RATE,
			GLUGLA_C, INIT_VALUE_LITTER, LITTER_REDUCTION_FACTOR, TIMESTEP, ET_METHOD, HEMISPHERE));

	/** Keys that moved to Land_Use_Parameters.txt, with their column name there. */
	static final Map<String, String> MOVED_TO_LAND_USE_TABLE = new LinkedHashMap<String, String>();
	static
	{
		MOVED_TO_LAND_USE_TABLE.put("Minimum LAI", "MinLAI");
		MOVED_TO_LAND_USE_TABLE.put("Maximum LAI", "MaxLAI");
		MOVED_TO_LAND_USE_TABLE.put("Direct runoff factor", "DirectRunoff");
		MOVED_TO_LAND_USE_TABLE.put("Cap. Litter", "LitterCap");
	}
	static
	{
		KNOWN_KEYS.addAll(ET_KEYS);
	}

	private final Map<String, String> values;

	// soil (volumetric water contents in vol-%, converted to mm with the root depth)
	public final double fieldCapacityPercent;
	public final double wiltingPointPercent;
	public final double startOfReductionPercent;  // NaN if absent (allowed for the Penman-Monteith method)
	public final double soilRootDepth;             // cm, as given in Soil_physics.txt
	private double rootDepth;                      // cm, after the land-use override (applyLandUse)
	private String rootDepthSource = "Soil_physics.txt";
	public final double initialSoilPercent;
	public final double gluglaCoefficient;
	// vegetation and surface
	public final String landUse;
	public final double vegetationFraction;
	public final double layerThickness;            // mm per unit LAI
	public final double drainageCoefficientB;
	public final double maxDrainageRate;           // mm/day
	public final double initialLitter;             // mm
	public final double litterReductionFactor;
	// method
	public final String etMethod;                  // HAUDE or PM
	// site
	public final boolean southernHemisphere;       // Hemisphere;S shifts the seasonal tables by half a year

	private ModelParameters(Map<String, String> values)
	{
		this.values = values;
		for (String key : values.keySet())
		{
			if (key.startsWith("OHM a"))
			{
				throw new IllegalArgumentException("'" + key + "' is no longer used: the storage heat term (OHM) was removed and "
						+ "the available energy is the net radiation from the forcing; remove the line from Soil_physics.txt");
			}
			if (MOVED_TO_LAND_USE_TABLE.containsKey(key))
			{
				throw new IllegalArgumentException("'" + key + "' is land-use dependent and has moved to "
						+ LandUse.FILE_NAME + " (column " + MOVED_TO_LAND_USE_TABLE.get(key) + "); remove it from Soil_physics.txt");
			}
			if (!KNOWN_KEYS.contains(key))
			{
				throw new IllegalArgumentException("Unknown key '" + key + "' in Soil_physics.txt" + suggestion(key));
			}
		}
		fieldCapacityPercent = required(FIELD_CAPACITY_PERCENT, 0., 100.);
		wiltingPointPercent = required(PERMANENT_WILTING_POINT, 0., 100.);
		startOfReductionPercent = optional(START_OF_REDUCTION_PERCENT, Double.NaN, 0., 100.);
		soilRootDepth = required(ROOT_DEPTH, 0., 10000.);
		rootDepth = soilRootDepth;
		initialSoilPercent = required(INIT_VALUE_SOIL_PERCENT, 0., 100.);
		gluglaCoefficient = required(GLUGLA_C, 0., 1e6);
		landUse = requiredText(LAND_USE);
		vegetationFraction = required(VEGETATION_FRACTION, 0., 1.);
		layerThickness = required(LAYER_THICKNESS, 0., 10.);
		drainageCoefficientB = required(DRAINAGE_COEFF_B, 0., 100.);
		maxDrainageRate = required(MAX_DRAINAGE_RATE, 0., 1000.);
		initialLitter = required(INIT_VALUE_LITTER, 0., 1000.);
		litterReductionFactor = required(LITTER_REDUCTION_FACTOR, 0., 1000.);
		etMethod = values.containsKey(ET_METHOD) ? values.get(ET_METHOD).toUpperCase() : HAUDE;
		if (!etMethod.equals(HAUDE) && !etMethod.equals(PM))
		{
			throw new IllegalArgumentException("Unknown ET method '" + values.get(ET_METHOD) + "', use " + HAUDE + " or " + PM);
		}
		String hemisphere = values.containsKey(HEMISPHERE) ? values.get(HEMISPHERE).toUpperCase() : "N";
		if (!hemisphere.equals("N") && !hemisphere.equals("S"))
		{
			throw new IllegalArgumentException("'" + HEMISPHERE + "' must be N or S, got '" + values.get(HEMISPHERE) + "'");
		}
		southernHemisphere = hemisphere.equals("S");

		if (wiltingPointPercent >= fieldCapacityPercent)
		{
			throw new IllegalArgumentException("'" + PERMANENT_WILTING_POINT + "' (" + wiltingPointPercent
					+ ") must be below '" + FIELD_CAPACITY_PERCENT + "' (" + fieldCapacityPercent + ")");
		}
		if (!Double.isNaN(startOfReductionPercent)
				&& (startOfReductionPercent <= wiltingPointPercent || startOfReductionPercent > fieldCapacityPercent))
		{
			throw new IllegalArgumentException("'" + START_OF_REDUCTION_PERCENT + "' (" + startOfReductionPercent
					+ ") must be above the wilting point (" + wiltingPointPercent + ") and at most field capacity ("
					+ fieldCapacityPercent + ")");
		}
		if (litterReductionFactor <= 0.)
		{
			throw new IllegalArgumentException("'" + LITTER_REDUCTION_FACTOR + "' must be above 0");
		}
	}

	/**
	 * Applies the land use: its RootDepth, if given, overrides Root Depth from Soil_physics.txt. Then checks
	 * that the root depth is above zero (it sets the size of the soil water store; 0 used to make every
	 * result NaN).
	 */
	public void applyLandUse(LandUse landUse)
	{
		if (!Double.isNaN(landUse.rootDepth))
		{
			if (landUse.rootDepth != soilRootDepth)
			{
				System.out.println("Root depth " + landUse.rootDepth + " cm from " + LandUse.FILE_NAME + " (land use "
						+ landUse.name + ") overrides Root Depth " + soilRootDepth + " cm in Soil_physics.txt");
			}
			rootDepth = landUse.rootDepth;
			rootDepthSource = LandUse.FILE_NAME + " (land use " + landUse.name + ")";
		}
		if (rootDepth <= 0.)
		{
			throw new IllegalArgumentException("Root depth must be above 0 cm (from " + rootDepthSource + "). It sets the size "
					+ "of the soil water store, and 0 makes every result NaN. For (nearly) sealed surfaces such as urban, "
					+ "use a small value, e.g. 1 cm.");
		}
	}

	/** Root depth in cm after the land-use override. */
	public double rootDepth() { return rootDepth; }

	/** Reads and checks Soil_physics.txt. */
	public static ModelParameters read(Path file) throws IOException
	{
		Map<String, String> values = new LinkedHashMap<String, String>();
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		for (int n = 0; n < lines.size(); n++)
		{
			String line = lines.get(n).trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] fields = line.split(";");
			if (fields.length < 2)
			{
				throw new IllegalArgumentException("Soil_physics.txt line " + (n + 1) + " should be 'key;value;description': " + line);
			}
			String key = fields[0].trim();
			if (values.containsKey(key))
			{
				throw new IllegalArgumentException("Soil_physics.txt has the key '" + key + "' twice");
			}
			values.put(key, fields[1].trim());
		}
		return new ModelParameters(values);
	}

	public boolean has(String key)
	{
		return values.containsKey(key);
	}

	/** Optional number with a default; checked against the range when present. */
	public double optional(String key, double defaultValue, double min, double max)
	{
		if (!values.containsKey(key)) return defaultValue;
		return number(key, min, max);
	}

	/** Optional number without a range check (for parameters whose range depends on the method). */
	public double optional(String key, double defaultValue)
	{
		return optional(key, defaultValue, -Double.MAX_VALUE, Double.MAX_VALUE);
	}

	double required(String key, double min, double max)
	{
		if (!values.containsKey(key))
		{
			throw new IllegalArgumentException("Soil_physics.txt is missing the key '" + key + "'");
		}
		return number(key, min, max);
	}

	String requiredText(String key)
	{
		if (!values.containsKey(key))
		{
			throw new IllegalArgumentException("Soil_physics.txt is missing the key '" + key + "'");
		}
		return values.get(key);
	}

	double number(String key, double min, double max)
	{
		double value;
		try
		{
			value = Double.parseDouble(values.get(key));
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("Soil_physics.txt: '" + key + "' = '" + values.get(key) + "' is not a number");
		}
		if (value < min || value > max)
		{
			throw new IllegalArgumentException("Soil_physics.txt: '" + key + "' = " + value + " is outside " + min + " to " + max);
		}
		return value;
	}

	static String suggestion(String key)
	{
		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (String known : KNOWN_KEYS)
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

	// Derived soil values in mm (vol-% x root depth in cm / 10)
	public double fieldCapacity() { return fieldCapacityPercent * rootDepth / 10.; }
	public double wiltingPoint() { return wiltingPointPercent * rootDepth / 10.; }
	public double startOfReduction() { return startOfReductionPercent * rootDepth / 10.; }
	public double initialSoilWater() { return initialSoilPercent * rootDepth / 10.; }
	public double soilDepthMm() { return rootDepth * 10.; }
	public double gluglaLambda() { return gluglaCoefficient / soilDepthMm() / soilDepthMm(); }
}
