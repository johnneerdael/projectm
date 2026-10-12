# Evidence: scaled quad lines (projectM #682)

Images, data and the harness behind the pull request that adds
`projectm_opengl_set_line_reference_size()` (branch
[`feat/quad-lines-682`](https://github.com/johnneerdael/projectm/tree/feat/quad-lines-682)).
This is an orphan branch for evidence only; it is not meant to be merged.

## What was compared

Every preset was rendered three ways, on the same frames:

| Role | Library | Render size | Lines |
|---|---|---|---|
| upstream | `master` at `349b7cc2d` | 3840×2160 (also 1920×1080) | 1 px GL lines |
| this PR | `feat/quad-lines-682` at `798483e93` (see below) | 3840×2160 (also 1920×1080) | reference size 1024×768: 3.25 px at 4K, 1.62 px at 1080p |
| authored look | `master` at `349b7cc2d` | 1182×665 | 1 px GL lines |

1182×665 is 16:9 with the area of 1024×768, Milkdrop's authoring resolution: the size at which a
1 px line covers the share of the picture the preset author saw.

The renders used `798483e93`. The branch's later commits change only the SDL test UI's line endings
(`f673d847c`) and create the motion vector buffers on first use (`e82d72777`); with the latter,
Fvese-mvfun2 (64×48 motion vectors) renders a byte-identical 4K frame 239.

A fourth run renders this PR with the reference size **0** (the default) and checks that it is
byte-identical to upstream.

## Method

- **Harness:** `harness/render.cpp`, a small program using only the public C API: hidden SDL2 window,
  OpenGL 3.3 core context (Apple M4 Pro, OpenGL 4.1 Metal), an RGBA8 framebuffer,
  `projectm_opengl_render_frame_fbo()`. Mesh 48×32, 30 fps, preset locked.
- **Audio:** `harness/gen_pcm.py` writes 8 s of float32 mono PCM (sines plus noise kicks, seed
  12345); 1470 samples per frame through `projectm_pcm_add_float()`.
- **Clock:** `projectm_set_frame_time(frame / 30)`.
- **Measured:** mean Rec. 709 luma of every 5th frame from 120 to 239 (4 s after a 4 s warm-up).
  Images are frame 239.
- **Determinism (evidence builds only):** unmodified upstream does not render the same frame twice.
  Random textures, noise textures and `rand_preset` are seeded from `std::random_device` or the system
  clock, `TimeKeeper`'s first update reads the wall clock before any frame time is set, and new
  framebuffer textures start with undefined contents, which feedback presets amplify. Both libraries
  were built with the same `harness/determinism-evidence-only.patch` (fixed seeds, zeroed new
  textures, a zero clock origin). The patch is **not** part of the pull request.
- **Presets:** the Cream of the Crop pack (9,606 presets, CC0). The run covered every 8th preset in
  name order and stopped after 124 presets (names `$$$ Royal - Mashup …`, digits, `A…`, `B…`), so
  the sample leans to those authors.

## Results (`data/summary.json`, `data/measured.json`)

124 presets rendered; 1 fails to compile its per-frame code in both libraries, and 20 are nearly
black at the authored size (mean luma < 0.02), which leaves 103 brightness comparisons.

Mean luma divided by the authored look's:

| | median | within ±10 % | below 0.5× | above 1.5× |
|---|---|---|---|---|
| upstream, 4K | 0.89 | 39 / 103 | 21 | 0 |
| this PR, 4K | 1.00 | 67 / 103 | 2 | 2 |
| upstream, 1080p | 0.95 | 56 / 103 | 4 | 0 |
| this PR, 1080p | 1.00 | 81 / 103 | 1 | 1 |

At 4K this PR is closer to the authored look for 70 presets and further for 20 (13 unchanged).

**Reference size 0 is upstream:** 109 of 123 presets give the same frame 239 bytes. The other 14 also
change between repeated runs of unmodified upstream (with the determinism patch); re-running them
three times each (`data/identity-retest.json`), 12 share a frame with upstream and the other 2 give a
different frame on every upstream run. 12 upstream repeats in the main run were byte-identical.

**Cost** (`data/timing.json`; 4K, median frame time of frames 60–239 with `glFinish` around each
frame, two runs each, upstream → this PR): Flexi - alien complex 03 (outlined shapes)
14.8/15.8 → 14.8/15.5 ms; Fvese-mvfun2 (64×48 motion vectors) 3.74/3.79 → 3.83/3.81 ms; Bdrv yin -
315 (custom waves) 5.21/4.97 → 5.07/4.82 ms; Royal - Mashup (103) 3.54/3.56 → 1.89/1.87 ms (the
sample-count rule draws a third of the waveform points at 4K). Desktop numbers only.

## Gallery (`img/`, `data/gallery.json`)

The 30 presets with the largest 4K difference between upstream and this PR were rendered again at
full resolution (`harness/crops.py`). Only those with a clear visual difference that moves toward the
authored look are shown: 16 of the 30. The others are chaotic feedback presets whose frame changes but
is not visibly closer to the authored look, or show no visible difference at this frame, or (one)
vary between upstream runs. They remain in the statistics.

- `img/full/NN.jpg`: upstream 4K | this PR 4K | authored look, each downscaled to 1280×720.
- `img/crop/NN.png`: an unscaled 640×360 region of the two 4K frames where they differ most, for the
  presets where it shows the line widths.

Labels sit above the pixels; nothing is brightened or recoloured. `presets/` holds the 16 preset
files.

## Limits

- One desktop GPU. Pixel-border ties on GLES are biased by 1/64 px to match Mali-G52's GL lines; no
  GLES device was run for this pull request.
- The authored look is a projectM render at 1182×665, not Milkdrop on Windows.
- Blur levels and `texsize` still follow the render size in this PR, so blur-heavy presets look sharper
  at 4K than at the authored size whatever the line width.
- A single synthetic audio signal and one frame per image.
