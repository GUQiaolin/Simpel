# A JAVA implementation of the Soil Water Balance Model SIMPEL
## General description
This repository includes a JAVA implementation of the Soil Water Balance Model SIMPEL, which has been originally developed by Georg Hörmann (University of Kiel) in a spreadsheet format (Hörmann 1997, Hörmann et al., 2007). The new implementation includes new features, while preserving relevant design criteria of the minimal original model, which makes it a versatile modelling tool:

* Flexible time step, also enabling sub-daily temporal integration of the water balance; the step is taken from the dates of the forcing (daily or 1–12 h)
* New interception model (Rutter et al., 1971)
* Snow model added (temperature index)
* External evapotranspiration calculation (ET0 column)
* Evapotranspiration method `PM`: Penman–Monteith with the surface conductance of Järvi et al. (2011), with the available energy taken straight from the radiation forcing (net radiation column `QN`, or computed from `KDOWN`/`LDOWN`).
* Land-use lookup table (`Land_Use_Parameters.txt`, one row per land use) and a default phenology function for LAI
* `Land Use Fraction`: the land use covers this share of the area, the gap is bare soil (with `PM` it evaporates through its own soil conductance)
* Southern-hemisphere option (`Hemisphere;S`)
* All inputs are checked before the run (missing or implausible values, misspelled keys, inconsistent soil values)

## Input files

| File | Contents |
|---|---|
| `Global_Input.txt` | Forcing, one row per time step: `Date`, `P`, `Ta`, `RH`, and for `PM` `KDOWN`, `WindSpeed` (+ optional `QN`, `LDOWN`, `PRES`). There is no `DOY` column: day of year and month come from `Date` |
| `Soil_physics.txt` | Soil, canopy, site and method parameters (`key;value;description`) |
| `Land_Use_Parameters.txt` | Land-use dependent parameters: LAI range, degree-day factor, direct runoff, litter capacity, optional root depth and phenology dates |
| `Haude_Coefficients.txt` | Monthly Haude coefficients per land use (only for ET method `HAUDE`) |

`GLOSSARY.md` describes every column, key and output column with units, and explains the root depth, the
phenology and the ET methods.

## How to run the model
Compile the source and call java on the command line with the arguments thud.simpel.SimpelModel (i.e., the main class) and the input directory (see run.sh script):

```
./run.sh                                   # compiles and runs example/
java -cp bin thud.simpel.SimpelModel <input directory> [readET|dontReadET]
```

The results (`SIMPLE_bucket_model.csv`) and a log of the run (`SIMPEL_log.txt`, everything printed to the console including errors) are written to a timestamped folder `output_<yyyyMMdd_HHmmss>/` in the input directory. Each run gets its own folder.

Examples:

* `example/` – daily run with the Haude method (original example, 1995, Central Europe).
* `example_hourly/` – hourly run with `ET method;PM`, forced with January 2004 of the AU-Preston (Melbourne) tower data from the TARGET repository, averaged to hourly (`Hemisphere;S`). That data set has no precipitation, so P is 0 and the run shows a dry-down; the measurement height is the 40 m tower. It uses an added land use `irrigated_grass`.
* `example_daily_pm/` – the same month aggregated to daily means (for comparison only, see "Penman–Monteith on a daily time step" below).

`./test.sh` compiles everything, runs the unit tests (Penman–Monteith against an independent Python implementation, phenology, single bucket steps, input checks) and checks the three examples against `test/reference/`, including the water balance at every step.

## Sensitivity of the water fluxes

```
./sensitivity.sh [input directory] [factor ...]     # default: example_hourly/ (PM), factors 0.5 1.5
```

This runs a one-at-a-time sensitivity analysis. Every parameter is set to 0.5× and 1.5× of its value, while all
others stay at their base value:

* soil keys
* canopy and litter keys
* the land-use columns (MinLAI and MaxLAI scaled together, DirectRunoff, LitterCap, DegreeDay, RootDepth)
* all `PM` keys

A value outside its allowed range is clipped (for example `Land Use Fraction` at 1, or `Init-Value Soil %` at field
capacity), and the table reports the value that was actually used. A parameter whose base value is 0 cannot be
scaled. `Start of Reduction %` is not included because `PM` does not use it.

The table `output_<yyyyMMdd_HHmmss>/sensitivity_oat.csv` lists, for every run, the sums over the run of:

* the potential ET (`ETP_Input`, `ETP_dry`, `ETP_dry_lu`, `ETP_dry_gap`), so you can check that the demand is
  plausible
