package thud.simpel;

/**
 * Checks PenmanMonteithEvaporation against reference values from an independent Python implementation of the
 * same equations (test/reference/penman_monteith_reference.py), plus a few physical sanity checks.
 */
public class PenmanMonteithEvaporationTest
{
	static int failures = 0;

	public static void main(String[] args)
	{
		PenmanMonteithEvaporation et = new PenmanMonteithEvaporation();
		double fieldCapacityMinusWiltingPoint = 130.;

		// Summer midday, moist soil: K_down 800, L_down 350 W m-2, 25 degC, 50 %, 1010 hPa, 3 m s-1, LAI 2 of 2,
		// soil moisture deficit 20 mm, dQ*/dt 50 W m-2 h-1
		Case midday = new Case(et, 800, 350, 25, 50, 1010, 3, 2, 2, 20, fieldCapacityMinusWiltingPoint, 50);
		check("midday net radiation", midday.netRadiation, 559.9152140339172, 1e-9);
		check("midday storage heat", midday.storageHeat, 178.7728684908535, 1e-9);
		check("midday aerodynamic resistance", midday.aerodynamicResistance, 69.216382798652, 1e-9);
		check("midday surface conductance", midday.conductance, 1.495726858191315, 1e-9);
		check("midday QE wet surface", midday.latentHeatWet, 387.6790256638746, 1e-9);
		check("midday QE dry surface", midday.latentHeatDry, 109.93881213022848, 1e-9);
		check("midday ET dry surface (mm/h)", midday.evaporationDryOneHour, 0.16207361814466673, 1e-12);

		// Night: no K_down, conductance at its minimum, dew is not counted as evaporation
		Case night = new Case(et, 0, 300, 12, 85, 1010, 1.5, 2, 2, 20, fieldCapacityMinusWiltingPoint, 0);
		check("night net radiation", night.netRadiation, -73.36911181244545, 1e-9);
		check("night surface conductance", night.conductance, et.minimumConductance, 1e-12);
		check("night QE dry surface", night.latentHeatDry, -0.004082037391907665, 1e-9);
		check("night ET (mm/h)", night.evaporationDryOneHour, 0., 0.);

		// Same weather as midday, soil 5 mm above the wilting point: conductance and QE drop sharply
		Case dry = new Case(et, 800, 350, 25, 50, 1010, 3, 2, 2, 125, fieldCapacityMinusWiltingPoint, 50);
		check("dry soil surface conductance", dry.conductance, 0.14724291466411502, 1e-9);
		check("dry soil QE dry surface", dry.latentHeatDry, 14.540021115506125, 1e-9);

		// Sanity: at the wilting point the conductance falls to its minimum
		double atWilt = et.surfaceConductance(800, 25, 50, 1010, 2, 2, fieldCapacityMinusWiltingPoint, fieldCapacityMinusWiltingPoint);
		check("conductance at wilting point", atWilt, et.minimumConductance, 1e-12);
		// Sanity: a wet surface evaporates more than a transpiring one
		if (!(midday.latentHeatWet > midday.latentHeatDry)) fail("wet QE should exceed dry QE");

		if (failures > 0)
		{
			System.out.println("PenmanMonteithEvaporationTest FAILED: " + failures);
			System.exit(1);
		}
		System.out.println("PenmanMonteithEvaporationTest OK");
	}

	static class Case
	{
		double netRadiation, storageHeat, aerodynamicResistance, conductance, latentHeatWet, latentHeatDry, evaporationDryOneHour;

		Case(PenmanMonteithEvaporation et, double kdown, double ldown, double airTemperature, double relativeHumidity, double pressure,
				double windSpeed, double lai, double maxLai, double soilMoistureDeficit, double deficitAtWilt, double netRadiationTendency)
		{
			netRadiation = et.netRadiation(kdown, ldown, airTemperature);
			storageHeat = et.storageHeatFlux(netRadiation, netRadiationTendency);
			double availableEnergy = netRadiation - storageHeat;
			aerodynamicResistance = et.aerodynamicResistance(windSpeed);
			conductance = et.surfaceConductance(kdown, airTemperature, relativeHumidity, pressure, lai, maxLai,
					soilMoistureDeficit, deficitAtWilt);
			latentHeatWet = et.latentHeatFlux(availableEnergy, airTemperature, relativeHumidity, pressure, aerodynamicResistance, 0.);
			latentHeatDry = et.latentHeatFlux(availableEnergy, airTemperature, relativeHumidity, pressure, aerodynamicResistance,
					PenmanMonteithEvaporation.surfaceResistance(conductance));
			evaporationDryOneHour = PenmanMonteithEvaporation.evaporationDepth(latentHeatDry, airTemperature, 3600.);
		}
	}

	static void check(String name, double actual, double expected, double tolerance)
	{
		if (Math.abs(actual - expected) > tolerance)
		{
			fail(name + ": expected " + expected + " got " + actual);
		}
	}

	static void fail(String message)
	{
		System.out.println("FAIL " + message);
		failures++;
	}
}
