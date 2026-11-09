# SIMPEL glossary

This file describes every input file, parameter and output column of the model. It is meant to be read
next to `src/thud/simpel/SimpelModel.java`.

## Input files

A run is described by one `run.txt`. It names the forcing file and the parameters folder (paths relative to
`run.txt`), and the results are written next to it, so every run folder keeps its settings and its outputs
together:

```
parameters/                 shared parameter tables
  soil.txt                  soil properties, one row per soil            always
  land_use.txt              land use, phenology, surface, one row each   always
  pm.txt                    surface conductance G1 ... Kmax per land use et_method = PM
  haude.txt                 monthly Haude coefficients per land use      et_method = HAUDE
forcing/
  <any name>.txt            meteorological forcing                       always
runs/<run>/
  run.txt                   choices, site, initial state, overrides      always
  output_<yyyyMMdd_HHmmss>/ SIMPLE_bucket_model.csv, SIMPEL_log.txt and a copy of run.txt
```

The model is called with `run.txt` or with the folder that contains it. All inputs are read and checked
once before the run. A missing or misspelled key or column, a missing forcing value, a value outside its
plausible range or inconsistent soil values stop the run with a message naming the file, line and value
(with a suggestion for likely typos), instead of producing NaN or silently using a default.

The old layout (`Global_Input.txt`, `Soil_physics.txt`, `Land_Use_Parameters.txt`, `Haude_Coefficients.txt`
in one folder) is no longer read; a folder with `Soil_physics.txt` and no `run.txt` stops with a message.
Where the old keys went:

| Old (`Soil_physics.txt` key or table column) | New |
|---|---|
| `Field Capacity %`, `Permanent Wilting Point %`, `Start of Reduction %` | `soil.txt`: `field_capacity`, `wilting_point`, `start_of_reduction` |
| `Glugla coeff.`, `Infiltration fraction`, `Soil surface conductance` | `soil.txt`: `glugla_c`, `infiltration_fraction`, `soil_surface_conductance` |
| `Root Depth` (and the `RootDepth` column) | `land_use.txt`: `root_depth` (one place only) |
| `Layer Thickness`, `Drainage Coeff. b`, `Max. Drainage Rate` | `land_use.txt`: `icept_cap_per_lai`, `icept_drain_b`, `icept_drain_max` |
| `Litter Reduction factor` | `land_use.txt`: `litter_reduction` |
| `MinLAI`, `MaxLAI`, `DegreeDay`, `DirectRunoff`, `LitterCap`, phenology dates | `land_use.txt`: `min_lai`, `max_lai`, `degree_day`, `direct_runoff`, `litter_cap`, `green_up_start` … |
| `Albedo`, `Surface emissivity`, `Vegetation height` | `land_use.txt`: `albedo`, `emissivity`, `veg_height` |
| `G1` … `G6`, `TH`, `TL`, `Kmax` | `pm.txt` |
| `Haude_Coefficients.txt` | `haude.txt` |
| `Land use`, `ET method`, `Hemisphere`, `Land Use Fraction` | `run.txt`: `land_use`, `et_method`, `hemisphere`, `land_use_fraction` |
| `Init-Value Soil %`, `Init-Value Litter`, `Elevation`, `Measurement height` | `run.txt`: `init_soil`, `init_litter`, `elevation`, `measurement_height` |
| `readET` / `dontReadET` command-line argument | `run.txt`: `et_method = ET0` |
| `Timestep` | removed (taken from the dates) |

### `run.txt`: the run

Lines `key = value`; `#` starts a comment. Keys given twice and unknown keys stop the run.

