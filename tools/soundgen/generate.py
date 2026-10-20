#!/usr/bin/env python3
"""Procedurally synthesizes every sound shipped with AmbientSounds and writes sounds.json.

All sounds are original (generated from noise / oscillators), so there are no licensing concerns.
Re-run after tweaking a recipe:

    pip install numpy scipy        # plus ffmpeg with libvorbis on PATH
    python3 tools/soundgen/generate.py
"""
import json
import os
import shutil
import zlib

import numpy as np

import dsp
from dsp import (SR, R, n_of, t_axis, db, c_noise, c_filter, c_curve, c_place, c_reverb, c_tone_freq,
                 h_band, h_lowpass, h_highpass, h_bandpass, pan, white, pink, brown, lp, hp, bp, resonator,
                 exp_env, ar_env, hann_env, smooth_rand, modal, sweep_tone, grains, karplus, make_ir,
                 reverb, place, trim_tail, dc_block, norm_peak, norm_rms, write_ogg)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ambientsounds")
SOUNDS = os.path.join(ASSETS, "sounds")

LOOP_SECONDS = 48.0

# event name -> {"category": ..., "sounds": [...]}
registry = {}


def register(event, category, files, stream=False):
    entry = registry.setdefault(event, {"category": category, "sounds": []})
    for f in files:
        entry["sounds"].append({"name": "ambientsounds:" + f, "stream": True} if stream else "ambientsounds:" + f)


def save(rel, x, quality=5):
    write_ogg(os.path.join(SOUNDS, rel + ".ogg"), x, quality)
    return rel


def finish_oneshot(x, peak_db):
    x = trim_tail(dc_block(x))
    mono = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    start = max(0, int(np.argmax(mono > mono.max() * db(-50))) - n_of(0.002))
    x = x[start:].copy()
    ramp = np.linspace(0, 1, n_of(0.001))
    x[: len(ramp)] *= ramp if x.ndim == 1 else ramp[:, None]
    return norm_peak(x, peak_db)


# =====================================================================================
# Ambience loops (stereo, perfectly seamless because everything is built circularly)
# =====================================================================================

def stereo_noise(n, color, corr=0.55):
    common = c_noise(n, color)
    return np.stack([corr * common + (1 - corr) * c_noise(n, color),
                     corr * common + (1 - corr) * c_noise(n, color)], axis=1)


def filter_bank_wind(n, centers, width, fc_curve, amp_curve, color="pink", spread=0.8, corr=0.5):
    """Wind: a bank of fixed band-pass noises, crossfaded by a moving "center" curve (click free)."""
    out = np.zeros((n, 2))
    src = stereo_noise(n, color, corr)
    lfc = np.log2(fc_curve)
    for fc in centers:
        w = np.exp(-0.5 * ((np.log2(fc) - lfc) / spread) ** 2)
        for c in range(2):
            out[:, c] += c_filter(src[:, c], h_band(fc, width)) * w
    return out * amp_curve[:, None]


def wind_bed(n, base_fc, range_oct, gust_hz=0.12, intensity=0.7, floor=0.3, color="pink"):
    g = c_curve(n, gust_hz, smooth=1.2)
    g = g ** 1.6
    fc = base_fc * 2 ** (range_oct * g)
    amp = floor + intensity * g
    centers = [base_fc * 2 ** (k * 0.5) for k in range(-2, int(range_oct * 2) + 4)]
    return filter_bank_wind(n, centers, 0.45, fc, amp, color), g


def leaves_rustle(n, gust, level=1.0):
    """Leaves: soft band-limited noise that swells with the gusts and flutters gently (no clicks)."""
    out = np.zeros((n, 2))
    for c in range(2):
        flutter = 0.7 + 0.3 * c_curve(n, 4.0, min_hz=0.8, smooth=1.0)
        hiss = c_filter(c_noise(n, "pink"), h_bandpass(700, 3800, 2))
        hiss *= (0.08 + 0.92 * gust ** 1.5) * flutter
        out[:, c] = hiss / (np.std(hiss) + 1e-9)
    return out * level * 0.5


def outdoor_ir():
    return make_ir(0.9, stereo=True, predelay=0.02, bright=7000, dark=2000, density=0.6)


def loop_plains(n):
    wind, g = wind_bed(n, 170, 2.2, gust_hz=0.1, intensity=0.85, floor=0.25)
    grass = leaves_rustle(n, g, 0.16)
    grass = np.stack([c_filter(grass[:, c], h_lowpass(4500)) for c in range(2)], axis=1)
    return wind + grass * np.std(wind) / 0.5


def loop_forest(n):
    wind, g = wind_bed(n, 140, 1.6, gust_hz=0.09, intensity=0.6, floor=0.35, color="brown")
    leaves = leaves_rustle(n, g, 0.45)
    return wind * 0.8 + leaves * np.std(wind) / 0.5


def cricket_track(n, freq, period, amp, pulses, pulse_len, pulse_gap, distance):
    track = np.zeros(n)
    count = max(1, int(round(n / SR / period)))
    spacing = n / count
    pl = n_of(pulse_len)
    chirp = np.zeros(n_of(pulses * (pulse_len + pulse_gap)) + pl)
    tt = t_axis(pl)
    for p in range(pulses):
        f = freq * (1 + 0.004 * R().standard_normal())
        tone = np.sin(2 * np.pi * f * tt) + 0.04 * np.sin(4 * np.pi * f * tt)
        e = np.sin(np.pi * np.arange(pl) / pl) ** 1.5 * (0.75 + 0.25 * (p + 1) / pulses)
        place(chirp, tone * e, p * (pulse_len + pulse_gap))
    for i in range(count):
        jitter = R().uniform(-0.12, 0.12) * spacing
        c_place(track, chirp * amp * R().uniform(0.8, 1.0), i * spacing + jitter)
    if distance > 0:
        track = c_filter(track, h_lowpass(9000 - 5500 * distance, 2))
    return track


