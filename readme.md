# A JAVA implementation of the Soil Water Balance Model SIMPEL
## General description
This repository includes a JAVA implementation of the Soil Water Balance Model SIMPEL, which has been originally developed by Georg Hörmann (University of Kiel) in a spreadsheet format (Hörmann 1997, Hörmann et al., 2007). The new implementation includes new features, while preserving relevant design criteria of the minimal original model, which makes it a versatile modelling tool:

* Flexible time step, also enabling sub-daily temporal integration of the water balance; the step is taken from the dates of the forcing (daily or 1–12 h)
* New interception model (Rutter et al., 1971)
* Snow model added (temperature index)
* External evapotranspiration calculation (ET0 column)
* Evapotranspiration method `PM`: Penman–Monteith with the surface conductance of Järvi et al. (2011), with the available energy taken straight from the radiation forcing (net radiation column `QN`, or computed from `KDOWN`/`LDOWN`).
* Land-use lookup table (`Land_Use_Parameters.txt`, one row per land use) and a default phenology function for LAI
* Southern-hemisphere option (`Hemisphere;S`)
* All inputs are checked before the run (missing or implausible values, misspelled keys, inconsistent soil values)

## Input files

| File | Contents |
|---|---|
| `Global_Input.txt` | Forcing, one row per time step: `Date`, `P`, `Ta`, `RH`, and for `PM` `KDOWN`, `WindSpeed` (+ optional `QN`, `LDOWN`, `PRES`) |
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

The output `SIMPLE_bucket_model.csv` is written into the input directory.

Examples:

* `example/` – daily run with the Haude method (original example, 1995, Central Europe).
* `example_hourly/` – hourly run with `ET method;PM`, forced with January 2004 of the AU-Preston (Melbourne) tower data from the TARGET repository, averaged to hourly (`Hemisphere;S`). That data set has no precipitation, so P is 0 and the run shows a dry-down; the measurement height is the 40 m tower. It uses an added land use `irrigated_grass`.
* `example_daily_pm/` – the same month aggregated to daily means.

`./test.sh` compiles everything, runs the unit tests (Penman–Monteith against an independent Python implementation, phenology, input checks) and checks the three examples against `test/reference/`, including the water balance at every step.

## Limitations

* One pervious surface over one soil store: no sealed fraction, no subsoil below the root zone, no capillary rise. For (nearly) sealed land uses such as urban use a small root depth (e.g. 1 cm).
* Static phenology: LAI does not respond to drought or weather.
* `PM`: neutral aerodynamic resistance (no stability correction); the Järvi et al. (2011) conductance parameters are from urban sites and need recalibration for other vegetation.

## References
Hörmann, G. (1997): SIMPEL - ein einfaches, benutzerfreundliches Bodenwassermodell zum Einsatz in der Ausbildung. Deutsche Gewässerkundliche Mitteilungen 41(2):67-72
Hörmann, G., X. Zhang and N. Fohrer (2007): Comparison of a simple and a spatially distributed hydrologic model for the simulation of a lowland catchment in Northern Germany. Ecological Modelling, 209 (1): 21-28. 
Järvi, L., Grimmond, C. S. B. and Christen, A. (2011): The Surface Urban Energy and Water Balance Scheme (SUEWS): Evaluation in Los Angeles and Vancouver. J. Hydrol. 411: 219-237, doi:10.1016/j.jhydrol.2011.10.001
Rutter, A. J., Kershaw, K. A., Robins, P. C., and Morton, A. J. (1971). A predictive model of rainfall interception in forests, 1. Derivation of the model from observations in a plantation of Corsican pine. Agr. Meteorol. 9, 367–384.