* ETaTotal, I-Leaf, I-Litter, ETa, Seepage, Surface_runoff and Runoff_total
* the final soil storage

It also gives each value's change against the base run in %. The script prints a ranking by the largest change of
ETaTotal, Seepage or Runoff_total, and flags a mean ETP outside 0–10 mm/day.

The `example_hourly/` forcing has no precipitation, so interception, litter, infiltration and runoff parameters
show no effect there. Run the script on a forcing with rain to test them. Also note that, with `PM`, LAI acts on
transpiration only through LAI/MaxLAI. Scaling MinLAI and MaxLAI together therefore changes only the
interception, not the transpiration.

## Land Use Fraction: land use and bare-soil gap

Each run models one land use (the `Land use` row of `Land_Use_Parameters.txt`). `Land Use Fraction` (f,
formerly `Vegetation Fraction`) is the share of the area that this land use covers; the gap (1 − f) is bare
soil. Both share the litter layer and one soil store.

* **Water:** precipitation (rain and snow melt) on the land use (f) enters the canopy interception
  (capacity = `Layer Thickness` × LAI); precipitation on the gap (1 − f) falls through directly to the
  litter and the soil. This is the same as the former "vegetation fraction" and leaves `HAUDE`/ET0 results
  unchanged.
* **Evapotranspiration with `PM`:** the dry-surface potential ET is computed for both parts and weighted by f:
  `ETP_dry = f · ETP_dry_lu + (1 − f) · ETP_dry_gap`. The land use transpires through the Järvi et al.
  (2011) conductance (scaled by LAI/MaxLAI); the bare soil evaporates through `Soil surface conductance`
  (default 5 mm s⁻¹ at field capacity) times the relative available water (W − PWP)/(FC − PWP). Before this
  change the Järvi conductance acted on the whole area, so a partly covered surface transpired as if fully
  covered, and a land use with MaxLAI 0 could lose soil water only by seepage.
* **Urban and other non-vegetated land uses** are represented as an ordinary land use with a low LAI, a
  shallow root depth (e.g. 1 cm) and a high `DirectRunoff`; there is no separate sealed store.
* **Updating old input:** rename the line `Vegetation Fraction` to `Land Use Fraction` in
  `Soil_physics.txt` (the run stops with a message otherwise). For `PM`, `Soil surface conductance` is
  optional. f = 1 reproduces the previous `PM` results exactly.

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

`example_daily_pm/` is kept only to compare with the hourly run of the same month.

## Changes in this version

Fixes, with the reason for each:

* **Initial soil water above field capacity is rejected.** With `Init-Value Soil %` above `Field Capacity %`
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
  in one step. Land uses with litter capacity 0 are unaffected. For beech on the daily example, litter
  evaporation drops from 46.3 to 39.6 mm/yr.
* **Input/output handling:**
  * An `ET0` column no longer stops a `PM` run; it is ignored with a note.
  * `Haude_Coefficients.txt` is required only for `HAUDE` without ET0.
  * A failed write of the results stops the run instead of being reported as success.

New features:

* **`Land Use Fraction` and two-part `PM`.** See the section above.
* **`Infiltration fraction`** (optional, default 0.25). The share of the soil deficit that may infiltrate
  per day used to be a fixed constant. It decides between infiltration and surface runoff, so it is now a
  parameter that can be tested for sensitivity.
* **Timestamped results folder and run log.** Results and `SIMPEL_log.txt` (the console output of the
  run, including errors) are written to `<input dir>/output_<yyyyMMdd_HHmmss>/`.

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
Hörmann, G. (1997): SIMPEL - ein einfaches, benutzerfreundliches Bodenwassermodell zum Einsatz in der Ausbildung. Deutsche Gewässerkundliche Mitteilungen 41(2):67-72
Hörmann, G., X. Zhang and N. Fohrer (2007): Comparison of a simple and a spatially distributed hydrologic model for the simulation of a lowland catchment in Northern Germany. Ecological Modelling, 209 (1): 21-28. 
Järvi, L., Grimmond, C. S. B. and Christen, A. (2011): The Surface Urban Energy and Water Balance Scheme (SUEWS): Evaluation in Los Angeles and Vancouver. J. Hydrol. 411: 219-237, doi:10.1016/j.jhydrol.2011.10.001
Rutter, A. J., Kershaw, K. A., Robins, P. C., and Morton, A. J. (1971). A predictive model of rainfall interception in forests, 1. Derivation of the model from observations in a plantation of Corsican pine. Agr. Meteorol. 9, 367–384.