def loop_night(n):
    out = np.zeros((n, 2))
    # only distant, softened crickets: a calm background, never piercing
    for _ in range(12):
        trk = cricket_track(n, R().uniform(3600, 4600), R().uniform(0.8, 1.6), R().uniform(0.15, 0.4),
                            int(R().integers(2, 4)), R().uniform(0.010, 0.014), R().uniform(0.010, 0.014),
                            R().uniform(0.5, 0.9))
        out += pan(trk, R().uniform(-0.9, 0.9))
    ir = outdoor_ir()
    out = np.stack([out[:, c] + 0.25 * c_reverb(out[:, c], ir[:, c]) for c in range(2)], axis=1)
    breeze, _ = wind_bed(n, 130, 1.0, gust_hz=0.07, intensity=0.3, floor=0.3, color="brown")
    return out + breeze * 0.6 * np.std(out) / (np.std(breeze) + 1e-9)


def loop_jungle(n):
    out = np.zeros((n, 2))
    # cicada style buzzes that slowly swell and fade
    for _ in range(4):
        fc = R().uniform(4200, 6500)
        rate = R().uniform(90, 160)
        src = c_noise(n, "white")
        band = c_filter(src, h_band(fc, 0.12))
        buzz = band * (0.55 + 0.45 * np.sin(2 * np.pi * c_tone_freq(rate, n) * t_axis(n)))
        swell = c_curve(n, 0.08, smooth=1.5) ** 3
        out += pan(buzz * swell * R().uniform(0.12, 0.3), R().uniform(-0.9, 0.9))
    for _ in range(10):
        trk = cricket_track(n, R().uniform(2800, 4200), R().uniform(0.3, 0.9), R().uniform(0.1, 0.3),
                            int(R().integers(3, 7)), 0.008, 0.008, R().uniform(0.4, 1.0))
        out += pan(trk, R().uniform(-1, 1))
    out = np.stack([c_filter(out[:, c], h_lowpass(6000)) for c in range(2)], axis=1)
    wind, g = wind_bed(n, 150, 1.2, gust_hz=0.08, intensity=0.4, floor=0.4, color="brown")
    leaves = leaves_rustle(n, g, 0.3)
    bed = wind * 0.7 + leaves * np.std(wind) / 0.5
    return out + bed * 0.9 * np.std(out) / (np.std(bed) + 1e-9)


def loop_swamp(n):
    out = np.zeros((n, 2))
    for _ in range(10):
        trk = cricket_track(n, R().uniform(3400, 4400), R().uniform(0.7, 1.4), R().uniform(0.12, 0.3),
                            3, 0.012, 0.012, R().uniform(0.6, 0.95))
        out += pan(trk, R().uniform(-1, 1))
    wind, _ = wind_bed(n, 120, 1.0, gust_hz=0.06, intensity=0.3, floor=0.35, color="brown")
    return out + wind * 0.9 * np.std(out) / (np.std(wind) + 1e-9)


def loop_ocean(n):
    out = np.zeros((n, 2))
    waves = 6
    for i in range(waves):
        dur = R().uniform(8.5, 11.0)
        wn = n_of(dur)
        t = t_axis(wn)
        crash = R().uniform(2.6, 3.4)
        swell = np.clip(t / crash, 0, 1) ** 2.2
        recede = np.exp(-np.clip(t - crash, 0, None) / R().uniform(1.6, 2.4))
        body = np.where(t < crash, swell, recede)
        foam = np.where(t < crash, swell ** 4, np.exp(-np.clip(t - crash, 0, None) / R().uniform(2.5, 3.5)))
        hit = np.exp(-np.abs(t - crash) / 0.35)
        p = R().uniform(-0.6, 0.6)
        for c, side in enumerate((np.cos((p + 1) * np.pi / 4), np.sin((p + 1) * np.pi / 4))):
            src = pink(wn)
            low = lp(src, 380, 2) * body * 1.0
            mid = bp(src, 380, 1600, 2) * (0.35 * body + 0.65 * hit)
            high = bp(white(wn), 1600, 7000, 2) * foam * 0.22
            ev = (low + mid + high) * side * R().uniform(0.75, 1.0)
            ch = np.zeros(n)
            c_place(ch, ev * hann_env(wn) ** 0.15, (i + R().uniform(-0.12, 0.12)) * n / waves)
            out[:, c] += ch
    rumble = stereo_noise(n, "brown", 0.7)
    rumble = np.stack([c_filter(rumble[:, c], h_lowpass(180)) for c in range(2)], axis=1)
    wash = stereo_noise(n, "pink", 0.4)
    wash = np.stack([c_filter(wash[:, c], h_bandpass(300, 1800)) for c in range(2)], axis=1)
    s = np.std(out)
    return out + rumble * 0.5 * s / np.std(rumble) + wash * 0.18 * s / np.std(wash)


