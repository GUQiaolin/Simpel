package thud.simpel;

/**
 * Evapotranspiration module of SIMPEL's ET method PM: the Penman-Monteith equation with the
 * surface conductance of Järvi et al. (2011, J. Hydrol. 411, 219-237, doi:10.1016/j.jhydrol.2011.10.001,
 * section 2.4). The available energy comes straight from the radiation forcing:
 *
 *   available energy  A  = Q*                 (no storage heat, no anthropogenic heat)
 *   net radiation     Q* = QN column of Global_Input.txt if present, otherwise
 *                     Q* = (1 - albedo) K_down + emissivity (L_down - sigma T_air^4)
 *   latent heat       QE = (s A + rho cp VPD / ra) / (s + gamma (1 + rs / ra))
 *   surface conductance after Järvi et al. (2011), gsModel 1:
 *                     gs = G1 * (LAI/LAI_max) * g(K_down) * g(dq) * g(T_air) * g(soil moisture deficit)
 *   bare soil in the gap of the land-use cover:
 *                     gs_soil = soil surface conductance * (W - PWP) / (FC - PWP)
 *
 * The air temperature stands in for the surface temperature in the outgoing long-wave term.
 * Aerodynamic resistance uses a neutral log profile (no stability correction).
 *
 * All parameters are public fields; the conductance defaults are the Järvi et al. (2011) values
 * (Järvi et al. 2011).
 */
public class PenmanMonteithEvaporation
{
	public static final double STEFAN_BOLTZMANN = 5.67e-8;   // W m-2 K-4
	public static final double VON_KARMAN = 0.41;
	public static final double SPECIFIC_HEAT_AIR = 1005.;    // J kg-1 K-1
	public static final double GAS_CONSTANT_DRY_AIR = 287.04; // J kg-1 K-1
	public static final double KELVIN = 273.15;

	// Radiation
	public double albedo = 0.18;                  // -
	public double surfaceEmissivity = 0.98;       // -

	// Aerodynamics
	public double vegetationHeight = 0.12;        // m
	public double measurementHeight = 2.0;        // m, height of wind (and temperature) measurement
	public double minimumWindSpeed = 0.5;         // m s-1, keeps ra finite in calm conditions

	// Surface conductance, Järvi et al. (2011) gsModel 1 (Järvi et al. 2011 values)
	public double maxConductanceG1 = 16.4764;     // mm s-1
	public double kdownShapeG2 = 566.0923;        // W m-2
	public double humiditySlopeG3 = 0.2163;       // (g kg-1)-1
	public double humidityThresholdG4 = 3.3649;   // g kg-1
	public double optimumTemperatureG5 = 11.0764; // degC
	public double soilMoistureShapeG6 = 0.0176;   // mm-1
	public double upperTemperatureTH = 40.;       // degC
	public double lowerTemperatureTL = 0.;        // degC
	public double maxKdown = 1200.;               // W m-2
	public double minimumConductance = 0.01;      // mm s-1, keeps rs finite at night

	// Bare soil in the gap between the land-use cover (1 - Land Use Fraction)
	public double soilSurfaceConductance = 5.;    // mm s-1, soil surface at field capacity (rs = 200 s m-1)

	/** Net all-wave radiation (W m-2); the air temperature stands in for the surface temperature. */
	public double netRadiation(double kdown, double ldown, double airTemperature)
	{
		double airTemperatureK = airTemperature + KELVIN;
		return (1. - albedo) * kdown
				+ surfaceEmissivity * (ldown - STEFAN_BOLTZMANN * Math.pow(airTemperatureK, 4));
	}

	/**
	 * Clear-sky incoming long-wave radiation after Brutsaert (1975), only for forcing without LDOWN.
	 * Underestimates L_down under cloud, so Q* and ET are too low at night and on cloudy days.
	 */
	public static double clearSkyLdown(double airTemperature, double relativeHumidity)
	{
		double airTemperatureK = airTemperature + KELVIN;
		double vapourPressure = relativeHumidity / 100. * saturationVapourPressure(airTemperature); // hPa
		double emissivity = 1.24 * Math.pow(vapourPressure / airTemperatureK, 1. / 7.);
		return emissivity * STEFAN_BOLTZMANN * Math.pow(airTemperatureK, 4);
	}

	/** Aerodynamic resistance for heat and vapour (s m-1), neutral log profile. */
	public double aerodynamicResistance(double windSpeed)
	{
		double displacementHeight = 0.67 * vegetationHeight;
		double roughnessMomentum = 0.123 * vegetationHeight;
		double roughnessVapour = 0.1 * roughnessMomentum;
		double effectiveHeight = measurementHeight - displacementHeight;
		double u = Math.max(windSpeed, minimumWindSpeed);
		return Math.log(effectiveHeight / roughnessMomentum) * Math.log(effectiveHeight / roughnessVapour)
				/ (VON_KARMAN * VON_KARMAN * u);
	}

