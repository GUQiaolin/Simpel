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
		// soil moisture deficit 20 mm. Available energy = net radiation.
		Case midday = new Case(et, 800, 350, 25, 50, 1010, 3, 2, 2, 20, fieldCapacityMinusWiltingPoint);
		check("midday net radiation", midday.netRadiation, 559.9152140339172, 1e-9);
		check("midday aerodynamic resistance", midday.aerodynamicResistance, 69.216382798652, 1e-9);
		check("midday surface conductance", midday.conductance, 1.495726858191315, 1e-9);
		check("midday QE wet surface", midday.latentHeatWet, 519.694475676941, 1e-9);
		check("midday QE dry surface", midday.latentHeatDry, 147.37602383498984, 1e-9);
		check("midday ET dry surface (mm/h)", midday.evaporationDryOneHour, 0.21726417584371807, 1e-12);

		// Night: no K_down, conductance at its minimum, dew is not counted as evaporation
		Case night = new Case(et, 0, 300, 12, 85, 1010, 1.5, 2, 2, 20, fieldCapacityMinusWiltingPoint);
		check("night net radiation", night.netRadiation, -73.36911181244545, 1e-9);
		check("night surface conductance", night.conductance, et.minimumConductance, 1e-12);
		check("night QE dry surface", night.latentHeatDry, -0.1024485919606425, 1e-9);
		check("night ET (mm/h)", night.evaporationDryOneHour, 0., 0.);

		// Same weather as midday, soil 5 mm above the wilting point: conductance and QE drop sharply
		Case dry = new Case(et, 800, 350, 25, 50, 1010, 3, 2, 2, 125, fieldCapacityMinusWiltingPoint);
		check("dry soil surface conductance", dry.conductance, 0.14724291466411502, 1e-9);
		check("dry soil QE dry surface", dry.latentHeatDry, 19.491301178893615, 1e-9);

		// Sanity: at the wilting point the conductance falls to its minimum
		double atWilt = et.surfaceConductance(800, 25, 50, 1010, 2, 2, fieldCapacityMinusWiltingPoint, fieldCapacityMinusWiltingPoint);
		check("conductance at wilting point", atWilt, et.minimumConductance, 1e-12);
		// Sanity: a wet surface evaporates more than a transpiring one
		if (!(midday.latentHeatWet > midday.latentHeatDry)) fail("wet QE should exceed dry QE");

		// Bare soil in the gap: soil surface conductance (5 mm/s) times (W - PWP) / (FC - PWP)
		check("soil conductance at field capacity", et.soilConductance(250, 250, 120), 5., 1e-12);
		check("soil conductance above field capacity", et.soilConductance(260, 250, 120), 5., 1e-12);
		check("soil conductance halfway", et.soilConductance(185, 250, 120), 2.5, 1e-12);
		check("soil conductance at wilting point", et.soilConductance(120, 250, 120), et.minimumConductance, 1e-12);

		if (failures > 0)
		{
			System.out.println("PenmanMonteithEvaporationTest FAILED: " + failures);
			System.exit(1);
		}
		System.out.println("PenmanMonteithEvaporationTest OK");
	}

	static class Case
	{
		double netRadiation, aerodynamicResistance, conductance, latentHeatWet, latentHeatDry, evaporationDryOneHour;

		Case(PenmanMonteithEvaporation et, double kdown, double ldown, double airTemperature, double relativeHumidity, double pressure,
				double windSpeed, double lai, double maxLai, double soilMoistureDeficit, double deficitAtWilt)
		{
			netRadiation = et.netRadiation(kdown, ldown, airTemperature);
			double availableEnergy = netRadiation;
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