def loop_desert(n):
    wind, g = wind_bed(n, 320, 2.0, gust_hz=0.11, intensity=0.9, floor=0.2)
    src = stereo_noise(n, "white", 0.2)
    sand = np.stack([c_filter(src[:, c], h_bandpass(4500, 11000)) for c in range(2)], axis=1)
    sand *= (0.05 + g ** 2.5)[:, None]
    return wind + sand * 0.25 * np.std(wind) / (np.std(sand) + 1e-9)


def loop_snow(n):
    wind, _ = wind_bed(n, 160, 1.6, gust_hz=0.07, intensity=0.8, floor=0.3)
    return wind


def loop_mountain(n):
    wind, _ = wind_bed(n, 120, 2.0, gust_hz=0.09, intensity=0.9, floor=0.25)
    return wind


def loop_cave(n):
    """Almost silent: a faint, slowly moving draft of air. Caves should feel quiet, not threatening."""
    air = stereo_noise(n, "pink", 0.3)
    air = np.stack([c_filter(air[:, c], h_bandpass(180, 700)) for c in range(2)], axis=1)
    air *= (0.35 + 0.65 * c_curve(n, 0.04, smooth=1.5) ** 2)[:, None]
    return air


def loop_nether(n):
    roar = stereo_noise(n, "pink", 0.5)
    roar = np.stack([c_filter(roar[:, c], h_bandpass(120, 500)) for c in range(2)], axis=1)
    roar *= (0.5 + 0.5 * c_curve(n, 0.06, smooth=1.4))[:, None]
    bubbles = np.zeros((n, 2))
    for _ in range(45):
        dur = R().uniform(0.06, 0.16)
        bn = n_of(dur)
        f0 = R().uniform(90, 220)
        b = sweep_tone(f0 * (1 + 1.5 * t_axis(bn) / dur), exp_env(bn, dur / 3, 0.004))
        b += 0.3 * lp(white(bn), 900) * exp_env(bn, 0.01)
        tmp = np.zeros(n)
        c_place(tmp, b * R().uniform(0.3, 1.0), R().uniform(0, n))
        bubbles += pan(tmp, R().uniform(-0.8, 0.8))
    bubbles = np.stack([c_filter(bubbles[:, c], h_lowpass(1500)) for c in range(2)], axis=1)
    s = np.std(roar)
    return roar + bubbles * 0.3 * s / np.std(bubbles)


def loop_end(n):
    """A very soft, airy hush (no tones or chords)."""
    air = stereo_noise(n, "pink", 0.2)
    air = np.stack([c_filter(air[:, c], h_bandpass(300, 2500)) for c in range(2)], axis=1)
    air *= (0.4 + 0.6 * c_curve(n, 0.04, smooth=1.5) ** 2)[:, None]
    return air


def loop_underwater(n):
    deep = stereo_noise(n, "brown", 0.8)
    deep = np.stack([c_filter(deep[:, c], h_lowpass(320, 2)) for c in range(2)], axis=1)
    deep *= (0.7 + 0.3 * c_curve(n, 0.15, smooth=1.0))[:, None]
    bubbles = np.zeros((n, 2))
    for _ in range(40):
        dur = R().uniform(0.02, 0.06)
        bn = n_of(dur)
        f0 = R().uniform(300, 900)
        b = sweep_tone(f0 * (1 + 1.2 * t_axis(bn) / dur), exp_env(bn, dur / 3, 0.002))
        tmp = np.zeros(n)
        c_place(tmp, lp(b, 1200), R().uniform(0, n))
        bubbles += pan(tmp, R().uniform(-0.7, 0.7)) * R().uniform(0.3, 1.0)
    return deep + bubbles * 0.25 * np.std(deep) / np.std(bubbles)


LOOPS = {
    "plains": (loop_plains, -24),
    "forest": (loop_forest, -24),
    "night": (loop_night, -26),
    "jungle": (loop_jungle, -26),
    "swamp": (loop_swamp, -25),
    "ocean": (loop_ocean, -22),
    "desert": (loop_desert, -25),
    "snow": (loop_snow, -24),
    "mountain": (loop_mountain, -23),
    "cave": (loop_cave, -28),
    "nether": (loop_nether, -22),
    "end": (loop_end, -28),
    "underwater": (loop_underwater, -22),
}


def build_loops():
    n = n_of(LOOP_SECONDS)
    for name, (fn, rms) in LOOPS.items():
        dsp.seed(zlib.crc32(name.encode()))
        x = fn(n)
        x -= x.mean(axis=0)
        x = norm_rms(x, rms, -1.5)
        rel = save("ambient/loop/" + name, x, quality=4)
        register("ambient.loop." + name, "ambient", [rel], stream=True)
        print("loop", name)


# =====================================================================================
# Ambient one-shots (mono, positioned around the player)
# =====================================================================================

def bird_harm():
    return ((1, 1.0), (2, 0.06), (3, 0.015))


def note(dur, f_start, f_end, curve=1.0, attack=0.008, release=0.02, vibrato=0.0, vib_rate=30.0):
    nn = n_of(dur)
    t = t_axis(nn)
    x = (t / dur) ** curve
    f = f_start * (f_end / f_start) ** x
    if vibrato:
        f = f * (1 + vibrato * np.sin(2 * np.pi * vib_rate * t))
    env = np.minimum(1, t / attack) * np.minimum(1, (dur - t) / release)
    return sweep_tone(f, np.clip(env, 0, 1) ** 1.5, bird_harm())


