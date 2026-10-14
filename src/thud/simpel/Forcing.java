package thud.simpel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Meteorological forcing from Global_Input.txt, read and checked once.
 *
 * Columns are matched by their header name (see COLUMNS for names, aliases, units and allowed ranges).
 * Every value is parsed once into a named array; a missing value marker (empty, NA, NaN, -999), a value
 * that is not a number or a value outside the plausible range stops the run with the row and column named.
 * Optional columns that are absent are null.
 */
public class Forcing
{
	/** One forcing variable: header name, accepted aliases, unit and plausible range. */
	static final class Column
	{
		final String name;
		final String[] aliases;
		final String unit;
		final double min;
		final double max;

		Column(String name, String unit, double min, double max, String... aliases)
		{
			this.name = name;
			this.unit = unit;
			this.min = min;
			this.max = max;
			this.aliases = aliases;
		}

		boolean matches(String header)
		{
			if (name.equals(header)) return true;
			for (String alias : aliases)
			{
				if (alias.equals(header)) return true;
			}
			return false;
		}
	}

	// The single place where forcing variables are defined
	static final Column PRECIPITATION = new Column("P", "mm/step", 0., 500.);
	static final Column AIR_TEMPERATURE = new Column("Ta", "degC", -60., 60., "T14");
	static final Column RELATIVE_HUMIDITY = new Column("RH", "%", 0., 105., "RF14");
	static final Column KDOWN = new Column("KDOWN", "W m-2", -50., 1500.);
	static final Column LDOWN = new Column("LDOWN", "W m-2", 50., 700.);
	static final Column WIND_SPEED = new Column("WindSpeed", "m s-1", 0., 60.);
	static final Column PRESSURE = new Column("PRES", "hPa", 500., 1100.);
	static final Column ET0 = new Column("ET0", "mm/step", 0., 50.);
	static final Column DAY_OF_YEAR = new Column("DOY", "-", 1., 366.);
	static final Column[] COLUMNS = { PRECIPITATION, AIR_TEMPERATURE, RELATIVE_HUMIDITY, KDOWN, LDOWN,
			WIND_SPEED, PRESSURE, ET0, DAY_OF_YEAR };

	static final String[] MISSING_VALUE_MARKERS = { "", "NA", "NaN", "nan", "-999", "-999.0", "-9999" };

	public final String[] dateLabels;
	public final LocalDateTime[] dates;
	public final double timestepHours;

	public final double[] precipitation;     // mm per step
	public final double[] airTemperature;    // degC
	public final double[] relativeHumidity;  // %
	public final double[] kdown;             // W m-2, null if absent
	public final double[] ldown;             // W m-2, null if absent
	public final double[] windSpeed;         // m s-1, null if absent
	public final double[] pressure;          // hPa, null if absent
	public final double[] et0;               // mm per step, null if absent

	private Forcing(String[] dateLabels, Map<Column, double[]> values)
	{
		this.dateLabels = dateLabels;
		this.dates = new LocalDateTime[dateLabels.length];
		for (int i = 0; i < dateLabels.length; i++)
		{
			try
			{
				dates[i] = parseDate(dateLabels[i]);
			}
			catch (RuntimeException e)
			{
				throw new IllegalArgumentException("Global_Input.txt row " + (i + 2) + ": cannot read date '" + dateLabels[i]
						+ "', expected dd.mm.yyyy or dd.mm.yyyy.hh");
			}
		}
		this.timestepHours = timestepHours(dates, dateLabels.length > 0 ? dateLabels[0] : "");
		this.precipitation = values.get(PRECIPITATION);
		this.airTemperature = values.get(AIR_TEMPERATURE);
		this.relativeHumidity = values.get(RELATIVE_HUMIDITY);
		this.kdown = values.get(KDOWN);
		this.ldown = values.get(LDOWN);
		this.windSpeed = values.get(WIND_SPEED);
		this.pressure = values.get(PRESSURE);
		this.et0 = values.get(ET0);
	}

	public int size()
	{
		return dates.length;
	}

	public boolean hasKdown() { return kdown != null; }
	public boolean hasLdown() { return ldown != null; }
	public boolean hasWindSpeed() { return windSpeed != null; }
	public boolean hasPressure() { return pressure != null; }
	public boolean hasEt0() { return et0 != null; }

	/** Reads and checks Global_Input.txt. */
	public static Forcing read(Path file) throws IOException
	{
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		List<String[]> rows = new ArrayList<String[]>();
		List<Integer> lineNumbers = new ArrayList<Integer>();
		String[] header = null;
		for (int n = 0; n < lines.size(); n++)
		{
			String line = lines.get(n).trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] fields = line.split("\\s+");
			if (header == null)
			{
				header = fields;
			}
			else
			{
				rows.add(fields);
				lineNumbers.add(n + 1);
			}
		}
		if (header == null || rows.isEmpty())
		{
			throw new IllegalArgumentException(file + " has no header line or no data rows");
		}

