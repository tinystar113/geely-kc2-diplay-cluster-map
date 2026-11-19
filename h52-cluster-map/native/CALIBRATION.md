# H52 cluster map calibration (2026-11-09, 21:03–21:05)

Source: `vfb_selftest grid 120` in navigation mode; photos `C:\Users\Hasee\Desktop\Car\IMG_0343.jpg` (blue theme),
`IMG_0344.jpg` (COMFORT), `IMG_0346.jpg` (SPORT). Percentages are of the 1920×720 cluster surface (x from left,
y from top), read from the colour-coded 10 % lines (yellow = 50 %).

## What the cluster shows per drive-mode theme

| Theme | Map area shown | Overlays on the map |
|---|---|---|
| COMFORT (IMG_0344) | **whole frame**, scaled to the full panel (all nine lines each way visible; corner squares hidden by the panel's curved/cut corners) | rev counter x≈5–28 %, y≈40–95 %; speedometer x≈78–97 %, y≈37–85 %; top bar + guidance banner x≈38–68 %, y≈0–18 %; bottom row (ODO, temp, gear P, fuel) y≈85–100 % |
| blue theme (IMG_0343) | **window on the right**, frame cropped (not scaled) to x≈35–67 %, y≈27–86 % | guidance banner across the window top |
| SPORT (IMG_0346) | **window on the right**, frame cropped to x≈30–68 %, y≈22–89 % | guidance banner across the window top (y≈22–32 %) |

## Clear area in every theme

x ≈ 33–66 %, y ≈ 33–85 % (window crop ∩ COMFORT gauge-free centre, below the guidance banner).
The stock Gaode cluster frame (`hud-probe\fb2.png`) puts its car marker at x≈50 %, y≈66 % — inside this area.

## Decision for Task 4

Use the existing `CarPlayClusterDisplay.config` with **verticalStep = +1** (no change to its BYD safe-area constants):
marker at x 49.5 %, y 55.5 %; resulting safe area x 35–64 %, y 26–85 %. Horizontally inside the clear area;
the top 26–33 % may sit under the guidance banner in the windowed themes, which only hides the far
look-ahead of the route, not the marker. Call: `config(1920, 720, 83, 0, 1, Content.MAP)`.