def bird(kind):
    out = np.zeros(n_of(2.5))
    if kind == "descending":
        k = int(R().integers(3, 6))
        base = R().uniform(3800, 4600)
        for i in range(k):
            fs = base * (0.96 ** i)
            place(out, note(0.09, fs, fs * 0.66, 0.7) * (1 - 0.1 * i), 0.05 + i * R().uniform(0.13, 0.17))
    elif kind == "trill":
        dur = R().uniform(0.6, 0.9)
        nn = n_of(dur)
        t = t_axis(nn)
        rate = R().uniform(22, 30)
        fc = R().uniform(3300, 4100)
        saw = (t * rate) % 1.0
        f = fc * (1 + 0.12 * saw) * (1 - 0.08 * t / dur)
        am = np.sin(np.pi * saw) ** 2
        env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.6
        place(out, sweep_tone(f, am * env, bird_harm()), 0.05)
    elif kind == "feebee":
        f1 = R().uniform(3700, 4100)
        place(out, note(0.28, f1, f1 * 0.98, attack=0.03, release=0.06), 0.05)
        f2 = f1 * R().uniform(0.8, 0.86)
        place(out, note(0.3, f2 * 1.02, f2 * 0.99, attack=0.03, release=0.08, vibrato=0.006, vib_rate=40), 0.42)
    elif kind == "warble":
        dur = R().uniform(1.0, 1.5)
        nn = n_of(dur)
        f = smooth_rand(nn, R().uniform(10, 16), 2500, 4600)
        gate = (smooth_rand(nn, 9, 0, 1) > 0.3).astype(float)
        gate = lp(gate, 60, 1)
        env = np.sin(np.pi * np.clip(t_axis(nn) / dur, 0, 1)) ** 0.4
        place(out, sweep_tone(f, np.clip(gate, 0, 1) * env, bird_harm()), 0.05)
    elif kind == "chirps":
        k = int(R().integers(2, 5))
        for i in range(k):
            fs = R().uniform(2400, 2900)
            place(out, note(0.04, fs, fs * R().uniform(1.6, 1.9), 1.5, attack=0.004, release=0.01),
                  0.05 + i * R().uniform(0.1, 0.14))
    elif kind == "robin":
        pos = 0.05
        for _ in range(int(R().integers(4, 7))):
            fs = R().uniform(2200, 3300)
            fe = fs * R().uniform(0.8, 1.25)
            d = R().uniform(0.09, 0.15)
            place(out, note(d, fs, fe, 1.0, attack=0.012, release=0.03), pos)
            pos += d + R().uniform(0.03, 0.08)
    elif kind == "dove":
        f = R().uniform(480, 560)
        pos = 0.05
        for d, rel, a in ((0.35, 1.0, 0.7), (0.55, 1.12, 1.0), (0.3, 1.0, 0.6), (0.3, 1.0, 0.55)):
            nn = n_of(d)
            t = t_axis(nn)
            ff = f * rel * (1 + 0.06 * np.sin(np.pi * t / d)) * (1 - 0.04 * t / d)
            env = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 1.2
            tone = sweep_tone(ff, env, ((1, 1.0), (2, 0.12), (3, 0.03)))
            tone += 0.05 * lp(white(nn), 900) * env
            place(out, tone * a, pos)
            pos += d + 0.08
    out = hp(lp(out, 9000, 2), 400, 2) if kind != "dove" else lp(out, 4000, 2)
    ir = make_ir(0.5, predelay=0.015, bright=6000, dark=2000, density=0.7)
    return finish_oneshot(reverb(out, ir, 0.15), -3)


def woodpecker():
    out = np.zeros(n_of(3.0))
    k = int(R().integers(14, 22))
    rate = R().uniform(15, 20)
    f = R().uniform(0.9, 1.15)
    for i in range(k):
        hit = modal(n_of(0.06), [880 * f, 1460 * f, 2350 * f, 3500 * f], [0.012, 0.009, 0.006, 0.004],
                    [1, 0.6, 0.4, 0.2], 0.03)
        hit += 0.3 * hp(white(n_of(0.06)), 2000, 1) * exp_env(n_of(0.06), 0.002)
        place(out, hit * (1 - 0.35 * i / k), 0.03 + i / rate)
    ir = make_ir(1.3, predelay=0.03, bright=5000, dark=1500)
    return finish_oneshot(reverb(out, ir, 0.5), -3)


def cave_drip(variant):
    out = np.zeros(n_of(0.2))
    big = variant >= 4
    f0 = R().uniform(450, 750) if big else R().uniform(1000, 2200)
    dur = 0.06 if big else 0.035
    nn = n_of(dur)
    t = t_axis(nn)
    fcurve = f0 * (1 + 1.4 * t / dur)
    drop = sweep_tone(fcurve, exp_env(nn, dur / 2.5, 0.0015))
    drop += 0.15 * hp(white(nn), 3000, 1) * exp_env(nn, 0.0015)
    place(out, drop, 0.01)
    if R().uniform() < 0.5:
        nn2 = n_of(dur * 0.7)
        place(out, 0.35 * sweep_tone(f0 * 1.3 * (1 + 1.2 * t_axis(nn2) / (dur * 0.7)),
                                    exp_env(nn2, dur / 4, 0.001)), 0.09)
    ir = make_ir(1.4, predelay=0.02, bright=4000, dark=1200)
    return finish_oneshot(reverb(out, ir, 0.5), -3)


