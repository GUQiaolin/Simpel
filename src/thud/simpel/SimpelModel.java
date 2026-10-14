package thud.simpel;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.TreeMap;

public class SimpelModel
{	
	  //TODO utilize kdown, ldown, wind speed, now available in Global_Input
	  //TODO, Use timestep values, Hourly value for timestep now configured in Soil_physics: Timestep;24;
	
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
    // columns filled by the PM evapotranspiration method (ET method;PM)
    public final static int NET_RADIATION=31;          // W m-2
    public final static int STORAGE_HEAT=32;           // W m-2
    public final static int AERODYNAMIC_RESISTANCE=33; // s m-1
    public final static int SURFACE_RESISTANCE=34;     // s m-1
    public final static int ETP_DRY=35;                // mm/step, Penman-Monteith with surface resistance
    public final static int LATENT_HEAT_FLUX=36;       // W m-2, from ETaTotal (all methods)
    public final static int NUMBER_OF_COLUMNS=37;
    
    public static int INPUT_DATE=0;
    public static int INPUT_DOY=1;	
    public static int INPUT_P=2;	
    public static int INPUT_T14=3;	
    public static int INPUT_R14=4;	
    public static int INPUT_ET0=5;    
    public static int INPUT_K_DOWN=6;
    public static int INPUT_WIND_SPEED=7;
    public static int INPUT_L_DOWN=8;
    public static int INPUT_PRESSURE=9;
    
    public final static int LAI_IND = 0;
    public final static int LAI_DOY = 1;
    public final static int LAI_PHASE = 2;
    public final static int LAI_LAI = 3;
    public final static double SECONDS_PER_DAY = 86400.;
    // Fraction of the soil water deficit (field capacity minus storage) that can infiltrate per day
    public final static double INFILTRATION_FRACTION_PER_DAY = 0.25;
    // Upper limit of the Haude potential evapotranspiration
    public final static double HAUDE_MAX_ETP_PER_DAY = 7.;   // mm/day
    // Haude uses the air temperature and humidity at this hour
    public final static int HAUDE_HOUR = 14;
    
    
    
    public final static String FIELD_CAPACITY_PERCENT = "Field Capacity %";
    public final static String PERMANENT_WILTING_POINT = "Permanent Wilting Point %";
    public final static String START_OF_REDUCTION_PERCENT = "Start of Reduction %";
    public final static String ROOT_DEPTH = "Root Depth";
    public final static String INIT_VALUE_SOIL_PERCENT = "Init-Value Soil %";
    //public final static String FIELD_CAPACITY = "Field Capacity";
    //public final static String PERM_WILTING_POINT = "Perm. Wilting Point";
    //public final static String FWC = "FWC";
    //public final static String START_OF_REDUCTION = "Start of Reduction";
    //public final static String INIT_VALUE_SOIL = "Init-Value Soil";
    //public final static String DEPTH_OF_SOIL = "Depth of soil";	
    public final static String LAND_USE = "Land use";
    public final static String MINIMUM_LAI = "Minimum LAI";
    public final static String MAXIMUM_LAI = "Maximum LAI";
    public final static String INTC_COVERED_FRACTION = "Vegetation Fraction";
    public final static String INTC_LAYER_THICKNESS = "Layer Thickness";
    public final static String INTC_DRAINAGE_EXP_B = "Drainage Coeff. b";
    public final static String INTC_DRAINAGE_MAX = "Max. Drainage Rate";
    public final static String DIRECT_RUNOFF_FACTOR = "Direct runoff factor";				
    public final static String GLUGLA_C = "Glugla coeff.";
    //public final static String LAMBDA = "Lambda";	
    public final static String CAP_LITTER = "Cap. Litter";
    public final static String INIT_VALUE_LITTER = "Init-Value Litter";
    public final static String LITTER_REDUCTION_FACTOR = "Litter Reduction factor";	
    public final static String TIMESTEP = "Timestep";	
    // Optional keys for the evapotranspiration method; see GLOSSARY.md
    public final static String ET_METHOD = "ET method"; // HAUDE (default) or PM
    
    public final String tab = "\t";
    public final String linefeed = "\n";
    private ArrayList<String> bucket_model_dates;
    
  //public int numColumnsInput = 6;  //hardcoding this for now.
    private Boolean readETfromFile = null; // null: decide from the ET0 column in Global_Input.txt
    // private double ts = 86400.; // model time step in seconds
    int krowEnd = 0;
    boolean hasKdown = false;
    boolean hasWindSpeed = false;
    boolean hasLdown = false;
    boolean hasPressure = false;

    //public double INTC_DRAINAGE_MAX_D = 2.88; // mm/d
    //public double INTC_DRAINAGE_EXP_B = 3.7;  // mm^(-1)

	public static void main(String[] args)
	{
		SimpelModel s = new SimpelModel();
		if (args.length<1)
		{
			System.err.println("Usage: java thud.simpel.SimpelModel <input directory> [readET|dontReadET]");
			System.exit(1);
		}
		s.setDirectory(args[0]);
		if (args.length>1)
		{
			String arg1 = args[1];
			s.setReadETFromFile(arg1);
		}
		s.process();
	}
	
