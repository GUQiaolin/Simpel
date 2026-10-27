# SIMPEL glossary

This file describes every input file, parameter and output column of the model. It is meant to be read
next to `src/thud/simpel/SimpelModel.java`.

## Input files

A run reads four files from one directory:

| File | Contents | Needed for |
|---|---|---|
| `Global_Input.txt` | Meteorological forcing, one row per time step | always |
| `Soil_physics.txt` | Soil, site and method parameters (`key;value;description`) | always |
| `Land_Use_Parameters.txt` | Land-use dependent parameters, one row per land use | always |
| `Haude_Coefficients.txt` | Monthly Haude coefficients, one row per land use | ET method `HAUDE` |

`Land_Use.txt` and `LAI_model.txt` of earlier versions are no longer read. Their contents moved to
`Land_Use_Parameters.txt`, `Haude_Coefficients.txt` and the default phenology (see below).

All inputs are read and checked once before the run. A missing or misspelled key, a missing forcing value,
a value outside its plausible range or inconsistent soil values stop the run with a message naming the
file, line and value, instead of producing NaN or silently using a default.

### `Global_Input.txt`: forcing

Whitespace-separated columns, matched by header name (the order does not matter). Lines starting with `#`
are comments.

| Column | Meaning | Unit | Plausible range | Needed for |
|---|---|---|---|---|
| `Date` | `dd.mm.yyyy` (daily) or `dd.mm.yyyy.hh` (sub-daily). Sets the time step, day of year and month | – | – | always |
| `P` | Precipitation | mm/step | 0 – 500 | always |
| `Ta` (or `T14`) | Air temperature (for daily Haude runs: the 14:00 value) | °C | −60 – 60 | always |
| `RH` (or `RF14`) | Relative humidity | % | 0 – 105 | always |
| `KDOWN` | Incoming short-wave radiation | W m⁻² | −50 – 1500 | `PM`; with sub-daily `HAUDE` it spreads the daily ETP over the day |
| `WindSpeed` | Wind speed at the measurement height | m s⁻¹ | 0 – 60 | `PM` |
| `QN` | Net all-wave radiation (available energy) | W m⁻² | −300 – 1100 | `PM`, optional: if missing, computed from `KDOWN` and `LDOWN` |
| `LDOWN` | Incoming long-wave radiation | W m⁻² | 50 – 700 | `PM` without `QN`, optional: if missing, a clear-sky estimate is used (too low under cloud) |
| `PRES` | Air pressure | hPa | 500 – 1100 | `PM`, optional: if missing, from `Elevation` |
| `ET0` | External potential ET | mm/step | 0 – 50 | optional: read with the `readET` argument, or automatically if present |
| `DOY` | Ignored; the day of year comes from `Date` | – | – | optional |

- **Time step:** the spacing of the dates. It must be constant and divide a day evenly (1, 2, 3, 4, 6, 8,
  12 or 24 h); daily dates must be consecutive days.
- **Missing values:** empty fields, `NA`, `NaN` and `-999` are not accepted; fill gaps before running.

### `Soil_physics.txt`: soil, site and method

Lines `key;value;description`; only key and value are read. Unknown keys stop the run (with a suggestion
for likely typos), and so do keys given twice.

**Soil.** Volumetric contents in vol-%; multiplied by the root depth (cm) / 10 they give mm.

| Key | Meaning | Unit | Checks |
|---|---|---|---|
| `Field Capacity %` | Water content at field capacity; upper limit of the soil store | vol-% | above the wilting point |
| `Permanent Wilting Point %` | Water content at which plants stop taking up water | vol-% | below field capacity |
| `Start of Reduction %` | Below this water content, ET is reduced linearly to zero at the wilting point | vol-% | between wilting point and field capacity; required for `HAUDE`/ET0, ignored with `PM` |
| `Root Depth` | Depth of the soil water store (see "Root depth" below) | cm | > 0; overridden by `RootDepth` in `Land_Use_Parameters.txt` if given there |
| `Init-Value Soil %` | Initial water content | vol-% | 0 – `Field Capacity %` |
| `Glugla coeff.` | Coefficient c of the Glugla seepage law; λ = c / (root depth in mm)² | – | ≥ 0 |

