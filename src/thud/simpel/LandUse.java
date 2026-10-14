package thud.simpel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Land-use dependent parameters from Land_Use_Parameters.txt (one row per land use). To add a land use,
 * add a row; the land use of a run is chosen with "Land use;name" in Soil_physics.txt.
 *
 * Required columns: MinLAI, MaxLAI, DegreeDay, DirectRunoff, LitterCap.
 * Optional columns ('-' = not given):
 *   RootDepth       cm; overrides Root Depth in Soil_physics.txt for this land use
 *   GreenUpStart, GreenUpEnd, SenescenceStart, SenescenceEnd
 *                   days of the year for the phenology; defaults in Phenology
 */
public class LandUse
{
	public static final String FILE_NAME = "Land_Use_Parameters.txt";

	static final List<String> REQUIRED_COLUMNS = Arrays.asList("MinLAI", "MaxLAI", "DegreeDay", "DirectRunoff", "LitterCap");
	static final List<String> OPTIONAL_COLUMNS = Arrays.asList("RootDepth", "GreenUpStart", "GreenUpEnd",
			"SenescenceStart", "SenescenceEnd");

	public final String name;
	public final Phenology phenology;
	public final double degreeDay;          // mm per degC per day, snow melt
	public final double directRunoffFactor; // % of the water passing the litter layer that runs off directly
	public final double litterCapacity;     // mm
	public final double rootDepth;          // cm, NaN if not given

	private LandUse(LookupTable table, String name)
	{
		this.name = name;
		double minimumLai = table.required(name, "MinLAI", 0., 20.);
		double maximumLai = table.required(name, "MaxLAI", 0., 20.);
		if (maximumLai <= 0. || minimumLai > maximumLai)
		{
			throw new IllegalArgumentException(FILE_NAME + ": land use '" + name + "' needs 0 <= MinLAI <= MaxLAI and MaxLAI > 0 (got "
					+ minimumLai + ", " + maximumLai + ")");
		}
		this.phenology = new Phenology(minimumLai, maximumLai,
				day(table, name, "GreenUpStart", Phenology.DEFAULT_GREEN_UP_START),
				day(table, name, "GreenUpEnd", Phenology.DEFAULT_GREEN_UP_END),
				day(table, name, "SenescenceStart", Phenology.DEFAULT_SENESCENCE_START),
				day(table, name, "SenescenceEnd", Phenology.DEFAULT_SENESCENCE_END));
		this.degreeDay = table.required(name, "DegreeDay", 0., 20.);
		this.directRunoffFactor = table.required(name, "DirectRunoff", 0., 100.);
		this.litterCapacity = table.required(name, "LitterCap", 0., 1000.);
		this.rootDepth = table.optional(name, "RootDepth", 0., 10000.);
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

	/** Reads Land_Use_Parameters.txt and returns the row of one land use. */
	public static LandUse read(Path file, String name) throws IOException
	{
		return new LandUse(LookupTable.read(file, REQUIRED_COLUMNS, OPTIONAL_COLUMNS), name);
	}

	public double minimumLai() { return phenology.minimumLai; }
	public double maximumLai() { return phenology.maximumLai; }
}
