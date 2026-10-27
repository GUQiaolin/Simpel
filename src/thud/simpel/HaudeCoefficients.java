package thud.simpel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Monthly Haude coefficients (mm per hPa per day) from Haude_Coefficients.txt, one row per land use with
 * the columns Jan ... Dec. Only needed for the HAUDE evapotranspiration method.
 *
 * The months are written as for the northern hemisphere (the original values are for Central Europe).
 * With Hemisphere;S the coefficient of the month half a year later is used, e.g. July's for January.
 */
public class HaudeCoefficients
{
	public static final String FILE_NAME = "Haude_Coefficients.txt";
	static final List<String> MONTHS = Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
			"Oct", "Nov", "Dec");

	private final double[] coefficients = new double[12];

	private HaudeCoefficients(LookupTable table, String landUse)
	{
		for (int m = 0; m < 12; m++)
		{
			coefficients[m] = table.required(landUse, MONTHS.get(m), 0., 1.);
		}
	}

	public static HaudeCoefficients read(Path file, String landUse) throws IOException
	{
		return new HaudeCoefficients(LookupTable.read(file, MONTHS, Collections.<String>emptyList()), landUse);
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
