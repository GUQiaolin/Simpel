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
 */
public class ModelParameters
{
	public static final String FIELD_CAPACITY_PERCENT = "Field Capacity %";
	public static final String PERMANENT_WILTING_POINT = "Permanent Wilting Point %";
	public static final String START_OF_REDUCTION_PERCENT = "Start of Reduction %";
	public static final String ROOT_DEPTH = "Root Depth";
	public static final String INIT_VALUE_SOIL_PERCENT = "Init-Value Soil %";
	public static final String LAND_USE = "Land use";
	public static final String MINIMUM_LAI = "Minimum LAI";
	public static final String MAXIMUM_LAI = "Maximum LAI";
	public static final String VEGETATION_FRACTION = "Vegetation Fraction";
	public static final String LAYER_THICKNESS = "Layer Thickness";
	public static final String DRAINAGE_COEFF_B = "Drainage Coeff. b";
	public static final String MAX_DRAINAGE_RATE = "Max. Drainage Rate";
	public static final String DIRECT_RUNOFF_FACTOR = "Direct runoff factor";
	public static final String GLUGLA_C = "Glugla coeff.";
	public static final String CAP_LITTER = "Cap. Litter";
	public static final String INIT_VALUE_LITTER = "Init-Value Litter";
	public static final String LITTER_REDUCTION_FACTOR = "Litter Reduction factor";
	public static final String TIMESTEP = "Timestep";
	public static final String ET_METHOD = "ET method";

	/** Optional keys of the Penman-Monteith (Penman-Monteith) ET method. */
	public static final List<String> ET_KEYS = Arrays.asList("Albedo", "Surface emissivity", "Vegetation height",
			"Measurement height", "Elevation", "OHM a1", "OHM a2", "OHM a3", "G1", "G2", "G3", "G4", "G5", "G6",
			"TH", "TL", "Kmax");

	static final Set<String> KNOWN_KEYS = new LinkedHashSet<String>(Arrays.asList(FIELD_CAPACITY_PERCENT,
			PERMANENT_WILTING_POINT, START_OF_REDUCTION_PERCENT, ROOT_DEPTH, INIT_VALUE_SOIL_PERCENT, LAND_USE,
			MINIMUM_LAI, MAXIMUM_LAI, VEGETATION_FRACTION, LAYER_THICKNESS, DRAINAGE_COEFF_B, MAX_DRAINAGE_RATE,
			DIRECT_RUNOFF_FACTOR, GLUGLA_C, CAP_LITTER, INIT_VALUE_LITTER, LITTER_REDUCTION_FACTOR, TIMESTEP,
			ET_METHOD));
	static
	{
		KNOWN_KEYS.addAll(ET_KEYS);
	}

	private final Map<String, String> values;

	// soil (volumetric water contents in vol-%, converted to mm with the root depth)
	public final double fieldCapacityPercent;
	public final double wiltingPointPercent;
	public final double startOfReductionPercent;  // NaN if absent (allowed for the Penman-Monteith method)
	public final double rootDepth;                 // cm
	public final double initialSoilPercent;
	public final double gluglaCoefficient;
	// vegetation and surface
	public final String landUse;
	public final double minimumLai;
	public final double maximumLai;
	public final double vegetationFraction;
	public final double layerThickness;            // mm per unit LAI
	public final double drainageCoefficientB;
	public final double maxDrainageRate;           // mm/day
	public final double directRunoffFactor;        // %
	public final double litterCapacity;            // mm
	public final double initialLitter;             // mm
	public final double litterReductionFactor;
	// method
	public final String etMethod;                  // HAUDE or PM

	private ModelParameters(Map<String, String> values)
	{
		this.values = values;
		for (String key : values.keySet())
		{
			if (!KNOWN_KEYS.contains(key))
			{
				throw new IllegalArgumentException("Unknown key '" + key + "' in Soil_physics.txt" + suggestion(key));
			}
		}
		fieldCapacityPercent = required(FIELD_CAPACITY_PERCENT, 0., 100.);
		wiltingPointPercent = required(PERMANENT_WILTING_POINT, 0., 100.);
		startOfReductionPercent = optional(START_OF_REDUCTION_PERCENT, Double.NaN, 0., 100.);
		rootDepth = required(ROOT_DEPTH, 0., 10000.);
		initialSoilPercent = required(INIT_VALUE_SOIL_PERCENT, 0., 100.);
		gluglaCoefficient = required(GLUGLA_C, 0., 1e6);
		landUse = requiredText(LAND_USE);
		minimumLai = required(MINIMUM_LAI, 0., 20.);
		maximumLai = required(MAXIMUM_LAI, 0., 20.);
		vegetationFraction = required(VEGETATION_FRACTION, 0., 1.);
		layerThickness = required(LAYER_THICKNESS, 0., 10.);
		drainageCoefficientB = required(DRAINAGE_COEFF_B, 0., 100.);
		maxDrainageRate = required(MAX_DRAINAGE_RATE, 0., 1000.);
		directRunoffFactor = required(DIRECT_RUNOFF_FACTOR, 0., 100.);
		litterCapacity = required(CAP_LITTER, 0., 1000.);
		initialLitter = required(INIT_VALUE_LITTER, 0., 1000.);
		litterReductionFactor = required(LITTER_REDUCTION_FACTOR, 0., 1000.);
		etMethod = values.containsKey(ET_METHOD) ? values.get(ET_METHOD).toUpperCase() : "HAUDE";

		if (rootDepth <= 0.)
		{
			throw new IllegalArgumentException("'" + ROOT_DEPTH + "' must be above 0 cm (it sets the size of the soil water "
					+ "store; 0 makes every result NaN). For (nearly) sealed surfaces such as urban, use a small value, e.g. 1 cm.");
		}
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
		if (maximumLai <= 0. || minimumLai > maximumLai)
		{
			throw new IllegalArgumentException("LAI must satisfy 0 <= '" + MINIMUM_LAI + "' <= '" + MAXIMUM_LAI
					+ "' and '" + MAXIMUM_LAI + "' > 0 (got " + minimumLai + ", " + maximumLai + ")");
		}
		if (litterReductionFactor <= 0.)
		{
			throw new IllegalArgumentException("'" + LITTER_REDUCTION_FACTOR + "' must be above 0");
		}
	}

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