| Key | Meaning | Default |
|---|---|---|
| `forcing` | Forcing file, relative to `run.txt` | required |
| `parameters` | Folder with `soil.txt`, `land_use.txt`, `pm.txt`, `haude.txt`, relative to `run.txt` | required |
| `soil` | Row of `soil.txt` | `luvisol` |
| `land_use` | Row of `land_use.txt` (and of `pm.txt`/`haude.txt`) | required |
| `et_method` | `HAUDE`, `PM`, or `ET0` (the `ET0` column of the forcing; see "Evapotranspiration methods") | `HAUDE` |
| `hemisphere` | `N` or `S`. Seasonal tables (phenology dates, Haude months) are written for the northern hemisphere and shifted by half a year for `S` | `N` |
| `land_use_fraction` | Share of the area covered by the land use. The gap (1 − fraction) is bare soil: its precipitation falls through directly to litter and soil, and with `PM` it evaporates through `soil_surface_conductance` | 1 |
| `init_soil` | Initial soil water content, at most the field capacity | field capacity (vol-%) |
| `init_litter` | Initial water in the litter layer | 0 mm |
| `elevation` | `PM` only: air pressure when the forcing has no `PRES` column | 0 m |
| `measurement_height` | `PM` only: height of the wind (and temperature) measurement; must be above 0.67 × `veg_height` | 2 m |

**Overrides.** Any value of the chosen rows of the parameter tables can be set in `run.txt` with
`table.column = value`, for example

```
soil.field_capacity = 34
land_use.max_lai    = 3
pm.G1               = 10
haude.Jul           = 0.30
```

The tables stay the shared defaults and `run.txt` records what is different in this run. Overrides are
applied before the checks, so they are checked like the table values. The log lists every override, and the
output folder keeps a copy of `run.txt`. An override of a table that the ET method does not use (e.g. `pm.`
with `HAUDE`) stops the run.

### Parameter tables: common format

`soil.txt`, `land_use.txt`, `pm.txt` and `haude.txt` are whitespace-separated tables with one row per soil or
land use, so the rows can be compared column by column. The first non-comment line is the header; its first
cell is the row label. Lines starting with `#` are comments (each file documents its columns and units
there). `-` means "not given". Every column must be present; a missing or unknown column stops the run.

### `soil.txt`: soil properties

Water contents in vol-%; multiplied by the root depth of the land use (cm) / 10 they give mm.

| Column | Meaning | Unit | Checks |
|---|---|---|---|
| `field_capacity` | Water content at field capacity (pF 1.8); upper limit of the soil store | vol-% | above the wilting point |
| `wilting_point` | Permanent wilting point (pF 4.2); plants take up no water below it | vol-% | below field capacity |
| `start_of_reduction` | Below this water content, ET is reduced linearly to zero at the wilting point | vol-% | `HAUDE`/ET0 only: required, between wilting point and field capacity; not used by `PM` |
| `glugla_c` | Coefficient c of the Glugla seepage law; λ = c / (root depth in mm)² | – | ≥ 0 |
| `infiltration_fraction` | Share of the soil deficit (field capacity − storage) that may infiltrate per day, scaled to the step length; the rest of the water reaching the soil surface runs off | day⁻¹ | 0 – 1 |
| `soil_surface_conductance` | `PM` only: conductance of the bare soil in the gap (1 − `land_use_fraction`) at field capacity, reduced linearly to the minimum at the wilting point | mm s⁻¹ | 0 – 50 |

Rows: `luvisol` (the default, below) and `simpel_example` (the soil of the original SIMPEL example: field
capacity 25, wilting point 12, start of reduction 20 vol-%; used by the example runs). To add a soil, add a row.

#### Default soil: `luvisol` (Parabraunerde from loess)

A Luvisol (German *Parabraunerde*) on loess is the typical soil of the loess belt of Central Europe and of
much of its farmland. The profile is an eluvial topsoil (Ap/Al, silt loam, KA5 texture class Ut3) over a
clay-enriched subsoil (Bt, loamy silt to silty clay loam, Lu). It stores a lot of plant-available water and
drains freely (no groundwater or stagnant water in the root zone), which fits the assumptions of SIMPEL.

The values are a profile mean over the root zone, about 40 % Ut3 topsoil and 60 % Lu subsoil, at medium
bulk density (KA5 class Ld3, about 1.4–1.6 g cm⁻³), from the soil-physical tables of the German soil mapping
guide (KA5, AG Boden 2005, table 70):

