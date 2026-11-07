"""S4 시험: C1 곡 고르기 → C2 편집 → 미리보기 → C3 저장·코드 → B1 그 코드 입력 → 같은 플래시몹 준비. B2 친구 플래시몹.

합격선(미리 고정, docs/carflash-sprints.md):
  만들기 → 저장 → 코드 발급 → 그 코드 입력 → 준비 화면에 같은 플래시몹.
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402


def type_code(code):
    nodes = lib.dump()
    for d in code:
        n = lib.find(f"key_{d}", nodes=nodes)
        x, y = n.center
        lib.shell(f"input tap {x} {y}")
        time.sleep(0.15)


def main(install=True):
    lib.fresh(install)
    before = lib.car()

    # ---- C1 ----
    lib.tap("home_create")
    lib.check("C1 곡 고르기 화면", lib.wait("screen_songs", timeout=5) is not None)
    lib.tap("song_sweet_night")
    lib.tap("song_play_sweet_night")
    lib.check("C1 미리듣기", lib.wait_log(r"PREVIEW song=sweet_night", timeout=4) is not None)
    nodes = lib.dump()
    lib.check("C1 곡 선택 표시", lib.find(desc="song:sweet_night:on", nodes=nodes) is not None)
    lib.check("C1 기본 시작 방법 = 추천", lib.find("start_recommended", nodes=nodes).desc == "start:on")
    lib.screenshot("s4_c1_songs")
    lib.tap("songs_next")

    # ---- C2 ----
    lib.check("C2 편집기", lib.wait("screen_editor", timeout=5) is not None)
    nodes = lib.dump()
    cells = [n for n in nodes if n.rid.startswith("cell_")]
    tracks = sorted({n.rid.split("_")[1] for n in cells})
    # S7(11-07)에서 전조등 트랙이 더해져 5개
    lib.check("C2 트랙 = 차가 지원하는 5개", tracks == ["FAN", "HEADLAMP", "LIGHT", "WINDOW", "WIPER"], str(tracks))
    lib.check("C2 한 쪽 4마디", len(cells) == 20, str(len(cells)))
    lib.check("C2 추천에서 시작 → 칸이 채워져 있음", lib.find("cell_LIGHT_0", nodes=nodes).desc.split(":")[3] != "NONE", lib.find("cell_LIGHT_0", nodes=nodes).desc)
    lib.tap("cell_LIGHT_0")
    lib.tap("block_light_solid_cyan")
    time.sleep(0.5)
    nodes = lib.dump()
    lib.check("C2 블록 넣기 → 칸 바뀜", lib.find("cell_LIGHT_0", nodes=nodes).desc == "cell:LIGHT:0:SOLID:00E5FF", lib.find("cell_LIGHT_0", nodes=nodes).desc)
    lib.tap("cell_WINDOW_1")
    lib.tap("block_window_60")
    time.sleep(0.5)
    lib.check("C2 창문 칸", lib.find("cell_WINDOW_1").desc == "cell:WINDOW:1:60")
    lib.screenshot("s4_c2_editor")
    lib.tap("page_next")
    time.sleep(0.5)
    nodes = lib.dump()
    lib.check("C2 다음 쪽(5~8마디)", lib.find("cell_LIGHT_4", nodes=nodes) is not None and lib.find("page_now", nodes=nodes).desc == "page:1")
    lib.tap("page_prev")
    time.sleep(0.5)

    # ---- C2 미리보기: 1~4마디를 실제 차로 ----
    lib.logclear()
    lib.tap("editor_preview")
    lib.check("미리보기 재생", lib.wait_log(r"SHOW playing", timeout=6) is not None)
    colors, wins = set(), []
    t0 = time.time()
    while time.time() - t0 < 6.5:
        c = lib.car(); colors.add(c["color"]); wins.append(max(c["windows"])); time.sleep(0.3)
    lib.check("미리보기 1마디 청록", "#00e5ff" in colors, str(colors))
    lib.check("미리보기 2마디 창문 열림", max(wins) > 0, f"max={max(wins)}")
    lib.check("미리보기 4마디 뒤 끝", lib.wait_log(r"SHOW preview end", timeout=12) is not None)
    lib.check("미리보기 끝 → 편집기로", lib.wait("screen_editor", timeout=5) is not None)
    time.sleep(0.5)
    c = lib.car()
    lib.check("미리보기 끝 → 차 복원", c["color"] == before["color"] and c["windows"] == before["windows"], str(c))

    # ---- C3 ----
    lib.tap("editor_save")
    lib.check("C3 저장 화면", lib.wait("screen_saved", timeout=5) is not None)
    n = lib.find(desc="code:")
    code = n.desc.split(":")[1] if n else None
    lib.check("C3 6자리 코드", code is not None and re.fullmatch(r"\d{6}", code) is not None, str(code))
    lib.screenshot("s4_c3_saved")
    lib.back()
    lib.check("홈", lib.wait("screen_home", timeout=5) is not None)
    lib.check("홈에 내 플래시몹", lib.find(text="내 감미로운 밤") is not None)

    # ---- B1: 그 코드 입력 ----
    lib.tap("home_code")
    lib.check("B1 코드 화면", lib.wait("screen_code", timeout=5) is not None)
    lib.screenshot("s4_b1_empty")
    lib.logclear()
    type_code(code)
    lib.screenshot("s4_b1_found")  # 찾음 배너는 1초만 뜬다(화면 읽기 한 번 약 2초라 사진과 로그로 본다)
    lib.check("B1 6번째 숫자에서 바로 찾음", lib.wait_log(rf"SERVER lookup code={code} → m_", timeout=3) is not None)
    lib.check("B1 1초 뒤 준비로", lib.wait_log(rf"CODE found {code}", timeout=3) is not None)
    lib.check("B1 → 준비 화면", lib.wait("screen_prep", timeout=6) is not None)
    nodes = lib.dump()
    lib.check("같은 플래시몹(제목)", any(x.text == "내 감미로운 밤" for x in nodes))
    descs = {x.desc for x in nodes if x.desc.startswith("fx_")}
    lib.check("같은 플래시몹(효과: 조명·창문 포함)", {"fx_light:on", "fx_window:on"} <= descs, str(sorted(descs)))
    lib.back()
    lib.wait("screen_home", timeout=5)

    # ---- B1: 없는 코드 ----
    lib.tap("home_code")
    lib.wait("screen_code", timeout=5)
    type_code("000001")
    lib.check("B1 없는 코드 → 안내", lib.wait("code_missing", timeout=4) is not None)
    nodes = lib.dump()
    lib.check("B1 최근 코드 칩", lib.find(f"chip_code_{code}", nodes=nodes) is not None)
    x, y = lib.find("key_del", nodes=nodes).center
    for _ in range(6):
        lib.shell(f"input tap {x} {y}")
    time.sleep(0.4)
    lib.check("B1 지우기", lib.find(desc="entered:") is not None and lib.find(desc="entered:").desc == "entered:")

    # ---- B2: 친구 플래시몹(다른 차종) ----
    type_code("415926")
    lib.check("B2 → 준비 화면", lib.wait("screen_prep", timeout=8) is not None)
    nodes = lib.dump()
    lib.check("B2 친구 플래시몹 표시", any("친구 플래시몹" in x.text for x in nodes))
    lib.check("B2 차종 차이 배너", lib.find("banner_friend", nodes=nodes) is not None)
    lib.screenshot("s4_b2_friend")
    lib.back()
    lib.wait("screen_home", timeout=5)
    lib.check("홈 최근에 친구 플래시몹", lib.find(text="불꽃 세레나데") is not None)
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
