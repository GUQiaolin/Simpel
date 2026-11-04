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
 * A whitespace-separated parameter table with one row per soil or land use: the first non-comment line is
 * the header, its first cell names the row label (e.g. "soil" or "land_use") and the other cells the
 * columns. Lines starting with '#' are comments (e.g. the units line) and '-' marks a value that is not
 * given. Used for soil.txt, land_use.txt, pm.txt and haude.txt.
 *
 * Every column of the table must be in the list of columns the model knows, and every known column must
 * be present (a missing or misspelled column stops the run). Values of the chosen row can be replaced
 * with override() before they are read (overrides from run.txt).
 */
public class LookupTable
{
	public static final String NOT_GIVEN = "-";

	final String fileName;
	final String rowLabel;
	final List<String> columns;
	final Map<String, String[]> rows = new LinkedHashMap<String, String[]>();

	private LookupTable(String fileName, String rowLabel, List<String> columns)
	{
		this.fileName = fileName;
		this.rowLabel = rowLabel;
		this.columns = columns;
	}

	/** Reads a table and checks that its header has exactly the given columns (in any order). */
	public static LookupTable read(Path file, List<String> knownColumns) throws IOException
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
				table = new LookupTable(file.getFileName().toString(), fields[0], columns);
				for (String column : columns)
				{
					if (!knownColumns.contains(column))
					{
						throw new IllegalArgumentException(table.fileName + " has an unknown column '" + column
								+ "'" + ModelParameters.suggestion(column, knownColumns) + "; known columns: " + knownColumns);
					}
				}
				for (String known : knownColumns)
				{
					if (!columns.contains(known))
					{
						throw new IllegalArgumentException(table.fileName + " is missing the column " + known
								+ " (use - in a row where it is not given)");
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
				throw new IllegalArgumentException(table.fileName + " has the " + table.rowLabel + " '" + fields[0] + "' twice");
			}
			table.rows.put(fields[0], Arrays.copyOfRange(fields, 1, fields.length));
		}
		if (table == null)
		{
			throw new IllegalArgumentException(file + " has no header line");
		}
		return table;
	}

	public boolean hasRow(String row)
	{
		return rows.containsKey(row);
	}

	/** Replaces one value of a row (an override from run.txt); the row and the column must exist. */
	public void override(String row, String column, String value)
	{
		String[] values = row(row);
		int index = columns.indexOf(column);
		if (index < 0)
		{
			throw new IllegalArgumentException("run.txt: " + fileName + " has no column '" + column + "'"
					+ ModelParameters.suggestion(column, columns) + "; columns: " + columns);
		}
		values[index] = value;
	}

	private String[] row(String row)
	{
		String[] values = rows.get(row);
		if (values == null)
		{
			throw new IllegalArgumentException(rowLabel + " '" + row + "' is not in " + fileName + "; available: " + rows.keySet());
		}
		return values;
	}

	/** Raw text of a cell; checks that the row exists. */
	String text(String row, String column)
	{
		return row(row)[columns.indexOf(column)];
	}

	public boolean given(String row, String column)
	{
		return !text(row, column).equals(NOT_GIVEN);
	}

	/** A value that must be given, checked against a range. */
	public double required(String row, String column, double min, double max)
	{
		double value = optional(row, column, min, max);
		if (Double.isNaN(value))
		{
			throw new IllegalArgumentException(fileName + ": " + column + " must be given for " + rowLabel + " '" + row + "'");
		}
		return value;
	}

	/** A value that may be '-' (returned as NaN), checked against a range when given. */
	public double optional(String row, String column, double min, double max)
	{
		String text = text(row, column);
		if (text.equals(NOT_GIVEN)) return Double.NaN;
		double value;
		try
		{
			value = Double.parseDouble(text);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(fileName + ": " + column + " = '" + text + "' for " + rowLabel + " '" + row
					+ "' is not a number");
		}
		if (value < min || value > max)
		{
			throw new IllegalArgumentException(fileName + ": " + column + " = " + value + " for " + rowLabel + " '" + row
					+ "' is outside " + min + " to " + max);
		}
		return value;
	}
}
