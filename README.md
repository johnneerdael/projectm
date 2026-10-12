# Evidence: resolution-scaled lines (projectM #682)

Images, data and the harness behind the pull request that adds
`projectm_opengl_set_line_reference_size()` (branch
[`feat/quad-lines-682`](https://github.com/johnneerdael/projectm/tree/feat/quad-lines-682)).
This is an orphan branch for evidence only; it is not meant to be merged.

## What was compared

Every preset was rendered three ways, on the same frames:

| Role | Library | Render size | Lines |
|---|---|---|---|
| upstream | `master` at `349b7cc2d` | 3840×2160 and 1920×1080 | 1 px GL lines |
| this PR | `feat/quad-lines-682`, library code of `fc6fd7e19` | 3840×2160 and 1920×1080 | reference size 1024×768 |
| authored look | `master` at `349b7cc2d` | 1182×665 | 1 px GL lines |

1182×665 is 16:9 with the area of 1024×768, Milkdrop's authoring resolution: the canvas size at
which lines, blur radii and `texsize` steps cover the share of the picture the preset author saw.

A fourth run renders this PR with the reference size **0** (the default) and checks that it gives the
same bytes as upstream.

**Per change:** the same presets were first rendered with the branch at `798483e93` (quad lines, sample
count and wave fade only). Those results are in `data/lines-only/`. The blur and `texsize` commit
(`fc6fd7e19`) was then added and only the branch roles were rendered again (`harness/rerun_branch.py`);
upstream and authored frames are shared. The later commits `f673d847c` (SDL test UI line endings),
`e82d72777` (motion vector buffers created on first use; Fvese-mvfun2's 64×48 vectors give a
byte-identical 4K frame) and `36b669c0b` (header comment) change no rendering.

## Method

- **Harness:** `harness/render.cpp` uses only the public C API: hidden SDL2 window, OpenGL 3.3 core
  context (Apple M4 Pro, OpenGL 4.1 Metal), an RGBA8 framebuffer, `projectm_opengl_render_frame_fbo()`.
  Mesh 48×32, 30 fps, preset locked. `harness/drive.py` runs the sample, `harness/analyze.py` summarizes,
  `harness/crops.py` makes the full-size images.
- **Audio:** `harness/gen_pcm.py` writes 8 s of float32 mono PCM (sines plus noise kicks, seed 12345);
  1470 samples per frame through `projectm_pcm_add_float()`.
- **Clock:** `projectm_set_frame_time(frame / 30)`.
- **Measured:** mean Rec. 709 luma of every 5th frame from 120 to 239 (4 s after a 4 s warm-up).
  Images are frame 239.
- **Determinism (evidence builds only):** unmodified upstream does not render the same frame twice.
  Random textures, noise textures and `rand_preset` are seeded from `std::random_device` or the system
  clock, `TimeKeeper`'s first update reads the wall clock before any frame time is set, and new
  framebuffer textures start with undefined contents, which feedback presets amplify. Both libraries
  were built with the same `harness/determinism-evidence-only.patch` (fixed seeds, zeroed new textures,
  a zero clock origin). The patch is **not** part of the pull request. A few feedback presets still vary
  between runs after it (see below).
- **Presets:** the Cream of the Crop pack (9,606 presets, CC0). The run covered every 8th preset in name
  order and stopped after 124 presets (names `$$$ Royal - Mashup …`, digits, `A…`, `B…`), so the sample
  leans to those authors.

## Results

124 presets rendered; 1 fails to compile its per-frame code in both libraries, and 20 are nearly black
at the authored size (mean luma < 0.02), which leaves 103 brightness comparisons.

Mean luma divided by the authored look's (`data/summary.json`, `data/lines-only/summary.json`):

| | median | within ±10 % | below 0.5× | above 1.5× |
|---|---|---|---|---|
| upstream, 4K | 0.89 | 39 / 103 | 21 | 0 |
| lines only, 4K | 1.00 | 67 / 103 | 2 | 2 |
| **this PR, 4K** | **1.02** | **75 / 103** | **0** | **6** |
| upstream, 1080p | 0.95 | 56 / 103 | 4 | 0 |
| lines only, 1080p | 1.00 | 81 / 103 | 1 | 1 |
| **this PR, 1080p** | **1.01** | **83 / 103** | **0** | **2** |

At 4K this PR is closer to the authored look for 77 presets and further for 22.

The six above 1.5× at 4K (upstream → this PR): `$$$ Royal - Mashup (147)` 0.13 → 1.51,
`$$$ Royal - Mashup (158)` 0.76 → 1.62, `$$$ Royal - Mashup (389)` 1.15 → 1.64, `153` 0.18 → 1.51,
`271 nz+ m21` 0.39 → 3.15, `280 nz+` 0.25 → 1.51. These are feedback presets whose seeds grow or die
depending on small differences; bilinear feedback smoothing per real texel (not changed here) is the
remaining size-dependent quantity.

**Reference size 0 is upstream:** in the main run 109 of 123 presets give the same frame 239 bytes. The
other 14 also change between repeated runs of unmodified upstream. Re-rendered three times each
(`data/identity-retest.json`), 13 share a frame with upstream. The last, `$$$ Royal - Mashup (220)`,
gave three equal frames on upstream and a different one with reference 0 in that retest; four more runs
of each produced the same set of frames on both sides (upstream 2c9354…, 14ed97…; reference 0
2c9354…, 2c68e0…, 586cea…, the frame upstream gave in the retest). 12 upstream repeats in the main run
were byte-identical.

**Cost** (`data/timing.json`; 4K, median frame time of frames 60–239 with `glFinish` around each
frame, two runs each, upstream → this PR): Flexi - alien complex 03 (outlined shapes) 16.03/16.43 →
16.28/16.26 ms; Fvese-mvfun2 (64×48 motion vectors) 3.85/3.74 → 3.98/3.91 ms; Bdrv yin - 315 (custom
waves) 5.07/5.03 → 5.03/5.03 ms; Royal - Mashup (103) 3.94/3.68 → 1.99/1.92 ms (the sample count rule
draws a third of the waveform points at 4K); fat cancer tour meant t nz+ (blur) 3.35/3.38 → 3.21/3.27 ms;
TonyMilkdrop - Nuclear (blur) 3.05/3.06 → 2.92/2.97 ms. The lines-only build's numbers are kept in the
same file. Desktop numbers only.

## Images

- `img/full/NN.jpg`: upstream 4K | this PR 4K | authored look, each downscaled to 1280×720. The 30
  presets with the largest 4K difference between upstream and this PR were rendered again at full size
  (`data/top30-manifest.json`); the 16 shown are those with a clear visual difference toward the
  authored look (`data/gallery.json`). The other 14 are chaotic feedback presets whose frame changes but
  is not visibly closer, show no visible difference at this frame, end up brighter than authored, or
  vary between upstream runs.
- `img/crop/NN.png`: unscaled 640×360 regions of the two 4K frames where they differ most, for the
  presets where they show the stroke and texel widths.
- `img/per-change/NN.jpg`: upstream 4K | lines only 4K | this PR 4K | authored look (960×540 each),
  showing what the blur and `texsize` commit adds.

Labels sit above the pixels; nothing is brightened or recoloured. `presets/` holds the 16 preset files.

## Limits

- One desktop GPU. Pixel-border ties on GLES are biased by 1/64 px to match Mali-G52's GL lines; no GLES
  device was run for this pull request, and Tegra is not verified.
- The authored look is a projectM render at 1182×665, not Milkdrop on Windows.
- A single synthetic audio signal and one frame per image.
