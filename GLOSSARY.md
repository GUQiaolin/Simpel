# SIMPEL glossary

The output columns (and the matching `*_` constants in `SimpelModel.java`) keep the names of the
original SIMPEL spreadsheet columns, so results can be compared with the Excel and R versions.
This table says what each one means. Fluxes are in mm per time step, stores in mm.

| Column (constant) | Spreadsheet col. | Meaning | Unit |
|---|---|---|---|
| Precipitation (`PRECIPITATION`) | B | Precipitation from `Global_Input.txt` | mm/step |
| Snow_Water_Equi (`SNOW_WATER_EQUI`) | C | Water stored in the snow pack | mm |
| Snow_melt+rain (`SNOW_MELT_RAIN`) | D | Rain plus snow melt reaching the canopy | mm/step |
| ETP_Coeff (`ETP_COEFF`) | E | Monthly Haude coefficient for the land use (`Land_Use.txt`) | mm/hPa per day |
| ETP_Input (`ETP_INPUT`) | F | Potential evapotranspiration used by the model | mm/step |
| LAI (`LAI`) | G | Leaf area index from `LAI_model.txt` | m²/m² |
| I-Cap (`I_CAP`) | H | Interception capacity = layer thickness × LAI | mm |
| Int. ETi(leaf) (`INT_ETI_LEAF`) | I | Evaporation of intercepted water | mm/step |
| I-Bal (`I_BAL`) | J | Interception storage at the end of the step | mm |
| I-Prec (`I_PREC`) | K | Throughfall + canopy drainage reaching the litter | mm/step |
| I-Rem (`I_REM`) | L | Evaporative demand left after interception (ETP − leaf evaporation) | mm/step |
| I_ETi(litter) (`I_ETI_LITTER`) | M | Evaporation from the litter layer | mm/step |
| Bilanz (`BILANZ`) | N | Litter water balance before capping ("Bilanz" = balance) | mm |
| Content (`CONTENT`) | O | Litter storage at the end of the step | mm |
| S-REstn (`S_RESTN`) | P | Water passing the litter layer (excess over litter capacity) | mm/step |
| Inf-Limit (`INF_LIMIT`) | Q | Maximum infiltration this step | mm/step |
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

## Soil and vegetation parameters (`Soil_physics.txt`)

| Key | Meaning | Unit |
|---|---|---|
| Timestep | Length of one row of `Global_Input.txt` | h |
| Field Capacity % / Permanent Wilting Point % / Start of Reduction % / Init-Value Soil % | Volumetric water contents; × root depth gives mm | vol-% |
| Root Depth | Depth of the soil bucket | cm |
| Start of Reduction | Below this soil water, ET is reduced linearly to zero at the wilting point | vol-% |
| Glugla coeff. | Coefficient c of the Glugla seepage law; lambda = c / depth² | – |
| Litter Reduction factor | At most 1/factor of the litter water can evaporate per day | – |
| Direct runoff factor | Percentage of the water passing the litter layer that runs off directly | % |
