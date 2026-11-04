package thud.simpel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One row of land_use.txt: everything that depends on the land use, including its phenology (LAI over the
 * year) and the surface properties used by the PM method. The land use of a run is chosen with
 * "land_use = name" in run.txt.
 *
 * Which values are needed follows from the components the land use has:
 *   canopy       max_lai > 0       icept_cap_per_lai, icept_drain_b, icept_drain_max required
 *   seasonal LAI min_lai < max_lai the four phenology dates; '-' uses the defaults (1-15 May, 1-15 Nov)
 *   litter layer litter_cap > 0    litter_reduction required
 *   ET method PM                   albedo, emissivity, veg_height required
 * A value given for a component that does not exist has no effect; it is ignored with a note in the log.
 */
public class LandUse
{
	public static final String FILE_NAME = "land_use.txt";

	public static final String MIN_LAI = "min_lai";
	public static final String MAX_LAI = "max_lai";
	public static final String GREEN_UP_START = "green_up_start";
	public static final String GREEN_UP_END = "green_up_end";
	public static final String SENESCENCE_START = "senescence_start";
	public static final String SENESCENCE_END = "senescence_end";
	public static final String ROOT_DEPTH = "root_depth";
	public static final String DEGREE_DAY = "degree_day";
	public static final String DIRECT_RUNOFF = "direct_runoff";
	public static final String LITTER_CAP = "litter_cap";
	public static final String LITTER_REDUCTION = "litter_reduction";
	public static final String ICEPT_CAP_PER_LAI = "icept_cap_per_lai";
	public static final String ICEPT_DRAIN_B = "icept_drain_b";
	public static final String ICEPT_DRAIN_MAX = "icept_drain_max";
	public static final String ALBEDO = "albedo";
	public static final String EMISSIVITY = "emissivity";
	public static final String VEG_HEIGHT = "veg_height";

	static final List<String> PHENOLOGY_DATES = Arrays.asList(GREEN_UP_START, GREEN_UP_END, SENESCENCE_START, SENESCENCE_END);
	static final List<String> CANOPY = Arrays.asList(ICEPT_CAP_PER_LAI, ICEPT_DRAIN_B, ICEPT_DRAIN_MAX);
	static final List<String> SURFACE = Arrays.asList(ALBEDO, EMISSIVITY, VEG_HEIGHT);
	static final List<String> COLUMNS = Arrays.asList(MIN_LAI, MAX_LAI, GREEN_UP_START, GREEN_UP_END, SENESCENCE_START,
			SENESCENCE_END, ROOT_DEPTH, DEGREE_DAY, DIRECT_RUNOFF, LITTER_CAP, LITTER_REDUCTION, ICEPT_CAP_PER_LAI,
			ICEPT_DRAIN_B, ICEPT_DRAIN_MAX, ALBEDO, EMISSIVITY, VEG_HEIGHT);

	public final String name;
	public final Phenology phenology;
	public final double rootDepth;             // cm, depth of the soil water store
	public final double degreeDay;             // mm per degC per day, snow melt
	public final double directRunoffFactor;    // % of the water passing the litter layer that runs off directly
	public final double litterCapacity;        // mm, 0 = no litter layer
	public final double litterReductionFactor; // -, 1 (unused) without litter layer
	public final double layerThickness;        // mm interception capacity per unit LAI, 0 without canopy
	public final double drainageCoefficientB;  // -, 0 without canopy
	public final double maxDrainageRate;       // mm/day, 0 without canopy
	public final double albedo;                // -, NaN if not given (only PM)
	public final double emissivity;            // -, NaN if not given (only PM)
	public final double vegetationHeight;      // m, NaN if not given (only PM)
	/** Notes for the run log: defaults used and values that have no effect. */
	public final List<String> notes = new ArrayList<String>();