| Column | Value | Range | Basis |
|---|---|---|---|
| `field_capacity` | 36 vol-% | 33 – 41 | KA5: Ut3 35–39, Lu 33–41 vol-% (high to low bulk density). Regional profile data give about 350–390 mm per metre for Parabraunerde from loess (LGRB Baden-Württemberg) |
| `wilting_point` | 16 vol-% | 12 – 20 | KA5 dead water (field capacity − available water): Ut3 12–13, Lu 18–20 vol-% |
| available water (field capacity − wilting point) | 20 vol-% = 200 mm per metre | 15 – 26 vol-% | KA5: Ut3 23–26, Lu 15–21 vol-%. Profile values of 160–220 mm per metre (LGRB), 187–196 mm in the main root zone of 1 m (LfL Bayern), > 200 mm (LfULG Sachsen) |
| `start_of_reduction` | 28 vol-% | 24 – 30 | Convention, not a soil measurement: ET is reduced below 60 % of the available water (16 + 0.6 × 20). Values of 40–70 % of the available water are common (e.g. 50 % in FAO-56); the SIMPEL example uses 62 % |
| `glugla_c` | 150 | – | SIMPEL default. Empirical; no texture-specific value is known for this soil, calibrate on measured seepage if available |
| `infiltration_fraction` | 0.25 day⁻¹ | 0 – 1 | Model default of this implementation, not from the original SIMPEL (which lets all water infiltrate); calibrate on runoff |
| `soil_surface_conductance` | 5 mm s⁻¹ | 0 – 50 | Model default (surface resistance 200 s m⁻¹ at field capacity) |

The effective root depth is set by the land use (`root_depth`, 100 cm in `land_use.txt`). KA5 gives an
effective root depth of about 10–11 dm for arable crops on silty loess soils, and less for grass. Within the
ranges above the values depend on the bulk density, the humus content (more humus, more water) and the
depth of the decalcified and eroded layers. Use local measurements where available. The KA5 values are
estimates for the texture class, not measurements of one site.

### `land_use.txt`: land use, phenology and surface

One row per land use; add a row to add a land use. The values needed follow from the components the land
use has: a value for a component that does not exist has no effect and is ignored with a note in the log.

| Column | Meaning | Unit | Needed |
|---|---|---|---|
| `land_use` | Name used in `land_use = …` | – | always |
| `min_lai`, `max_lai` | Leaf area index outside / inside the growing season | m² m⁻² | always; 0 ≤ `min_lai` ≤ `max_lai`, `max_lai` 0 = no canopy |
| `green_up_start`, `green_up_end`, `senescence_start`, `senescence_end` | Phenology dates (see "Phenology") | day of year | if `min_lai` < `max_lai`; `-` uses the defaults 121, 135, 305, 319 |
| `root_depth` | Depth of the soil water store (see "Root depth") | cm | always, > 0 |
| `degree_day` | Snow melt per degree above 0 °C | mm °C⁻¹ day⁻¹ | always |
| `direct_runoff` | Share of the water passing the litter layer that runs off directly | % | always |
| `litter_cap` | Storage capacity of the litter layer | mm | always; 0 = no litter layer |
| `litter_reduction` | Litter evaporation per day is at most min(litter capacity, litter water) / factor (as in the SIMPEL spreadsheet) | – (> 0) | if `litter_cap` > 0 |
| `icept_cap_per_lai` | Interception storage per unit LAI; capacity = `icept_cap_per_lai` × LAI | mm | if `max_lai` > 0 |
| `icept_drain_b` | Exponent of the canopy drainage (Rutter et al. 1971) | – | if `max_lai` > 0 |
| `icept_drain_max` | Maximum canopy drainage | mm/day | if `max_lai` > 0 |
| `albedo`, `emissivity` | Net radiation from `KDOWN` and `LDOWN` (not used when the forcing has `QN`) | – | `PM` |
| `veg_height` | Roughness and displacement height for the aerodynamic resistance | m | `PM` |

LAI, degree-day factor, direct runoff and litter capacity of the seven original land uses (beech, corn,
grassland, spruce, sugar_beet, urban, winter_wheat) are those of the original SIMPEL land-use table. The
interception values, the root depth (100 cm, urban 1 cm) and the litter drying factor (3) are those of the
SIMPEL examples. The phenology dates are not yet set (`-`, so the defaults are used), and only
`irrigated_grass` has the `PM` surface values (albedo 0.18, emissivity 0.98, height 0.12 m); fill them in
before using `PM` with another land use.

