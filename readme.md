# A JAVA implementation of the Soil Water Balance Model SIMPEL
## General description
This repository includes a JAVA implementation of the Soil Water Balance Model SIMPEL, which has been originally developed by Georg Hörmann (University of Kiel) in a spreadsheet format (Hörmann 1997, Hörmann et al., 2007). The new implementation includes new features, while preserving relevant design criteria of the minimal original model, which makes it a versatile modelling tool:

* Flexible time step, also enabling sub-daily temporal integration of the water balance; the step is taken from the dates of the forcing (daily or 1–12 h)
* New interception model (Rutter et al., 1971)
* Snow model added (temperature index)
* External evapotranspiration calculation (`et_method = ET0`, ET0 column of the forcing)
* Evapotranspiration method `PM`: Penman–Monteith with the surface conductance of Järvi et al. (2011), with the available energy taken straight from the radiation forcing (net radiation column `QN`, or computed from `KDOWN`/`LDOWN`).
* Parameter tables shared by all runs (`parameters/`: soil, land use with phenology, PM conductance, Haude), one row per soil or land use, with a default soil (Luvisol from loess) and a default phenology function for LAI
* One `run.txt` per run that chooses the forcing, soil, land use and ET method and can override any table value
* `land_use_fraction`: the land use covers this share of the area, the gap is bare soil (with `PM` it evaporates through its own soil conductance)
* Southern-hemisphere option (`hemisphere = S`)
* All inputs are checked before the run (missing or implausible values, misspelled keys and columns, inconsistent soil values)

## Input files

```
parameters/                 shared parameter tables (one row per soil or land use)
  soil.txt                  soil properties; default soil: luvisol (Parabraunerde from loess)
  land_use.txt              land use, phenology (LAI over the year), surface (albedo, emissivity, height)
  pm.txt                    Jarvi et al. (2011) conductance G1 ... Kmax per land use (et_method = PM)
  haude.txt                 monthly Haude coefficients per land use (et_method = HAUDE)
forcing/                    forcing files, any name: Date, P, Ta, RH (+ KDOWN, WindSpeed, QN, LDOWN, PRES, ET0)
runs/<run>/
  run.txt                   forcing and parameters paths, soil, land use, ET method, site, initial state, overrides
  output_<yyyyMMdd_HHmmss>/ results, log and a copy of run.txt
```

A minimal `run.txt` (paths are relative to it):

```
forcing    = ../../forcing/simpel_1995_daily.txt
parameters = ../../parameters
land_use   = grassland
et_method  = HAUDE            # HAUDE, PM or ET0

# change single table values for this run only
soil.field_capacity = 34
land_use.max_lai    = 3
```

The tables hold the shared defaults; `run.txt` says what this run uses and what is different. Every value
needed depends on what the land use has: a land use without leaves (`max_lai = 0`) needs no interception
values, one without litter (`litter_cap = 0`) no litter drying factor, and only `PM` needs the surface values
and `pm.txt`. `GLOSSARY.md` describes every key and column with units, ranges and sources (including the
default soil), and lists where each key of the old input files went.

## How to run the model
Compile the source and call java with the main class thud.simpel.SimpelModel and the run (`run.txt` or the
folder that contains it; see run.sh):

```
./run.sh                                   # compiles and runs runs/daily_haude
./run.sh runs/hourly_pm
java -cp bin thud.simpel.SimpelModel runs/hourly_pm/run.txt
```

The results (`SIMPLE_bucket_model.csv`), a log of the run (`SIMPEL_log.txt`, everything printed to the
console including errors, the overrides and notes on defaults used) and a copy of `run.txt` are written to a
timestamped folder `output_<yyyyMMdd_HHmmss>/` next to `run.txt`. Each run gets its own folder.

Example runs (`runs/`):

* `daily_haude` – daily run with the Haude method (original example, 1995, Central Europe).
* `hourly_pm` – hourly run with `et_method = PM`, forced with January 2004 of the AU-Preston (Melbourne) tower data from the TARGET repository, averaged to hourly (`hemisphere = S`). That data set has no precipitation, so P is 0 and the run shows a dry-down; the measurement height is the 40 m tower. It uses the land use `grassland_irrigated`.
* `hourly_pm_winter` and `hourly_pm_winter_rain` – paired, synthetic Germany-like January scenarios for sensitivity comparisons, with no rain and three rain events respectively.
* `daily_pm` – the same month as `hourly_pm` aggregated to daily means (for comparison only, see "Penman–Monteith on a daily time step" below).

