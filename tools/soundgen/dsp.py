"""Small DSP toolkit used by generate.py to synthesize every sound of the mod.

Two families of helpers live here:
  * "circular" helpers (prefix c_) operate on whole loops in the frequency domain, so the
    rendered ambience loops are perfectly seamless (the end flows into the start).
  * regular helpers for one-shot sounds (footsteps, clicks, birds...).
"""
import os
import subprocess
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
rng = np.random.default_rng(20261020)


def seed(value):
    global rng
    rng = np.random.default_rng(value)


def R():
    return rng


def n_of(dur):
    return int(round(dur * SR))


def t_axis(n):
    return np.arange(n) / SR


def db(v):
    return 10 ** (v / 20.0)


# ---------------------------------------------------------------- circular (loop) helpers

def c_freqs(n):
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = 1e-3
    return f


def c_noise(n, color="white"):
    """Circular noise: its spectrum is shaped in the FFT domain, so it wraps seamlessly."""
    x = np.fft.rfft(rng.standard_normal(n))
    f = c_freqs(n)
    if color == "pink":
        x /= np.sqrt(np.maximum(f, 15.0))
    elif color == "brown":
        x /= np.maximum(f, 15.0)
    x[0] = 0
    y = np.fft.irfft(x, n)
    return y / (np.std(y) + 1e-12)


def c_filter(x, response):
    """Zero-phase circular filtering with an arbitrary magnitude response function of frequency."""
    n = len(x)
    return np.fft.irfft(np.fft.rfft(x) * response(c_freqs(n)), n)


def h_band(fc, oct_width):
    return lambda f: np.exp(-0.5 * (np.log2(f / fc) / oct_width) ** 2)


def h_lowpass(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def h_highpass(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / f) ** (2 * order))


def h_bandpass(lo, hi, order=2):
    return lambda f: h_highpass(lo, order)(f) * h_lowpass(hi, order)(f)


def c_curve(n, max_hz, min_hz=0.0, smooth=1.0):
    """Smooth periodic random curve normalized to [0, 1].

    Built from sinusoids with an integer number of cycles over the loop, so it wraps.
    """
    dur = n / SR
    k_min = max(1, int(np.ceil(min_hz * dur)))
    k_max = max(k_min + 1, int(max_hz * dur))
    t = np.arange(n) / n
    y = np.zeros(n)
    for k in range(k_min, k_max + 1):
        y += (1.0 / k ** smooth) * np.sin(2 * np.pi * k * t + rng.uniform(0, 2 * np.pi))
    y -= y.min()
    return y / (y.max() + 1e-12)


def c_tone_freq(f, n):
    """Snap a frequency so that it completes an integer number of cycles over the loop."""
    dur = n / SR
    return max(1, round(f * dur)) / dur


def c_place(buf, event, start):
    """Add an event into a loop buffer, wrapping around the end."""
    n = len(buf)
    start = int(start) % n
    m = len(event)
    end = start + m
    if end <= n:
        buf[start:end] += event
    else:
        first = n - start
        buf[start:] += event[:first]
        rest = event[first:]
        while len(rest) > 0:
            k = min(len(rest), n)
            buf[:k] += rest[:k]
            rest = rest[k:]


def c_reverb(x, ir):
    n = len(x)
    if len(ir) > n:
        ir = ir[:n]
    pad = np.zeros(n)
    pad[: len(ir)] = ir
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(pad), n)


def pan(x, p):
    """Equal power pan, p in [-1, 1] -> (n, 2)."""
    a = (p + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


# ---------------------------------------------------------------- one-shot helpers

def white(n):
    return rng.standard_normal(n)


def pink(n):
    m = max(n, 2048)
    return c_noise(m, "pink")[:n]


def brown(n):
    m = max(n, 2048)
    return c_noise(m, "brown")[:n]


def _sos(kind, f, order=2):
    nyq = SR / 2
    if isinstance(f, (list, tuple)):
        wn = [min(max(v / nyq, 1e-4), 0.999) for v in f]
    else:
        wn = min(max(f / nyq, 1e-4), 0.999)
    return signal.butter(order, wn, btype=kind, output="sos")


def lp(x, f, order=2):
    return signal.sosfilt(_sos("lowpass", f, order), x)


def hp(x, f, order=2):
    return signal.sosfilt(_sos("highpass", f, order), x)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos("bandpass", [lo, hi], order), x)


def resonator(x, f, q):
    b, a = signal.iirpeak(min(f, SR / 2 * 0.98), q, fs=SR)
    return signal.lfilter(b, a, x)


def exp_env(n, decay, attack=0.0005):
    t = t_axis(n)
    e = np.exp(-t / max(decay, 1e-5))
    na = max(1, n_of(attack))
    e[:na] *= np.linspace(0, 1, na) ** 2
    return e


def ar_env(n, attack, release, curve=2.0):
    t = t_axis(n)
    na = max(1, n_of(attack))
    e = np.zeros(n)
    e[:na] = (np.linspace(0, 1, na)) ** curve
    rest = n - na
    if rest > 0:
        e[na:] = np.exp(-(t[: rest]) / max(release, 1e-5))
    return e


def hann_env(n):
    return np.hanning(max(n, 3))[:n] if n > 0 else np.zeros(0)


def smooth_rand(n, rate_hz, lo=0.0, hi=1.0):
    """Non periodic smooth random curve sampled at control rate rate_hz."""
    pts = max(4, int(n / SR * rate_hz) + 4)
    ctrl = rng.uniform(lo, hi, pts)
    xs = np.linspace(0, n, pts)
    from scipy.interpolate import CubicSpline
    return np.clip(CubicSpline(xs, ctrl)(np.arange(n)), min(lo, hi), max(lo, hi))