	public void process()
	{
		ArrayList<String[]> Input = readLargerTextFileAlternateToArray(Paths.get(directory, "Global_Input.txt").toString(), true, "\\s+",false);
		ArrayList<String[]> InputHeader = readLargerTextFileAlternateToArray(Paths.get(directory, "Global_Input.txt").toString(), false, "\\s+",true);
		matchInputHeaders(InputHeader);
		ArrayList<String[]> Landuse = readLargerTextFileAlternateToArray(Paths.get(directory, "Land_Use.txt").toString(), true, "\\s+",false);
		ArrayList<String[]> Landuses = readLargerTextFileAlternateToArray(Paths.get(directory, "Land_Use.txt").toString(), false, "\\s+",false);
		String[] landusesHeader = Landuses.get(0);
		TreeMap<String,Integer> landusesIndexes = new TreeMap<String,Integer>();
		for (int i=1;i<landusesHeader.length;i++)
		{
			landusesIndexes.put( landusesHeader[i], i);
		}
		ArrayList<String[]> LAI_model = readLargerTextFileAlternateToArray(Paths.get(directory, "LAI_model.txt").toString(), true, "\\s+",false);
		TreeMap<String,String[]> Soil = readLargerTextFileAlternateToTreeMap(Paths.get(directory, "Soil_physics.txt").toString(), false, ";");
		String output = Paths.get(directory, "SIMPLE_bucket_model.csv").toString();

		double[][] bucket_model = SIMPLE_function(Input, Landuse, LAI_model, Soil, landusesIndexes);
		write_csv(bucket_model, output);

	}
	
	
	public double[][] SIMPLE_function(ArrayList<String[]> Input, ArrayList<String[]> Landuse, 
			ArrayList<String[]> LAI_model, TreeMap<String,String[]> Soil, TreeMap<String,Integer> landusesIndexes)
	{		
		double Field_Capacity_Percent=Double.parseDouble(Soil.get(FIELD_CAPACITY_PERCENT)[1]);//Soil[1][2]
		double Permanent_Wilting_Point=Double.parseDouble(Soil.get(PERMANENT_WILTING_POINT)[1]);//Soil[2][2]
		// Start of Reduction is only used by the HAUDE and ET0 methods; in PM mode the surface conductance
		// limits ET as the soil dries, so the key can be left out
		double Start_of_Reduction_Percent = Soil.containsKey(START_OF_REDUCTION_PERCENT) 
				? Double.parseDouble(Soil.get(START_OF_REDUCTION_PERCENT)[1]) : Double.NaN;
		double Root_Depth=Double.parseDouble(Soil.get(ROOT_DEPTH)[1]);//Soil[4][2]
		double Init_Value_Soil_Percent=Double.parseDouble(Soil.get(INIT_VALUE_SOIL_PERCENT)[1]);//Soil[5][2]
		double Field_Capacity= Field_Capacity_Percent * Root_Depth / 10.;//Double.parseDouble(Soil.get(FIELD_CAPACITY)[1]);//Soil[6][2]
		double Perm_Wilting_Point=Permanent_Wilting_Point * Root_Depth / 10.;//Double.parseDouble(Soil.get(PERM_WILTING_POINT)[1]);//Soil[7][2]
		double fwc=Field_Capacity-Perm_Wilting_Point;//Double.parseDouble(Soil.get(FWC)[1]);//Soil[8][2]
		double Start_of_Reduction= Start_of_Reduction_Percent * Root_Depth / 10.;///Double.parseDouble(Soil.get(START_OF_REDUCTION)[1]);//Soil[9][2]
		double Init_Value_Soil=Init_Value_Soil_Percent * Root_Depth / 10.;//Double.parseDouble(Soil.get(INIT_VALUE_SOIL)[1]);//Soil[10][2]
		double Depth_of_soil=Root_Depth*10;//Double.parseDouble(Soil.get(DEPTH_OF_SOIL)[1]);//Soil[11][2]
		String Land_use=Soil.get(LAND_USE)[1];//Soil[12][2]
		double Minimum_LAI=Double.parseDouble(Soil.get(MINIMUM_LAI)[1]);//Soil[13][2]
		double Maximum_LAI=Double.parseDouble(Soil.get(MAXIMUM_LAI)[1]);//Soil[14][2]
		double vcover=Double.parseDouble(Soil.get(INTC_COVERED_FRACTION)[1]);
		double layer_thickness=Double.parseDouble(Soil.get(INTC_LAYER_THICKNESS)[1]);
		double intc_drainage_coeff_b=Double.parseDouble(Soil.get(INTC_DRAINAGE_EXP_B)[1]);
		double intc_drainage_max_d=Double.parseDouble(Soil.get(INTC_DRAINAGE_MAX)[1]);
		double Direct_runoff_factor=Double.parseDouble(Soil.get(DIRECT_RUNOFF_FACTOR)[1]);//Soil[15][2]			
		double Glugla_Coeff_c=Double.parseDouble(Soil.get(GLUGLA_C)[1]);//Soil[16][2]
		double Lambda=Glugla_Coeff_c/Depth_of_soil/Depth_of_soil;///Double.parseDouble(Soil.get(LAMBDA)[1]);//Soil[17][2]
		double Cap_Litter=Double.parseDouble(Soil.get(CAP_LITTER)[1]);//Soil[18][2]
		double Init_Value_Litter=Double.parseDouble(Soil.get(INIT_VALUE_LITTER)[1]);//Soil[19][2]
		double Litter_Reduction_factor=Double.parseDouble(Soil.get(LITTER_REDUCTION_FACTOR)[1]);//Soil[20][2]

		// The time step, day of year and month come from the Date column of Global_Input.txt:
		// dd.mm.yyyy for daily rows, dd.mm.yyyy.hh for sub-daily rows (e.g. 02.01.1995.12).
		LocalDateTime[] dates = new LocalDateTime[Input.size()];
		for (int i=0;i<Input.size();i++)
		{
			dates[i] = parseDate(Input.get(i)[INPUT_DATE]);
		}
		double timestep = timestepHours(dates, Input.get(0)[INPUT_DATE]);
		if (Soil.containsKey(TIMESTEP))
		{
			double configuredTimestep = Double.parseDouble(Soil.get(TIMESTEP)[1].trim());
			if (Math.abs(configuredTimestep - timestep) > 1e-9)
			{
				throw new IllegalArgumentException("Timestep in Soil_physics.txt (" + configuredTimestep 
						+ " h) does not match the dates in Global_Input.txt (" + timestep 
						+ " h). The time step is now taken from the dates; remove the Timestep line.");
			}
		}
		
		int landuseIndex = landusesIndexes.get(Land_use); 



		// Check parameters (just to show what is mandatory and what is derived, can be deleted)
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
		double[][] bucket_model = new double[Input.size()][NUMBER_OF_COLUMNS];
		bucket_model_dates = new ArrayList<String>();
		
//		  # copy Input into bucket-model
//		  #col 1: A Date
//		  # col 2: B Precipitation	
		  for (int i=0;i<Input.size();i++)
		  {
			  bucket_model_dates.add(Input.get(i)[INPUT_DATE]);
			  bucket_model[i][PRECIPITATION]=Double.parseDouble(Input.get(i)[INPUT_P]);
			  if (hasKdown)
			  {
				  bucket_model[i][K_DOWN]=Double.parseDouble(Input.get(i)[INPUT_K_DOWN]);
			  }
			  if (hasWindSpeed)
			  {
				  bucket_model[i][WIND_SPEED]=Double.parseDouble(Input.get(i)[INPUT_WIND_SPEED]);
			  }
			  if (hasLdown)
			  {
				  bucket_model[i][L_DOWN]=Double.parseDouble(Input.get(i)[INPUT_L_DOWN]);
			  }
		  }
		  
		  
//		  # water balance check
		  double sum_prec   = 0.;
		  double sum_etr    = 0.;
		  double sum_runoff = 0.;
		  double init_stor  = Init_Value_Soil; 
		  double init_swe   = 0.;
		    
		  // LAI_model.txt has four rows: start and end of green-up, start and end of senescence.
		  // LAI is held at the minimum before green-up and after senescence, at the maximum in between,
		  // and interpolated linearly during green-up and senescence.
		  double[][] laiModel = new double[4][4];
		  for (int i=0;i<LAI_model.size();i++)
		  {
			  laiModel[i][LAI_IND] = Double.parseDouble(LAI_model.get(i)[0]);
			  laiModel[i][LAI_DOY] = Double.parseDouble(LAI_model.get(i)[1]);
			  laiModel[i][LAI_PHASE] = Double.parseDouble(LAI_model.get(i)[2]);
		  }
		  
//		  # update LAI model according to soil physics ($kf 2023-03-28) -> ignore land use table
//		  # update Litter according to land use table
		  laiModel[0][LAI_LAI]= Double.parseDouble(Soil.get(MINIMUM_LAI)[1]);
		  laiModel[1][LAI_LAI]= Double.parseDouble(Soil.get(MAXIMUM_LAI)[1]);
		  laiModel[2][LAI_LAI]= Double.parseDouble(Soil.get(MAXIMUM_LAI)[1]);
		  laiModel[3][LAI_LAI]= Double.parseDouble(Soil.get(MINIMUM_LAI)[1]);
//		  # update Litter according to land use table
		  double landuse_DayDegree = landuseValue(Landuse, "Day_degree", landuseIndex);
		  
		  double greenUpStartLai = laiModel[0][LAI_LAI];
		  double greenUpStartDoy = laiModel[0][LAI_DOY];
		  
		  double greenUpEndLai = laiModel[1][LAI_LAI];
		  double greenUpEndDoy = laiModel[1][LAI_DOY];
		  
		  double senescenceStartLai = laiModel[2][LAI_LAI];
		  double senescenceStartDoy = laiModel[2][LAI_DOY];
		  
		  double senescenceEndLai = laiModel[3][LAI_LAI];
		  double senescenceEndDoy = laiModel[3][LAI_DOY];

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
		  String etMethod = Soil.containsKey(ET_METHOD) ? Soil.get(ET_METHOD)[1].trim().toUpperCase() : "HAUDE";
		  boolean usePenmanMonteith = etMethod.equals("PM");
		  if (!usePenmanMonteith && !etMethod.equals("HAUDE"))
		  {
			  throw new IllegalArgumentException("Unknown ET method '" + etMethod + "', use HAUDE or PM");
		  }
		  if (!usePenmanMonteith && Double.isNaN(Start_of_Reduction_Percent))
		  {
			  throw new IllegalArgumentException("'" + START_OF_REDUCTION_PERCENT + "' is required in Soil_physics.txt for ET method " 
					  + (getReadETFromFile() ? "ET0 from file" : "HAUDE"));
		  }
		  if (usePenmanMonteith && !Double.isNaN(Start_of_Reduction_Percent))
		  {
			  System.out.println("Note: '" + START_OF_REDUCTION_PERCENT + "' is ignored with ET method PM");
		  }
		  PenmanMonteithEvaporation penmanMonteith = null;
		  double elevation = optionalValue(Soil, "Elevation", 0.);
		  double[] netRadiation = null;
		  double[] storageHeat = null;
		  if (usePenmanMonteith)
		  {
			  if (!hasKdown || !hasWindSpeed)
			  {
				  throw new IllegalArgumentException("ET method PM needs KDOWN and WindSpeed columns in Global_Input.txt");
			  }
			  if (getReadETFromFile())
			  {
				  throw new IllegalArgumentException("ET method PM cannot be combined with reading ET0 from file");
			  }
			  boolean dailySteps = timestep >= 24.;
			  if (dailySteps)
			  {
				  System.out.println("PM ET at daily steps: storage heat set to 0 (it roughly cancels over a day); "
						  + "forcing should be daily means of KDOWN, LDOWN, Ta, RH, WindSpeed");
			  }
			  if (!hasLdown)
			  {
				  System.out.println("Warning: no LDOWN column, using clear-sky long-wave radiation (too low under cloud)");
			  }
			  penmanMonteith = configurePenmanMonteith(Soil);
			  System.out.println("ET method: PM (Penman-Monteith, Jarvi et al. 2011 surface conductance)");
			  netRadiation = new double[Input.size()];
			  storageHeat = new double[Input.size()];
			  for (int i=0;i<Input.size();i++)
			  {
				  double airTemperature = Double.parseDouble(Input.get(i)[INPUT_T14]);
				  double relativeHumidity = Double.parseDouble(Input.get(i)[INPUT_R14]);
				  double kdown = Double.parseDouble(Input.get(i)[INPUT_K_DOWN]);
				  double ldown = hasLdown ? Double.parseDouble(Input.get(i)[INPUT_L_DOWN])
						  : PenmanMonteithEvaporation.clearSkyLdown(airTemperature, relativeHumidity);
				  netRadiation[i] = penmanMonteith.netRadiation(kdown, ldown, airTemperature);
			  }
			  for (int i=0;i<Input.size();i++)
			  {
				  // centred difference of Q* in W m-2 per hour (one-sided at the ends)
				  int previous = Math.max(i-1, 0);
				  int next = Math.min(i+1, Input.size()-1);
				  double tendency = next > previous ? (netRadiation[next]-netRadiation[previous]) / ((next-previous)*timestep) : 0.;
				  // OHM is fitted to hourly data; over a whole day the storage heat flux is close to zero
				  storageHeat[i] = dailySteps ? 0. : penmanMonteith.storageHeatFlux(netRadiation[i], tendency);
			  }
		  }
		  
		   
		  // Haude is a daily method based on the 14:00 temperature and humidity. For sub-daily steps the
		  // daily value is computed from the row at 14:00 (or the nearest hour) and spread over the day's
		  // steps in proportion to KDOWN, or evenly when KDOWN is missing or the day is incomplete.
		  double[] haudeStepEtp = null;
		  if (!usePenmanMonteith && !getReadETFromFile() && timestep < 24.)
		  {
			  haudeStepEtp = haudeSubDaily(Input, dates, Landuse, landuseIndex, timestep);
		  }

//		  # calculate the rest
		  for(int krow=0;krow<Input.size();krow++)
		  {			  
			  double t14 = Double.parseDouble(Input.get(krow)[INPUT_T14]);
			  double rh14 = Double.parseDouble(Input.get(krow)[INPUT_R14]); // $kf: added for internal ETP calculation
			  double doy = dates[krow].getDayOfYear();
			  double p = Double.parseDouble(Input.get(krow)[INPUT_P]);// Input[krow][INPUT_P]
//		    # col 3+4:  
//		    # first row
		    if(krow==0)
		    {    	
//		      # col 3: C snow water equi
		    	bucket_model[krow][SNOW_WATER_EQUI] = init_swe; //# col 3 // $kf set to init_swe for consistency with water balance
		      
//		      # col 4: D Snow melt + rain
		      double[] temp = new double[]{0, landuse_DayDegree*t14 * nt};
		      if(t14 >= 0)
		      {
		        bucket_model[krow][SNOW_MELT_RAIN] = Math.min(0,(max(temp))) + bucket_model[krow][PRECIPITATION];
		      }
		      else
		      {
		        bucket_model[krow][SNOW_MELT_RAIN] = Math.min(0,(max(temp))) + 0;
		      }
		      
//		      # col 3 (dependent on col 4)
		      bucket_model[krow][SNOW_WATER_EQUI] = bucket_model[krow][SNOW_WATER_EQUI] + bucket_model[krow][PRECIPITATION] 
		    		  - bucket_model[krow][SNOW_MELT_RAIN];
		    }
		    
//		    # rest of the rows
		    if(krow > 0) // $kf: applies to all rows greater than zero
		    {
		      double[] temp = new double[]{0, landuse_DayDegree*t14 * nt};
		      if(t14 < 0)
		      {
//		        # col 4: Snow melt + rain 
		        bucket_model[krow][SNOW_MELT_RAIN] = 
		        		Math.min(max(temp), bucket_model[krow-1][SNOW_WATER_EQUI]+bucket_model[krow][PRECIPITATION]);
		        
//		        # col 3 (dependent on col 4)
		        bucket_model[krow][SNOW_WATER_EQUI] = bucket_model[krow-1][SNOW_WATER_EQUI] + bucket_model[krow][PRECIPITATION] 
		        		- bucket_model[krow][SNOW_MELT_RAIN]+0;
		      }
		      else
		      {
		        bucket_model[krow][SNOW_MELT_RAIN] = Math.min(max(temp),(bucket_model[krow-1][SNOW_WATER_EQUI])+0) 
		        		+ bucket_model[krow][PRECIPITATION];
		        bucket_model[krow][SNOW_WATER_EQUI] = bucket_model[krow-1][SNOW_WATER_EQUI] + 0 
		        		- bucket_model[krow][SNOW_MELT_RAIN] +  bucket_model[krow][PRECIPITATION];
		      }
		    }
//		    # col 7: G LAI
		    if(doy <= greenUpStartDoy)
		    {
		      bucket_model[krow][LAI] = greenUpStartLai;
		    }
		    else if(doy <= greenUpEndDoy)
		    {
		      bucket_model[krow][LAI] = Minimum_LAI+(greenUpEndLai-greenUpStartLai)*((doy-greenUpStartDoy)/(greenUpEndDoy-greenUpStartDoy));
		    }
		    else if(doy <= senescenceStartDoy)
		    {
		      bucket_model[krow][LAI] = senescenceStartLai;
		    }
		    else if(doy <= senescenceEndDoy)
		    {
		      bucket_model[krow][LAI] = Maximum_LAI+(senescenceEndLai-senescenceStartLai)*((doy-senescenceStartDoy)/(senescenceEndDoy-senescenceStartDoy));
		    }
		    else
		    {
		      bucket_model[krow][LAI] = senescenceEndLai;
		    }

//		    # col 5: E ETP Coeff.Landuse[17,which(colnames(Landuse)==Soil[12,2])]
		    int month = dates[krow].getMonthValue();
		    bucket_model[krow][ETP_COEFF] = landuseValue(Landuse, Integer.toString(month), landuseIndex);
		    
//		    # col 6: F ETP Input
		    if(readETfromFile) // if(numColumnsInput >= 6) // $kf
		    {   
		    	  //# check if input variable is given
//		          # if yes, use given ETP0
		    	 double et0_1 = Double.parseDouble(Input.get(krow)[INPUT_ET0]);
			      bucket_model[krow][ETP_INPUT] = et0_1;
			      if(krow==2)
			      {
			    	  System.out.println("Reading external ETP from file...");
			      }
		    }
		    else if(usePenmanMonteith)
		    {
		    	// Penman-Monteith with the available energy from the radiation forcing.
		    	// ETP_INPUT: wet surface (rs = 0), used for interception and litter like the Haude ETP.
		    	// ETP_DRY: transpiring surface with the Jarvi et al. (2011) surface conductance; its ratio to
		    	// ETP_INPUT scales the demand left for the soil (see soilEvapotranspiration).
		    	double pressure = hasPressure ? Double.parseDouble(Input.get(krow)[INPUT_PRESSURE])
		    			: PenmanMonteithEvaporation.pressureFromElevation(elevation);
		    	double windSpeed = Double.parseDouble(Input.get(krow)[INPUT_WIND_SPEED]);
		    	double kdown = Double.parseDouble(Input.get(krow)[INPUT_K_DOWN]);
		    	double soilWaterBefore = krow==0 ? Init_Value_Soil : bucket_model[krow-1][STORAGE];
		    	double availableEnergy = netRadiation[krow] - storageHeat[krow];
		    	double aerodynamicResistance = penmanMonteith.aerodynamicResistance(windSpeed);
		    	double conductance = penmanMonteith.surfaceConductance(kdown, t14, rh14, pressure, bucket_model[krow][LAI], Maximum_LAI,
		    			Field_Capacity - soilWaterBefore, Field_Capacity - Perm_Wilting_Point);
		    	double surfaceResistance = PenmanMonteithEvaporation.surfaceResistance(conductance);
		    	bucket_model[krow][ETP_INPUT] = PenmanMonteithEvaporation.evaporationDepth(
		    			penmanMonteith.latentHeatFlux(availableEnergy, t14, rh14, pressure, aerodynamicResistance, 0.), t14, ts);
		    	bucket_model[krow][ETP_DRY] = PenmanMonteithEvaporation.evaporationDepth(
		    			penmanMonteith.latentHeatFlux(availableEnergy, t14, rh14, pressure, aerodynamicResistance, surfaceResistance), t14, ts);
		    	bucket_model[krow][NET_RADIATION] = netRadiation[krow];
		    	bucket_model[krow][STORAGE_HEAT] = storageHeat[krow];
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
		    //double ntf = 0.25; // fraction of precipitation that always becomes throughfall, todo: make parameter adjustable
		    double direct_throughfall =  bucket_model[krow][SNOW_MELT_RAIN] *(1.-vcover);
		    double intc_in =  bucket_model[krow][SNOW_MELT_RAIN] - direct_throughfall;
		    double intc_stor_guess = intc_in+ci; // storage depth

//		    # col 9: I ETi: Evaporation from wetted leaf 
		    // bucket_model[krow][INT_ETI_LEAF] = Math.min(bucket_model[krow][I_CAP], bucket_model[krow][ETP_INPUT]);
		    if(intc_stor_guess > bucket_model[krow][I_CAP])
			    bucket_model[krow][INT_ETI_LEAF] = Math.min(bucket_model[krow][ETP_INPUT],intc_stor_guess); // real evaporation
    		    else
			    bucket_model[krow][INT_ETI_LEAF] = Math.min(bucket_model[krow][ETP_INPUT]*intc_stor_guess/ bucket_model[krow][I_CAP], intc_stor_guess);
//		    # col 10: J I-Bal.: Temp calcalation
		    //bucket_model[krow][I_BAL] = bucket_model[krow][SNOW_MELT_RAIN] - bucket_model[krow][INT_ETI_LEAF];
		    double intc_drainage;
		    if(bucket_model[krow][I_CAP] < intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]) 
			intc_drainage = Math.max(intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]-bucket_model[krow][I_CAP],intc_drainage_max);
    		    else
		    	intc_drainage = Math.min(intc_drainage_max*Math.exp(intc_drainage_coeff_b*(intc_stor_guess-bucket_model[krow][INT_ETI_LEAF] - bucket_model[krow][I_CAP])/bucket_model[krow][I_CAP]), intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]);