The example runs use the soil `simpel_example` of the original SIMPEL example and override the land-use values
they were run with before the land-use table was updated (LAI, root depth, and with `PM` albedo and emissivity),
so their results are unchanged. Leave out `soil` to use the default `luvisol`, and remove the `land_use.`
overrides to use the table values.

`./test.sh` compiles everything, runs the unit tests (Penman–Monteith against an independent Python implementation, phenology, single bucket steps, input checks) and checks `daily_haude`, `hourly_pm` and `daily_pm` against `test/reference/`, including the water balance at every step.

## Sensitivity of the water fluxes

```
./sensitivity.sh [run folder or run.txt]     # default: runs/hourly_pm_winter (PM)
```

This runs a one-at-a-time sensitivity analysis: each test run is the `run.txt` of the run with one override
added (e.g. `soil.field_capacity = 30`). Each parameter is tested at five levels: its valid lower limit,
the midpoint from the lower limit to its configured value, its configured value, the midpoint from its configured
value to the valid upper limit, and the valid upper limit. Other parameters stay at their configured values.
Cross-parameter constraints (such as `wilting_point < field_capacity`) are respected. `min_lai` and `max_lai`
are varied together (`land_use.lai`), with both set to the tested value.

* the soil columns, `init_soil`, `init_litter` and `land_use_fraction`
* the land-use columns (shared `min_lai`/`max_lai`, `root_depth`, `direct_runoff`, `litter_cap`, `degree_day`,
  interception and litter drying)
* with `PM`: `albedo`, `emissivity`, `veg_height`, `measurement_height`, `elevation`, `soil_surface_conductance`
  and the `pm.txt` columns

Parameters without effect in the run are skipped (e.g. `PM` parameters with `HAUDE`, interception without
leaves). The valid limits follow each parameter's allowed range and are tightened where needed to keep
combinations valid: initial soil water cannot exceed field capacity (a lower field capacity lowers `init_soil`
with it, noted in the table), and a litter capacity above 0 on a land use without litter layer gets the
drying factor 3.

The table `output_<yyyyMMdd_HHmmss>/sensitivity_oat.csv` lists, for every run, the sums over the run of:

* the potential ET (`ETP_Input`, `ETP_dry`, `ETP_dry_lu`, `ETP_dry_gap`), so you can check that the demand is
  plausible
* ETaTotal, I-Leaf, I-Litter, ETa, Seepage, Surface_runoff and Runoff_total
* the final soil storage

It also gives each value's change against the base run in %. The script prints a ranking by the largest change of
ETaTotal, Seepage or Runoff_total, and flags a mean ETP outside 0–10 mm/day.

The `hourly_pm_winter` forcing has no precipitation. A matching rain scenario is provided in
`hourly_pm_winter_rain`, with 10, 20, and 40 mm hourly events on 15, 16, and 17 January 2004 at 12:00. Both
use a synthetic, Germany-like January meteorological series based on the stated climate targets (2 °C mean air
temperature, about 140 W/m² mean daily KDOWN peak, 290–295 W/m² LDOWN, and 4.5 m/s wind at 10 m). These are
illustrative forcing values, not observed measurements. Run both:

```
./sensitivity.sh runs/hourly_pm_winter
./sensitivity.sh runs/hourly_pm_winter_rain
```

Each run writes `sensitivity_oat.csv` under a timestamped output folder next to its `run.txt`. With `PM`, LAI
affects transpiration through LAI/max_lai; the sensitivity analysis changes both LAI limits together.
Open `plot_sensitivity_oat.ipynb` to compare the forcing precipitation, monthly water-balance summaries, and
parameter sensitivities side by side for the dry and rainfall scenarios.

## Land Use Fraction: land use and bare-soil gap

Each run models one land use (the `land_use` row of `land_use.txt`). `land_use_fraction` (f, in `run.txt`,
default 1; formerly `Vegetation Fraction`) is the share of the area that this land use covers; the gap (1 − f) is bare
soil. Both share the litter layer and one soil store.

