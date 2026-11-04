package thud.simpel;

import java.util.Arrays;
import java.util.List;

/**
 * One row of soil.txt: the physical properties of a soil, in vol-% so that they do not depend on the root
 * depth (the root depth comes from the land use and sets the size of the soil water store).
 *
 * The soil of a run is chosen with "soil = name" in run.txt (default luvisol, a Parabraunerde from loess;
 * see GLOSSARY.md for the values and their ranges).
 */
public class SoilType
{
	public static final String FILE_NAME = "soil.txt";
	public static final String DEFAULT = "luvisol";

	public static final String FIELD_CAPACITY = "field_capacity";
	public static final String WILTING_POINT = "wilting_point";
	public static final String START_OF_REDUCTION = "start_of_reduction";
	public static final String GLUGLA_C = "glugla_c";
	public static final String INFILTRATION_FRACTION = "infiltration_fraction";
	public static final String SOIL_SURFACE_CONDUCTANCE = "soil_surface_conductance";

	static final List<String> COLUMNS = Arrays.asList(FIELD_CAPACITY, WILTING_POINT, START_OF_REDUCTION, GLUGLA_C,
			INFILTRATION_FRACTION, SOIL_SURFACE_CONDUCTANCE);

	public final String name;
	public final double fieldCapacityPercent;     // vol-%
	public final double wiltingPointPercent;      // vol-%
	public final double startOfReductionPercent;  // vol-%, NaN if not given (not used by PM)
	public final double gluglaCoefficient;        // -, lambda = c / (root depth in mm)^2
	public final double infiltrationFraction;     // share of the soil deficit (FC - storage) that may infiltrate per day
	public final double soilSurfaceConductance;   // mm s-1, bare soil at field capacity (PM only)

	SoilType(LookupTable table, String name)
	{
		this.name = name;
		fieldCapacityPercent = table.required(name, FIELD_CAPACITY, 0., 100.);
		wiltingPointPercent = table.required(name, WILTING_POINT, 0., 100.);
		startOfReductionPercent = table.optional(name, START_OF_REDUCTION, 0., 100.);
		gluglaCoefficient = table.required(name, GLUGLA_C, 0., 1e6);
		infiltrationFraction = table.required(name, INFILTRATION_FRACTION, 0., 1.);
		soilSurfaceConductance = table.required(name, SOIL_SURFACE_CONDUCTANCE, 0., 50.);

		if (wiltingPointPercent >= fieldCapacityPercent)
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + WILTING_POINT + " (" + wiltingPointPercent
					+ ") must be below " + FIELD_CAPACITY + " (" + fieldCapacityPercent + ") for soil '" + name + "'");
		}
	}

	/** Checks start_of_reduction (only for the ET methods that use it, HAUDE and ET0). */
	void checkStartOfReduction(String etMethod)
	{
		if (Double.isNaN(startOfReductionPercent))
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + START_OF_REDUCTION + " must be given for soil '" + name
					+ "' with et_method = " + etMethod);
		}
		if (startOfReductionPercent <= wiltingPointPercent || startOfReductionPercent > fieldCapacityPercent)
		{
			throw new IllegalArgumentException(FILE_NAME + ": " + START_OF_REDUCTION + " (" + startOfReductionPercent
					+ ") must be above the wilting point (" + wiltingPointPercent + ") and at most field capacity ("
					+ fieldCapacityPercent + ") for soil '" + name + "'");
		}
	}
}
