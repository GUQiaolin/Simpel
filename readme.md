# A JAVA implementation of the Soil Water Balance Model SIMPEL
## General description
This repository includes a JAVA implementation of the Soil Water Balance Model SIMPEL, which has been originally developed by Georg Hörmann (University of Kiel) in a spreadsheet format (Hörmann 1997, Hörmann et al., 2007). The new implementation includes new features, while preserving relevant design criteria of the minimal original model, which makes it a versatile modelling tool:

* Flexible time step, also enabling sub-daily temporal integration of the water balance
* New interception model (Rutter et al., 1971)
* Snow model added (temperature index)
* External evapotranspiration calculation
* Hourly evapotranspiration from radiation forcing (`ET method;PM`): Penman–Monteith with the available energy from KDOWN/LDOWN (net radiation minus OHM storage heat) and the surface conductance of Järvi et al. (2011)

## How to run the model
Compile the source and call java on the command line with the arguments thud.simpel.SimpelModel (i.e., the main class) and the example path (see run.sh script).

* `example/` – daily run with the Haude method (original example).
* `example_hourly/` – hourly run with `ET method;PM`, forced with January 2004 of the AU-Preston (Melbourne) tower data from the TARGET repository, averaged to hourly. That data set has no precipitation, so P is 0 and the run shows a dry-down; the measurement height is the 40 m tower.

`GLOSSARY.md` explains all output columns, forcing columns and parameters. `./test.sh` compiles everything, runs the unit tests and checks both examples against `test/reference/` (including the water balance at every step).

## References
Hörmann, G. (1997): SIMPEL - ein einfaches, benutzerfreundliches Bodenwassermodell zum Einsatz in der Ausbildung. Deutsche Gewässerkundliche Mitteilungen 41(2):67-72
Hörmann, G., X. Zhang and N. Fohrer (2007): Comparison of a simple and a spatially distributed hydrologic model for the simulation of a lowland catchment in Northern Germany. Ecological Modelling, 209 (1): 21-28. 
Järvi, L., Grimmond, C. S. B. and Christen, A. (2011): The Surface Urban Energy and Water Balance Scheme (SUEWS): Evaluation in Los Angeles and Vancouver. J. Hydrol. 411: 219-237, doi:10.1016/j.jhydrol.2011.10.001
Grimmond, C. S. B. and Oke, T. R. (1999): Heat storage in urban areas: local-scale observations and evaluation of a simple model. J. Appl. Meteorol. 38: 922-940.
Rutter, A. J., Kershaw, K. A., Robins, P. C., and Morton, A. J. (1971). A predictive model of rainfall interception in forests, 1. Derivation of the model from observations in a plantation of Corsican pine. Agr. Meteorol. 9, 367–384.