**Land use and canopy.**

| Key | Meaning | Unit |
|---|---|---|
| `Land use` | Name of the row in `Land_Use_Parameters.txt` (and `Haude_Coefficients.txt`) | – |
| `Vegetation Fraction` | Fraction of precipitation that reaches the canopy (the rest is direct throughfall) | – (0 – 1) |
| `Layer Thickness` | Interception storage per unit LAI; capacity = thickness × LAI | mm |
| `Drainage Coeff. b` | Exponent of the canopy drainage (Rutter et al. 1971) | – |
| `Max. Drainage Rate` | Maximum canopy drainage | mm/day |
| `Init-Value Litter` | Initial water in the litter layer | mm |
| `Litter Reduction factor` | At most 1/factor of the litter water can evaporate per day | – (> 0) |

**Site and method.**

| Key | Meaning | Default |
|---|---|---|
| `Hemisphere` | `N` or `S`. Seasonal tables (phenology dates, Haude months) are written for the northern hemisphere and shifted by half a year for `S` | `N` |
| `ET method` | `HAUDE` or `PM` (see "Evapotranspiration methods") | `HAUDE` |
| `Timestep` | No longer needed (taken from the dates); if present it must match them | – |

**`PM` parameters** (all optional):

| Key | Meaning | Default |
|---|---|---|
| `Albedo`, `Surface emissivity` | Net radiation from `KDOWN` and `LDOWN` (not used when `QN` is given) | 0.18, 0.98 |
| `Vegetation height` | Roughness and displacement height for the aerodynamic resistance | 0.12 m |
| `Measurement height` | Height of the wind (and temperature) measurement; must be above 0.67 × vegetation height | 2 m |
| `Elevation` | Air pressure when there is no `PRES` column | 0 m |
| `G1` … `G6`, `TH`, `TL`, `Kmax` | Surface conductance after Järvi et al. (2011), parameter names and values of Järvi et al. (2011): max. conductance (mm s⁻¹), K↓ shape (W m⁻²), humidity slope (kg g⁻¹) and threshold (g kg⁻¹), optimum temperature (°C), soil-moisture shape (mm⁻¹), temperature limits (°C), max. K↓ (W m⁻²) | 16.4764, 566.0923, 0.2163, 3.3649, 11.0764, 0.0176, 40, 0, 1200 |

The Järvi et al. (2011) conductance parameters were fitted to urban sites (Los Angeles, Vancouver) and
should be changed together, not one by one. For a different vegetation they need recalibration.

### `Land_Use_Parameters.txt`: land-use lookup table

One row per land use; add a row to add a land use. Whitespace-separated, `#` comment lines, `-` = not
given (optional columns only). The file itself documents its columns.

| Column | Meaning | Unit | Required |
|---|---|---|---|
| `Land_use` | Name used in `Land use;…` | – | yes |
| `MinLAI`, `MaxLAI` | Leaf area index outside / inside the growing season | m² m⁻² | yes (0 ≤ MinLAI ≤ MaxLAI; MaxLAI 0 = non-vegetated) |
| `DegreeDay` | Snow melt per degree above 0 °C | mm °C⁻¹ day⁻¹ | yes |
| `DirectRunoff` | Share of the water passing the litter layer that runs off directly | % | yes |
| `LitterCap` | Storage capacity of the litter layer | mm | yes |
| `RootDepth` | Overrides `Root Depth` in `Soil_physics.txt` for this land use | cm | optional |
| `GreenUpStart`, `GreenUpEnd`, `SenescenceStart`, `SenescenceEnd` | Phenology dates | day of year | optional (defaults 121, 135, 305, 319) |

