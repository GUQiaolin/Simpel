package thud.simpel;

import java.util.Arrays;
import java.util.List;

/**
 * Monthly Haude coefficients (mm per hPa per day) from haude.txt (in the parameters folder), one row per
 * land use with the columns Jan ... Dec. Only read for et_method = HAUDE.
 *
 * The months are written as for the northern hemisphere (the original values are for Central Europe).
 * With hemisphere = S the coefficient of the month half a year later is used, e.g. July's for January.
 */
public class HaudeCoefficients
{
	public static final String FILE_NAME = "haude.txt";
	static final List<String> MONTHS = Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
			"Oct", "Nov", "Dec");

	private final double[] coefficients = new double[12];

	HaudeCoefficients(LookupTable table, String landUse)
	{
		for (int m = 0; m < 12; m++)
		{
			coefficients[m] = table.required(landUse, MONTHS.get(m), 0., 1.);
		}
	}

	/** Coefficient for a month (1 = January). */
	private double forMonth(int month)
	{
		return coefficients[month - 1];
	}

	/** Coefficient for a calendar month, shifted by six months in the southern hemisphere. */
	public double forMonth(int month, boolean southernHemisphere)
	{
		return forMonth(seasonalMonth(month, southernHemisphere));
	}

	public static int seasonalMonth(int month, boolean southernHemisphere)
	{
		return southernHemisphere ? (month - 1 + 6) % 12 + 1 : month;
	}
}
