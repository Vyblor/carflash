"""카플래시 임시 자체 곡 합성기.

창업자 요청(2026-11-05): BPM이 정해진 짧은 곡 3~5개, 로맨틱 / 캠핑장 댄스파티 / 감미로운 밤 등 다양한 테마.
모든 소리는 이 파일의 수식으로 만든다(샘플·외부 음원 없음). 결과:
  app/src/main/assets/songs/<id>.ogg
  app/src/main/assets/songs/songs.json   (BPM, 마디 수, 구간, 길이 — 앱의 플래시몹 엔진이 박자 기준으로 쓴다)

사용: python3 tools/make_songs.py   (numpy, scipy, ffmpeg 필요)
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter, sosfilt

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "songs")

NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def hz(name):
    """'A3' 같은 이름 → 주파수"""
    n, o = name[:-1], int(name[-1])
    midi = 12 * (o + 1) + NOTE[n]
    return 440.0 * 2 ** ((midi - 69) / 12)


def chord(root, kind, octave=3):
    iv = {"maj": [0, 4, 7], "min": [0, 3, 7], "maj7": [0, 4, 7, 11], "min7": [0, 3, 7, 10],
          "dom7": [0, 4, 7, 10], "min9": [0, 3, 7, 10, 14], "maj9": [0, 4, 7, 11, 14], "sus4": [0, 5, 7]}[kind]
    base = hz(f"{root}{octave}")
    return [base * 2 ** (i / 12) for i in iv]


class Song:
    def __init__(self, sid, title, bpm, sections, seed):
        self.id, self.title, self.bpm, self.sections = sid, title, bpm, sections
        self.bars = sum(n for _, n in sections)
        self.beat = 60.0 / bpm
        self.length = self.bars * 4 * self.beat
        self.n = int((self.length + 2.5) * SR)  # 꼬리 2.5초(잔향)
        self.L = np.zeros(self.n)
        self.R = np.zeros(self.n)
        self.rng = np.random.default_rng(seed)
        self.side = np.ones(self.n)  # 사이드체인(킥에 눌림)

    def t(self, bar, beat=0.0):
        return (bar * 4 + beat) * self.beat

    def add(self, sig, start, gain=1.0, pan=0.0):
        i = int(start * SR)
        if i >= self.n:
            return
        sig = sig[: self.n - i] * gain
        l = np.cos((pan + 1) * np.pi / 4)
        r = np.sin((pan + 1) * np.pi / 4)
        self.L[i:i + len(sig)] += sig * l * np.sqrt(2) / 2 * 1.4
        self.R[i:i + len(sig)] += sig * r * np.sqrt(2) / 2 * 1.4

    def section_at(self, bar):
        b = 0
        for name, n in self.sections:
            if bar < b + n:
                return name, bar - b
            b += n
        return self.sections[-1][0], 0


# ---------- 소리 ----------
def env(n, a=0.005, d=0.2, s=0.0, r=0.05, hold=None):
    t = np.arange(n) / SR
    e = np.where(t < a, t / max(a, 1e-6), 1.0)
    dec = s + (1 - s) * np.exp(-(t - a) / max(d, 1e-6))
    e = np.where(t >= a, dec, e)
    if hold is not None:
        rel = np.clip((t - hold) / max(r, 1e-6), 0, 1)
        e = e * (1 - rel)
    return e


def kick(gain=1.0):
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    f = 45 + 110 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) * np.exp(-t * 7.5)
    click = np.random.default_rng(1).standard_normal(n) * np.exp(-t * 300) * 0.25
    return np.tanh((s + click) * 1.6) * gain


def noise(n, rng):
    return rng.standard_normal(n)


def hp(x, fc, order=2):
    sos = butter(order, fc / (SR / 2), "highpass", output="sos")
    return sosfilt(sos, x)


def lp(x, fc, order=2):
    sos = butter(order, min(fc, SR / 2 - 100) / (SR / 2), "lowpass", output="sos")
    return sosfilt(sos, x)


def bp(x, lo, hi):
    sos = butter(2, [lo / (SR / 2), hi / (SR / 2)], "bandpass", output="sos")
    return sosfilt(sos, x)


def snare(rng, gain=1.0, tone=190):
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * tone * t) * np.exp(-t * 28)
    nz = bp(noise(n, rng), 1200, 8000) * np.exp(-t * 18)
    return (body * 0.6 + nz * 0.9) * gain


def clap(rng, gain=1.0):
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    e = np.zeros(n)
    for k, off in enumerate([0, 0.011, 0.022]):
        i = int(off * SR)
        e[i:] += np.exp(-(t[: n - i]) * (60 if k < 2 else 14))
    return bp(noise(n, rng), 900, 6000) * e * 0.8 * gain


def hat(rng, open_=False, gain=1.0):
    n = int((0.28 if open_ else 0.06) * SR)
    t = np.arange(n) / SR
    return hp(noise(n, rng), 7000) * np.exp(-t * (12 if open_ else 70)) * gain


def saw(f, n, detune=0.0, rng=None):
    t = np.arange(n) / SR
    out = np.zeros(n)
    voices = [0.0] if detune == 0 else [-detune, 0.0, detune]
    for d in voices:
        ff = f * 2 ** (d / 1200)
        ph = (t * ff + (rng.random() if rng is not None else 0)) % 1.0
        out += 2 * ph - 1
    return out / len(voices)


def sine(f, n, phase=0.0):
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * f * t + phase)


def tri(f, n):
    t = np.arange(n) / SR
    return 2 * np.abs(2 * ((t * f) % 1) - 1) - 1


def square(f, n, duty=0.5):
    t = np.arange(n) / SR
    return np.where((t * f) % 1 < duty, 1.0, -1.0)


def pad(freqs, dur, rng, bright=1800, gain=1.0):
    n = int(dur * SR)
    s = sum(saw(f, n, detune=9, rng=rng) for f in freqs) / len(freqs)
    s = lp(s, bright)
    return s * env(n, a=min(0.4, dur / 3), d=10, s=1, r=min(0.5, dur / 3), hold=dur - min(0.5, dur / 3)) * gain


def epiano(freqs, dur, gain=1.0, trem=4.5):
    """FM 느낌 전자피아노"""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f in freqs:
        mod = np.sin(2 * np.pi * f * 2 * t) * 1.2 * np.exp(-t * 3)
        out += np.sin(2 * np.pi * f * t + mod) * np.exp(-t * 1.1)
    out *= (1 + 0.15 * np.sin(2 * np.pi * trem * t))
    return out / len(freqs) * env(n, a=0.004, d=10, s=1, r=0.2, hold=dur - 0.2) * gain


def bass(f, dur, kind="saw", cutoff=600, gain=1.0):
    n = int(dur * SR)
    if kind == "saw":
        s = lp(saw(f, n), cutoff)
    elif kind == "sub":
        s = sine(f, n) + 0.25 * sine(2 * f, n)
    else:
        s = lp(square(f, n, 0.5), cutoff)
    return np.tanh(s * 1.5) * env(n, a=0.004, d=0.35, s=0.55, r=0.05, hold=dur * 0.9) * gain


def pluck(f, dur, kind="tri", gain=1.0, decay=5.0):
    n = int(dur * SR)
    s = tri(f, n) if kind == "tri" else (square(f, n, 0.25) * 0.6 if kind == "sq" else sine(f, n) + 0.3 * sine(3 * f, n))
    return lp(s, 5000) * env(n, a=0.003, d=1 / decay, s=0, r=0.05) * gain


def bell(f, dur, gain=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    mod = np.sin(2 * np.pi * f * 3.5 * t) * 2.0 * np.exp(-t * 4)
    return np.sin(2 * np.pi * f * t + mod) * np.exp(-t * 2.2) * gain


def reverb(x, mix=0.25, room=0.82, damp=3500):
    """간단한 슈뢰더 잔향"""
    out = np.zeros_like(x)
    for d in [1557, 1617, 1491, 1422, 1277, 1356]:
        b = np.zeros(d + 1); b[0] = 1
        a = np.zeros(d + 1); a[0] = 1; a[d] = -room
        out += lfilter(b, a, x)
    out = lp(out / 6, damp)
    for d, g in [(225, 0.5), (556, 0.5), (441, 0.5)]:
        b = np.zeros(d + 1); b[0] = -g; b[d] = 1
        a = np.zeros(d + 1); a[0] = 1; a[d] = -g
        out = lfilter(b, a, out)
    return x * (1 - mix) + out * mix


def delay(x, sec, fb=0.35, mix=0.25):
    d = int(sec * SR)
    y = x.copy()
    for k in range(1, 5):
        sh = np.zeros_like(x)
        sh[d * k:] = x[: len(x) - d * k] * (fb ** k)
        y += sh * mix / 0.35
    return y


def vinyl(n, rng, gain=0.015):
    crackle = (rng.random(n) > 0.9993).astype(float) * rng.standard_normal(n) * 6
    return (lp(noise(n, rng), 3000) * 0.3 + hp(crackle, 1500)) * gain


def rain(n, rng, gain=0.06):
    base = bp(noise(n, rng), 400, 9000) * 0.6
    drops = (rng.random(n) > 0.997).astype(float) * rng.standard_normal(n)
    drops = hp(lfilter([1], [1, -0.97], drops), 2000)
    return (base + drops * 1.5) * gain


class Bus:
    """같은 소리 묶음을 따로 모아 잔향·필터 처리 뒤 섞는다."""

    def __init__(self, song):
        self.s = song
        self.L = np.zeros(song.n)
        self.R = np.zeros(song.n)

    def add(self, sig, start, gain=1.0, pan=0.0):
        i = int(start * SR)
        if i >= self.s.n:
            return
        sig = sig[: self.s.n - i] * gain
        l = np.cos((pan + 1) * np.pi / 4); r = np.sin((pan + 1) * np.pi / 4)
        self.L[i:i + len(sig)] += sig * l * 1.41
        self.R[i:i + len(sig)] += sig * r * 1.41

    def mix(self, rev=0.0, sidechain=False, gain=1.0):
        L, R = self.L, self.R
        if rev:
            L, R = reverb(L, rev), reverb(R, rev, room=0.8)
        if sidechain:
            L, R = L * self.s.side, R * self.s.side
        self.s.L += L * gain
        self.s.R += R * gain


def duck(song, start, depth=0.6, length=None):
    length = length or song.beat * 0.9
    i = int(start * SR); n = int(length * SR)
    if i >= song.n:
        return
    curve = 1 - depth * np.exp(-np.arange(n) / SR * 9)
    end = min(song.n, i + n)
    song.side[i:end] = np.minimum(song.side[i:end], curve[: end - i])


def melody_notes(song, scale, bar, pattern, octave, rng):
    """pattern: [(beat, length_beats, degree)]"""
    out = []
    for b, ln, deg in pattern:
        o = octave + deg // len(scale)
        name = scale[deg % len(scale)]
        out.append((song.t(bar, b), ln * song.beat, hz(f"{name}{o}")))
    return out


# ---------- 곡 1: 캠핑장 댄스파티 (124 BPM, 디스코 하우스) ----------
def song_camp():
    s = Song("camp_party", "캠핑장 댄스파티", 124,
             [("intro", 4), ("verse", 8), ("build", 4), ("drop", 8), ("break", 4), ("drop2", 8), ("outro", 4)], 11)
    rng = s.rng
    prog = [("A", "min7"), ("F", "maj7"), ("C", "maj"), ("G", "dom7")]
    drums, music, lead = Bus(s), Bus(s), Bus(s)
    scale = ["A", "C", "D", "E", "G"]
    hook = [(0, 0.5, 4), (0.5, 0.5, 5), (1.5, 0.5, 4), (2, 1, 2), (3, 0.5, 3), (3.5, 0.5, 4)]
    hook2 = [(0, 0.5, 2), (0.5, 0.5, 3), (1, 0.5, 4), (2, 0.5, 5), (2.5, 1.5, 7)]
    for bar in range(s.bars):
        sec, k = s.section_at(bar)
        root, kind = prog[bar % 4]
        full = sec in ("drop", "drop2")
        for b in range(4):
            tb = s.t(bar, b)
            if sec != "break" and not (sec == "intro" and bar < 2):
                drums.add(kick(), tb, 0.95); duck(s, tb)
            if sec in ("verse", "drop", "drop2", "outro", "build") and b in (1, 3):
                drums.add(clap(rng), tb, 0.55, 0.05)
            if sec != "intro" or bar >= 2:
                drums.add(hat(rng, open_=True), tb + s.beat / 2, 0.22, 0.25)
            if full:
                drums.add(hat(rng), tb + s.beat / 4, 0.12, -0.3); drums.add(hat(rng), tb + 3 * s.beat / 4, 0.12, -0.3)
        if sec == "build":
            for i in range(16):
                drums.add(snare(rng, tone=210), s.t(bar, i / 4), 0.10 + 0.25 * (k * 16 + i) / 64, 0)
        # 베이스: 오프비트 옥타브
        if sec not in ("intro",) or bar >= 2:
            f = hz(f"{root}1") * (2 if root in ("G", "A") and False else 1)
            for b in range(4):
                music.add(bass(f, s.beat * 0.45, "saw", 700 if full else 450), s.t(bar, b + 0.5), 0.55)
                if full:
                    music.add(bass(f * 2, s.beat * 0.2, "saw", 900), s.t(bar, b + 0.75), 0.25)
        # 코드 스탭
        cf = chord(root, kind, 3)
        if sec in ("verse", "drop", "drop2", "break", "build"):
            for b in ([0.5, 1.5, 2.5, 3.5] if sec != "break" else [0]):
                ln = s.beat * (0.35 if sec != "break" else 4)
                sig = pad(cf, ln, rng, bright=2600 if full else 1600) if sec != "break" else pad(cf, ln, rng, 1200)
                music.add(sig, s.t(bar, b), 0.32 if sec != "break" else 0.45)
        else:
            music.add(pad(cf, s.beat * 4, rng, 900), s.t(bar), 0.3)
        # 리드
        if full or sec == "break":
            pat = hook if bar % 2 == 0 else hook2
            for st, ln, f in melody_notes(s, scale, bar, pat, 4, rng):
                lead.add(pluck(f, ln * 1.2, "sq", decay=4), st, 0.22, 0.15)
    drums.mix(rev=0.08)
    music.mix(rev=0.18, sidechain=True)
    l2 = Bus(s); l2.L, l2.R = delay(lead.L, s.beat * 0.75), delay(lead.R, s.beat * 0.5)
    l2.mix(rev=0.3)
    return s


# ---------- 곡 2: 로맨틱 드라이브 (100 BPM, 시티팝) ----------
def song_romance():
    s = Song("romantic_drive", "로맨틱 드라이브", 100,
             [("intro", 4), ("verse", 8), ("chorus", 8), ("bridge", 4), ("chorus2", 4), ("outro", 4)], 23)
    rng = s.rng
    prog = [("F", "maj7"), ("E", "min7"), ("D", "min7"), ("C", "maj7")]
    drums, keys, mel = Bus(s), Bus(s), Bus(s)
    scale = ["C", "D", "E", "G", "A"]
    m1 = [(0, 1, 7), (1, 0.5, 6), (1.5, 1.5, 5), (3, 1, 4)]
    m2 = [(0, 0.5, 4), (0.5, 0.5, 5), (1, 1, 6), (2, 2, 7)]
    for bar in range(s.bars):
        sec, k = s.section_at(bar)
        root, kind = prog[bar % 4]
        for b in range(4):
            tb = s.t(bar, b)
            if sec not in ("intro", "bridge"):
                if b in (0, 2) or (b == 3 and bar % 2):
                    drums.add(kick(0.8), tb + (s.beat * 0.5 if b == 3 else 0), 0.75)
                if b in (1, 3):
                    drums.add(snare(rng, tone=180), tb, 0.38, 0.05)
                for e in range(2):
                    drums.add(hat(rng), tb + e * s.beat / 2 + (0.018 if e else 0), 0.10 if e else 0.14, -0.25)
        cf = chord(root, kind, 3)
        keys.add(epiano(cf, s.beat * 2, 0.9), s.t(bar), 0.5, -0.1)
        keys.add(epiano(cf, s.beat * 1.5, 0.7), s.t(bar, 2.5), 0.35, 0.1)
        if sec != "intro":
            bf = hz(f"{root}2")
            for b, ln in [(0, 1.5), (1.5, 0.5), (2, 1), (3, 0.5), (3.5, 0.5)]:
                keys.add(bass(bf * (2 if b == 3.5 else 1), s.beat * ln, "sub"), s.t(bar, b), 0.5)
        if sec in ("chorus", "chorus2", "bridge"):
            keys.add(pad(chord(root, kind, 4), s.beat * 4, rng, 2200), s.t(bar), 0.16)
        if sec in ("verse", "chorus", "chorus2"):
            for st, ln, f in melody_notes(s, scale, bar, m1 if bar % 2 == 0 else m2, 4, rng):
                mel.add(bell(f, ln + 0.6), st, 0.24, 0.2)
    drums.mix(rev=0.12)
    keys.mix(rev=0.25)
    l2 = Bus(s); l2.L, l2.R = delay(mel.L, s.beat * 0.75), delay(mel.R, s.beat)
    l2.mix(rev=0.35)
    return s


# ---------- 곡 3: 감미로운 밤 (80 BPM, 로파이) ----------
def song_night():
    s = Song("sweet_night", "감미로운 밤", 80,
             [("intro", 2), ("verse", 8), ("chorus", 6), ("outro", 4)], 37)
    rng = s.rng
    prog = [("D", "min9"), ("G", "dom7"), ("C", "maj9"), ("A", "min7")]
    drums, keys, mel = Bus(s), Bus(s), Bus(s)
    scale = ["D", "E", "F", "A", "C"]
    m = [(0, 1.5, 5), (1.5, 0.5, 4), (2, 1, 3), (3, 1, 2)]
    m2 = [(0.5, 0.5, 3), (1, 1, 4), (2, 2, 5)]
    swing = 0.12
    for bar in range(s.bars):
        sec, k = s.section_at(bar)
        root, kind = prog[bar % 4]
        if sec != "intro":
            for b in range(4):
                tb = s.t(bar, b)
                if b in (0,) or (b == 2 and bar % 2 == 1):
                    drums.add(lp(kick(0.7), 2500), tb, 0.7)
                if b == 2 and bar % 2 == 0:
                    drums.add(lp(kick(0.6), 2500), tb + s.beat * 0.5, 0.5)
                if b in (1, 3):
                    drums.add(lp(snare(rng, tone=170), 4000), tb, 0.32)
                drums.add(lp(hat(rng), 9000), tb, 0.08, -0.2)
                drums.add(lp(hat(rng), 9000), tb + s.beat * (0.5 + swing), 0.06, -0.2)
        cf = chord(root, kind, 3)
        keys.add(epiano(cf, s.beat * 3.5, 0.8, trem=3), s.t(bar), 0.55)
        if sec != "intro":
            keys.add(bass(hz(f"{root}1") * 2, s.beat * 3, "sub"), s.t(bar), 0.55)
        if sec in ("verse", "chorus") and bar % 2 == 0 or sec == "chorus":
            for st, ln, f in melody_notes(s, scale, bar, m if bar % 2 == 0 else m2, 4, rng):
                mel.add(pluck(f, ln + 0.5, "sine", decay=2.2), st, 0.2, 0.15)
    drums.mix(rev=0.1)
    keys.mix(rev=0.3)
    mel.mix(rev=0.45)
    v = vinyl(s.n, rng)
    s.L += v; s.R += np.roll(v, 37)
    s.L = lp(s.L, 9000); s.R = lp(s.R, 9000)
    return s


# ---------- 곡 4: 생일 축하 파티 (120 BPM, 밝은 팝) ----------
def song_birthday():
    s = Song("birthday_party", "생일 축하 파티", 120,
             [("intro", 2), ("verse", 8), ("chorus", 8), ("outro", 4)], 41)
    rng = s.rng
    prog = [("C", "maj"), ("A", "min"), ("F", "maj"), ("G", "maj")]
    drums, music, mel = Bus(s), Bus(s), Bus(s)
    scale = ["C", "D", "E", "F", "G", "A", "B"]
    m1 = [(0, 0.5, 4), (0.5, 0.5, 4), (1, 1, 5), (2, 1, 4), (3, 1, 7)]
    m2 = [(0, 0.5, 6), (0.5, 0.5, 5), (1, 1, 4), (2, 0.5, 2), (2.5, 0.5, 3), (3, 1, 4)]
    for bar in range(s.bars):
        sec, k = s.section_at(bar)
        root, kind = prog[bar % 4]
        for b in range(4):
            tb = s.t(bar, b)
            if sec != "intro":
                if b in (0, 2) or (sec == "chorus"):
                    drums.add(kick(0.9), tb, 0.85); duck(s, tb, 0.4)
                if b in (1, 3):
                    drums.add(clap(rng), tb, 0.5); drums.add(snare(rng), tb, 0.25)
                drums.add(hat(rng), tb + s.beat / 2, 0.14, 0.3)
            else:
                drums.add(hat(rng), tb, 0.12)
        cf = chord(root, kind, 4)
        for b in range(4):
            music.add(pluck(cf[0], s.beat * 0.4, "tri", decay=7) + pluck(cf[1], s.beat * 0.4, "tri", decay=7) + pluck(cf[2], s.beat * 0.4, "tri", decay=7),
                      s.t(bar, b + 0.5), 0.18, -0.2)
        if sec != "intro":
            bf = hz(f"{root}2")
            for b in range(4):
                music.add(bass(bf, s.beat * 0.8, "square", 800), s.t(bar, b), 0.38)
        if sec == "chorus":
            music.add(pad(chord(root, kind, 3), s.beat * 4, rng, 2400), s.t(bar), 0.2)
        if sec in ("verse", "chorus"):
            pat = m1 if bar % 2 == 0 else m2
            for st, ln, f in melody_notes(s, scale, bar, pat, 4 if sec == "verse" else 5, rng):
                mel.add(pluck(f, ln * 1.1, "sq", decay=3.5), st, 0.2)
                if sec == "chorus":
                    mel.add(bell(f, ln + 0.3), st, 0.08, 0.3)
    drums.mix(rev=0.1)
    music.mix(rev=0.18, sidechain=True)
    mel.mix(rev=0.25)
    return s


# ---------- 곡 5: 비 오는 주차장 (88 BPM, 칠아웃) ----------
def song_rain():
    s = Song("rainy_parking", "비 오는 주차장", 88,
             [("intro", 4), ("verse", 8), ("chorus", 8), ("outro", 4)], 53)
    rng = s.rng
    prog = [("E", "min7"), ("C", "maj7"), ("G", "maj"), ("D", "sus4")]
    drums, keys, mel = Bus(s), Bus(s), Bus(s)
    scale = ["E", "G", "A", "B", "D"]
    m = [(0, 1, 3), (1, 1, 4), (2, 1.5, 5), (3.5, 0.5, 4)]
    for bar in range(s.bars):
        sec, k = s.section_at(bar)
        root, kind = prog[bar % 4]
        if sec != "intro":
            for b in range(4):
                tb = s.t(bar, b)
                if b == 0 or (b == 2 and sec == "chorus"):
                    drums.add(lp(kick(0.75), 3000), tb, 0.72)
                if b == 2:
                    drums.add(snare(rng, tone=160) * 0.8, tb, 0.3)
                drums.add(hat(rng), tb + s.beat * 0.5, 0.07, 0.3)
        keys.add(pad(chord(root, kind, 3), s.beat * 4, rng, 1500), s.t(bar), 0.32)
        keys.add(epiano(chord(root, kind, 4), s.beat * 2, 0.5, trem=2.5), s.t(bar, 2), 0.2)
        if sec != "intro":
            keys.add(bass(hz(f"{root}2"), s.beat * 3.5, "sub"), s.t(bar), 0.5)
        if sec in ("verse", "chorus"):
            for st, ln, f in melody_notes(s, scale, bar, m, 4, rng):
                mel.add(bell(f, ln + 0.8), st, 0.18, -0.2)
    drums.mix(rev=0.15)
    keys.mix(rev=0.35)
    mel.mix(rev=0.5)
    r = rain(s.n, rng)
    s.L += r; s.R += np.roll(r, 211)
    return s


def master(s):
    x = np.stack([s.L, s.R], axis=1)
    # 끝 2.5초 페이드아웃
    fade = int(2.4 * SR)
    x[-fade:] *= np.linspace(1, 0, fade)[:, None]
    x = np.tanh(x * 0.9)
    peak = np.max(np.abs(x)) or 1
    x = x / peak * 0.89
    return x


def main():
    os.makedirs(OUT, exist_ok=True)
    songs = [song_camp(), song_romance(), song_night(), song_birthday(), song_rain()]
    meta = []
    for s in songs:
        x = master(s)
        with tempfile.TemporaryDirectory() as td:
            wav = os.path.join(td, f"{s.id}.wav")
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            ogg = os.path.join(OUT, f"{s.id}.ogg")
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
        bars_start, b = [], 0
        for name, n in s.sections:
            bars_start.append({"name": name, "bar": b, "bars": n})
            b += n
        meta.append({"id": s.id, "title": s.title, "bpm": s.bpm, "bars": s.bars,
                     "durationSec": round(s.length + 2.4, 3), "musicSec": round(s.length, 3),
                     "asset": f"songs/{s.id}.ogg", "sections": bars_start})
        print(f"{s.id}: {s.bpm} BPM, {s.bars} bars, {s.length:.1f}s", file=sys.stderr)
    with open(os.path.join(OUT, "songs.json"), "w") as f:
        json.dump({"songs": meta}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