		    // updated meaning of col 10 "I-BAL" is interception storage
		    bucket_model[krow][I_BAL] = intc_stor_guess-bucket_model[krow][INT_ETI_LEAF]-intc_drainage;
		    if(bucket_model[krow][I_BAL] <0){
      			intc_drainage = Math.max(intc_stor_guess+ bucket_model[krow][I_BAL],0);
      			 bucket_model[krow][I_BAL] = 0.;
    		    }
		    
//		    # col 11: K I-Prec.: Throughfall   
		    bucket_model[krow][I_PREC] = direct_throughfall + intc_drainage + t_surplus;
		    
//		    # col 12: L I-Rem.: Remaining ETa passed to subsequent model
		    // bucket_model[krow][I_REM] = -1*Math.min(bucket_model[krow][I_BAL],0)
		    // 		-bucket_model[krow][INT_ETI_LEAF]+bucket_model[krow][ETP_INPUT];
		    bucket_model[krow][I_REM] = bucket_model[krow][ETP_INPUT] - bucket_model[krow][INT_ETI_LEAF];	

//		    # col 13+14+15
//		    # first row
//		    # litter reduction is now adjusted to time step of input series
		    if(krow==0)
		    {
//		      # col 13: M ETi Litter
		      double[] temp = new double[]{Cap_Litter,(Init_Value_Litter+bucket_model[krow][I_PREC])/Litter_Reduction_factor_dt};
		      bucket_model[krow][I_ETI_LITTER] = Math.min(bucket_model[krow][I_REM],min(temp));
		      
//		      # col 14: N Bilanz (needed for col 13 & 15)
		      bucket_model[krow][BILANZ] = Init_Value_Litter + bucket_model[krow][I_PREC] - bucket_model[krow][I_ETI_LITTER];
		      
//		      # col 15: O Content (needed for col 13 & 14)
		      if(bucket_model[krow][BILANZ] > Cap_Litter)
		      {
		        bucket_model[krow][CONTENT] = Cap_Litter;
		      }
		      else 
		      {
		    	  bucket_model[krow][CONTENT] = Math.max(0,bucket_model[krow][BILANZ]);
		      }		      
		    }
		    else
		    {//# rest of the rows
//		      # col 13: M ETi Litter
		      double[] temp = new double[]{Cap_Litter, (bucket_model[krow-1][CONTENT]+bucket_model[krow][I_PREC])/Litter_Reduction_factor_dt};
		      bucket_model[krow][I_ETI_LITTER] = Math.min(bucket_model[krow][I_REM],min(temp));
		      
//		      # col 14: N Bilanz (needed for col 13 & 15)
		      bucket_model[krow][BILANZ] = bucket_model[krow-1][CONTENT]  + bucket_model[krow][I_PREC] - bucket_model[krow][I_ETI_LITTER];
		      
//		      # col 15: O Content (needed for col 13 & 14)
		      if(bucket_model[krow][BILANZ] > Cap_Litter)
		      {
		        bucket_model[krow][CONTENT] = Cap_Litter;
		      }
		      else
		      {
		    	  bucket_model[krow][CONTENT] = Math.max(0,bucket_model[krow][BILANZ]);
		      }
		    }		    
//		    # col 16: P S-REstn
		    bucket_model[krow][S_RESTN] = Math.max(0, (bucket_model[krow][BILANZ]-bucket_model[krow][CONTENT]));
		    
//		    # col 17-24:
//		    # first row
//		    # Groundwater recharge computation with Glugla approach now adjusted to time step length
		    if(krow==0)
		    {
//		      # col 17: Q Inf-Limit
		      // infiltration limit per time step: a fixed fraction of the soil deficit per day, scaled to the step length
		      bucket_model[krow][INF_LIMIT] = (Field_Capacity-Init_Value_Soil)*INFILTRATION_FRACTION_PER_DAY*nt;
		      
//		      # col 18: R P-Inf
		      bucket_model[krow][P_LINF] = Math.min(bucket_model[krow][S_RESTN],bucket_model[krow][INF_LIMIT])*(1.-Direct_runoff_factor/100.);
		      
//		      # col 19: S S-Rest
		      bucket_model[krow][REST_ETA] = -1*Math.min(0,bucket_model[krow][BILANZ])+bucket_model[krow][I_REM]-bucket_model[krow][I_ETI_LITTER];
		      
//		      # col 20: T Balance soil
		      bucket_model[krow][BALANCE_SOIL] = Init_Value_Soil + bucket_model[krow][P_LINF];
		      
//		      # col 21: U ETa
		      bucket_model[krow][ETA] = soilEvapotranspiration(usePenmanMonteith, bucket_model[krow], Start_of_Reduction, Perm_Wilting_Point);		      
//		      # col 22: V ET-Balance
		      bucket_model[krow][ET_BALANCE] = bucket_model[krow][BALANCE_SOIL] - bucket_model[krow][ETA];
		      
//		      # col 23: W Seepage
		      bucket_model[krow][SEEPAGE] = gluglaSeepage(bucket_model[krow][ET_BALANCE], Field_Capacity, Perm_Wilting_Point, Lambda, nt);		      
//		      # col 24: X Storage Init.-Value
		      if(bucket_model[krow][ET_BALANCE] > Field_Capacity)
		      {
		        bucket_model[krow][STORAGE] =  Field_Capacity;
		      }
		      else
		      {
		        bucket_model[krow][STORAGE] = bucket_model[krow][ET_BALANCE] - bucket_model[krow][SEEPAGE];
		      }		      
		    }
		    else
		    {
//		      # col 17: Q Inf-Limit
		      bucket_model[krow][INF_LIMIT] = (Field_Capacity-bucket_model[krow-1][STORAGE])*INFILTRATION_FRACTION_PER_DAY*nt;
		      
//		      # col 18: R P-Inf
		      bucket_model[krow][P_LINF] = Math.min(bucket_model[krow][S_RESTN],bucket_model[krow][INF_LIMIT])*(1.-Direct_runoff_factor/100.);
		      
//		      # col 19: S S-Rest
		      bucket_model[krow][REST_ETA] = -1*Math.min(0,bucket_model[krow][BILANZ])+bucket_model[krow][I_REM]-bucket_model[krow][I_ETI_LITTER];
		      
//		      # col 20: T Balance soil
		      bucket_model[krow][BALANCE_SOIL] = bucket_model[krow-1][STORAGE] + bucket_model[krow][P_LINF];
		      
//		      # col 21: U ETa
		      bucket_model[krow][ETA] = soilEvapotranspiration(usePenmanMonteith, bucket_model[krow], Start_of_Reduction, Perm_Wilting_Point);
		      
//		      # col 22: V ET-Balance
		      bucket_model[krow][ET_BALANCE] = bucket_model[krow][BALANCE_SOIL] - bucket_model[krow][ETA];
		      
//		      # col 23: W Seepage
		      bucket_model[krow][SEEPAGE] = gluglaSeepage(bucket_model[krow][ET_BALANCE], Field_Capacity, Perm_Wilting_Point, Lambda, nt);
		      
//		      # col 24: X Storage Init.-Value
		      if(bucket_model[krow][ET_BALANCE] > Field_Capacity)
		      {
		        bucket_model[krow][STORAGE] =  Field_Capacity;
		      }
		      else
		      {
		        bucket_model[krow][STORAGE] = bucket_model[krow][ET_BALANCE] - bucket_model[krow][SEEPAGE];
		      }
		    }
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
		    krowEnd = krow;
		  }
