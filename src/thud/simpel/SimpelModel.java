package thud.simpel;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimpelModel
{	
	private String directory;
	
	public final static int PRECIPITATION=0; 
	public final static int SNOW_WATER_EQUI=1; 
	public final static int SNOW_MELT_RAIN=2;
    public final static int ETP_COEFF=3; 
    public final static int ETP_INPUT=4; 
    public final static int LAI=5; 
    public final static int I_CAP=6; 
    public final static int INT_ETI_LEAF=7;
    public final static int I_BAL=8;
    public final static int I_PREC=9;
    public final static int I_REM=10; 
    public final static int I_ETI_LITTER=11; 
    public final static int BILANZ=12; 
    public final static int CONTENT=13; 
    public final static int S_RESTN=14; 
    public final static int INF_LIMIT=15; 
    public final static int P_LINF=16; 
    public final static int REST_ETA=17; 
    public final static int BALANCE_SOIL=18;
    public final static int ETA=19; 
    public final static int ET_BALANCE=20; 
    public final static int SEEPAGE=21;	
    public final static int STORAGE=22;	
    public final static int SURFACE_RUNOFF=23;
    public final static int RUNOFF_TOTAL=24; 
    public final static int I_LEAF=25;	
    public final static int I_LITTER=26;	
    public final static int ETA_TOTAL=27;	
    
    public final static int K_DOWN=28;	
    public final static int WIND_SPEED=29;	
    public final static int L_DOWN=30;	
    // columns filled by the ET method PM
    public final static int NET_RADIATION=31;          // W m-2, available energy
    public final static int AERODYNAMIC_RESISTANCE=32; // s m-1
    public final static int SURFACE_RESISTANCE=33;     // s m-1
    public final static int ETP_DRY=34;                // mm/step, Penman-Monteith with surface resistance
    public final static int LATENT_HEAT_FLUX=35;       // W m-2, from ETaTotal (all methods)
    public final static int NUMBER_OF_COLUMNS=36;
    
    public final static double SECONDS_PER_DAY = 86400.;
    // Default fraction of the soil water deficit (field capacity minus storage) that can infiltrate per day;
    // set with "Infiltration fraction" in Soil_physics.txt
    public final static double INFILTRATION_FRACTION_PER_DAY = 0.25;
    // Upper limit of the Haude potential evapotranspiration
    public final static double HAUDE_MAX_ETP_PER_DAY = 7.;   // mm/day
    // Haude uses the air temperature and humidity at this hour
    public final static int HAUDE_HOUR = 14;
    
    
    
    public final String tab = "\t";
    public final String linefeed = "\n";
    private String[] bucket_model_dates;
    
    private Boolean readETfromFile = null; // null: decide from the ET0 column in Global_Input.txt
    private String commandLine = "(called from Java)";

    // Results and the run log are written into this folder of the input directory
    public final static String OUTPUT_FOLDER = "output";
    public final static String OUTPUT_FILE = "SIMPLE_bucket_model.csv";

	public static void main(String[] args)
	{
		SimpelModel s = new SimpelModel();
		if (args.length<1)
		{
			System.err.println("Usage: java thud.simpel.SimpelModel <input directory> [readET|dontReadET]");
			System.exit(1);
		}
		s.setDirectory(args[0]);
		s.commandLine = "java thud.simpel.SimpelModel " + String.join(" ", args);
		if (args.length>1)
		{
			String arg1 = args[1];
			s.setReadETFromFile(arg1);
		}
		s.process();
	}
	
	/**
	 * Runs the model on the input directory. The results and a log of everything printed to the console
	 * (System.out and System.err, including an error that stops the run) are written to the folder
	 * output/ of the input directory: SIMPLE_bucket_model.csv (overwritten) and SIMPEL_log_<date>_<time>.txt.
	 */
	public void process()
	{
		Path outputDirectory = Paths.get(directory, OUTPUT_FOLDER);
		LocalDateTime start = LocalDateTime.now();
		Path log = outputDirectory.resolve("SIMPEL_log_" + start.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
		PrintStream console = System.out;
		PrintStream consoleErr = System.err;
		try (OutputStream logFile = Files.newOutputStream(Files.createDirectories(outputDirectory).resolve(log.getFileName())))
		{
			System.setOut(new PrintStream(new TeeOutputStream(console, logFile), true, "UTF-8"));
			System.setErr(new PrintStream(new TeeOutputStream(consoleErr, logFile), true, "UTF-8"));
			try
			{
				System.out.println("SIMPEL run started " + start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
				System.out.println("Command line: " + commandLine);
				System.out.println("Input directory: " + Paths.get(directory).toAbsolutePath().normalize());
				System.out.println("Output directory: " + outputDirectory.toAbsolutePath().normalize());
				System.out.println();
				run(outputDirectory);
				System.out.println("\nResults written to " + outputDirectory.resolve(OUTPUT_FILE));
			}
			catch (RuntimeException e)
			{
				System.err.println("Run stopped: " + e);
				throw e;
			}
			finally
			{
				System.out.flush();
				System.err.flush();
				System.setOut(console);
				System.setErr(consoleErr);
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Cannot write the log " + log, e);
		}
	}

	/** Copies everything written to the console into the log file as well. */
	static class TeeOutputStream extends OutputStream
	{
		private final OutputStream first;
		private final OutputStream second;

		TeeOutputStream(OutputStream first, OutputStream second)
		{
			this.first = first;
			this.second = second;
		}

		@Override
		public void write(int b) throws IOException
		{
			first.write(b);
			second.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			first.write(b, off, len);
			second.write(b, off, len);
		}

		@Override
		public void flush() throws IOException
		{
			first.flush();
			second.flush();
		}
	}

	private void run(Path outputDirectory)
	{
		Forcing forcing;
		ModelParameters parameters;
		try
		{
			forcing = Forcing.read(Paths.get(directory, "Global_Input.txt"));
			parameters = ModelParameters.read(Paths.get(directory, "Soil_physics.txt"));
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		// Without a command-line choice, read ET0 whenever the column is present, except with ET method PM
		// (which computes its own demand and cannot be combined with ET0 from file)
		if (readETfromFile == null)
		{
			readETfromFile = forcing.hasEt0() && !ModelParameters.PM.equals(parameters.etMethod);
			if (forcing.hasEt0() && !readETfromFile)
			{
				System.out.println("Note: the ET0 column of Global_Input.txt is ignored with ET method " + ModelParameters.PM);
			}
		}
		if (readETfromFile && !forcing.hasEt0())
		{
			throw new IllegalArgumentException("readET was requested but Global_Input.txt has no ET0 column");
		}
		LandUse landUse;
		HaudeCoefficients haude = null;
		try
		{
			if (!Files.exists(Paths.get(directory, LandUse.FILE_NAME)) && Files.exists(Paths.get(directory, "Land_Use.txt")))
			{
				throw new IllegalArgumentException(LandUse.FILE_NAME + " not found. Land_Use.txt and LAI_model.txt have been "
						+ "replaced by " + LandUse.FILE_NAME + " (one row per land use) and " + HaudeCoefficients.FILE_NAME
						+ "; see GLOSSARY.md");
			}
			landUse = LandUse.read(Paths.get(directory, LandUse.FILE_NAME), parameters.landUse);
			// Haude coefficients are only needed for ET method HAUDE without ET0 from file (otherwise they only fill ETP_Coeff)
			boolean haudeNeeded = ModelParameters.HAUDE.equals(parameters.etMethod) && !readETfromFile;
			if (haudeNeeded || Files.exists(Paths.get(directory, HaudeCoefficients.FILE_NAME)))
			{
				haude = HaudeCoefficients.read(Paths.get(directory, HaudeCoefficients.FILE_NAME), parameters.landUse);
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		parameters.applyLandUse(landUse);
		for (String unused : new String[] {"Land_Use.txt", "LAI_model.txt"})
		{
			if (Files.exists(Paths.get(directory, unused)))
			{
				System.out.println("Note: " + unused + " is no longer read (replaced by " + LandUse.FILE_NAME + ")");
			}
		}
		String output = outputDirectory.resolve(OUTPUT_FILE).toString();

		double[][] bucket_model = SIMPLE_function(forcing, parameters, landUse, haude);
		write_csv(bucket_model, output);

	}
	
	
	public double[][] SIMPLE_function(Forcing forcing, ModelParameters parameters, LandUse landUse, HaudeCoefficients haude)
	{		
		double Field_Capacity_Percent=parameters.fieldCapacityPercent;
		double Permanent_Wilting_Point=parameters.wiltingPointPercent;
		// Start of Reduction is only used by the HAUDE and ET0 methods; with PM the surface conductance
		// limits ET as the soil dries, so the key can be left out (NaN)
		double Start_of_Reduction_Percent = parameters.startOfReductionPercent;
		double Root_Depth=parameters.rootDepth();
		double Init_Value_Soil_Percent=parameters.initialSoilPercent;
		double Field_Capacity=parameters.fieldCapacity();
		double Perm_Wilting_Point=parameters.wiltingPoint();
		double fwc=Field_Capacity-Perm_Wilting_Point;
		double Start_of_Reduction=parameters.startOfReduction();
		double Init_Value_Soil=parameters.initialSoilWater();
		double Depth_of_soil=parameters.soilDepthMm();
		String Land_use=parameters.landUse;
		double Minimum_LAI=landUse.minimumLai();
		double Maximum_LAI=landUse.maximumLai();
		double vcover=parameters.vegetationFraction;
		double layer_thickness=parameters.layerThickness;
		double intc_drainage_coeff_b=parameters.drainageCoefficientB;
		double intc_drainage_max_d=parameters.maxDrainageRate;
		double Direct_runoff_factor=landUse.directRunoffFactor;
		double Glugla_Coeff_c=parameters.gluglaCoefficient;
		double Lambda=parameters.gluglaLambda();
		double Infiltration_fraction=parameters.infiltrationFraction;
		double Cap_Litter=landUse.litterCapacity;
		double Init_Value_Litter=parameters.initialLitter;
		double Litter_Reduction_factor=parameters.litterReductionFactor;

		// The time step, day of year and month come from the Date column of Global_Input.txt
		LocalDateTime[] dates = forcing.dates;
		double timestep = forcing.timestepHours;
		if (parameters.has(ModelParameters.TIMESTEP))
		{
			double configuredTimestep = parameters.optional(ModelParameters.TIMESTEP, timestep);
			if (Math.abs(configuredTimestep - timestep) > 1e-9)
			{
				throw new IllegalArgumentException("Timestep in Soil_physics.txt (" + configuredTimestep 
						+ " h) does not match the dates in Global_Input.txt (" + timestep 
						+ " h). The time step is now taken from the dates; remove the Timestep line.");
			}
		}




		// Echo the parameters (given and derived) to the console and log
		System.out.println("SIMPEL");
		System.out.println("======");
		System.out.println("\nParameters:");
		System.out.println("Time step: "+String.format("%,.2f", timestep)+" hours");
		System.out.println("Field Capacity: "+String.format("%,.2f", Field_Capacity_Percent)+"%");
		System.out.println("Permanent Wilting Point: "+String.format("%,.2f", Permanent_Wilting_Point)+"%");
		System.out.println("Start of Reduction: "+String.format("%,.2f", Start_of_Reduction_Percent)+"%");
		System.out.println("Root depth: "+String.format("%,.2f", Root_Depth)+" cm");
		System.out.println("Init-Value Soil: "+String.format("%,.2f", Init_Value_Soil_Percent)+"%");
		System.out.println("Land use: "+Land_use);
		System.out.println("Minimum_LAI: "+String.format("%,.2f", Minimum_LAI)+"");
		System.out.println("Maximum_LAI: "+String.format("%,.2f", Maximum_LAI)+"");
		System.out.println("Vegetation cover fraction: "+String.format("%,.2f", vcover)+"");
		System.out.println("Layer Thickness: "+String.format("%,.2f", layer_thickness)+"mm");
		System.out.println("Drainage Coeff. b: "+String.format("%,.2f", intc_drainage_coeff_b)+"");
		System.out.println("Max. Drainage rate: "+String.format("%,.2f", intc_drainage_max_d)+"mm/d");
		System.out.println("Capacity of Litter Layer: "+String.format("%,.2f", Cap_Litter)+" mm");
		System.out.println("Initial value of Litter Layer: "+String.format("%,.2f", Init_Value_Litter)+" mm");
		System.out.println("Litter Reduction factor "+String.format("%,.2f", Litter_Reduction_factor)+"");
		System.out.println("Direct runoff factor: "+String.format("%,.2f", Direct_runoff_factor)+"");
		System.out.println("Coefficient c (Glugla): "+String.format("%,.2f", Glugla_Coeff_c)+"");
		System.out.println("Infiltration fraction: "+String.format("%,.2f", Infiltration_fraction)+" of the soil deficit per day");

		System.out.println("\nDerived values");
		System.out.println("Field Capacity: "+String.format("%,.2f", Field_Capacity)+" mm");
		System.out.println("Perm_Wilting_Point: "+String.format("%,.2f", Perm_Wilting_Point)+" mm");
		System.out.println("FWC: "+String.format("%,.2f", fwc)+" mm");
		System.out.println("Start_of_Reduction: "+String.format("%,.2f", Start_of_Reduction)+" mm");
		System.out.println("Init-Value Soil: "+String.format("%,.2f", Init_Value_Soil)+" mm");
		System.out.println("Depth of soil: "+String.format("%,.2f", Depth_of_soil)+" mm");
		System.out.println("Lambda (Glugla): "+String.format("%,.8f", Lambda)+"");

//		# SIMPLE_function
//		# original Excel spreadsheet developed by Georg Hörmann
//		# extended (snow, surface runoff) by Kristian Förster 2022
//		# translated to R by Zoe Bovermann 2023
//      # translated to Java by Kerry Nice 20 June 2023

//		  # prepare bucket model matrix
		int rows = forcing.size();
		double[][] bucket_model = new double[rows][NUMBER_OF_COLUMNS];
		bucket_model_dates = forcing.dateLabels;
		
//		  # copy Input into bucket-model
//		  #col 1: A Date
//		  # col 2: B Precipitation	
		  for (int i=0;i<rows;i++)
		  {
			  bucket_model[i][PRECIPITATION]=forcing.precipitation[i];
			  if (forcing.hasKdown())
			  {
				  bucket_model[i][K_DOWN]=forcing.kdown[i];
			  }
			  if (forcing.hasWindSpeed())
			  {
				  bucket_model[i][WIND_SPEED]=forcing.windSpeed[i];
			  }
			  if (forcing.hasLdown())
			  {
				  bucket_model[i][L_DOWN]=forcing.ldown[i];
			  }
		  }
		  
		  
//		  # water balance check
		  double sum_prec   = 0.;
		  double sum_etr    = 0.;
		  double sum_runoff = 0.;
		  double init_stor  = Init_Value_Soil; 
		  double init_swe   = 0.;
		    
		  // LAI from the default phenology of the land use (see Phenology)
		  Phenology phenology = landUse.phenology;
		  boolean southernHemisphere = parameters.southernHemisphere;
		  if (southernHemisphere)
		  {
			  System.out.println("Southern hemisphere: phenology dates and Haude months are shifted by half a year");
		  }
		  double landuse_DayDegree = landUse.degreeDay;

		  double ts = timestep * 3600; // timestep from soil physics is given in hours, ts is a conversion to seconds
          	  double nt = ts / SECONDS_PER_DAY; // fractional time step
		  // In the model the drying is controlled by the ”litter reduction factor” which 
		  // specifies the maximum evaporation expressed as part of the water content. 
		  // Therefore, a factor 2 indicates that within each step of calculation a maximum 
		  // of half of the storage capacity can evaporate. (source: SIMPEL dcos)
		  // The input value refers to daily time step. Thus it's adjusted to arbitrary
		  // time steps through division by nt.
		  double Litter_Reduction_factor_dt = Litter_Reduction_factor / nt;

		  // Evapotranspiration method: HAUDE (default), ET0 from Global_Input.txt (readET), or PM
		  String etMethod = parameters.etMethod;
		  boolean usePenmanMonteith = etMethod.equals(ModelParameters.PM);
		  if (!usePenmanMonteith && Double.isNaN(Start_of_Reduction_Percent))
		  {
			  throw new IllegalArgumentException("'" + ModelParameters.START_OF_REDUCTION_PERCENT + "' is required in Soil_physics.txt for ET method " 
					  + (getReadETFromFile() ? "ET0 from file" : ModelParameters.HAUDE));
		  }
		  if (usePenmanMonteith && !Double.isNaN(Start_of_Reduction_Percent))
		  {
			  System.out.println("Note: '" + ModelParameters.START_OF_REDUCTION_PERCENT + "' is ignored with ET method " + ModelParameters.PM);
		  }
		  PenmanMonteithEvaporation penmanMonteith = null;
		  double elevation = parameters.optional("Elevation", 0., -500., 9000.);
		  double[] netRadiation = null;
		  if (usePenmanMonteith)
		  {
			  if (!forcing.hasKdown() || !forcing.hasWindSpeed())
			  {
				  throw new IllegalArgumentException("ET method " + ModelParameters.PM 
						  + " needs KDOWN and WindSpeed columns in Global_Input.txt");
			  }
			  if (getReadETFromFile())
			  {
				  throw new IllegalArgumentException("ET method " + ModelParameters.PM + " cannot be combined with reading ET0 from file");
			  }
			  penmanMonteith = configurePenmanMonteith(parameters);
			  System.out.println("ET method: " + ModelParameters.PM + " (surface conductance after Jarvi et al. 2011)");
			  // Available energy = net radiation from the forcing: the QN column if present, otherwise computed
			  // from KDOWN and LDOWN (clear-sky LDOWN if that column is missing too)
			  netRadiation = new double[rows];
			  if (forcing.hasNetRadiation())
			  {
				  System.out.println("Net radiation: QN column of Global_Input.txt");
			  }
			  else if (!forcing.hasLdown())
			  {
				  System.out.println("Warning: no QN or LDOWN column, net radiation uses clear-sky long-wave radiation (too low under cloud)");
			  }
			  for (int i=0;i<rows;i++)
			  {
				  if (forcing.hasNetRadiation())
				  {
					  netRadiation[i] = forcing.netRadiation[i];
					  continue;
				  }
				  double airTemperature = forcing.airTemperature[i];
				  double relativeHumidity = forcing.relativeHumidity[i];
				  double ldown = forcing.hasLdown() ? forcing.ldown[i]
						  : PenmanMonteithEvaporation.clearSkyLdown(airTemperature, relativeHumidity);
				  netRadiation[i] = penmanMonteith.netRadiation(forcing.kdown[i], ldown, airTemperature);
			  }
		  }
		  
		   
		  // Haude is a daily method based on the 14:00 temperature and humidity. For sub-daily steps the
		  // daily value is computed from the row at 14:00 (or the nearest hour) and spread over the day's
		  // steps in proportion to KDOWN, or evenly when KDOWN is missing or the day is incomplete.
		  if (getReadETFromFile())
		  {
			  System.out.println("Reading external ETP from file...");
		  }
		  double[] haudeStepEtp = null;
		  if (!usePenmanMonteith && !getReadETFromFile() && timestep < 24.)
		  {
			  haudeStepEtp = haudeSubDaily(forcing, haude, southernHemisphere);
		  }

//		  # calculate the rest
		  for(int krow=0;krow<rows;krow++)
		  {			  
			  double t14 = forcing.airTemperature[krow];
			  double rh14 = forcing.relativeHumidity[krow]; // $kf: added for internal ETP calculation
			  double doy = dates[krow].getDayOfYear();
			  double p = forcing.precipitation[krow];
//		    # col 3+4: snow (degree-day melt above 0 degC; below 0 degC all precipitation is snow)
		    double prevSwe = krow==0 ? init_swe : bucket_model[krow-1][SNOW_WATER_EQUI];
		    double potentialMelt = Math.max(0, landuse_DayDegree*t14*nt);
		    if(t14 < 0)
		    {
//		      # col 4: D Snow melt + rain
		      bucket_model[krow][SNOW_MELT_RAIN] = Math.min(potentialMelt, prevSwe+bucket_model[krow][PRECIPITATION]);
//		      # col 3: C Snow water equivalent (dependent on col 4)
		      bucket_model[krow][SNOW_WATER_EQUI] = prevSwe + bucket_model[krow][PRECIPITATION] - bucket_model[krow][SNOW_MELT_RAIN];
		    }
		    else
		    {
		      bucket_model[krow][SNOW_MELT_RAIN] = Math.min(potentialMelt, prevSwe) + bucket_model[krow][PRECIPITATION];
		      bucket_model[krow][SNOW_WATER_EQUI] = prevSwe - bucket_model[krow][SNOW_MELT_RAIN] + bucket_model[krow][PRECIPITATION];
		    }
//		    # col 7: G LAI
		    bucket_model[krow][LAI] = phenology.lai(Phenology.seasonalDay(doy, southernHemisphere));

//		    # col 5: E ETP Coeff (monthly Haude coefficient of the land use)
		    int month = dates[krow].getMonthValue();
		    bucket_model[krow][ETP_COEFF] = haude == null ? 0. : haude.forMonth(month, southernHemisphere);
		    
//		    # col 6: F ETP Input
		    if(readETfromFile)
		    {   
		    	  //# check if input variable is given
//		          # if yes, use given ETP0
		    	 bucket_model[krow][ETP_INPUT] = forcing.et0[krow];
		    }
		    else if(usePenmanMonteith)
		    {
		    	// Penman-Monteith with the available energy from the radiation forcing.
		    	// ETP_INPUT: wet surface (rs = 0), used for interception and litter like the Haude ETP.
		    	// ETP_DRY: transpiring surface with the Jarvi et al. (2011) surface conductance; its ratio to
		    	// ETP_INPUT scales the demand left for the soil (see soilEvapotranspiration).
		    	double pressure = forcing.hasPressure() ? forcing.pressure[krow]
		    			: PenmanMonteithEvaporation.pressureFromElevation(elevation);
		    	double windSpeed = forcing.windSpeed[krow];
		    	double kdown = forcing.kdown[krow];
		    	double soilWaterBefore = krow==0 ? Init_Value_Soil : bucket_model[krow-1][STORAGE];
		    	double availableEnergy = netRadiation[krow];
		    	double aerodynamicResistance = penmanMonteith.aerodynamicResistance(windSpeed);
		    	double conductance = penmanMonteith.surfaceConductance(kdown, t14, rh14, pressure, bucket_model[krow][LAI], Maximum_LAI,
		    			Field_Capacity - soilWaterBefore, Field_Capacity - Perm_Wilting_Point);
		    	double surfaceResistance = PenmanMonteithEvaporation.surfaceResistance(conductance);
		    	bucket_model[krow][ETP_INPUT] = PenmanMonteithEvaporation.evaporationDepth(
		    			penmanMonteith.latentHeatFlux(availableEnergy, t14, rh14, pressure, aerodynamicResistance, 0.), t14, ts);
		    	bucket_model[krow][ETP_DRY] = PenmanMonteithEvaporation.evaporationDepth(
		    			penmanMonteith.latentHeatFlux(availableEnergy, t14, rh14, pressure, aerodynamicResistance, surfaceResistance), t14, ts);
		    	bucket_model[krow][NET_RADIATION] = netRadiation[krow];
		    	bucket_model[krow][AERODYNAMIC_RESISTANCE] = aerodynamicResistance;
		    	bucket_model[krow][SURFACE_RESISTANCE] = surfaceResistance;
		    }
		    else 
		    { 
		    	//# if not calculate ETP
		    	if (haudeStepEtp != null)
		    	{
		    		bucket_model[krow][ETP_INPUT] = haudeStepEtp[krow];
		    	}
		    	else
		    	{
		    		bucket_model[krow][ETP_INPUT] = haudeDailyEtp(bucket_model[krow][ETP_COEFF], t14, rh14);
		    	}
		    }
		    
		    // NEW INTERCEPTION MODEL (which works with different time steps)
		    // Rutter, A.J., Kershaw, K.A., Robins, P.C., Morton, A.J., 1971. 
		    // A predictive model of rainfall interception in forests, 1. Derivation 
		    // of the model from observations in a plantation of Corsican pine. 
		    // Agr. Meteorol. 9, 367–384.

//		    # col 8: H I-Cap
		    bucket_model[krow][I_CAP] = layer_thickness*bucket_model[krow][LAI];

		    double intc_drainage_max = intc_drainage_max_d / SECONDS_PER_DAY * ts; // max. drainage per time step
		    // Maximum Drainage rate adjusted to capacity
	 	    if(intc_drainage_max > bucket_model[krow][I_CAP]) intc_drainage_max = bucket_model[krow][I_CAP];
		    
		    // define empty storage for first time step and correct for adjustments in C
		    double t_surplus = 0.;
                    double ci = 0.; // first time step: initialize interception capacity, assuming empty storage
		     if(krow>0){
		    	ci = bucket_model[krow-1][I_BAL]; // intercepted water from previous time step
			// Adjust storage as a result of changing capacity
		    	if( bucket_model[krow-1][I_CAP] !=  bucket_model[krow][I_CAP]  && ci >  bucket_model[krow][I_CAP] ){
			        t_surplus = ci - bucket_model[krow][I_CAP];
        			ci = bucket_model[krow][I_CAP];
		        }
    		    }
		    double direct_throughfall =  bucket_model[krow][SNOW_MELT_RAIN] *(1.-vcover);
		    double intc_in =  bucket_model[krow][SNOW_MELT_RAIN] - direct_throughfall;
		    double intc_stor_guess = intc_in+ci; // storage depth

//		    # col 9: I ETi: Evaporation from wetted leaf 
		    double intc_drainage;
		    if(bucket_model[krow][I_CAP] <= 0.)
		    {
		    	// no canopy storage (LAI or layer thickness 0): nothing is intercepted, all water passes through
		    	// (the formulas below would also divide by the zero capacity)
		    	bucket_model[krow][INT_ETI_LEAF] = 0.;
		    	intc_drainage = intc_stor_guess;
		    }
		    else
		    {
		    // Saturated canopy: at most the water the canopy can hold (I-Cap) evaporates in the step, as in the
		    // original SIMPEL spreadsheet (min(I-Cap, ETP)); water above the capacity drains. Evaporating up to the
		    // full ETP from all intercepted rain let even a nearly leafless canopy evaporate the whole demand on
		    // every rain day.
		    if(intc_stor_guess > bucket_model[krow][I_CAP])
			    bucket_model[krow][INT_ETI_LEAF] = Math.min(bucket_model[krow][ETP_INPUT],bucket_model[krow][I_CAP]);
    		    else
			    bucket_model[krow][INT_ETI_LEAF] = Math.min(bucket_model[krow][ETP_INPUT]*intc_stor_guess/ bucket_model[krow][I_CAP], intc_stor_guess);
//		    # col 10: J I-Bal.: Temp calcalation
		    if(bucket_model[krow][I_CAP] < intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]) 
			intc_drainage = Math.max(intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]-bucket_model[krow][I_CAP],intc_drainage_max);
    		    else
		    	intc_drainage = Math.min(intc_drainage_max*Math.exp(intc_drainage_coeff_b*(intc_stor_guess-bucket_model[krow][INT_ETI_LEAF] - bucket_model[krow][I_CAP])/bucket_model[krow][I_CAP]), intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]);
		    }

		    // updated meaning of col 10 "I-BAL" is interception storage
		    bucket_model[krow][I_BAL] = intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]-intc_drainage;
		    if(bucket_model[krow][I_BAL] <0){
      			intc_drainage = Math.max(intc_stor_guess+ bucket_model[krow][I_BAL],0);
      			 bucket_model[krow][I_BAL] = 0.;
    		    }
		    
//		    # col 11: K I-Prec.: Throughfall   
		    bucket_model[krow][I_PREC] = direct_throughfall + intc_drainage + t_surplus;
		    
//		    # col 12: L I-Rem.: Remaining ETa passed to subsequent model
		    bucket_model[krow][I_REM] = bucket_model[krow][ETP_INPUT] - bucket_model[krow][INT_ETI_LEAF];	

//		    # col 13+14+15: litter layer, litter reduction adjusted to the time step of the input series
		    double prevContent = krow==0 ? Init_Value_Litter : bucket_model[krow-1][CONTENT];
//		    # col 13: M ETi Litter
		    bucket_model[krow][I_ETI_LITTER] = litterEvaporation(bucket_model[krow][I_REM], Cap_Litter,
		    		prevContent + bucket_model[krow][I_PREC], Litter_Reduction_factor_dt);
//		    # col 14: N Bilanz (needed for col 13 & 15)
		    bucket_model[krow][BILANZ] = prevContent + bucket_model[krow][I_PREC] - bucket_model[krow][I_ETI_LITTER];
//		    # col 15: O Content (needed for col 13 & 14)
		    bucket_model[krow][CONTENT] = Math.min(Cap_Litter, Math.max(0, bucket_model[krow][BILANZ]));
//		    # col 16: P S-REstn
		    bucket_model[krow][S_RESTN] = Math.max(0, (bucket_model[krow][BILANZ]-bucket_model[krow][CONTENT]));
		    
//		    # col 17-24: soil store, groundwater recharge with the Glugla approach adjusted to the time step length
		    double prevStorage = krow==0 ? Init_Value_Soil : bucket_model[krow-1][STORAGE];
//		    # col 17: Q Inf-Limit
		    // infiltration limit per time step: a fixed fraction of the soil deficit per day, scaled to the step length
		    bucket_model[krow][INF_LIMIT] = (Field_Capacity-prevStorage)*Infiltration_fraction*nt;
//		    # col 18: R P-Inf
		    bucket_model[krow][P_LINF] = Math.min(bucket_model[krow][S_RESTN],bucket_model[krow][INF_LIMIT])*(1.-Direct_runoff_factor/100.);
//		    # col 19: S S-Rest
		    bucket_model[krow][REST_ETA] = -1*Math.min(0,bucket_model[krow][BILANZ])+bucket_model[krow][I_REM]-bucket_model[krow][I_ETI_LITTER];
//		    # col 20: T Balance soil
		    bucket_model[krow][BALANCE_SOIL] = prevStorage + bucket_model[krow][P_LINF];
//		    # col 21: U ETa
		    bucket_model[krow][ETA] = soilEvapotranspiration(usePenmanMonteith, bucket_model[krow], Start_of_Reduction, Perm_Wilting_Point);
//		    # col 22: V ET-Balance
		    bucket_model[krow][ET_BALANCE] = bucket_model[krow][BALANCE_SOIL] - bucket_model[krow][ETA];
//		    # col 23: W Seepage
		    bucket_model[krow][SEEPAGE] = gluglaSeepage(bucket_model[krow][ET_BALANCE], Field_Capacity, Perm_Wilting_Point, Lambda, nt);
//		    # col 24: X Storage
		    bucket_model[krow][STORAGE] = soilStorage(bucket_model[krow][ET_BALANCE], bucket_model[krow][SEEPAGE], Field_Capacity);
//		    # col 25: Y surface runoff
		    if(bucket_model[krow][ET_BALANCE] > Field_Capacity)
		    {
		      bucket_model[krow][SURFACE_RUNOFF] = bucket_model[krow][ET_BALANCE]-Field_Capacity+bucket_model[krow][S_RESTN]
		    		  -bucket_model[krow][P_LINF];
		    }
		    else
		    {
		        bucket_model[krow][SURFACE_RUNOFF] = bucket_model[krow][S_RESTN]-bucket_model[krow][P_LINF];
		    }
		    
//		    # col 26: Z Runofftotal
		    bucket_model[krow][RUNOFF_TOTAL] = bucket_model[krow][SURFACE_RUNOFF] + bucket_model[krow][SEEPAGE];
		    
//		    # col 27: AA I-Leaf
		    bucket_model[krow][I_LEAF] = bucket_model[krow][ETP_INPUT] -  bucket_model[krow][I_REM];
		    
//		    # col 28: AB I-Litter
		    bucket_model[krow][I_LITTER] = bucket_model[krow][I_REM] -  bucket_model[krow][REST_ETA];
		    
//		    # col 29: AC ETa Total
		    bucket_model[krow][ETA_TOTAL] = bucket_model[krow][I_LITTER] + bucket_model[krow][I_LEAF] + bucket_model[krow][ETA];
		    bucket_model[krow][LATENT_HEAT_FLUX] = bucket_model[krow][ETA_TOTAL] * PenmanMonteithEvaporation.latentHeatOfVaporisation(t14) / ts;

		    sum_prec = sum_prec + p;
		    sum_etr  = sum_etr  + bucket_model[krow][ETA_TOTAL];
		    sum_runoff = sum_runoff + bucket_model[krow][RUNOFF_TOTAL];
		  }
//		  # water balance check
		  double water_balance = sum_prec - sum_etr - sum_runoff + init_swe + init_stor + Init_Value_Litter - bucket_model[rows-1][STORAGE] - bucket_model[rows-1][SNOW_WATER_EQUI] - bucket_model[rows-1][I_BAL] - bucket_model[rows-1][CONTENT];
		  System.out.println("water balance check");
		  System.out.println(water_balance);
		  
		  return bucket_model;
		}
	

	/** Haude potential evapotranspiration (mm/day) from the 14:00 air temperature (degC) and relative humidity (%). */
	static double haudeDailyEtp(double haudeCoefficient, double t14, double rh14)
	{
		double saturationVapourPressure = 6.11 * Math.pow(10, (7.5 * t14) / (237.3 + t14)); // hPa
		return Math.min(HAUDE_MAX_ETP_PER_DAY, haudeCoefficient * saturationVapourPressure * (1. - rh14 / 100.));
	}

	/** Haude ETP per sub-daily step: daily value from the row nearest 14:00, distributed over the day. */
	static double[] haudeSubDaily(Forcing forcing, HaudeCoefficients haude, boolean southernHemisphere)
	{
		LocalDateTime[] dates = forcing.dates;
		int rows = forcing.size();
		int stepsPerDay = (int) Math.round(24. / forcing.timestepHours);
		double[] stepEtp = new double[rows];
		int dayStart = 0;
		while (dayStart < rows)
		{
			LocalDate day = dates[dayStart].toLocalDate();
			int dayEnd = dayStart;
			while (dayEnd < rows && dates[dayEnd].toLocalDate().equals(day)) dayEnd++;

			int haudeRow = dayStart;
			for (int i=dayStart;i<dayEnd;i++)
			{
				if (Math.abs(dates[i].getHour() - HAUDE_HOUR) < Math.abs(dates[haudeRow].getHour() - HAUDE_HOUR)) haudeRow = i;
			}
			double coefficient = haude.forMonth(day.getMonthValue(), southernHemisphere);
			double dailyEtp = haudeDailyEtp(coefficient, forcing.airTemperature[haudeRow], forcing.relativeHumidity[haudeRow]);

			double kdownSum = 0.;
			if (forcing.hasKdown())
			{
				for (int i=dayStart;i<dayEnd;i++) kdownSum += Math.max(0., forcing.kdown[i]);
			}
			boolean completeDay = (dayEnd - dayStart) == stepsPerDay;
			for (int i=dayStart;i<dayEnd;i++)
			{
				double share = 1. / stepsPerDay;
				if (forcing.hasKdown() && completeDay && kdownSum > 0.)
				{
					share = Math.max(0., forcing.kdown[i]) / kdownSum;
				}
				stepEtp[i] = dailyEtp * share;
			}
			dayStart = dayEnd;
		}
		return stepEtp;
	}

	/**
	 * Actual evapotranspiration from the soil: the remaining demand, reduced linearly between the
	 * start-of-reduction point and the permanent wilting point. Never negative and never more than
	 * the water above the wilting point.
	 */
	static double actualSoilEvapotranspiration(double remainingDemand, double soilWater, 
			double startOfReduction, double wiltingPoint)
	{
		double availableWater = Math.max(0., soilWater - wiltingPoint);
		double reductionFactor = 1.;
		if (soilWater <= startOfReduction)
		{
			reductionFactor = availableWater / (startOfReduction - wiltingPoint);
		}
		return Math.min(Math.max(0., remainingDemand * reductionFactor), availableWater);
	}

	/**
	 * Soil evapotranspiration for the chosen ET method. HAUDE/ET0: SIMPEL's linear reduction below the
	 * start-of-reduction point. PM: the soil-moisture limitation is already in the surface conductance,
	 * so the remaining wet-surface demand is scaled by ETP_DRY / ETP_INPUT and SIMPEL's reduction is not
	 * applied a second time. Both are limited to the water above the wilting point.
	 */
	static double soilEvapotranspiration(boolean usePenmanMonteith, double[] row, double startOfReduction, double wiltingPoint)
	{
		if (!usePenmanMonteith)
		{
			return actualSoilEvapotranspiration(row[REST_ETA], row[BALANCE_SOIL], startOfReduction, wiltingPoint);
		}
		double dryToWetRatio = row[ETP_INPUT] > 0. ? row[ETP_DRY] / row[ETP_INPUT] : 0.;
		return Math.min(Math.max(0., row[REST_ETA] * dryToWetRatio), Math.max(0., row[BALANCE_SOIL] - wiltingPoint));
	}

	/** Reads the PM parameters from Soil_physics.txt; keys that are absent keep the defaults. */
	static PenmanMonteithEvaporation configurePenmanMonteith(ModelParameters parameters)
	{
		PenmanMonteithEvaporation pm = new PenmanMonteithEvaporation();
		pm.albedo = parameters.optional("Albedo", pm.albedo, 0., 1.);
		pm.surfaceEmissivity = parameters.optional("Surface emissivity", pm.surfaceEmissivity, 0.5, 1.);
		pm.vegetationHeight = parameters.optional("Vegetation height", pm.vegetationHeight, 0.001, 100.);
		pm.measurementHeight = parameters.optional("Measurement height", pm.measurementHeight, 0.1, 500.);
		if (pm.measurementHeight <= 0.67 * pm.vegetationHeight)
		{
			throw new IllegalArgumentException("'Measurement height' (" + pm.measurementHeight + " m) must be above the "
					+ "displacement height 0.67 x 'Vegetation height' (" + 0.67 * pm.vegetationHeight + " m)");
		}
		pm.maxConductanceG1 = parameters.optional("G1", pm.maxConductanceG1);
		pm.kdownShapeG2 = parameters.optional("G2", pm.kdownShapeG2);
		pm.humiditySlopeG3 = parameters.optional("G3", pm.humiditySlopeG3);
		pm.humidityThresholdG4 = parameters.optional("G4", pm.humidityThresholdG4);
		pm.optimumTemperatureG5 = parameters.optional("G5", pm.optimumTemperatureG5);
		pm.soilMoistureShapeG6 = parameters.optional("G6", pm.soilMoistureShapeG6);
		pm.upperTemperatureTH = parameters.optional("TH", pm.upperTemperatureTH);
		pm.lowerTemperatureTL = parameters.optional("TL", pm.lowerTemperatureTL);
		pm.maxKdown = parameters.optional("Kmax", pm.maxKdown);
		return pm;
	}

	/**
	 * Evaporation from the litter layer as in the SIMPEL spreadsheet: MIN(remaining demand; MIN(capacity;
	 * content + throughfall) / factor). The reduction factor divides the smaller of the capacity and the
	 * available water; before, it divided only the water (min(cap, water / factor)), so the litter
	 * evaporated up to the full capacity per step. The factor is already scaled to the time step.
	 */
	static double litterEvaporation(double remainingDemand, double capacity, double availableWater, double reductionFactor)
	{
		return Math.min(remainingDemand, Math.min(capacity, availableWater) / reductionFactor);
	}

	/**
	 * Seepage below the root zone after Glugla: lambda * (W - PWP)^2 per day, with W capped at field
	 * capacity. Zero at or below the wilting point and never more than the water between the wilting point
	 * and min(W, FC), so the store cannot drain below the wilting point.
	 */
	static double gluglaSeepage(double soilWater, double fieldCapacity, double wiltingPoint, double lambda, double nt)
	{
		double drainableWater = Math.max(0., Math.min(soilWater, fieldCapacity) - wiltingPoint);
		double seepage = lambda * drainableWater * drainableWater * nt;
		return Math.min(seepage, drainableWater);
	}

	/**
	 * Soil storage at the end of a step. Water above field capacity runs off (see SURFACE_RUNOFF), and the
	 * seepage always leaves the store, also when the store was above field capacity: storage = FC - seepage.
	 * Before, the storage was reset to FC there: the seepage of that step was counted as outflow but never
	 * taken out of the store, which created water and broke the water balance.
	 */
	static double soilStorage(double etBalance, double seepage, double fieldCapacity)
	{
		return Math.min(etBalance, fieldCapacity) - seepage;
	}

	public void writeFile(String text, String filename)
	{
		try
		{
			Files.write(Paths.get(filename), (text + linefeed).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Cannot write " + filename, e);
		}
	}
	public void write_csv(double[][] bucket_model, String output)
	{
//		Date	Precipitation	Snow_Water_Equi	Snow_melt+rain	ETP_Coeff	ETP_Input	LAI	I-Cap	Int. ETi(leaf)	I-Bal	I-Prec	I-Rem	I_ETi(litter)	Bilanz	Content	S-REstn	Inf-Limit	P-linf	Rest-ETA	Balance_soil	ETa	ET-Balance	Seepage	Storage	Surface_runoff	Runoff_total	I-Leaf	I-Litter	ETaTotal
		String header = "Date" + tab
				+ "Precipitation" + tab
				+ "Snow_Water_Equi" + tab
				+ "Snow_melt+rain" + tab
				+ "ETP_Coeff" + tab
				+ "ETP_Input" + tab
				+ "LAI" + tab
				+ "I-Cap" + tab
				+ "Int. ETi(leaf)" + tab
				+ "I-Bal" + tab
				+ "I-Prec" + tab
				+ "I-Rem" + tab
				+ "I_ETi(litter)" + tab
				+ "Bilanz" + tab
				+ "Content" + tab
				+ "S-REstn" + tab
				+ "Inf-Limit" + tab
				+ "P-linf" + tab
				+ "Rest-ETA" + tab
				+ "Balance_soil" + tab
				+ "ETa" + tab
				+ "ET-Balance" + tab
				+ "Seepage" + tab
				+ "Storage" + tab
				+ "Surface_runoff" + tab
				+ "Runoff_total" + tab
				+ "I-Leaf" + tab
				+ "I-Litter" + tab
				+ "ETaTotal" + tab
				+ "K_DOWN" + tab
				+ "WINDSPEED" + tab
				+ "L_DOWN" + tab
				+ "NetRadiation" + tab
				+ "ra" + tab
				+ "rs" + tab
				+ "ETP_dry" + tab
				+ "QE";
		StringBuilder sb = new StringBuilder();
		sb.append(header + linefeed);
		int width = bucket_model[0].length;
		int height = bucket_model.length;
		for (int i=0;i<height;i++)
		{
			sb.append(bucket_model_dates[i]);
			for (int j=0;j<width;j++)
			{
				sb.append(tab + bucket_model[i][j]);
			}
			sb.append(linefeed);
		}
		writeFile(sb.toString(), output);
	}

	public void setDirectory(String directory)
	{
		this.directory = directory;
	}	
	public boolean getReadETFromFile()
	{
		return readETfromFile != null && readETfromFile;
	}
	public void setReadETFromFile(String readET)
	{
		if (readET.equals("readET"))
		{
			readETfromFile=true;
		}
		else
		{
			readETfromFile=false;
		}
	}
}
