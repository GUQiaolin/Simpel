package thud.simpel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whitespace-separated table with one row per land use: the first non-comment line is the header,
 * the first column is the land-use name, lines starting with '#' are comments and '-' marks a value
 * that is not given. Used for Land_Use_Parameters.txt and Haude_Coefficients.txt.
 */
public class LookupTable
{
	public static final String NOT_GIVEN = "-";

	final String fileName;
	final List<String> columns;
	final Map<String, String[]> rows = new LinkedHashMap<String, String[]>();

	private LookupTable(String fileName, List<String> columns)
	{
		this.fileName = fileName;
		this.columns = columns;
	}

	/**
	 * Reads a table and checks its header: every name in requiredColumns must be present, and every
	 * other column must be in optionalColumns (so a misspelled column name is not silently ignored).
	 */
	public static LookupTable read(Path file, List<String> requiredColumns, List<String> optionalColumns) throws IOException
	{
		if (!Files.exists(file))
		{
			throw new IllegalArgumentException(file + " not found");
		}
		LookupTable table = null;
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		for (int n = 0; n < lines.size(); n++)
		{
			String line = lines.get(n).trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] fields = line.split("\\s+");
			if (table == null)
			{
				List<String> columns = new ArrayList<String>(Arrays.asList(fields).subList(1, fields.length));
				table = new LookupTable(file.getFileName().toString(), columns);
				for (String required : requiredColumns)
				{
					if (!columns.contains(required))
					{
						throw new IllegalArgumentException(table.fileName + " is missing the column " + required);
					}
				}
				for (String column : columns)
				{
					if (!requiredColumns.contains(column) && !optionalColumns.contains(column))
					{
						throw new IllegalArgumentException(table.fileName + " has an unknown column '" + column
								+ "'; known columns: " + requiredColumns + " (required), " + optionalColumns + " (optional)");
					}
				}
				continue;
			}
			if (fields.length != table.columns.size() + 1)
			{
				throw new IllegalArgumentException(table.fileName + " line " + (n + 1) + " has " + (fields.length - 1)
						+ " values, the header has " + table.columns.size() + " columns (use - for a value that is not given)");
			}
			if (table.rows.containsKey(fields[0]))
			{
				throw new IllegalArgumentException(table.fileName + " has the land use '" + fields[0] + "' twice");
			}
			table.rows.put(fields[0], Arrays.copyOfRange(fields, 1, fields.length));
		}
		if (table == null)
		{
			throw new IllegalArgumentException(file + " has no header line");
		}
		return table;
	}

	/** Raw text of a cell; checks that the land use exists. */
	String text(String landUse, String column)
	{
		String[] row = rows.get(landUse);
		if (row == null)
		{
			throw new IllegalArgumentException("Land use '" + landUse + "' is not in " + fileName + "; available: " + rows.keySet());
		}
		int index = columns.indexOf(column);
		return index < 0 ? NOT_GIVEN : row[index];
	}

	/** A value that must be given, checked against a range. */
	public double required(String landUse, String column, double min, double max)
	{
		double value = optional(landUse, column, min, max);
		if (Double.isNaN(value))
		{
			throw new IllegalArgumentException(fileName + ": " + column + " must be given for land use '" + landUse + "'");
		}
		return value;
	}

	/** A value that may be '-' (returned as NaN), checked against a range when given. */
	public double optional(String landUse, String column, double min, double max)
	{
		String text = text(landUse, column);
		if (text.equals(NOT_GIVEN)) return Double.NaN;
		double value;
		try
		{
			value = Double.parseDouble(text);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(fileName + ": " + column + " = '" + text + "' for land use '" + landUse + "' is not a number");
		}
		if (value < min || value > max)
		{
			throw new IllegalArgumentException(fileName + ": " + column + " = " + value + " for land use '" + landUse
					+ "' is outside " + min + " to " + max);
		}
		return value;
	}
}