The values of the seven original land uses (beech, corn, grassland, spruce, sugar_beet, urban,
winter_wheat) are those of the original SIMPEL land-use table. `urban` has `RootDepth` 1 cm (see below).

### `Haude_Coefficients.txt`

One row per land use with the columns `Jan` … `Dec`: the monthly Haude coefficient (mm hPa⁻¹ day⁻¹).
The original values are for Central Europe. Only needed for ET method `HAUDE`; with `PM` the
file may be absent (`ETP_Coeff` is then 0).

## Model concepts

### Root depth

The root depth sets the size of the single soil water store: field capacity, wilting point, start of
reduction and initial water (all vol-% × root depth / 10, in mm), and the Glugla seepage coefficient
λ = c / depth². Nothing else depends on it directly. It must be above 0: with 0 the store has no size, and
the model used to divide by zero and return NaN for every value. For (nearly) sealed surfaces such as
urban, use a small value, e.g. 1 cm (the `urban` row does this).

SIMPEL models one surface over one soil store. There is no separate subsoil below the root zone and no
mixed sealed/pervious fraction within a run: water that does not fit into the root zone becomes surface
runoff or seepage. A fully sealed surface is set up as a land use with `MinLAI` = `MaxLAI` = 0 (no
interception, no transpiration), `DirectRunoff` 100 and a small `RootDepth` (e.g. 1 cm): all precipitation
then becomes surface runoff. The `urban` row (LAI 0.1–0.5, direct runoff 50 %) is a partly sealed surface.

### Phenology (LAI)

LAI follows a fixed curve every year, independent of weather or drought (class `Phenology`):

| Period | LAI |
|---|---|
| before green-up start | MinLAI |
| green-up start → green-up end | rises linearly to MaxLAI |
| green-up end → senescence start | MaxLAI |
| senescence start → senescence end | falls linearly to MinLAI |
| after senescence end | MinLAI |

The default dates are 121, 135, 305 and 319 (1–15 May, 1–15 November). They can be set per land use, and a
season may cross the turn of the year. With `Hemisphere;S` the dates are shifted by half a year. Drought
acts only on ET (through the reduction below `Start of Reduction` or the soil-moisture term of the
conductance), not on LAI or the interception capacity.

### Evapotranspiration methods

- **`HAUDE`** (default): ETP = monthly Haude coefficient × saturation vapour pressure deficit at 14:00,
  at most 7 mm/day. For sub-daily forcing, the daily value is computed from the 14:00 row and spread over
  the day in proportion to `KDOWN` (evenly without `KDOWN`). Soil ET is reduced linearly between
  `Start of Reduction` and the wilting point.
- **`PM`**: Penman–Monteith
  with the available energy taken straight from the radiation forcing (`QN`, or Q* from `KDOWN`/`LDOWN`; no
  storage heat), a neutral aerodynamic resistance and the Järvi et al. (2011) surface conductance. The
  wet-surface value (surface resistance 0) is `ETP_Input` and drives interception and litter evaporation;
  the dry-surface value `ETP_dry` scales the demand left for the soil. The soil-moisture limitation is part
  of the conductance (deficit = field capacity − soil water, zero conductance at the wilting point), so
  SIMPEL's linear reduction is not applied a second time. Dew (negative QE) is not counted.
- **ET0 from file**: an `ET0` column replaces the demand; soil ET as with `HAUDE`.

## Output columns (`SIMPLE_bucket_model.csv`)

The columns (and the matching constants in `SimpelModel.java`) keep the names of the original SIMPEL
spreadsheet columns, so results can be compared with the Excel and R versions. Fluxes are in mm per time
step, stores in mm.

