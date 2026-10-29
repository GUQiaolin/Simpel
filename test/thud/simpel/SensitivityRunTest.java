package thud.simpel;

import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
		for (String name : new String[] { "LAI (MinLAI and MaxLAI)", "Layer Thickness" })
		{
			if (!parameters.containsKey(name))
			{
				throw new AssertionError("Missing required sensitivity parameter " + name);
			}
		}

		Map<String, String> soil = SensitivityRun.readSoil(Paths.get("example_hourly"));
		String landUse = soil.get("Land use");
		assertBounds(parameters.get("LAI (MinLAI and MaxLAI)"), soil, landUse, 0., 20.);
		assertBounds(parameters.get("Layer Thickness"), soil, landUse, 0., 10.);
		Path temp = Files.createTempDirectory("simpel-sensitivity-lai-test");
		try
		{
			Files.copy(Paths.get("example_hourly", LandUse.FILE_NAME), temp.resolve(LandUse.FILE_NAME));
			SensitivityRun.setLandUseValue(temp, landUse, parameters.get("LAI (MinLAI and MaxLAI)"), 5.);
			Map<String, String> changed = SensitivityRun.landUseRow(temp, landUse);
			if (!changed.get("MinLAI").equals("5.0") || !changed.get("MaxLAI").equals("5.0"))
			{
				throw new AssertionError("LAI sensitivity must assign the tested MaxLAI value to both limits");
			}
		}
		finally
		{
			Files.deleteIfExists(temp.resolve(LandUse.FILE_NAME));
			Files.deleteIfExists(temp);
		}
		System.out.println("SensitivityRunTest OK");
	}

	static void assertBounds(SensitivityRun.Parameter parameter, Map<String, String> soil, String landUse,
			double expectedMin, double expectedMax) throws Exception
	{
		double[] bounds = SensitivityRun.parameterBounds(parameter, soil, Paths.get("example_hourly"), landUse);
		if (bounds[0] != expectedMin || bounds[1] != expectedMax)
		{
			throw new AssertionError(parameter.name + " expected bounds [" + expectedMin + ", " + expectedMax
					+ "], got [" + bounds[0] + ", " + bounds[1] + "]");
		}
	}
}
