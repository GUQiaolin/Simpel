package thud.simpel;

/** Checks the default phenology curve, a season across the turn of the year, and invalid dates. */
public class PhenologyTest
{
	static int failures = 0;

	public static void main(String[] args)
	{
		Phenology defaults = new Phenology(1., 2., Phenology.DEFAULT_GREEN_UP_START, Phenology.DEFAULT_GREEN_UP_END,
				Phenology.DEFAULT_SENESCENCE_START, Phenology.DEFAULT_SENESCENCE_END);
		check("before green-up", defaults.lai(1), 1.);
		check("green-up start", defaults.lai(121), 1.);
		check("half way through green-up", defaults.lai(128), 1.5);
		check("green-up end", defaults.lai(135), 2.);
		check("summer", defaults.lai(200), 2.);
		check("senescence start", defaults.lai(305), 2.);
		check("half way through senescence", defaults.lai(312), 1.5);
		check("senescence end", defaults.lai(319), 1.);
		check("winter", defaults.lai(360), 1.);
		check("leap day 366", defaults.lai(366), 1.);

		// growing season across the turn of the year (e.g. southern hemisphere): green-up in October
		Phenology wrapped = new Phenology(0.5, 3., 280, 300, 100, 120);
		check("wrapped: during green-up", wrapped.lai(290), 1.75);
		check("wrapped: new year", wrapped.lai(1), 3.);
		check("wrapped: during senescence", wrapped.lai(110), 1.75);
		check("wrapped: winter", wrapped.lai(200), 0.5);

		try
		{
			new Phenology(1., 2., 121, 300, 200, 250);
			fail("dates out of order should be rejected");
		}
		catch (IllegalArgumentException expected)
		{
		}

		if (failures > 0)
		{
			System.out.println("PhenologyTest FAILED: " + failures);
			System.exit(1);
		}
		System.out.println("PhenologyTest OK");
	}

	static void check(String name, double actual, double expected)
	{
		if (Math.abs(actual - expected) > 1e-12) fail(name + ": expected " + expected + " got " + actual);
	}

	static void fail(String message)
	{
		System.out.println("FAIL " + message);
		failures++;
	}
}