		int dateColumn = -1;
		Map<Column, Integer> columnIndex = new LinkedHashMap<Column, Integer>();
		for (int i = 0; i < header.length; i++)
		{
			String name = header[i].trim();
			if (name.equals("Date"))
			{
				dateColumn = i;
				continue;
			}
			Column column = null;
			for (Column c : COLUMNS)
			{
				if (c.matches(name)) column = c;
			}
			if (column == null)
			{
				System.out.println("Warning: ignoring unknown column '" + name + "' in Global_Input.txt");
				continue;
			}
			if (columnIndex.containsKey(column))
			{
				throw new IllegalArgumentException("Global_Input.txt has the column " + column.name
						+ " twice (also accepted as " + String.join(", ", column.aliases) + ")");
			}
			columnIndex.put(column, i);
		}
		if (dateColumn < 0 || !columnIndex.containsKey(PRECIPITATION) || !columnIndex.containsKey(AIR_TEMPERATURE)
				|| !columnIndex.containsKey(RELATIVE_HUMIDITY))
		{
			throw new IllegalArgumentException("Global_Input.txt must have the columns Date, P, T14 (or Ta) and RF14 (or RH)");
		}

		String[] dateLabels = new String[rows.size()];
		Map<Column, double[]> values = new LinkedHashMap<Column, double[]>();
		for (Column column : columnIndex.keySet())
		{
			values.put(column, new double[rows.size()]);
		}
		for (int r = 0; r < rows.size(); r++)
		{
			String[] fields = rows.get(r);
			if (fields.length < header.length)
			{
				throw new IllegalArgumentException("Global_Input.txt line " + lineNumbers.get(r) + " has " + fields.length
						+ " values, the header has " + header.length + " columns");
			}
			dateLabels[r] = fields[dateColumn].trim();
			for (Map.Entry<Column, Integer> entry : columnIndex.entrySet())
			{
				Column column = entry.getKey();
				values.get(column)[r] = parseValue(fields[entry.getValue()], column, lineNumbers.get(r), dateLabels[r]);
			}
		}
		return new Forcing(dateLabels, values);
	}

	static double parseValue(String text, Column column, int lineNumber, String date)
	{
		String trimmed = text.trim();
		for (String marker : MISSING_VALUE_MARKERS)
		{
			if (trimmed.equals(marker))
			{
				throw new IllegalArgumentException("Global_Input.txt line " + lineNumber + " (" + date + "): missing value '"
						+ trimmed + "' in column " + column.name + "; fill the gap before running");
			}
		}
		double value;
		try
		{
			value = Double.parseDouble(trimmed);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("Global_Input.txt line " + lineNumber + " (" + date + "): '" + trimmed
					+ "' in column " + column.name + " is not a number");
		}
		if (value < column.min || value > column.max)
		{
			throw new IllegalArgumentException("Global_Input.txt line " + lineNumber + " (" + date + "): " + column.name
					+ " = " + value + " " + column.unit + " is outside the plausible range " + column.min + " to "
					+ column.max + " " + column.unit);
		}
		return value;
	}

	static final DateTimeFormatter DAILY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	static final DateTimeFormatter HOURLY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy.HH");

	/** Parses dd.mm.yyyy (start of the day) or dd.mm.yyyy.hh. */
	static LocalDateTime parseDate(String date)
	{
		String trimmed = date.trim();
		if (trimmed.split("\\.").length == 3)
		{
			return LocalDate.parse(trimmed, DAILY_DATE).atStartOfDay();
		}
		return LocalDateTime.parse(trimmed, HOURLY_DATE);
	}

	/**
	 * Time step in hours from the dates: daily dates (dd.mm.yyyy) give 24 h, otherwise the spacing of the
	 * rows. The spacing must be constant and divide a day evenly (1, 2, 3, 4, 6, 8, 12 or 24 h).
	 */
	static double timestepHours(LocalDateTime[] dates, String firstDate)
	{
		boolean dailyFormat = firstDate.trim().split("\\.").length == 3;
		if (dates.length < 2)
		{
			return dailyFormat ? 24. : 1.;
		}
		long stepMinutes = Duration.between(dates[0], dates[1]).toMinutes();
		for (int i = 2; i < dates.length; i++)
		{
			long minutes = Duration.between(dates[i - 1], dates[i]).toMinutes();
			if (minutes != stepMinutes)
			{
				throw new IllegalArgumentException("Global_Input.txt rows are not evenly spaced: " + dates[i - 1]
						+ " to " + dates[i] + " is " + minutes + " min, expected " + stepMinutes + " min");
			}
		}
		if (stepMinutes <= 0 || stepMinutes > 24 * 60 || (24 * 60) % stepMinutes != 0)
		{
			throw new IllegalArgumentException("Time step of " + stepMinutes + " min does not divide a day evenly");
		}
		if (dailyFormat && stepMinutes != 24 * 60)
		{
			throw new IllegalArgumentException("Daily dates (dd.mm.yyyy) must be consecutive days");
		}
		return stepMinutes / 60.;
	}
}