def lava_pop():
    out = np.zeros(n_of(0.6))
    for i in range(int(R().integers(1, 4))):
        dur = R().uniform(0.08, 0.2)
        bn = n_of(dur)
        f0 = R().uniform(80, 200)
        b = sweep_tone(f0 * (1 + 1.8 * t_axis(bn) / dur), exp_env(bn, dur / 3, 0.006))
        b += 0.4 * lp(white(bn), 1500) * exp_env(bn, 0.008)
        place(out, b * R().uniform(0.6, 1.0), i * R().uniform(0.08, 0.15))
    for _ in range(int(R().integers(4, 12))):
        cn = n_of(0.004)
        place(out, 0.3 * bp(white(cn + 32), 1500, 5000, 1)[32:] * hann_env(cn), R().uniform(0.05, 0.5))
    ir = make_ir(2.5, predelay=0.03, bright=2500, dark=700)
    return finish_oneshot(reverb(out, ir, 0.7), -3)


def build_ambient_oneshots():
    dsp.seed(101)
    files = []
    for kind in ("descending", "feebee", "chirps"):
        for v in range(3):
            files.append(save("ambient/bird/%s%d" % (kind, v + 1), bird(kind)))
    register("ambient.bird", "ambient", files)
    register("ambient.woodpecker", "ambient", [save("ambient/woodpecker%d" % (v + 1), woodpecker()) for v in range(2)])
    register("ambient.drip", "ambient", [save("ambient/cave/drip%d" % (v + 1), cave_drip(v)) for v in range(6)])
    register("ambient.lava_pop", "ambient", [save("ambient/nether/lava%d" % (v + 1), lava_pop()) for v in range(4)])
    print("ambient one-shots")


# =====================================================================================
# Footsteps, armor, jump & land (mono, played at the player's feet)
# =====================================================================================

def heel(nn, f=85, amp=1.0, decay=0.025, noise_lp=350):
    """Soft contact of a shoe: mostly filtered noise, only a hint of low body (no booming sine)."""
    t = t_axis(nn)
    thump = 0.25 * np.sin(2 * np.pi * f * 1.6 * t) * exp_env(nn, decay * 0.5, 0.003)
    thump += lp(white(nn), noise_lp * 1.8, 2) * exp_env(nn, decay * 0.5, 0.0015)
    return hp(thump, 70, 2) * amp * 0.6


def step(material):
    nn = n_of(0.45)
    out = np.zeros(nn)
    toe = R().uniform(0.05, 0.08)
    if material == "grass":
        out += heel(nn, 80, 0.5)
        rust = bp(white(nn), 1200, 5500, 2) * ar_env(nn, 0.012, 0.045)
        out += rust * (0.3 + 0.7 * smooth_rand(nn, 80, 0, 1) ** 2) * 0.8
        out += 0.4 * grains(nn, 30, 0.0, 0.12, 1200, 5000, density_decay=0.4)
        place(out, 0.3 * bp(white(nn), 1500, 5500, 2) * ar_env(nn, 0.01, 0.03), toe)
        out = lp(out, 6000)
    elif material == "dirt":
        out += heel(nn, 75, 0.8, noise_lp=500)
        out += 0.7 * grains(nn, 22, 0.0, 0.1, 300, 1800, 0.003, 0.009)
        out += 0.25 * bp(white(nn), 400, 2500, 2) * ar_env(nn, 0.006, 0.04)
    elif material == "gravel":
        out += heel(nn, 80, 0.6)
        out += grains(nn, 70, 0.0, 0.16, 1200, 5500, 0.002, 0.006, 0.35, 0.7)
        out += 0.5 * grains(nn, 30, toe, 0.1, 1200, 5000, 0.002, 0.006, 0.35, 0.7)
        out = lp(out, 7000)
    elif material == "stone":
        out += heel(nn, 95, 0.55, 0.015)
        f = R().uniform(0.85, 1.15)
        out += 0.5 * modal(nn, [720 * f, 1270 * f, 2150 * f, 3300 * f], [0.012, 0.009, 0.006, 0.004],
                           [1, 0.6, 0.4, 0.2], 0.04)
        out += 0.5 * bp(white(nn), 1000, 6000, 2) * exp_env(nn, 0.006)
        scuff = bp(white(nn), 1500, 5000, 2) * ar_env(nn, 0.01, 0.015) * 0.06
        place(out, scuff, toe)
        out = lp(out, 7000)
    elif material == "wood":
        out += heel(nn, 90, 0.5, 0.02)
        f = R().uniform(0.85, 1.2)
        out += 0.9 * modal(nn, [150 * f, 320 * f, 540 * f, 830 * f, 1250 * f], [0.06, 0.045, 0.03, 0.02, 0.012],
                           [1, 0.75, 0.5, 0.3, 0.2], 0.05)
        out += 0.3 * bp(white(nn), 800, 4000, 2) * exp_env(nn, 0.004)
        if R().uniform() < 0.35:
            cn = n_of(0.18)
            rate = smooth_rand(cn, 20, 60, 140)
            ph = np.cumsum(rate) / SR
            pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float)
            creak = resonator(pulses, R().uniform(500, 800), 8) + 0.5 * resonator(pulses, 1300, 10)
            place(out, 0.05 * creak * hann_env(cn), 0.04)
        out = lp(out, 6000)
    elif material == "sand":
        out += heel(nn, 70, 0.35, 0.02)
        body = bp(white(nn), 500, 5000, 2) * ar_env(nn, 0.018, 0.08)
        out += body * (0.5 + 0.5 * smooth_rand(nn, 120, 0, 1)) * 0.8
        out += 0.25 * grains(nn, 60, 0.0, 0.14, 2000, 7000, 0.001, 0.003, 0.5, 0.4)
        out = lp(out, 7000)
    elif material == "snow":
        out += heel(nn, 70, 0.35, 0.02)
        out += grains(nn, 140, 0.005, 0.2, 400, 2600, 0.002, 0.006, 0.5, 0.45)
        out += 0.3 * bp(white(nn), 300, 1500, 2) * ar_env(nn, 0.03, 0.08)
        out = lp(out, 4500)
    elif material == "metal":
        out += heel(nn, 95, 0.5, 0.015)
        f = R().uniform(0.85, 1.15)
        out += 0.35 * modal(nn, [430 * f, 1085 * f, 1960 * f, 3150 * f, 4620 * f],
                            [0.18, 0.13, 0.09, 0.06, 0.04], [1, 0.7, 0.5, 0.3, 0.2], 0.03)
        out += 0.4 * bp(white(nn), 1500, 6000, 2) * exp_env(nn, 0.004)
        out = lp(out, 7000)
    elif material == "glass":
        out += heel(nn, 100, 0.45, 0.015)
        f = R().uniform(0.85, 1.15)
        out += 0.3 * modal(nn, [1650 * f, 2950 * f, 4350 * f, 6100 * f], [0.05, 0.035, 0.025, 0.015],
                           [1, 0.6, 0.4, 0.2], 0.03)
        out += 0.3 * bp(white(nn), 2000, 7000, 2) * exp_env(nn, 0.003)
        out = lp(out, 8000)
    elif material == "cloth":
        out += heel(nn, 70, 0.6, 0.02, 250)
        out += 0.3 * lp(white(nn), 1200, 2) * ar_env(nn, 0.01, 0.05)
    elif material == "leaves":
        out += heel(nn, 80, 0.3)
        out += grains(nn, 45, 0.0, 0.25, 1000, 6000, 0.003, 0.01, 0.6, 0.6) * 0.8
        out += 0.4 * bp(white(nn), 1500, 6000, 2) * ar_env(nn, 0.02, 0.1) * smooth_rand(nn, 50, 0.3, 1)
        out = lp(out, 6000, 4)
    elif material == "wade":
        body = bp(white(nn), 300, 3500, 2) * ar_env(nn, 0.03, 0.12)
        out += body * (0.5 + 0.5 * smooth_rand(nn, 30, 0, 1))
        for _ in range(int(R().integers(2, 5))):
            d = R().uniform(0.02, 0.05)
            bn = n_of(d)
            place(out, 0.4 * sweep_tone(R().uniform(400, 1000) * (1 + 1.3 * t_axis(bn) / d), exp_env(bn, d / 3)),
                  R().uniform(0.02, 0.15))
        out = lp(out, 5000)
    return out