def modal(n, freqs, decays, amps, detune=0.0):
    """Sum of exponentially decaying sinusoids (modal synthesis for impacts)."""
    t = t_axis(n)
    y = np.zeros(n)
    for f, d, a in zip(freqs, decays, amps):
        f = f * (1 + rng.uniform(-detune, detune))
        if f >= SR / 2 * 0.95:
            continue
        y += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / d)
    # tiny attack ramp avoids digital clicks
    na = n_of(0.0008)
    y[:na] *= np.linspace(0, 1, na)
    return y


def sweep_tone(freq_curve, amp_curve, harmonics=((1, 1.0),)):
    """Tone following an instantaneous frequency curve (Hz per sample)."""
    phase = 2 * np.pi * np.cumsum(freq_curve) / SR
    y = np.zeros(len(freq_curve))
    for h, a in harmonics:
        mask = (freq_curve * h) < SR / 2 * 0.9
        y += a * np.sin(h * phase) * mask
    return y * amp_curve


def grains(n, count, t_start, t_span, f_lo, f_hi, g_lo=0.002, g_hi=0.007, density_decay=0.4,
           amp_sigma=0.6):
    """Granular crunch: many tiny band limited noise bursts (gravel, snow, leaves)."""
    y = np.zeros(n)
    for _ in range(count):
        pos = t_start + t_span * min(1.0, rng.exponential(density_decay))
        g = rng.uniform(g_lo, g_hi)
        gn = max(8, n_of(g))
        fc = np.exp(rng.uniform(np.log(f_lo), np.log(f_hi)))
        burst = bp(white(gn + 64), fc / 1.6, min(fc * 1.6, SR / 2 * 0.95), 1)[64:]
        burst *= hann_env(gn) * rng.lognormal(0, amp_sigma)
        s = n_of(pos)
        if s + gn < n:
            y[s:s + gn] += burst
    return y


def karplus(n, f, decay=0.996, bright=0.5):
    period = max(2, int(SR / f))
    buf = rng.uniform(-1, 1, period)
    buf = lp(buf, 1500 + 5000 * bright, 1)
    out = np.zeros(n)
    for i in range(n):
        j = i % period
        out[i] = buf[j]
        nxt = buf[(j + 1) % period]
        buf[j] = decay * 0.5 * (buf[j] + nxt)
    return out


def make_ir(rt60, stereo=False, predelay=0.012, bright=6000, dark=1500, density=1.0):
    dur = rt60 * 1.15
    n = n_of(dur)
    t = t_axis(n)
    chans = []
    for _ in range(2 if stereo else 1):
        x = white(n) * np.exp(-6.9 * t / rt60)
        if density < 1.0:
            x *= (rng.uniform(0, 1, n) < density) / np.sqrt(density)
        bright_x = lp(x, bright, 2)
        dark_x = lp(x, dark, 2)
        mix = np.clip(t / (rt60 * 0.6), 0, 1)
        x = bright_x * (1 - mix) + dark_x * mix
        x = np.concatenate([np.zeros(n_of(predelay)), x])
        x /= np.sqrt(np.sum(x ** 2)) + 1e-12
        chans.append(x)
    return np.stack(chans, axis=1) if stereo else chans[0]


def reverb(x, ir, wet, dry=1.0):
    """Mono -> mono (mono IR) or mono -> stereo (stereo IR)."""
    if ir.ndim == 1:
        w = signal.fftconvolve(x, ir)
        out = np.zeros(len(w))
        out[: len(x)] += x * dry
        return out + w * wet
    wl = signal.fftconvolve(x, ir[:, 0])
    wr = signal.fftconvolve(x, ir[:, 1])
    out = np.zeros((len(wl), 2))
    out[: len(x), 0] += x * dry
    out[: len(x), 1] += x * dry
    out[:, 0] += wl * wet
    out[:, 1] += wr * wet
    return out


def place(buf, event, start_s):
    s = n_of(start_s)
    if s >= len(buf):
        return
    m = min(len(event), len(buf) - s)
    buf[s:s + m] += event[:m]


def trim_tail(x, thresh_db=-60.0, fade=0.02):
    """Cut trailing silence and apply a short fade so a one-shot ends cleanly."""
    mono = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    peak = mono.max() + 1e-12
    idx = np.where(mono > peak * db(thresh_db))[0]
    end = min(len(mono), (idx[-1] if len(idx) else len(mono) - 1) + n_of(0.01))
    x = x[:end].copy()
    nf = min(n_of(fade), len(x))
    ramp = np.linspace(1, 0, nf) ** 2
    if x.ndim == 1:
        x[-nf:] *= ramp
    else:
        x[-nf:] *= ramp[:, None]
    return x


def dc_block(x):
    sos = _sos("highpass", 20, 2)
    if x.ndim == 1:
        return signal.sosfilt(sos, x)
    return np.stack([signal.sosfilt(sos, x[:, c]) for c in range(x.shape[1])], axis=1)


def norm_peak(x, peak_db=-1.0):
    return x / (np.max(np.abs(x)) + 1e-12) * db(peak_db)


def norm_rms(x, rms_db, peak_db=-1.0):
    r = np.sqrt(np.mean(x ** 2)) + 1e-12
    y = x / r * db(rms_db)
    p = np.max(np.abs(y))
    if p > db(peak_db):
        y = soft_limit(y, db(peak_db))
    return y


def soft_limit(x, ceiling):
    return np.tanh(x / ceiling) * ceiling


def write_ogg(path, x, quality=5):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav = tmp.name
    try:
        wavfile.write(wav, SR, pcm)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-map_metadata", "-1",
                        "-c:a", "libvorbis", "-q:a", str(quality), path], check=True)
    finally:
        os.remove(wav)
