"""Writes bass-0.30.f32: 8 s of float32 mono PCM at 44.1 kHz (seed 12345).

Carrier of 80/440/5000 Hz sines plus 20-250 Hz noise kicks with a 10 ms attack and 120 ms decay,
one per second after a 4 s warm-up, at level 0.30.
"""
import numpy as np

warm, meas, seed, level = 4.0, 4.0, 12345, 0.30
count = round((warm + meas) * 44100)
t = np.arange(count) / 44100
carrier = sum(.04 * np.sin(2 * np.pi * f * t) for f in (80, 440, 5000))
spec = np.fft.rfft(np.random.default_rng(seed).standard_normal(count))
fr = np.fft.rfftfreq(count, 1 / 44100)
spec[(fr < 20) | (fr > 250)] = 0
bass = np.fft.irfft(spec, n=count)
bass /= max(float(np.max(np.abs(bass))), 1e-12)
rel = np.maximum(t - warm, 0) % 1
env = np.minimum(rel / .01, 1) * np.exp(-rel / .12)
env *= t >= warm
(carrier + level * env * bass).astype('<f4').tofile('bass-0.30.f32')
