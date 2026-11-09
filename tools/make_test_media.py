"""
Builds Mvoon's device-test media from AndroidX Media3's public sample files (no encoder needed).

Output (in OUT_DIR):
  mvoon_multiaudio.mkv      H.264 1080x720 video, 2 audio tracks (E-AC-3 5.0 "eng" default, AC-3 5.0 "ger"),
                              2 embedded SRT tracks ("eng", "fre"), seekable (Cues), ~12 s
  mvoon_multiaudio.en.srt   sidecar subtitle, UTF-8           -> tests external subtitle detection
  mvoon_multiaudio.ru.srt   sidecar subtitle, Windows-1251    -> tests charset repair

Inputs (from androidx/media libraries/test_data/src/test/assets/media):
  mkv/sample_with_srt.mkv (video), ts/sample.eac3, ts/sample.ac3

Usage: python tools/make_test_media.py <input_dir> <output_dir>
"""
import os
import struct
import sys

# ---------------------------------------------------------------- EBML helpers

def vint_len(first):
    mask, length = 0x80, 1
    while length <= 8 and not (first & mask):
        mask >>= 1
        length += 1
    return length


def read_vint(b, i):
    length = vint_len(b[i])
    value = b[i] & ((0x80 >> (length - 1)) - 1)
    for k in range(1, length):
        value = (value << 8) | b[i + k]
    return value, length


def read_id(b, i):
    length = vint_len(b[i])
    return int.from_bytes(b[i:i + length], "big"), length


def enc_size(n):
    """EBML variable-length size: a length-marker bit followed by the value."""
    for length in range(1, 9):
        if n < (1 << (7 * length)) - 1:
            return (n | (1 << (7 * length))).to_bytes(length, "big")
    raise ValueError(n)


