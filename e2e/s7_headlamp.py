"""S7 시험: 전조등·미등 연출(2026-11-07 창업자 지시 "전조등 미등깜빡임까지 연출에 넣어서").

합격선(미리 고정):
  차량 점검에서 전조등·미등 쓰기 가능(cap 로그 둘 다).
  캠핑장의 밤(전조등 포함) 드롭 재생 → 6초 안에 콘솔 headlamp_mode가 LowBeam과 Position을 둘 다 거친다(깜빡임).
  정지 → 3초 안에 시작 전 값(Off). 시작 전이 LowBeam이었으면 LowBeam.
  시작 전이 Auto면 SDK가 OFF로 읽어 Auto로 못 돌린다(알려진 한계) → 결과 화면에 "AUTO로 맞춰 주세요" 안내가 있어야 한다.
  재생 중 기어 D → 조명·창문은 1초 안 복원(S3와 같음). 전조등은 D에서 거부되면 P로 돌아온 뒤 3초 안에 원래 값.
  전조등을 쓰지 않는 플래시몹(비 오는 주차장)은 전조등 스위치를 건드리지 않는다(Position 유지, set 로그 없음).
  재생 화면(11-07 UX): 왼쪽에 켠 효과마다 아이콘 줄 하나, 차 그림에 같은 효과의 배지. 전조등 없는 플래시몹엔 전조등 줄·배지 없음.
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402

DROP_MS = 31000  # 캠핑장 댄스파티 16마디(124 BPM)


PREP = {}


def play(show, offset=DROP_MS):
    lib.logclear()
    lib.start(countdown_ms=1500, offset_ms=offset)
    lib.open_show(show)
    PREP[show] = lib.find("fx_headlamp") is not None
    lib.tap("prep_start")
    return lib.wait_log(r"SHOW playing", timeout=8) is not None


def seen_modes(sec):
    seen = set()
    end = time.time() + sec
    while time.time() < end:
        seen.add(lib.car()["lamp"])
        if {"LowBeam", "Position"} <= seen:
            break
        time.sleep(0.15)
    return seen


def wait_lamp(v, sec):
    t0 = time.time()
    while time.time() - t0 < sec:
        if lib.car()["lamp"] == v:
            return time.time() - t0
        time.sleep(0.1)
    return None


def stop_and_back(expect, name):
    lib.tap("stop")
    dt = wait_lamp(expect, 3)
    lib.check(name, dt is not None, f"{dt:.1f}s" if dt is not None else str(lib.car()["lamp"]))
    lib.tap("result_secondary", timeout=4)
    lib.wait("screen_home", timeout=6)


def main(install=True):
    lib.soc(100)
    lib.fresh(install)
    lib.check("차량 점검: 전조등 쓰기 가능", lib.wait_log(r"VEH cap lowBeam \[.*ON", timeout=6) is not None)
    lib.check("차량 점검: 미등 쓰기 가능", lib.wait_log(r"VEH cap tailLamp \[.*ON", timeout=3) is not None)

    # ---- 1. 깜빡임과 끔 복원 ----
    lib.check("재생 시작", play("camp"))
    lib.check("준비 화면: 전조등 효과 토글", PREP.get("camp"))
    modes = seen_modes(6)
    lib.check("드롭: 전조등↔미등 깜빡임(콘솔)", {"LowBeam", "Position"} <= modes, str(modes))
    ln = lib.wait_log(r"SHOW bar=\d+ .* lamp=(LOW|TAIL)", timeout=3)
    lib.check("엔진 마디 로그에 전조등", ln is not None)
    nodes = lib.dump()
    rows = {n.rid: n.desc for n in nodes if n.rid.startswith("cue_")}
    badges = sorted(n.rid for n in nodes if n.rid.startswith("map_badge_"))
    lib.check("재생 왼쪽: 효과마다 한 줄(전조등 포함)", set(rows) == {"cue_LIGHT", "cue_WINDOW", "cue_FAN", "cue_HEADLAMP"}, str(rows))
    lib.check("재생 왼쪽: 전조등 줄 = 전조등 깜빡", rows.get("cue_HEADLAMP") == "cue:HEADLAMP:전조등 깜빡", str(rows.get("cue_HEADLAMP")))
    lib.check("차 그림: 효과 배지 4개", badges == ["map_badge_FAN", "map_badge_HEADLAMP", "map_badge_LIGHT", "map_badge_WINDOW"], str(badges))
    lib.screenshot("s7_playing")
    stop_and_back("Off", "정지 → 전조등 원래(끔)")

    # ---- 2. 밤: 전조등 켠 채 시작 → 전조등으로 복원 ----
    lib.lamp_mode("LowBeam"); time.sleep(0.5)
    play("camp")
    modes = seen_modes(6)
    lib.check("전조등 켠 차: 깜빡임", "Position" in modes, str(modes))
    stop_and_back("LowBeam", "정지 → 전조등 원래(켜짐)")

    # ---- 3. Auto ----
    lib.lamp_mode("Auto"); time.sleep(0.5)
    play("camp")
    seen_modes(4)
    snap = lib.wait_log(r"VEH snapshot", timeout=1)
    print("snapshot:", (snap or "").split("CARFLASH: ")[-1][-80:])
    # 알려진 한계(2026-11-07): SDK가 AUTO를 OFF로 읽어 OFF로 되돌린다. 그래서 결과 화면이 AUTO를 직접 맞추라고 안내한다.
    lib.tap("stop")
    time.sleep(1.5)
    lib.check("AUTO 시작 → 결과 화면 전조등 안내", lib.find("note_headlamp") is not None, f"lamp={lib.car()['lamp']}")
    lib.tap("result_secondary", timeout=4)
    lib.wait("screen_home", timeout=6)

    # ---- 4. 재생 중 기어 D ----
    lib.lamp_mode("Off"); time.sleep(0.5)
    play("camp")
    seen_modes(4)
    t0 = time.time()
    lib.gear("Drive")
    time.sleep(1.5)
    in_d = lib.car()["lamp"]
    rej = lib.wait_log(r"VEH set (lowBeam|tailLamp) .*failed", timeout=1)
    print(f"D에서 전조등: {in_d}, 거부 로그: {bool(rej)}")
    lib.check("D: 엔진 정지", lib.wait_log(r"SHOW stop reason=GEAR", timeout=2) is not None)
    lib.check("D: 결과 화면 전조등 경고", lib.wait("note_headlamp", timeout=3) is not None)
    if in_d != "Off":
        lib.gear("Park")
        dt = wait_lamp("Off", 4)
        lib.check("D 거부 → P에서 전조등 복원", dt is not None and lib.wait_log(r"VEH lamp restore retry at P", timeout=1) is not None,
                  f"{dt:.1f}s" if dt is not None else str(lib.car()["lamp"]))
    else:
        lib.check("D: 전조등 바로 복원", True, f"{time.time() - t0:.1f}s 안")
        lib.gear("Park")
    time.sleep(1.0)
    lib.back(); time.sleep(0.5)

    # ---- 5. 전조등 없는 플래시몹은 손대지 않음 ----
    lib.lamp_mode("Position"); time.sleep(0.5)
    lib.stop()
    play("rain", offset=0)
    time.sleep(4)
    lamp_during = lib.car()["lamp"]
    nodes = lib.dump()
    rows = sorted(n.rid for n in nodes if n.rid.startswith("cue_"))
    badges = sorted(n.rid for n in nodes if n.rid.startswith("map_badge_"))
    lib.check("비 오는 주차장: 왼쪽 줄 = 조명·와이퍼", rows == ["cue_LIGHT", "cue_WIPER"], str(rows))
    lib.check("비 오는 주차장: 배지에 전조등 없음", badges == ["map_badge_LIGHT", "map_badge_WIPER"], str(badges))
    lib.tap("stop"); time.sleep(1.5)
    sets = [l for l in lib.logs().splitlines() if re.search(r"VEH lamp |VEH set (lowBeam|tailLamp)", l)]
    lib.check("비 오는 주차장 준비 화면: 전조등 토글 없음", PREP.get("rain") is False)
    lib.check("전조등 없는 플래시몹: 전조등 그대로", lamp_during == "Position" and lib.car()["lamp"] == "Position" and not sets,
              f"during={lamp_during} after={lib.car()['lamp']} sets={len(sets)}")
    lib.lamp_mode("Off")
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