### `pm.txt`: surface conductance (`PM`)

One row per land use with the parameters of the Järvi et al. (2011) surface conductance (gsModel 1). Only
read for `et_method = PM`, and needed for every land use with leaves (`max_lai` > 0).

| Column | Meaning | Unit |
|---|---|---|
| `G1` | Maximum surface conductance | mm s⁻¹ |
| `G2` | K↓ shape | W m⁻² |
| `G3` | Humidity slope | (g kg⁻¹)⁻¹ |
| `G4` | Humidity deficit threshold | g kg⁻¹ |
| `G5` | Optimum temperature | °C |
| `G6` | Soil-moisture shape | mm⁻¹ |
| `TH`, `TL` | Upper and lower temperature limits | °C |
| `Kmax` | Maximum K↓ | W m⁻² |

The `irrigated_grass` row has the values of Järvi et al. (2011): 16.4764, 566.0923, 0.2163, 3.3649, 11.0764,
0.0176, 40, 0, 1200. They were fitted to urban sites (Los Angeles, Vancouver) and should be changed together,
not one by one. For a different vegetation they need recalibration.

### `haude.txt`: Haude coefficients

One row per land use with the columns `Jan` … `Dec`: the monthly Haude coefficient (mm hPa⁻¹ day⁻¹). The
original values are for Central Europe. Only read for `et_method = HAUDE` (`ETP_Coeff` is 0 otherwise).

### Forcing file

Named in `run.txt` (`forcing`), any file name. Whitespace-separated columns, matched by header name (the
order does not matter). Lines starting with `#` are comments.

| Column | Meaning | Unit | Plausible range | Needed for |
|---|---|---|---|---|
| `Date` | `dd.mm.yyyy` (daily) or `dd.mm.yyyy.hh` (sub-daily); ISO 8601 `yyyy-mm-dd` or `yyyy-mm-ddThh:mm` is also accepted. Sets the time step, day of year and month | – | – | always |
| `P` | Precipitation | mm/step | 0 – 500 | always |
| `Ta` (or `T14`) | Air temperature (for daily Haude runs: the 14:00 value) | °C | −60 – 60 | always |
| `RH` (or `RF14`) | Relative humidity | % | 0 – 105 | always |
| `KDOWN` | Incoming short-wave radiation | W m⁻² | −50 – 1500 | `PM`; with sub-daily `HAUDE` it spreads the daily ETP over the day |
| `WindSpeed` | Wind speed at the measurement height | m s⁻¹ | 0 – 60 | `PM` |
| `QN` | Net all-wave radiation (available energy) | W m⁻² | −300 – 1100 | `PM`, optional: if missing, computed from `KDOWN` and `LDOWN` |
| `LDOWN` | Incoming long-wave radiation | W m⁻² | 50 – 700 | `PM` without `QN`, optional: if missing, a clear-sky estimate is used (too low under cloud) |
| `PRES` | Air pressure | hPa | 500 – 1100 | `PM`, optional: if missing, from `elevation` |
| `ET0` | External potential ET | mm/step | 0 – 50 | `et_method = ET0` (ignored with a note otherwise) |

- **Time step:** the spacing of the dates. It must be constant and divide a day evenly (1, 2, 3, 4, 6, 8,
  12 or 24 h); daily dates must be consecutive days.
- **Missing values:** empty fields, `NA`, `NaN` and `-999` are not accepted; fill gaps before running.
- **No `DOY` column:** the day of year and the month come from `Date` for every method (also daily `HAUDE`).
  An old `DOY` column is ignored with a warning, like any unknown column.

## Model concepts

### Root depth

The root depth sets the size of the single soil water store: field capacity, wilting point, start of
reduction and initial water (all vol-% × root depth / 10, in mm), and the Glugla seepage coefficient
λ = c / depth². Nothing else depends on it directly. It must be above 0: with 0 the store has no size, and
the model used to divide by zero and return NaN for every value. For (nearly) sealed surfaces such as
urban, use a small value, e.g. 1 cm (the `urban` row does this).

