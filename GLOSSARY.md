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
| K_DOWN, WINDSPEED, L_DOWN | – | Copies of the KDOWN, WindSpeed, LDOWN forcing columns | W m⁻², m s⁻¹, W m⁻² |
| NetRadiation (`NET_RADIATION`) | – | Q*, PM method only | W m⁻² |
| StorageHeat (`STORAGE_HEAT`) | – | ΔQs from OHM, PM method only | W m⁻² |
| ra (`AERODYNAMIC_RESISTANCE`) | – | Aerodynamic resistance, PM method only | s m⁻¹ |
| rs (`SURFACE_RESISTANCE`) | – | Surface resistance = 1000 / gs, PM method only | s m⁻¹ |
| ETP_dry (`ETP_DRY`) | – | Penman–Monteith ET of the transpiring surface (with rs), PM method only. ETP_Input is then the wet-surface value (rs = 0) | mm/step |
| QE (`LATENT_HEAT_FLUX`) | – | ETaTotal converted to a latent heat flux (all methods) | W m⁻² |

## Soil and vegetation parameters (`Soil_physics.txt`)

| Key | Meaning | Unit |
|---|---|---|
| Timestep | No longer needed: the step length is taken from the Date column. If present it must match the dates | h |
| Field Capacity % / Permanent Wilting Point % / Start of Reduction % / Init-Value Soil % | Volumetric water contents; × root depth gives mm | vol-% |
| Root Depth | Depth of the soil bucket | cm |
| Start of Reduction | Below this soil water, ET is reduced linearly to zero at the wilting point | vol-% |
| Glugla coeff. | Coefficient c of the Glugla seepage law; lambda = c / depth² | – |
| Litter Reduction factor | At most 1/factor of the litter water can evaporate per day | – |
| Direct runoff factor | Percentage of the water passing the litter layer that runs off directly | % |

## Forcing columns (`Global_Input.txt`, matched by header name)

| Column | Meaning | Unit | Needed for |
|---|---|---|---|
| Date | `dd.mm.yyyy` (daily) or `dd.mm.yyyy.hh` (sub-daily); sets the time step, day of year and month | – | always |
| DOY | Ignored; the day of year comes from Date | – | optional |
| P | Precipitation | mm/step | always |
| T14 or Ta | Air temperature (14:00 value for daily Haude runs) | °C | always |
| RF14 or RH | Relative humidity | % | always |
| KDOWN | Incoming short-wave radiation | W m⁻² | PM |
| WindSpeed | Wind speed at the measurement height | m s⁻¹ | PM |
| LDOWN | Incoming long-wave radiation (clear-sky estimate if missing) | W m⁻² | PM, optional |
| PRES | Air pressure (from Elevation if missing) | hPa | PM, optional |
| ET0 | External potential ET (read with the `readET` argument, or automatically if present) | mm/step | optional |

## Evapotranspiration method keys (`Soil_physics.txt`, all optional)

| Key | Meaning | Default |
|---|---|---|
| ET method | `HAUDE` (monthly coefficient × vapour pressure deficit) or `PM` | HAUDE |
| Albedo, Surface emissivity | Net radiation from KDOWN and LDOWN | 0.18, 0.98 |
| Vegetation height, Measurement height | Aerodynamic resistance (neutral log profile) | 0.12 m, 2 m |
| Elevation | Air pressure when there is no PRES column | 0 m |
| OHM a1, OHM a2, OHM a3 | Storage heat ΔQs = a1 Q* + a2 dQ*/dt + a3 (Grimmond & Oke 1999, short grass) | 0.32, 0.54 h, −27.4 W m⁻² |
| G1 … G6, TH, TL, Kmax | Surface conductance, Järvi et al. (2011), parameter names and values of Järvi et al. (2011) | 16.4764 mm s⁻¹, 566.0923, 0.2163, 3.3649, 11.0764, 0.0176, 40, 0, 1200 |

The soil-moisture term of the conductance uses the SIMPEL bucket: deficit = field capacity − soil water,
and the deficit at the wilting point is field capacity − permanent wilting point (instead of separate wilting-point parameters).
In PM mode SIMPEL's own linear ET reduction is switched off, so soil drying is counted once.