STEP_PEAKS = {"grass": -4, "dirt": -3, "gravel": -3, "stone": -3, "wood": -2, "sand": -5, "snow": -4,
              "metal": -3, "glass": -5, "cloth": -9, "leaves": -4, "wade": -4}


def armor(kind):
    nn = n_of(0.35)
    out = np.zeros(nn)
    if kind == "leather":
        cn = n_of(0.2)
        rate = smooth_rand(cn, 25, 40, 110)
        ph = np.cumsum(rate) / SR
        pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float)
        creak = resonator(pulses, R().uniform(900, 1400), 6)
        place(out, 0.3 * creak * hann_env(cn), 0.0)
        out += 0.5 * bp(white(nn), 700, 3500, 2) * ar_env(nn, 0.02, 0.06)
    elif kind == "chain":
        for _ in range(int(R().integers(18, 30))):
            pn = n_of(0.08)
            ping = modal(pn, [R().uniform(2500, 6500)], [R().uniform(0.01, 0.025)], [1])
            place(out, ping * R().lognormal(0, 0.5), min(0.2, R().exponential(0.05)))
        out = lp(out + 0.15 * bp(white(nn), 1500, 5000, 2) * ar_env(nn, 0.01, 0.04), 6500)
    elif kind == "plate":
        f = R().uniform(0.85, 1.15)
        out += modal(nn, [780 * f, 1930 * f, 3020 * f], [0.07, 0.05, 0.03], [1, 0.6, 0.35], 0.04)
        for _ in range(int(R().integers(3, 7))):
            pn = n_of(0.08)
            place(out, 0.35 * modal(pn, [R().uniform(1500, 4500)], [R().uniform(0.01, 0.03)], [1]),
                  R().uniform(0.02, 0.12))
        out = lp(out, 7000)
    return out


def jump():
    nn = n_of(0.4)
    t = t_axis(nn)
    out = 0.5 * bp(white(nn), 300, 3000, 2) * exp_env(nn, 0.025, 0.002)
    fc = 350 * 2 ** (1.8 * np.clip(t / 0.2, 0, 1))
    whoosh = np.zeros(nn)
    src = pink(nn)
    for f in (350, 500, 700, 1000, 1400, 2000):
        w = np.exp(-0.5 * ((np.log2(f) - np.log2(fc)) / 0.5) ** 2)
        whoosh += bp(src, f / 1.3, f * 1.3, 1) * w
    out += whoosh * np.sin(np.pi * np.clip(t / 0.28, 0, 1)) ** 1.5 * 0.9
    return lp(out, 6000)