	LandUse(LookupTable table, String name, boolean penmanMonteith)
	{
		this.name = name;
		double minimumLai = table.required(name, MIN_LAI, 0., 20.);
		double maximumLai = table.required(name, MAX_LAI, 0., 20.);
		// max_lai = 0 is allowed: a non-vegetated surface (no interception, no transpiration with PM)
		if (minimumLai > maximumLai)
		{
			throw new IllegalArgumentException(FILE_NAME + ": land use '" + name + "' needs 0 <= " + MIN_LAI + " <= "
					+ MAX_LAI + " (got " + minimumLai + ", " + maximumLai + ")");
		}
		boolean canopy = maximumLai > 0.;
		boolean seasonal = minimumLai < maximumLai;
		if (seasonal && !allGiven(table, name, PHENOLOGY_DATES))
		{
			notes.add("Note: phenology dates of '" + name + "' not all given: defaults used for the missing ones ("
					+ Phenology.DEFAULT_GREEN_UP_START + ", " + Phenology.DEFAULT_GREEN_UP_END + ", "
					+ Phenology.DEFAULT_SENESCENCE_START + ", " + Phenology.DEFAULT_SENESCENCE_END + ")");
		}
		if (!seasonal) ignored(table, name, PHENOLOGY_DATES, MIN_LAI + " = " + MAX_LAI + " (constant LAI)");
		this.phenology = new Phenology(minimumLai, maximumLai,
				day(table, name, GREEN_UP_START, Phenology.DEFAULT_GREEN_UP_START),
				day(table, name, GREEN_UP_END, Phenology.DEFAULT_GREEN_UP_END),
				day(table, name, SENESCENCE_START, Phenology.DEFAULT_SENESCENCE_START),
				day(table, name, SENESCENCE_END, Phenology.DEFAULT_SENESCENCE_END));

		this.rootDepth = table.required(name, ROOT_DEPTH, 0., 10000.);
		if (rootDepth <= 0.)
		{
			throw new IllegalArgumentException("Root depth must be above 0 cm (land use '" + name + "' in " + FILE_NAME
					+ "). It sets the size of the soil water store, and 0 makes every result NaN. For (nearly) sealed "
					+ "surfaces such as urban, use a small value, e.g. 1 cm.");
		}
		this.degreeDay = table.required(name, DEGREE_DAY, 0., 20.);
		this.directRunoffFactor = table.required(name, DIRECT_RUNOFF, 0., 100.);

		this.litterCapacity = table.required(name, LITTER_CAP, 0., 1000.);
		if (litterCapacity > 0.)
		{
			this.litterReductionFactor = table.required(name, LITTER_REDUCTION, 0., 1000.);
			if (litterReductionFactor <= 0.)
			{
				throw new IllegalArgumentException(FILE_NAME + ": " + LITTER_REDUCTION + " must be above 0 for land use '" + name + "'");
			}
		}
		else
		{
			ignored(table, name, Arrays.asList(LITTER_REDUCTION), LITTER_CAP + " = 0 (no litter layer)");
			this.litterReductionFactor = 1.; // the litter evaporation is 0 without capacity
		}

		if (canopy)
		{
			this.layerThickness = table.required(name, ICEPT_CAP_PER_LAI, 0., 10.);
			this.drainageCoefficientB = table.required(name, ICEPT_DRAIN_B, 0., 100.);
			this.maxDrainageRate = table.required(name, ICEPT_DRAIN_MAX, 0., 1000.);
		}
		else
		{
			ignored(table, name, CANOPY, MAX_LAI + " = 0 (no canopy)");
			this.layerThickness = 0.;
			this.drainageCoefficientB = 0.;
			this.maxDrainageRate = 0.;
		}

		if (penmanMonteith)
		{
			this.albedo = table.required(name, ALBEDO, 0., 1.);
			this.emissivity = table.required(name, EMISSIVITY, 0.5, 1.);
			this.vegetationHeight = table.required(name, VEG_HEIGHT, 0.001, 100.);
		}
		else
		{
			this.albedo = table.optional(name, ALBEDO, 0., 1.);
			this.emissivity = table.optional(name, EMISSIVITY, 0.5, 1.);
			this.vegetationHeight = table.optional(name, VEG_HEIGHT, 0.001, 100.);
		}
	}

	static boolean allGiven(LookupTable table, String name, List<String> columns)
	{
		for (String column : columns)
		{
			if (!table.given(name, column)) return false;
		}
		return true;
	}

	/** Notes the given values of columns that have no effect for this land use. */
	private void ignored(LookupTable table, String name, List<String> columns, String reason)
	{
		List<String> given = new ArrayList<String>();
		for (String column : columns)
		{
			if (table.given(name, column)) given.add(column);
		}
		if (!given.isEmpty())
		{
			notes.add("Note: " + String.join(", ", given) + " of land use '" + name + "' ignored: " + reason);
		}
	}

	static int day(LookupTable table, String landUse, String column, int defaultDay)
	{
		double value = table.optional(landUse, column, 1., 366.);
		if (Double.isNaN(value)) return defaultDay;
		if (value != Math.rint(value))
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + column + " for land use '" + landUse + "' must be a whole day of the year");
		}
		return (int) value;
	}

	public double minimumLai() { return phenology.minimumLai; }
	public double maximumLai() { return phenology.maximumLai; }
}
