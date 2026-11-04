package thud.simpel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SensitivityRunTest
{
	public static void main(String[] args) throws Exception
	{
		double[] values = SensitivityRun.sensitivityValues(0., 10., 20.);
		if (!Arrays.equals(values, new double[] { 0., 5., 10., 15., 20. }))
		{
			throw new AssertionError("Expected five evenly spaced levels around the default");
		}
		if (SensitivityRun.LEVELS.length != 5)
		{
			throw new AssertionError("Expected five named sensitivity levels");
		}

		Map<String, SensitivityRun.Parameter> parameters = SensitivityRun.parameters().stream()
				.collect(Collectors.toMap(parameter -> parameter.name, parameter -> parameter));
		for (String name : new String[] { SensitivityRun.LAI, "land_use.icept_cap_per_lai" })
		{
			if (!parameters.containsKey(name))
			{
				throw new AssertionError("Missing required sensitivity parameter " + name);
			}
		}

		ModelParameters run = ModelParameters.read(Paths.get("runs", "hourly_pm", ModelParameters.FILE_NAME));
		assertBounds(parameters.get(SensitivityRun.LAI), run, 0., 20.);
		assertBounds(parameters.get("land_use.icept_cap_per_lai"), run, 0., 10.);
		assertBounds(parameters.get("init_soil"), run, 0., 25.);

		// LAI sets both limits; a lower field capacity lowers the initial water content with it
		Map<String, String> lai = SensitivityRun.overrides(parameters.get(SensitivityRun.LAI), 5., run);
		if (!"5.0".equals(lai.get("land_use.min_lai")) || !"5.0".equals(lai.get("land_use.max_lai")))
		{
			throw new AssertionError("LAI sensitivity must assign the tested value to both limits, got " + lai);
		}
		Map<String, String> fieldCapacity = SensitivityRun.overrides(parameters.get("soil.field_capacity"), 20., run);
		if (!"20.0".equals(fieldCapacity.get("init_soil")))
		{
			throw new AssertionError("field capacity below init_soil must lower init_soil, got " + fieldCapacity);
		}

		// the copy of run.txt replaces existing keys and adds new ones
		Path temp = Files.createTempDirectory("simpel-sensitivity-test");
		Path copy = temp.resolve(ModelParameters.FILE_NAME);
		Map<String, String> changes = new LinkedHashMap<String, String>();
		changes.put("init_soil", "10.0");
		changes.put("pm.G1", "5.0");
		SensitivityRun.writeRunFile(Paths.get("runs", "hourly_pm", ModelParameters.FILE_NAME), copy, changes);
		String text = new String(Files.readAllBytes(copy), "UTF-8");
		if (!text.contains("init_soil = 10.0") || !text.contains("pm.G1 = 5.0") || text.contains("init_soil   = 24"))
		{
			throw new AssertionError("writeRunFile did not set the keys:\n" + text);
		}
		Files.delete(copy);
		Files.delete(temp);
		System.out.println("SensitivityRunTest OK");
	}

	static void assertBounds(SensitivityRun.Parameter parameter, ModelParameters run, double expectedMin, double expectedMax)
	{
		double[] bounds = SensitivityRun.parameterBounds(parameter, run);
		if (bounds[0] != expectedMin || bounds[1] != expectedMax)
		{
			throw new AssertionError(parameter.name + " expected bounds [" + expectedMin + ", " + expectedMax
					+ "], got [" + bounds[0] + ", " + bounds[1] + "]");
		}
	}
}