* **Water:** precipitation (rain and snow melt) on the land use (f) enters the canopy interception
  (capacity = `icept_cap_per_lai` × LAI); precipitation on the gap (1 − f) falls through directly to the
  litter and the soil. This is the same as the former "vegetation fraction" and leaves `HAUDE`/ET0 results
  unchanged.
* **Evapotranspiration with `PM`:** the dry-surface potential ET is computed for both parts and weighted by f:
  `ETP_dry = f · ETP_dry_lu + (1 − f) · ETP_dry_gap`. The land use transpires through the Järvi et al.
  (2011) conductance (scaled by LAI/max_lai); the bare soil evaporates through `soil_surface_conductance`
  (`soil.txt`, 5 mm s⁻¹ at field capacity) times the relative available water (W − PWP)/(FC − PWP). Before this
  change the Järvi conductance acted on the whole area, so a partly covered surface transpired as if fully
  covered, and a land use with max_lai 0 could lose soil water only by seepage.
* **Urban and other non-vegetated land uses** are represented as an ordinary land use with a low LAI, a
  shallow root depth (e.g. 1 cm) and a high `direct_runoff`; there is no separate sealed store.
* f = 1 reproduces the `PM` results from before the bare-soil gap was added exactly.

## Penman–Monteith on a daily time step is not recommended

The `PM` method runs on daily forcing, but use hourly (or at most 3-hourly) steps whenever possible:

* Penman–Monteith and the Järvi conductance are non-linear in their inputs, so ET computed from daily means
  is not the daily sum of ET. The errors do not cancel.
* Daily mean temperature and humidity understate the vapour pressure deficit of the daytime hours in which
  most ET happens.
* Daily mean net radiation includes the negative night-time values. It mixes energy that is not available
  for ET at night with the daytime energy.
* The conductance factors g(K↓), g(T) and g(Δq) evaluated at daily means are not the daily mean of the
  conductance. For example, the mean K↓ of a sunny day sits on a much flatter part of g(K↓) than the midday values.
* The Järvi et al. (2011) parameters were fitted to hourly data, and the neutral aerodynamic resistance
  misses the daytime instability.
* `Ta` accepts the alias `T14`, so a daily file prepared for Haude (14:00 temperatures) can silently enter
  `PM`.

`runs/daily_pm` is kept only to compare with the hourly run of the same month.

## Changes in this version

**New input structure** (breaking; the old files stop the run with a message saying where things moved):

* `run.txt` per run replaces `Soil_physics.txt` and the `readET`/`dontReadET` argument. It names the forcing
  file (any name) and the parameters folder, chooses soil, land use and `et_method` (`HAUDE`, `PM`, `ET0`),
  and holds the site settings and the initial state. Results are written next to it, so settings and outputs
  stay together, and each output folder keeps a copy of `run.txt`.
* The parameters moved into shared tables, one row per soil or land use, so rows can be compared column by
  column: `soil.txt` (soil properties only), `land_use.txt` (everything that depends on the land use: LAI and
  phenology, root depth, snow, runoff, litter, interception, albedo, emissivity, vegetation height),
  `pm.txt` (`G1` … `Kmax`) and `haude.txt`. Before, the land-use dependent interception and `PM` values were
  single keys in `Soil_physics.txt` and did not change with the land use, and each example folder had its own,
  diverging copy of the land-use table.
* Any table value can be overridden in `run.txt` (`soil.field_capacity = 34`), so the tables can stay the
  defaults.
* What a land use needs follows from what it has: without leaves no interception values, without litter no
  drying factor, phenology dates only for a seasonal LAI. A value without effect is noted in the log.
* Default soil `luvisol` (Parabraunerde from loess, KA5 values; see `GLOSSARY.md`). The example runs keep the
  soil of the original example (`simpel_example`) and give unchanged results.
* Root depth is set in one place (`land_use.txt`) instead of `Soil_physics.txt` with a land-use override.
* Keys and columns are in one style (`snake_case`), and every file uses one of two formats: `key = value`
  (`run.txt`) or a whitespace table (parameters, forcing).
* Dates may also be given as ISO 8601 (`2004-01-01`, `2004-01-01T13:00`).
* `start_of_reduction` is only checked when it is used (`HAUDE`, `ET0`).
* Land-use table updated: land uses renamed by type (`forest_deciduous`, `forest_evergreen`, `crop_corn`,
  `crop_sugarbeet`, `crop_winterwheat`, `grassland`, `grassland_irrigated`, `urban`; an old name stops the run
  with the new one), LAI, root depth and vegetation height set to typical values, albedo and emissivity for every
  land use, and a table of typical ranges at the top of `land_use.txt` (see `GLOSSARY.md`).