def land(hard):
    """Body and clothes settling after a jump: a soft cloth flop, the floor itself comes from the block sound."""
    nn = n_of(0.4)
    out = heel(nn, 90, 0.6, 0.03, 400)
    out += (0.8 if hard else 0.6) * bp(pink(nn), 300, 2200, 2) * ar_env(nn, 0.008, 0.06 if hard else 0.045)
    return lp(out, 5000)


def build_movement():
    dsp.seed(202)
    for mat, peak in STEP_PEAKS.items():
        files = [save("footstep/%s%d" % (mat, v + 1), finish_oneshot(step(mat), peak)) for v in range(6)]
        register("footstep." + mat, "player", files)
    for kind, peak in (("leather", -8), ("chain", -8), ("plate", -7)):
        files = [save("armor/%s%d" % (kind, v + 1), finish_oneshot(armor(kind), peak)) for v in range(4)]
        register("armor." + kind, "player", files)
    register("movement.jump", "player", [save("movement/jump%d" % (v + 1), finish_oneshot(jump(), -6)) for v in range(4)])
    register("movement.land", "player", [save("movement/land%d" % (v + 1), finish_oneshot(land(False), -6)) for v in range(4)])
    register("movement.land_hard", "player",
             [save("movement/land_hard%d" % (v + 1), finish_oneshot(land(True), -4)) for v in range(3)])
    print("movement")


# =====================================================================================
# Weapon / tool equip
# =====================================================================================

def equip(kind):
    if kind == "sword":
        nn = n_of(1.4)
        t = t_axis(nn)
        out = np.zeros(nn)
        sd = R().uniform(0.28, 0.36)
        sn = n_of(sd)
        st = t_axis(sn)
        fc = 2200 * 2 ** (1.4 * st / sd)
        # blade sliding out of the scabbard: friction grains exciting the blade's own resonances,
        # with a slowly rising emphasis so it reads as "shhing" instead of plain noise
        rate = 2500 + 6000 * (st / sd)
        mask = R().uniform(0, 1, sn) < rate / SR
        fric = np.zeros(sn)
        fric[mask] = R().lognormal(0, 0.6, int(mask.sum()))
        fric = hp(fric, 800, 2)
        scrape = np.zeros(sn)
        for f in (2200, 2900, 3800, 5000):
            w = np.exp(-0.5 * ((np.log2(f) - np.log2(fc)) / 0.45) ** 2)
            scrape += resonator(fric, f * R().uniform(0.97, 1.03), 18) * w
        scrape += 0.15 * bp(white(sn), 1500, 6000, 2)
        scrape *= np.sin(np.pi * np.clip(st / sd, 0, 1)) ** 0.8
        scrape /= np.max(np.abs(scrape)) + 1e-9
        place(out, 0.45 * lp(scrape, 7000), 0.02)
        place(out, 0.4 * bp(white(n_of(0.02)), 1500, 6000, 1) * exp_env(n_of(0.02), 0.004), 0.0)
        f = R().uniform(0.92, 1.08)
        ring = np.zeros(nn)
        for fr, d, a in ((1180, 0.8, 1.0), (2960, 0.5, 0.55), (5080, 0.3, 0.3), (7350, 0.15, 0.15)):
            ring += modal(nn, [fr * f, fr * f * 1.003], [d, d], [a, a * 0.7])
        ring *= exp_env(nn, 0.25)
        place(out, 0.25 * ring / (np.max(np.abs(ring)) + 1e-9), sd)
        return lp(out, 8500)
    if kind == "heavy":
        nn = n_of(0.8)
        t = t_axis(nn)
        out = 0.35 * bp(pink(nn), 200, 900, 2) * np.sin(np.pi * np.clip(t / 0.22, 0, 1)) ** 2
        f = R().uniform(0.9, 1.1)
        clunk = modal(nn, [380 * f, 960 * f, 1720 * f, 2600 * f], [0.16, 0.12, 0.07, 0.04], [1, 0.6, 0.4, 0.2], 0.03)
        knock = modal(nn, [220 * f, 510 * f, 900 * f], [0.04, 0.03, 0.02], [1, 0.6, 0.3])
        place(out, 0.45 * clunk + 0.4 * knock + heel(nn, 90, 0.3), 0.2)
        return lp(out, 7000)
    if kind == "tool":
        nn = n_of(0.6)
        out = 0.3 * bp(white(nn), 600, 3000, 2) * ar_env(nn, 0.04, 0.06)
        f = R().uniform(0.9, 1.1)
        clink = modal(nn, [1520 * f, 3710 * f, 5880 * f], [0.12, 0.07, 0.04], [1, 0.5, 0.25], 0.03)
        knock = modal(nn, [260 * f, 600 * f], [0.03, 0.02], [1, 0.5])
        place(out, 0.3 * clink + 0.5 * knock, 0.07)
        return lp(out, 8000)
    if kind == "bow":
        nn = n_of(0.9)
        out = np.zeros(nn)
        cn = n_of(0.22)
        rate = smooth_rand(cn, 15, 50, 120)
        ph = np.cumsum(rate) / SR
        pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float)
        creak = resonator(pulses, R().uniform(380, 520), 7) + 0.6 * resonator(pulses, R().uniform(850, 1100), 9)
        place(out, 0.06 * creak * hann_env(cn), 0.0)
        string = karplus(n_of(0.6), R().uniform(95, 120), 0.994, 0.3)
        place(out, 0.35 * string * exp_env(n_of(0.6), 0.2, 0.003), 0.16)
        out += 0.25 * bp(white(nn), 500, 2500, 2) * ar_env(nn, 0.03, 0.05)
        return lp(out, 6000)
    # generic: soft cloth swish
    nn = n_of(0.3)
    t = t_axis(nn)
    out = bp(pink(nn), 500, 3000, 2) * np.sin(np.pi * np.clip(t / 0.16, 0, 1)) ** 1.5
    return lp(out, 6000)