SIMPEL models one surface over one soil store. There is no separate subsoil below the root zone and no
mixed sealed/pervious fraction within a run: water that does not fit into the root zone becomes surface
runoff or seepage. A fully sealed surface is set up as a land use with `min_lai` = `max_lai` = 0 (no
interception, no transpiration), `direct_runoff` 100 and a small `root_depth` (e.g. 1 cm): all precipitation
then becomes surface runoff. The `urban` row (LAI 0.1–0.5, direct runoff 50 %) is a partly sealed surface.

### Phenology (LAI)

LAI follows a fixed curve every year, independent of weather or drought (class `Phenology`):

| Period | LAI |
|---|---|
| before green-up start | min_lai |
| green-up start → green-up end | rises linearly to max_lai |
| green-up end → senescence start | max_lai |
| senescence start → senescence end | falls linearly to min_lai |
| after senescence end | min_lai |

The default dates are 121, 135, 305 and 319 (1–15 May, 1–15 November). They can be set per land use, and a
season may cross the turn of the year. With `hemisphere = S` the dates are shifted by half a year. Drought
acts only on ET (through the reduction below `start_of_reduction` or the soil-moisture term of the
conductance), not on LAI or the interception capacity.

### Evapotranspiration methods

- **`HAUDE`** (default): ETP = monthly Haude coefficient × saturation vapour pressure deficit at 14:00,
  at most 7 mm/day. For sub-daily forcing, the daily value is computed from the 14:00 row and spread over
  the day in proportion to `KDOWN` (evenly without `KDOWN`). Soil ET is reduced linearly between
  `start_of_reduction` and the wilting point.
- **`PM`**: Penman–Monteith
  with the available energy taken straight from the radiation forcing (`QN`, or Q* from `KDOWN`/`LDOWN`; no
  storage heat), a neutral aerodynamic resistance and the Järvi et al. (2011) surface conductance. The
  wet-surface value (surface resistance 0) is `ETP_Input` and drives interception and litter evaporation;
  the dry-surface value `ETP_dry` scales the demand left for the soil. `ETP_dry` has two parts weighted by
  `land_use_fraction`: the land-use cover with the Järvi conductance (`ETP_dry_lu`) and the bare soil in the
  gap with the soil conductance (`ETP_dry_gap`). The soil-moisture limitation is part of both conductances
  (deficit = field capacity − soil water, minimum conductance at the wilting point), so SIMPEL's linear
  reduction is not applied a second time. Dew (negative QE) is not counted.
- **`ET0`**: the `ET0` column of the forcing is the demand; soil ET as with `HAUDE`.

## Output columns (`output_<yyyyMMdd_HHmmss>/SIMPLE_bucket_model.csv`, next to `run.txt`)

The columns (and the matching constants in `SimpelModel.java`) keep the names of the original SIMPEL
spreadsheet columns, so results can be compared with the Excel and R versions. Fluxes are in mm per time
step, stores in mm.