Fixes, with the reason for each:

* **Initial soil water above field capacity is rejected.** With `init_soil` above `field_capacity`
  the infiltration limit (FC − storage) × fraction is negative. P-linf then became negative, so water was
  taken out of the soil and booked as surface runoff even on dry days. Water above FC should drain as
  seepage, not run off.
* **Seepage leaves the soil store also above field capacity.** When the soil water after ET exceeded FC, the
  storage was reset to FC while the seepage of that step was still counted as outflow. That water was created
  and the balance did not close. Now storage = min(ET-Balance, FC) − Seepage, and the water above FC still
  runs off. Seepage is also capped so it cannot drain the store below the wilting point. With the infiltration
  limit this case is rare, so the fix is a safeguard.
* **Litter evaporation follows the SIMPEL spreadsheet:** MIN(demand; MIN(capacity; content + throughfall) /
  factor). The code divided only the water by the factor, so the litter could evaporate its full capacity
  in one step. Land uses with litter capacity 0 are unaffected. For beech (`forest_deciduous`) on the daily example, litter
  evaporation drops from 46.3 to 39.6 mm/yr.
* **Input/output handling:**
  * An `ET0` column no longer stops a `PM` run; it is ignored with a note.
  * The Haude coefficients are required only for `HAUDE`.
  * A failed write of the results stops the run instead of being reported as success.

New features:

* **`land_use_fraction` and two-part `PM`.** See the section above.
* **`infiltration_fraction`** (`soil.txt`, 0.25 in the included soils). The share of the soil deficit that may infiltrate
  per day used to be a fixed constant. It decides between infiltration and surface runoff, so it is now a
  parameter that can be tested for sensitivity.
* **Timestamped results folder and run log.** Results and `SIMPEL_log.txt` (the console output of the
  run, including errors) are written to `output_<yyyyMMdd_HHmmss>/` next to `run.txt`.

* **Sensitivity tool** (`sensitivity.sh`): see "Sensitivity of the water fluxes".

Clean-up, with unchanged results:

* The snow, litter and soil calculations existed twice: once for the first time step and once for all
  others. They are merged into one copy that uses the initial values at the first step.
* Unused helpers, fields and commented-out code are removed.
* The unused `DOY` forcing column is removed.
* The committed IDE settings and the git bundle are removed.

## Limitations

* One land use plus a bare-soil gap over one soil store: no separate sealed surface store, no subsoil below the root zone, no capillary rise. For (nearly) sealed land uses such as urban use a small root depth (e.g. 1 cm).
* Bare-soil evaporation in the gap only with `PM`; with `HAUDE`/ET0 the soil ET is not split between land use and gap.
* Static phenology: LAI does not respond to drought or weather.
* `PM`: neutral aerodynamic resistance (no stability correction); the Järvi et al. (2011) conductance parameters are from urban sites and need recalibration for other vegetation.

## References
AG Boden (2005): Bodenkundliche Kartieranleitung, 5. Auflage (KA5). Bundesanstalt für Geowissenschaften und Rohstoffe, Hannover.
Hörmann, G. (1997): SIMPEL - ein einfaches, benutzerfreundliches Bodenwassermodell zum Einsatz in der Ausbildung. Deutsche Gewässerkundliche Mitteilungen 41(2):67-72
Hörmann, G., X. Zhang and N. Fohrer (2007): Comparison of a simple and a spatially distributed hydrologic model for the simulation of a lowland catchment in Northern Germany. Ecological Modelling, 209 (1): 21-28. 
Järvi, L., Grimmond, C. S. B. and Christen, A. (2011): The Surface Urban Energy and Water Balance Scheme (SUEWS): Evaluation in Los Angeles and Vancouver. J. Hydrol. 411: 219-237, doi:10.1016/j.jhydrol.2011.10.001
Rutter, A. J., Kershaw, K. A., Robins, P. C., and Morton, A. J. (1971). A predictive model of rainfall interception in forests, 1. Derivation of the model from observations in a plantation of Corsican pine. Agr. Meteorol. 9, 367–384.