//		  # water balance check
		  double water_balance = sum_prec - sum_etr - sum_runoff + init_swe + init_stor + Init_Value_Litter - bucket_model[krowEnd][STORAGE] - bucket_model[krowEnd][SNOW_WATER_EQUI] - bucket_model[krowEnd][I_BAL] - bucket_model[krowEnd][CONTENT];
		  System.out.println("water balance check");
		  System.out.println(water_balance);
		  
		  return bucket_model;
		}
	

	//match input headers to forcing fields and figure out which are in the input. Some are mandatory, some optional
	public void matchInputHeaders(ArrayList<String[]> InputHeader)
	{
		// InputHeader holds the first line of Global_Input.txt, split into column names
		String[] columnNames = InputHeader.get(0);
		boolean hasDate = false, hasDoy = false, hasPrecipitation = false, hasT14 = false, hasRh14 = false;
		boolean hasEt0Column = false;
		for (int i=0;i<columnNames.length;i++)
		{
			String columnName = columnNames[i].trim();
			if (columnName.equals("Date"))
			{
				INPUT_DATE=i;
				hasDate = true;
			}
			else if (columnName.equals("DOY"))
			{
				// optional and not used: the day of year is taken from the Date column
				INPUT_DOY=i;
				hasDoy = true;
			}
			else if (columnName.equals("P"))
			{
				INPUT_P=i;
				hasPrecipitation = true;
			}
			else if (columnName.equals("T14") || columnName.equals("Ta"))
			{
				INPUT_T14=i;
				hasT14 = true;
			}
			else if (columnName.equals("RF14") || columnName.equals("RH"))
			{
				INPUT_R14=i;
				hasRh14 = true;
			}
			else if (columnName.equals("KDOWN"))
			{
				INPUT_K_DOWN=i;
				hasKdown = true;
			}
			else if (columnName.equals("LDOWN"))
			{
				INPUT_L_DOWN=i;
				hasLdown = true;
			}
			else if (columnName.equals("WindSpeed"))
			{
				INPUT_WIND_SPEED=i;
				hasWindSpeed = true;
			}
			else if (columnName.equals("PRES"))
			{
				INPUT_PRESSURE=i;
				hasPressure = true;
			}
			else if (columnName.equals("ET0"))
			{
				INPUT_ET0=i;
				hasEt0Column = true;
			}
			else
			{
				System.out.println("Warning: ignoring unknown column '" + columnName + "' in Global_Input.txt");
			}
		}
		if (!(hasDate && hasPrecipitation && hasT14 && hasRh14))
		{
			throw new IllegalArgumentException("Global_Input.txt must have the columns Date, P, T14 (or Ta) and RF14 (or RH)");
		}
		// Without a command-line choice, read ET0 whenever the column is present
		if (readETfromFile == null)
		{
			readETfromFile = hasEt0Column;
		}
		if (readETfromFile && !hasEt0Column)
		{
			throw new IllegalArgumentException("readET was requested but Global_Input.txt has no ET0 column");
		}
	}	
	
	/** Value of a Land_Use.txt row (found by its label in the first column, e.g. "7" or "Day_degree") for one land use. */
	static double landuseValue(ArrayList<String[]> landuse, String rowLabel, int landuseIndex)
	{
		for (String[] row : landuse)
		{
			if (row.length > landuseIndex && row[0].trim().equals(rowLabel))
			{
				return Double.parseDouble(row[landuseIndex]);
			}
		}
		throw new IllegalArgumentException("Land_Use.txt has no row labelled '" + rowLabel + "'");
	}

	/** Haude potential evapotranspiration (mm/day) from the 14:00 air temperature (degC) and relative humidity (%). */
	static double haudeDailyEtp(double haudeCoefficient, double t14, double rh14)
	{
		double saturationVapourPressure = 6.11 * Math.pow(10, (7.5 * t14) / (237.3 + t14)); // hPa
		return Math.min(HAUDE_MAX_ETP_PER_DAY, haudeCoefficient * saturationVapourPressure * (1. - rh14 / 100.));
	}

	/** Haude ETP per sub-daily step: daily value from the row nearest 14:00, distributed over the day. */
	double[] haudeSubDaily(ArrayList<String[]> Input, LocalDateTime[] dates, ArrayList<String[]> Landuse,
			int landuseIndex, double timestep)
	{
		int stepsPerDay = (int) Math.round(24. / timestep);
		double[] stepEtp = new double[Input.size()];
		int dayStart = 0;
		while (dayStart < Input.size())
		{
			LocalDate day = dates[dayStart].toLocalDate();
			int dayEnd = dayStart;
			while (dayEnd < Input.size() && dates[dayEnd].toLocalDate().equals(day)) dayEnd++;

			int haudeRow = dayStart;
			for (int i=dayStart;i<dayEnd;i++)
			{
				if (Math.abs(dates[i].getHour() - HAUDE_HOUR) < Math.abs(dates[haudeRow].getHour() - HAUDE_HOUR)) haudeRow = i;
			}
			double coefficient = landuseValue(Landuse, Integer.toString(day.getMonthValue()), landuseIndex);
			double dailyEtp = haudeDailyEtp(coefficient, Double.parseDouble(Input.get(haudeRow)[INPUT_T14]),
					Double.parseDouble(Input.get(haudeRow)[INPUT_R14]));

			double kdownSum = 0.;
			if (hasKdown)
			{
				for (int i=dayStart;i<dayEnd;i++) kdownSum += Math.max(0., Double.parseDouble(Input.get(i)[INPUT_K_DOWN]));
			}
			boolean completeDay = (dayEnd - dayStart) == stepsPerDay;
			for (int i=dayStart;i<dayEnd;i++)
			{
				double share = 1. / stepsPerDay;
				if (hasKdown && completeDay && kdownSum > 0.)
				{
					share = Math.max(0., Double.parseDouble(Input.get(i)[INPUT_K_DOWN])) / kdownSum;
				}
				stepEtp[i] = dailyEtp * share;
			}
			dayStart = dayEnd;
		}
		return stepEtp;
	}

	static final DateTimeFormatter DAILY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	static final DateTimeFormatter HOURLY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy.HH");

	/** Parses dd.mm.yyyy (start of the day) or dd.mm.yyyy.hh. */
	static LocalDateTime parseDate(String date)
	{
		String trimmed = date.trim();
		if (trimmed.split("\\.").length == 3)
		{
			return LocalDate.parse(trimmed, DAILY_DATE).atStartOfDay();
		}
		return LocalDateTime.parse(trimmed, HOURLY_DATE);
	}

	/**
	 * Time step in hours from the dates: daily dates (dd.mm.yyyy) give 24 h, otherwise the spacing of the
	 * rows. The spacing must be constant and divide a day evenly (1, 2, 3, 4, 6, 8, 12 or 24 h).
	 */
	static double timestepHours(LocalDateTime[] dates, String firstDate)
	{
		boolean dailyFormat = firstDate.trim().split("\\.").length == 3;
		if (dates.length < 2)
		{
			return dailyFormat ? 24. : 1.;
		}
		long stepMinutes = Duration.between(dates[0], dates[1]).toMinutes();
		for (int i=2;i<dates.length;i++)
		{
			long minutes = Duration.between(dates[i-1], dates[i]).toMinutes();
			if (minutes != stepMinutes)
			{
				throw new IllegalArgumentException("Global_Input.txt rows are not evenly spaced: " + dates[i-1] 
						+ " to " + dates[i] + " is " + minutes + " min, expected " + stepMinutes + " min");
			}
		}
		if (stepMinutes <= 0 || stepMinutes > 24*60 || (24*60) % stepMinutes != 0)
		{
			throw new IllegalArgumentException("Time step of " + stepMinutes + " min does not divide a day evenly");
		}
		if (dailyFormat && stepMinutes != 24*60)
		{
			throw new IllegalArgumentException("Daily dates (dd.mm.yyyy) must be consecutive days");
		}
		return stepMinutes / 60.;
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

	/** Reads the PM parameters from Soil_physics.txt; keys that are absent keep the PenmanMonteithEvaporation defaults. */
	static PenmanMonteithEvaporation configurePenmanMonteith(TreeMap<String,String[]> soil)
	{
		PenmanMonteithEvaporation penmanMonteith = new PenmanMonteithEvaporation();
		penmanMonteith.albedo = optionalValue(soil, "Albedo", penmanMonteith.albedo);
		penmanMonteith.surfaceEmissivity = optionalValue(soil, "Surface emissivity", penmanMonteith.surfaceEmissivity);
		penmanMonteith.vegetationHeight = optionalValue(soil, "Vegetation height", penmanMonteith.vegetationHeight);
		penmanMonteith.measurementHeight = optionalValue(soil, "Measurement height", penmanMonteith.measurementHeight);
		penmanMonteith.ohmA1 = optionalValue(soil, "OHM a1", penmanMonteith.ohmA1);
		penmanMonteith.ohmA2 = optionalValue(soil, "OHM a2", penmanMonteith.ohmA2);
		penmanMonteith.ohmA3 = optionalValue(soil, "OHM a3", penmanMonteith.ohmA3);
		penmanMonteith.maxConductanceG1 = optionalValue(soil, "G1", penmanMonteith.maxConductanceG1);
		penmanMonteith.kdownShapeG2 = optionalValue(soil, "G2", penmanMonteith.kdownShapeG2);
		penmanMonteith.humiditySlopeG3 = optionalValue(soil, "G3", penmanMonteith.humiditySlopeG3);
		penmanMonteith.humidityThresholdG4 = optionalValue(soil, "G4", penmanMonteith.humidityThresholdG4);
		penmanMonteith.optimumTemperatureG5 = optionalValue(soil, "G5", penmanMonteith.optimumTemperatureG5);
		penmanMonteith.soilMoistureShapeG6 = optionalValue(soil, "G6", penmanMonteith.soilMoistureShapeG6);
		penmanMonteith.upperTemperatureTH = optionalValue(soil, "TH", penmanMonteith.upperTemperatureTH);
		penmanMonteith.lowerTemperatureTL = optionalValue(soil, "TL", penmanMonteith.lowerTemperatureTL);
		penmanMonteith.maxKdown = optionalValue(soil, "Kmax", penmanMonteith.maxKdown);
		return penmanMonteith;
	}

	static double optionalValue(TreeMap<String,String[]> soil, String key, double defaultValue)
	{
		return soil.containsKey(key) ? Double.parseDouble(soil.get(key)[1].trim()) : defaultValue;
	}

	/**
	 * Seepage below the root zone after Glugla: lambda * (W - PWP)^2 per day, with W capped at field
	 * capacity. Zero at or below the wilting point and never more than the water above it.
	 */
	static double gluglaSeepage(double soilWater, double fieldCapacity, double wiltingPoint, double lambda, double nt)
	{
		double drainableWater = Math.max(0., Math.min(soilWater, fieldCapacity) - wiltingPoint);
		double seepage = lambda * drainableWater * drainableWater * nt;
		return Math.min(seepage, Math.max(0., soilWater - wiltingPoint));
	}

	double max(double[] items)
	{
		return Math.max(items[0], items[1]);
	}
	double min(double[] items)
	{
		return Math.min(items[0], items[1]);
	}
	
   final static Charset ENCODING = StandardCharsets.UTF_8;	
   public ArrayList<String[]> readLargerTextFileAlternateToArray(String aFileName, boolean skipHeader, String deliminator, boolean onlyFirstLine) 
   {
	   ArrayList<String[]> returnFile = new ArrayList<String[]>();
	   int count = 0;
	    Path path = Paths.get(aFileName);
	    try (BufferedReader reader = Files.newBufferedReader(path, ENCODING))
	    {
	      String line = null;
	      while ((line = reader.readLine()) != null) 
	      {
	    	  if (onlyFirstLine && count > 0)
	    	  {
	    		  return returnFile;
	    	  }
	    	  
	    	  if (skipHeader && count==0)
	    	  {
	    		  count++;
	    		  continue;
	    	  }
	    	  if (line != null && line.trim().equals(""))
	    	  {
	    		  continue;
	    	  }
	    	  String[] lineSplit = line.split(deliminator);
	    	  returnFile.add(lineSplit);
	    	  count ++;
	      }      
	    }
		catch (IOException e)
		{			
			e.printStackTrace();
		}
	    return returnFile;
	  }
   
   public TreeMap<String,String[]> readLargerTextFileAlternateToTreeMap(String aFileName, boolean skipHeader, String deliminator) 
   {
	   TreeMap<String,String[]> returnFile = new TreeMap<String,String[]>();
	   int count = 0;
	    Path path = Paths.get(aFileName);
	    try (BufferedReader reader = Files.newBufferedReader(path, ENCODING))
	    {
	      String line = null;
	      while ((line = reader.readLine()) != null) 
	      {
	    	  if (skipHeader && count==0)
	    	  {
	    		  count++;
	    		  continue;
	    	  }
	    	  if (line != null && line.trim().equals(""))
	    	  {
	    		  continue;
	    	  }
	    	  String[] lineSplit = line.split(deliminator);
	    	  String key = lineSplit[0];
	    	  returnFile.put(key,lineSplit);
	    	  count ++;
	      }      
	    }
		catch (IOException e)
		{			
			e.printStackTrace();
		}
	    return returnFile;
	  }
	public void writeFile(String text, String filename)
	{	
		FileOutputStream out; 
		PrintStream p; 
		try
		{
			out = new FileOutputStream(filename);
			p = new PrintStream(out);
			p.println(text);
			p.close();
		} catch (Exception e)
		{
			System.err.println("Error writing to file");
		}
		p=null;
		out=null;
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
				+ "StorageHeat" + tab
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
			sb.append(bucket_model_dates.get(i));
			for (int j=0;j<width;j++)
			{
				sb.append(tab + bucket_model[i][j]);
			}
			sb.append(linefeed);
		}
		writeFile(sb.toString(), output);
	}

	public String getDirectory()
	{
		return directory;
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