	/**
	 * Surface conductance (mm s-1) after Järvi et al. (2011).
	 *
	 * @param soilMoistureDeficit       field capacity minus soil water (mm)
	 * @param soilMoistureDeficitAtWilt deficit at which transpiration stops (mm); here field capacity
	 *                                  minus permanent wilting point of the SIMPEL soil bucket
	 */
	public double surfaceConductance(double kdown, double airTemperature, double relativeHumidity, double pressure,
			double lai, double maxLai, double soilMoistureDeficit, double soilMoistureDeficitAtWilt)
	{
		// no leaves (MaxLAI 0, non-vegetated surface): no transpiration
		double gLai = maxLai > 0. ? Math.min(1., Math.max(0., lai / maxLai)) : 0.;

		double k = Math.max(0., Math.min(kdown, maxKdown));
		double gKdown = (k / (kdownShapeG2 + k)) / (maxKdown / (kdownShapeG2 + maxKdown));

		double humidityDeficit = specificHumidityDeficit(airTemperature, relativeHumidity, pressure); // g kg-1
		double gHumidity = 1. - humiditySlopeG3 * Math.min(humidityDeficit, humidityThresholdG4);

		double gTemperature = 0.;
		if (airTemperature > lowerTemperatureTL && airTemperature < upperTemperatureTH)
		{
			double tc = (upperTemperatureTH - optimumTemperatureG5) / (optimumTemperatureG5 - lowerTemperatureTL);
			gTemperature = (airTemperature - lowerTemperatureTL) * Math.pow(upperTemperatureTH - airTemperature, tc)
					/ ((optimumTemperatureG5 - lowerTemperatureTL) * Math.pow(upperTemperatureTH - optimumTemperatureG5, tc));
		}

		double deficit = Math.max(0., soilMoistureDeficit);
		double gSoil = (1. - Math.exp(soilMoistureShapeG6 * (deficit - soilMoistureDeficitAtWilt)))
				/ (1. - Math.exp(-soilMoistureShapeG6 * soilMoistureDeficitAtWilt));
		gSoil = Math.min(1., Math.max(0., gSoil));

		double conductance = maxConductanceG1 * gLai * gKdown * Math.max(0., gHumidity) * gTemperature * gSoil;
		return Math.max(conductance, minimumConductance);
	}

	/**
	 * Surface conductance (mm s-1) of the bare soil in the gap: the soil surface conductance at field capacity,
	 * reduced linearly with the relative available water beta = (W - PWP) / (FC - PWP).
	 */
	public double soilConductance(double soilWater, double fieldCapacity, double wiltingPoint)
	{
		double beta = Math.min(1., Math.max(0., (soilWater - wiltingPoint) / (fieldCapacity - wiltingPoint)));
		return Math.max(soilSurfaceConductance * beta, minimumConductance);
	}

	/**
	 * Latent heat flux (W m-2) by Penman-Monteith (Järvi et al. 2011, eq. 6 with Q* + QF - dQs replaced
	 * by the available energy from the radiation forcing, here Q*). surfaceResistance 0 gives a wet surface.
	 */
	public double latentHeatFlux(double availableEnergy, double airTemperature, double relativeHumidity,
			double pressure, double aerodynamicResistance, double surfaceResistance)
	{
		double saturation = saturationVapourPressure(airTemperature);                  // hPa
		double vapourPressureDeficit = saturation * (1. - relativeHumidity / 100.);     // hPa
		double slope = 4098. * saturation / Math.pow(airTemperature + 237.3, 2);        // hPa K-1
		double airDensity = pressure * 100. / (GAS_CONSTANT_DRY_AIR * (airTemperature + KELVIN));
		double gamma = psychrometricConstant(airTemperature, pressure);                 // hPa K-1
		return (slope * availableEnergy + airDensity * SPECIFIC_HEAT_AIR * vapourPressureDeficit / aerodynamicResistance)
				/ (slope + gamma * (1. + surfaceResistance / aerodynamicResistance));
	}

	/** Converts a latent heat flux (W m-2) over a time step (s) into mm of water; dew (negative) is set to 0. */
	public static double evaporationDepth(double latentHeatFlux, double airTemperature, double timestepSeconds)
	{
		return Math.max(0., latentHeatFlux) * timestepSeconds / latentHeatOfVaporisation(airTemperature);
	}

	/** Surface resistance (s m-1) from a conductance in mm s-1. */
	public static double surfaceResistance(double conductanceMmPerSecond)
	{
		return 1000. / conductanceMmPerSecond;
	}

	/** J kg-1 */
	public static double latentHeatOfVaporisation(double airTemperature)
	{
		return (2.501 - 0.002361 * airTemperature) * 1e6;
	}

	/** hPa K-1 */
	public static double psychrometricConstant(double airTemperature, double pressure)
	{
		return SPECIFIC_HEAT_AIR * pressure / (0.622 * latentHeatOfVaporisation(airTemperature));
	}

	/** Saturation vapour pressure over water (hPa), Tetens/FAO form. */
	public static double saturationVapourPressure(double airTemperature)
	{
		return 6.108 * Math.exp(17.27 * airTemperature / (airTemperature + 237.3));
	}

	/** Specific humidity deficit (g kg-1). */
	public static double specificHumidityDeficit(double airTemperature, double relativeHumidity, double pressure)
	{
		double saturation = saturationVapourPressure(airTemperature);
		double actual = saturation * relativeHumidity / 100.;
		return 1000. * (specificHumidity(saturation, pressure) - specificHumidity(actual, pressure));
	}

	static double specificHumidity(double vapourPressure, double pressure)
	{
		return 0.622 * vapourPressure / (pressure - 0.378 * vapourPressure);
	}

	/** Mean air pressure (hPa) at an elevation (m), standard atmosphere; used when the forcing has no PRES column. */
	public static double pressureFromElevation(double elevation)
	{
		return 1013.25 * Math.pow((293. - 0.0065 * elevation) / 293., 5.26);
	}
}
