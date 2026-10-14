package thud.simpel;

/**
 * Default (static) phenology: LAI follows the same curve every year, independent of weather and drought.
 *
 *   before green-up start                 Minimum LAI
 *   green-up start  -> green-up end       rises linearly to Maximum LAI
 *   green-up end    -> senescence start   Maximum LAI
 *   senescence start-> senescence end     falls linearly to Minimum LAI
 *   after senescence end                  Minimum LAI
 *
 * The four dates are days of the year. They default to the values of the original SIMPEL LAI model
 * (121, 135, 305, 319, i.e. 1-15 May and 1-15 November) and can be set per land use in
 * Land_Use_Parameters.txt. The curve is evaluated cyclically, so a season may run across the turn of
 * the year (e.g. green-up in October and senescence in April).
 *
 * This class is the place where a dynamic phenology (e.g. responding to drought) can replace the
 * fixed curve later.
 */
public class Phenology
{
	public static final int DEFAULT_GREEN_UP_START = 121;
	public static final int DEFAULT_GREEN_UP_END = 135;
	public static final int DEFAULT_SENESCENCE_START = 305;
	public static final int DEFAULT_SENESCENCE_END = 319;
	static final int DAYS_PER_CYCLE = 365;

	public final double minimumLai;
	public final double maximumLai;
	public final int greenUpStart;
	public final int greenUpEnd;
	public final int senescenceStart;
	public final int senescenceEnd;

	public Phenology(double minimumLai, double maximumLai, int greenUpStart, int greenUpEnd, int senescenceStart, int senescenceEnd)
	{
		this.minimumLai = minimumLai;
		this.maximumLai = maximumLai;
		this.greenUpStart = greenUpStart;
		this.greenUpEnd = greenUpEnd;
		this.senescenceStart = senescenceStart;
		this.senescenceEnd = senescenceEnd;
		if (daysAfter(greenUpEnd, greenUpStart) + daysAfter(senescenceStart, greenUpEnd)
				+ daysAfter(senescenceEnd, senescenceStart) >= DAYS_PER_CYCLE)
		{
			throw new IllegalArgumentException("Phenology dates must follow each other within one year: green-up start "
					+ greenUpStart + ", green-up end " + greenUpEnd + ", senescence start " + senescenceStart
					+ ", senescence end " + senescenceEnd);
		}
	}

	/** Leaf area index (m2/m2) on a day of the year (1-366). */
	public double lai(double dayOfYear)
	{
		double greenUpLength = daysAfter(greenUpEnd, greenUpStart);
		double fullLeafLength = daysAfter(senescenceStart, greenUpEnd);
		double senescenceLength = daysAfter(senescenceEnd, senescenceStart);
		double day = Math.floorMod((int) dayOfYear - greenUpStart, DAYS_PER_CYCLE); // days since green-up start

		if (day == 0. || day > greenUpLength + fullLeafLength + senescenceLength)
		{
			return minimumLai;
		}
		if (day <= greenUpLength)
		{
			return minimumLai + (maximumLai - minimumLai) * (day / greenUpLength);
		}
		if (day <= greenUpLength + fullLeafLength)
		{
			return maximumLai;
		}
		return maximumLai + (minimumLai - maximumLai) * ((day - greenUpLength - fullLeafLength) / senescenceLength);
	}

	static int daysAfter(int later, int earlier)
	{
		return Math.floorMod(later - earlier, DAYS_PER_CYCLE);
	}
}