| Column (constant) | Spreadsheet col. | Meaning | Unit |
|---|---|---|---|
| Precipitation (`PRECIPITATION`) | B | Precipitation from `Global_Input.txt` | mm/step |
| Snow_Water_Equi (`SNOW_WATER_EQUI`) | C | Water stored in the snow pack | mm |
| Snow_melt+rain (`SNOW_MELT_RAIN`) | D | Rain plus snow melt reaching the canopy | mm/step |
| ETP_Coeff (`ETP_COEFF`) | E | Monthly Haude coefficient of the land use (`Haude_Coefficients.txt`; 0 if absent) | mm hPa⁻¹ day⁻¹ |
| ETP_Input (`ETP_INPUT`) | F | Potential evapotranspiration used by the model (`PM`: wet surface) | mm/step |
| LAI (`LAI`) | G | Leaf area index from the phenology | m² m⁻² |
| I-Cap (`I_CAP`) | H | Interception capacity = layer thickness × LAI | mm |
| Int. ETi(leaf) (`INT_ETI_LEAF`) | I | Evaporation of intercepted water | mm/step |
| I-Bal (`I_BAL`) | J | Interception storage at the end of the step | mm |
| I-Prec (`I_PREC`) | K | Throughfall + canopy drainage reaching the litter | mm/step |
| I-Rem (`I_REM`) | L | Evaporative demand left after interception (ETP − leaf evaporation) | mm/step |
| I_ETi(litter) (`I_ETI_LITTER`) | M | Evaporation from the litter layer | mm/step |
| Bilanz (`BILANZ`) | N | Litter water balance before capping ("Bilanz" = balance) | mm |
| Content (`CONTENT`) | O | Litter storage at the end of the step | mm |
| S-REstn (`S_RESTN`) | P | Water passing the litter layer (excess over litter capacity) | mm/step |
| Inf-Limit (`INF_LIMIT`) | Q | Maximum infiltration this step (25 % of the soil deficit per day) | mm/step |
| P-linf (`P_LINF`) | R | Water infiltrating into the soil | mm/step |
| Rest-ETA (`REST_ETA`) | S | Evaporative demand left for the soil (after leaf and litter) | mm/step |
| Balance_soil (`BALANCE_SOIL`) | T | Soil water after infiltration, before ET | mm |
| ETa (`ETA`) | U | Actual evapotranspiration from the soil | mm/step |
| ET-Balance (`ET_BALANCE`) | V | Soil water after ET, before seepage | mm |
| Seepage (`SEEPAGE`) | W | Drainage below the root zone (Glugla) | mm/step |
| Storage (`STORAGE`) | X | Soil water at the end of the step | mm |
| Surface_runoff (`SURFACE_RUNOFF`) | Y | Water that neither infiltrates nor fits in the soil | mm/step |
| Runoff_total (`RUNOFF_TOTAL`) | Z | Surface runoff + seepage | mm/step |
| I-Leaf (`I_LEAF`) | AA | Evaporation from leaves (= Int. ETi(leaf)) | mm/step |
| I-Litter (`I_LITTER`) | AB | Evaporation from litter | mm/step |
| ETaTotal (`ETA_TOTAL`) | AC | Total actual evapotranspiration: leaf + litter + soil | mm/step |
| K_DOWN, WINDSPEED, L_DOWN | – | Copies of the `KDOWN`, `WindSpeed`, `LDOWN` forcing columns (0 if absent) | W m⁻², m s⁻¹, W m⁻² |
| NetRadiation (`NET_RADIATION`) | – | Available energy Q* (`PM` only) | W m⁻² |
| ra (`AERODYNAMIC_RESISTANCE`) | – | Aerodynamic resistance (`PM` only) | s m⁻¹ |
| rs (`SURFACE_RESISTANCE`) | – | Surface resistance = 1000 / gs (`PM` only) | s m⁻¹ |
| ETP_dry (`ETP_DRY`) | – | Penman–Monteith ET of the transpiring surface (`PM` only) | mm/step |
| QE (`LATENT_HEAT_FLUX`) | – | ETaTotal converted to a latent heat flux (all methods) | W m⁻² |