def enc_id(element_id):
    return element_id.to_bytes((element_id.bit_length() + 7) // 8, "big")


def el(element_id, payload):
    return enc_id(element_id) + enc_size(len(payload)) + payload


def uint(element_id, value, width=None):
    width = width or max(1, (value.bit_length() + 7) // 8)
    return el(element_id, value.to_bytes(width, "big"))


def string(element_id, value):
    return el(element_id, value.encode("utf-8"))


def double(element_id, value):
    return el(element_id, struct.pack(">d", value))


# ---------------------------------------------------------------- source parsing

def parse_video(path):
    """Returns (codec_private, width, height, duration_ms, blocks[(ts_ms, keyframe, payload)]) for track 1."""
    data = open(path, "rb").read()
    out = {"blocks": [], "private": None, "w": 0, "h": 0, "duration": 0.0}
    masters = {0x18538067, 0x1654AE6B, 0xAE, 0x1F43B675, 0xA0, 0xE0, 0x1549A966}
    state = {"cluster_ts": 0, "track": None}

    def walk(i, end):
        while i < end:
            eid, l1 = read_id(data, i)
            size, l2 = read_vint(data, i + l1)
            h = i + l1 + l2
            if size == (1 << (7 * l2)) - 1:
                size = end - h
            payload = data[h:h + size]
            if eid in masters:
                if eid == 0xAE:
                    state["track"] = None
                walk(h, h + size)
            elif eid == 0xD7:
                state["track"] = int.from_bytes(payload, "big")
            elif eid == 0x63A2 and state["track"] == 1:
                out["private"] = payload
            elif eid == 0xB0:
                out["w"] = int.from_bytes(payload, "big")
            elif eid == 0xBA:
                out["h"] = int.from_bytes(payload, "big")
            elif eid == 0x4489:
                out["duration"] = struct.unpack(">d" if size == 8 else ">f", payload)[0]
            elif eid == 0xE7:
                state["cluster_ts"] = int.from_bytes(payload, "big")
            elif eid == 0xA3:
                track, tl = read_vint(payload, 0)
                if track == 1:
                    rel = int.from_bytes(payload[tl:tl + 2], "big", signed=True)
                    flags = payload[tl + 2]
                    out["blocks"].append((state["cluster_ts"] + rel, bool(flags & 0x80), payload[tl + 3:]))
            i = h + size

    walk(0, len(data))
    return out["private"], out["w"], out["h"], int(out["duration"]), out["blocks"]


def ac3_frames(path):
    """Splits a raw AC-3 / E-AC-3 elementary stream into (frame_bytes, samples, sample_rate, channels)."""
    d = open(path, "rb").read()
    i = d.find(b"\x0b\x77")
    frames = []
    acmod_channels = [2, 1, 2, 3, 3, 4, 4, 5]
    while i + 8 <= len(d) and d[i] == 0x0B and d[i + 1] == 0x77:
        header = int.from_bytes(d[i:i + 8], "big")
        bits = lambda start, n: (header >> (64 - start - n)) & ((1 << n) - 1)
        bsid = bits(40, 5)
        if bsid > 10:
            size = (bits(21, 11) + 1) * 2
            fscod = bits(32, 2)
            blocks = [1, 2, 3, 6][bits(34, 2)] if fscod != 3 else 6
            acmod, lfe = bits(36, 3), bits(39, 1)
        else:
            fscod, frmsizecod = bits(32, 2), bits(34, 6)
            kbps = [32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640][frmsizecod >> 1]
            size = {0: kbps * 4, 2: kbps * 6}[fscod]
            # bsi: bsid(5) bsmod(3) acmod(3) [cmixlev(2)] [surmixlev(2)] [dsurmod(2)] lfeon(1)
            acmod = bits(48, 3)
            pos = 51 + (2 if (acmod & 1) and acmod != 1 else 0) + (2 if acmod & 4 else 0) + (2 if acmod == 2 else 0)
            blocks, lfe = 6, bits(pos, 1)
        rate = [48000, 44100, 32000][fscod]
        frames.append((d[i:i + size], blocks * 256, rate, acmod_channels[acmod] + lfe))
        i += size
    return frames


# ---------------------------------------------------------------- muxing

def srt_cues(duration_ms, every_ms, length_ms, text):
    cues, t, n = [], 400, 1
    while t + length_ms < duration_ms:
        cues.append((t, length_ms, text.format(n=n)))
        t += every_ms
        n += 1
    return cues


def build(input_dir, out_dir):
    private, width, height, clip_ms, video = parse_video(os.path.join(input_dir, "sample_with_srt.mkv"))
    eac3 = ac3_frames(os.path.join(input_dir, "sample.eac3"))
    ac3 = ac3_frames(os.path.join(input_dir, "sample.ac3"))
    loops = 10
    total_ms = clip_ms * loops

    blocks = []  # (cluster_index, order_key, track, ts_ms, keyframe, payload, duration_ms or None)
    for loop in range(loops):
        for order, (ts, key, payload) in enumerate(video):
            blocks.append((loop * clip_ms + ts, 1, key, payload, None, (loop, order)))

    def add_audio(track, frames):
        t_samples, idx = 0, 0
        rate = frames[0][2]
        while t_samples * 1000 // rate < total_ms:
            payload, samples, _, _ = frames[idx % len(frames)]
            blocks.append((round(t_samples * 1000 / rate), track, True, payload, None, None))
            t_samples += samples
            idx += 1

    add_audio(2, eac3)
    add_audio(3, ac3)
    for ts, dur, text in srt_cues(total_ms, 1500, 1200, "Embedded English · line {n}"):
        blocks.append((ts, 4, True, text.encode("utf-8"), dur, None))
    for ts, dur, text in srt_cues(total_ms, 1500, 1200, "Sous-titre intégré · ligne {n}"):
        blocks.append((ts, 5, True, text.encode("utf-8"), dur, None))

    # Interleave: keep video in its stored (decode) order; slot other blocks before the first video
    # frame whose timestamp is later.
    video_blocks = [b for b in blocks if b[1] == 1]
    others = sorted((b for b in blocks if b[1] != 1), key=lambda b: b[0])
    ordered, j = [], 0
    for vb in video_blocks:
        while j < len(others) and others[j][0] <= vb[0]:
            ordered.append(others[j])
            j += 1
        ordered.append(vb)
    ordered.extend(others[j:])

    # One cluster per video loop (each starts with a keyframe -> a cue point).
    clusters, current, cluster_start = [], [], 0
    for b in ordered:
        if b[1] == 1 and b[5][1] == 0 and current:
            clusters.append((cluster_start, current))
            current = []
        if not current:
            cluster_start = (b[0] // clip_ms) * clip_ms
        current.append(b)
    clusters.append((cluster_start, current))

    cluster_blobs = []
    for start, items in clusters:
        body = uint(0xE7, start)
        for ts, track, key, payload, duration, _ in items:
            rel = ts - start
            block_payload = bytes([0x80 | track]) + struct.pack(">h", rel)
            if duration is None:
                body += el(0xA3, block_payload + bytes([0x80 if key else 0x00]) + payload)
            else:
                body += el(0xA0, el(0xA1, block_payload + b"\x00" + payload) + uint(0x9B, duration))
        cluster_blobs.append(el(0x1F43B675, body))

    eac3_channels, ac3_channels = eac3[0][3], ac3[0][3]
    tracks = el(0x1654AE6B, b"".join([
        el(0xAE, uint(0xD7, 1) + uint(0x73C5, 1) + uint(0x83, 1) + string(0x86, "V_MPEG4/ISO/AVC") + string(0x22B59C, "und")
           + uint(0x88, 1) + el(0x63A2, private) + el(0xE0, uint(0xB0, width) + uint(0xBA, height))),
        el(0xAE, uint(0xD7, 2) + uint(0x73C5, 2) + uint(0x83, 2) + string(0x86, "A_EAC3") + string(0x22B59C, "eng")
           + string(0x536E, "English · E-AC-3") + uint(0x88, 1) + el(0xE1, double(0xB5, 48000.0) + uint(0x9F, eac3_channels))),
        el(0xAE, uint(0xD7, 3) + uint(0x73C5, 3) + uint(0x83, 2) + string(0x86, "A_AC3") + string(0x22B59C, "ger")
           + string(0x536E, "Deutsch · AC-3") + uint(0x88, 0) + el(0xE1, double(0xB5, 48000.0) + uint(0x9F, ac3_channels))),
        el(0xAE, uint(0xD7, 4) + uint(0x73C5, 4) + uint(0x83, 17) + string(0x86, "S_TEXT/UTF8") + string(0x22B59C, "eng")
           + string(0x536E, "English") + uint(0x88, 0)),
        el(0xAE, uint(0xD7, 5) + uint(0x73C5, 5) + uint(0x83, 17) + string(0x86, "S_TEXT/UTF8") + string(0x22B59C, "fre")
           + string(0x536E, "Français") + uint(0x88, 0)),
    ]))
    info = el(0x1549A966, uint(0x2AD7B1, 1_000_000) + string(0x4D80, "mvoon-test-mux") + string(0x5741, "mvoon-test-mux")
              + double(0x4489, float(total_ms)))

    # Cues go before the clusters with fixed-width positions so their size is known up front.
    def cues_blob(positions):
        return el(0x1C53BB6B, b"".join(
            el(0xBB, uint(0xB3, start) + el(0xB7, uint(0xF7, 1) + uint(0xF1, pos, width=8)))
            for (start, _), pos in zip(clusters, positions)))

    placeholder = cues_blob([0] * len(clusters))
    first_cluster = len(info) + len(tracks) + len(placeholder)
    positions, offset = [], first_cluster
    for blob in cluster_blobs:
        positions.append(offset)
        offset += len(blob)
    segment_body = info + tracks + cues_blob(positions) + b"".join(cluster_blobs)

    ebml = el(0x1A45DFA3, uint(0x4286, 1) + uint(0x42F7, 1) + uint(0x42F2, 4) + uint(0x42F3, 8)
              + string(0x4282, "matroska") + uint(0x4287, 4) + uint(0x4285, 2))
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "mvoon_multiaudio.mkv"), "wb") as f:
        f.write(ebml + el(0x18538067, segment_body))

    def srt(cues):
        def stamp(ms):
            return "%02d:%02d:%02d,%03d" % (ms // 3_600_000, ms // 60_000 % 60, ms // 1000 % 60, ms % 1000)
        return "\r\n".join(f"{i}\r\n{stamp(t)} --> {stamp(t + d)}\r\n{text}\r\n" for i, (t, d, text) in enumerate(cues, 1))

    with open(os.path.join(out_dir, "mvoon_multiaudio.en.srt"), "w", encoding="utf-8", newline="") as f:
        f.write(srt(srt_cues(total_ms, 1500, 1200, "Sidecar English <i>line</i> {n}")))
    with open(os.path.join(out_dir, "mvoon_multiaudio.ru.srt"), "wb") as f:
        f.write(srt(srt_cues(total_ms, 1500, 1200, "Русские субтитры, строка {n}")).encode("windows-1251"))
    print(f"video frames/loop={len(video)} loops={loops} duration={total_ms} ms clusters={len(clusters)} "
          f"eac3 ch={eac3_channels} ac3 ch={ac3_channels}")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
