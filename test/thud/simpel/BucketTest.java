package thud.simpel;

/**
 * Unit checks of single bucket-model steps that the example runs do not reach.
 */
public class BucketTest
{
	static int failures = 0;

	public static void main(String[] args)
	{
		// Store above field capacity: excess runs off, seepage leaves the store, balance closes
		double fieldCapacity = 250., wiltingPoint = 120., lambda = 150. / 1000. / 1000., nt = 1.;
		double etBalance = 270.;
		double seepage = SimpelModel.gluglaSeepage(etBalance, fieldCapacity, wiltingPoint, lambda, nt);
		double storage = SimpelModel.soilStorage(etBalance, seepage, fieldCapacity);
		double excessRunoff = etBalance - fieldCapacity;
		check("seepage above FC uses W = FC", seepage, lambda * 130. * 130., 1e-12);
		check("storage above FC = FC - seepage", storage, fieldCapacity - seepage, 1e-12);
		check("balance above FC", etBalance - excessRunoff - seepage - storage, 0., 1e-12);

		// Store below field capacity: unchanged behaviour
		check("storage below FC", SimpelModel.soilStorage(200., 1., fieldCapacity), 199., 1e-12);

		// Seepage never drains the store below the wilting point, even with a large lambda
		double bigSeepage = SimpelModel.gluglaSeepage(300., 2.5, 1.2, 1.5, 1.);
		check("seepage capped at FC - PWP", bigSeepage, 2.5 - 1.2, 1e-12);

		// Litter (beech: capacity 1.5 mm, factor 3, daily): MIN(demand; MIN(cap; content + P) / factor)
		check("litter, water above capacity", SimpelModel.litterEvaporation(4., 1.5, 3., 3.), 0.5, 1e-12);
		check("litter, water below capacity", SimpelModel.litterEvaporation(4., 1.5, 0.6, 3.), 0.2, 1e-12);
		check("litter, demand limits", SimpelModel.litterEvaporation(0.1, 1.5, 3., 3.), 0.1, 1e-12);
		check("litter, no capacity", SimpelModel.litterEvaporation(4., 0., 3., 3.), 0., 1e-12);

		if (failures > 0)
		{
			System.out.println("BucketTest FAILED: " + failures);
			System.exit(1);
		}
		System.out.println("BucketTest OK");
	}

	static void check(String name, double actual, double expected, double tolerance)
	{
		if (Math.abs(actual - expected) > tolerance)
		{
			System.out.println("FAIL " + name + ": expected " + expected + " got " + actual);
			failures++;
		}
	}
}