| Column (constant) | Spreadsheet col. | Meaning | Unit |
|---|---|---|---|
| Precipitation (`PRECIPITATION`) | B | Precipitation from the forcing | mm/step |
| Snow_Water_Equi (`SNOW_WATER_EQUI`) | C | Water stored in the snow pack | mm |
| Snow_melt+rain (`SNOW_MELT_RAIN`) | D | Rain plus snow melt reaching the canopy | mm/step |
| ETP_Coeff (`ETP_COEFF`) | E | Monthly Haude coefficient of the land use (`haude.txt`; 0 unless `HAUDE`) | mm hPa⁻¹ day⁻¹ |
| ETP_Input (`ETP_INPUT`) | F | Potential evapotranspiration used by the model (`PM`: wet surface) | mm/step |
| LAI (`LAI`) | G | Leaf area index from the phenology | m² m⁻² |
| I-Cap (`I_CAP`) | H | Interception capacity = `icept_cap_per_lai` × LAI | mm |
| Int. ETi(leaf) (`INT_ETI_LEAF`) | I | Evaporation of intercepted water | mm/step |
| I-Bal (`I_BAL`) | J | Interception storage at the end of the step | mm |
| I-Prec (`I_PREC`) | K | Throughfall + canopy drainage reaching the litter | mm/step |
| I-Rem (`I_REM`) | L | Evaporative demand left after interception (ETP − leaf evaporation) | mm/step |
| I_ETi(litter) (`I_ETI_LITTER`) | M | Evaporation from the litter layer | mm/step |
| Bilanz (`BILANZ`) | N | Litter water balance before capping ("Bilanz" = balance) | mm |
| Content (`CONTENT`) | O | Litter storage at the end of the step | mm |
| S-REstn (`S_RESTN`) | P | Water passing the litter layer (excess over litter capacity) | mm/step |
| Inf-Limit (`INF_LIMIT`) | Q | Maximum infiltration this step (`infiltration_fraction` of the soil deficit per day) | mm/step |
| P-linf (`P_LINF`) | R | Water infiltrating into the soil | mm/step |
| Rest-ETA (`REST_ETA`) | S | Evaporative demand left for the soil (after leaf and litter) | mm/step |
| Balance_soil (`BALANCE_SOIL`) | T | Soil water after infiltration, before ET | mm |
| ETa (`ETA`) | U | Actual evapotranspiration from the soil | mm/step |
| ET-Balance (`ET_BALANCE`) | V | Soil water after ET, before seepage | mm |
| Seepage (`SEEPAGE`) | W | Drainage below the root zone (Glugla) | mm/step |
| Storage (`STORAGE`) | X | Soil water at the end of the step = min(ET-Balance, field capacity) − Seepage | mm |
| Surface_runoff (`SURFACE_RUNOFF`) | Y | Water that does not infiltrate, plus soil water above field capacity | mm/step |
| Runoff_total (`RUNOFF_TOTAL`) | Z | Surface runoff + seepage | mm/step |
| I-Leaf (`I_LEAF`) | AA | Evaporation from leaves (= Int. ETi(leaf)) | mm/step |
| I-Litter (`I_LITTER`) | AB | Evaporation from litter | mm/step |
| ETaTotal (`ETA_TOTAL`) | AC | Total actual evapotranspiration: leaf + litter + soil | mm/step |
| K_DOWN, WINDSPEED, L_DOWN | – | Copies of the `KDOWN`, `WindSpeed`, `LDOWN` forcing columns (0 if absent) | W m⁻², m s⁻¹, W m⁻² |
| NetRadiation (`NET_RADIATION`) | – | Available energy Q* (`PM` only) | W m⁻² |
| ra (`AERODYNAMIC_RESISTANCE`) | – | Aerodynamic resistance (`PM` only) | s m⁻¹ |
| rs (`SURFACE_RESISTANCE`) | – | Surface resistance = 1000 / gs (`PM` only) | s m⁻¹ |
| ETP_dry (`ETP_DRY`) | – | Penman–Monteith ET of the dry surface = f·ETP_dry_lu + (1 − f)·ETP_dry_gap, f = `land_use_fraction` (`PM` only) | mm/step |
| QE (`LATENT_HEAT_FLUX`) | – | ETaTotal converted to a latent heat flux (all methods) | W m⁻² |
| ETP_dry_lu (`ETP_DRY_LU`) | – | Penman–Monteith ET of the land-use cover with the Järvi conductance (`PM` only) | mm/step |
| ETP_dry_gap (`ETP_DRY_GAP`) | – | Penman–Monteith ET of the bare soil in the gap with the soil conductance (`PM` only) | mm/step |

## References

AG Boden (2005): Bodenkundliche Kartieranleitung, 5th edition (KA5). Bundesanstalt für Geowissenschaften und
Rohstoffe, Hannover. Soil-physical values: table 70 (corrected in the first reprint, 2007).

LGRB Baden-Württemberg, LfL Bayern (soil profile sheets "Parabraunerde aus Löss") and LfULG Sachsen
(Steckbrief Parabraunerden): regional values of the available water of Parabraunerde from loess.