def build_equip():
    dsp.seed(303)
    for kind, peak, count in (("sword", -4, 4), ("heavy", -4, 3), ("tool", -5, 3), ("bow", -5, 3), ("generic", -10, 3)):
        files = [save("equip/%s%d" % (kind, v + 1), finish_oneshot(equip(kind), peak)) for v in range(count)]
        register("equip." + kind, "player", files)
    print("equip")


# =====================================================================================
# Inventory item pick up / put down
# =====================================================================================

def inv(kind):
    nn = n_of(0.5)
    out = np.zeros(nn)
    f = R().uniform(0.9, 1.1)
    if kind == "metal":
        out += modal(nn, [2150 * f, 3870 * f, 5620 * f], [0.06, 0.04, 0.025], [1, 0.6, 0.35], 0.03)
        place(out, 0.4 * modal(nn, [2400 * f, 4300 * f], [0.04, 0.025], [1, 0.5], 0.05), R().uniform(0.04, 0.06))
        out += 0.2 * heel(nn, 150, 0.5, 0.01)
    elif kind == "gem":
        out += modal(nn, [2620 * f, 5270 * f, 7900 * f], [0.22, 0.1, 0.05], [1, 0.3, 0.1])
        place(out, 0.3 * modal(nn, [3500 * f], [0.12], [1]), 0.03)
    elif kind == "wood":
        out += modal(nn, [410 * f, 920 * f, 1610 * f], [0.03, 0.02, 0.012], [1, 0.6, 0.3], 0.03)
        out += 0.3 * bp(white(nn), 1000, 4000, 2) * exp_env(nn, 0.003)
    elif kind == "stone":
        out += modal(nn, [1100 * f, 2330 * f, 3700 * f], [0.012, 0.008, 0.005], [1, 0.6, 0.3], 0.04)
        out += 0.5 * bp(white(nn), 1500, 6000, 2) * exp_env(nn, 0.004)
        out += 0.4 * heel(nn, 120, 0.6, 0.01)
    elif kind == "cloth":
        out += bp(pink(nn), 400, 2600, 2) * ar_env(nn, 0.02, 0.04)
        out += 0.4 * heel(nn, 90, 0.4, 0.015, 250)
    elif kind == "paper":
        body = hp(white(nn), 2000, 2) * ar_env(nn, 0.005, 0.04)
        out += body * (0.4 + 0.6 * (np.sin(2 * np.pi * 45 * t_axis(nn)) > 0))
        out = lp(out, 7000, 4)
    elif kind == "glass":
        out += modal(nn, [2900 * f, 5140 * f, 7600 * f], [0.07, 0.045, 0.03], [1, 0.5, 0.3], 0.03)
        place(out, 0.35 * modal(nn, [3100 * f, 5500 * f], [0.05, 0.03], [1, 0.4], 0.04), R().uniform(0.05, 0.08))
        out += 0.25 * hp(white(nn), 3000, 1) * exp_env(nn, 0.002)
    elif kind == "food":
        out += lp(white(nn), 900, 2) * ar_env(nn, 0.006, 0.035)
        out += 0.6 * heel(nn, 140, 0.6, 0.015, 400)
    elif kind == "dust":
        out += grains(nn, 50, 0.0, 0.1, 1500, 7000, 0.001, 0.004, 0.5, 0.4)
        out += 0.4 * bp(white(nn), 1500, 6000, 2) * ar_env(nn, 0.01, 0.04)
    elif kind == "liquid":
        body = lp(white(nn), 1600, 2) * ar_env(nn, 0.03, 0.08)
        out += body * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t_axis(nn)) ** 2)
        for _ in range(int(R().integers(2, 4))):
            d = R().uniform(0.02, 0.04)
            bn = n_of(d)
            place(out, 0.5 * sweep_tone(R().uniform(500, 900) * (1 + 1.2 * t_axis(bn) / d), exp_env(bn, d / 3)),
                  R().uniform(0.01, 0.12))
    else:  # generic
        out += 0.6 * bp(white(nn), 900, 3500, 2) * exp_env(nn, 0.006, 0.0008)
        out += 0.6 * heel(nn, 160, 0.6, 0.012, 600)
    return lp(out, 10000)


INV_KINDS = {"metal": -5, "gem": -7, "wood": -4, "stone": -4, "cloth": -6, "paper": -8, "glass": -6,
             "food": -5, "dust": -7, "liquid": -5, "generic": -5}


def build_inventory():
    dsp.seed(404)
    for kind, peak in INV_KINDS.items():
        files = [save("inventory/%s%d" % (kind, v + 1), finish_oneshot(inv(kind), peak)) for v in range(4)]
        register("inventory." + kind, "player", files)
    print("inventory")


def main():
    if os.path.isdir(SOUNDS):
        shutil.rmtree(SOUNDS)
    build_ambient_oneshots()
    build_movement()
    build_equip()
    build_inventory()
    build_loops()
    with open(os.path.join(ASSETS, "sounds.json"), "w") as fh:
        json.dump(registry, fh, indent=2, sort_keys=True)
        fh.write("\n")
    print("wrote", len(registry), "sound events")


if __name__ == "__main__":
    main()
