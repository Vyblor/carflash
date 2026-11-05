"""S2 시험: 플래시몹 엔진, A3 준비, A4 카운트다운, A5 재생, A6 끝·코드, 정지 시 복원.

합격선(미리 고정, docs/carflash-sprints.md):
  시작 뒤 콘솔 조명 색이 곡 진행에 따라 바뀌고 창문이 열림.
  정지 누르면 조명·창문이 시작 전 값으로 돌아감. 끝까지 재생하면 A6.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402


def main(install=True):
    lib.fresh(install)
    before = lib.car()
    print("before:", before)

    # ---- 캠핑장의 밤: 조명·창문·송풍. 드롭(16마디 ≈ 31초)까지 보고 정지 ----
    lib.check("A3 준비 화면", lib.open_show("camp") is not None or lib.find("screen_prep") is not None)
    nodes = lib.dump()
    descs = {n.desc for n in nodes if n.desc.startswith("fx_")}
    lib.check("A3 효과 토글(조명·창문·송풍 켜짐)", {"fx_light:on", "fx_window:on", "fx_fan:on", "fx_sound:on"} <= descs, str(sorted(descs)))
    lib.check("A3 창문 경고", lib.find("banner_window", nodes=nodes) is not None)
    lib.check("A3 첫 장면 미리보기", lib.find("car_map", nodes=nodes) is not None)
    lib.screenshot("s2_a3_prep")
    lib.start(countdown_ms=9000)   # 시험에서만 카운트다운 9초(화면 읽기 한 번 2~4초)
    # 카운트다운은 3초뿐이라 이미 읽은 좌표로 바로 누르고 바로 읽는다(dump 한 번 약 2초)
    x, y = lib.find("prep_start", nodes=nodes).center
    lib.shell(f"input tap {x} {y}")
    t0 = time.time()
    nodes = lib.dump()
    lib.check("A4 카운트다운", lib.find("screen_countdown", nodes=nodes) is not None, f"{time.time() - t0:.1f}s")
    cnt = lib.find(desc="count:", nodes=nodes)
    lib.check("A4 숫자 표시", cnt is not None and cnt.desc in [f"count:{i}" for i in range(1, 10)], repr(cnt))
    lib.check("A4 정지 버튼", lib.find("stop", nodes=nodes) is not None)
    lib.screenshot("s2_a4_countdown")
    lib.start(countdown_ms=0)
    lib.check("A5 재생 시작(로그)", lib.wait_log(r"SHOW playing", timeout=8) is not None, f"{time.time() - t0:.1f}s")
    d0 = time.time()
    lib.check("A5 재생 화면", lib.wait("screen_playing", timeout=5) is not None)
    lib.check("재생 중 화면 읽기 3초 안(접근성 이벤트 폭주 없음)", time.time() - d0 < 3, f"{time.time() - d0:.1f}s")
    lib.check("해치 춤 BPM 124", lib.wait_log(r"HAECHI state=dance bpm=124", timeout=5) is not None)
    colors, fans, wins = set(), set(), []
    end = time.time() + 45
    shot = False
    while time.time() < end and not lib.wait_log(r"SHOW bar=19 ", timeout=0.1):
        c = lib.car()
        colors.add(c["color"]); fans.add(c["fan"]); wins.append(max(c["windows"]))
        if not shot and max(c["windows"]) > 0:
            lib.screenshot("s2_a5_playing"); shot = True
        time.sleep(0.7)
    lib.check("조명 색이 곡 따라 바뀜(3색 이상)", len(colors - {before["color"]}) >= 3, str(sorted(colors)))
    lib.check("창문이 열림(드롭 구간)", max(wins) > 0, f"max={max(wins)}")
    lib.check("송풍 단이 바뀜", len(fans) >= 2, str(sorted(fans)))
    lib.check("엔진 마디 로그", lib.wait_log(r"SHOW bar=16 sec=drop", timeout=2) is not None)
    if not shot:
        lib.screenshot("s2_a5_playing")

    lib.tap("stop")
    t1 = time.time()
    restored = None
    while time.time() - t1 < 6:
        c = lib.car()
        if c["color"] == before["color"] and c["windows"] == before["windows"] and c["fan"] == before["fan"]:
            restored = time.time() - t1
            break
        time.sleep(0.3)
    lib.check("정지 → 조명·창문·송풍 복원", restored is not None, f"{restored:.1f}s" if restored else str(lib.car()))
    lib.check("정지 화면(사용자)", lib.wait(desc="result:USER", timeout=4) is not None)
    lib.screenshot("s2_stopped")
    lib.back()
    lib.check("뒤로 → 홈", lib.wait("screen_home", timeout=5) is not None)

    # ---- 생일 축하: 조명·와이퍼·송풍, 46초 끝까지 ----
    lib.check("생일 축하 준비", lib.open_show("birthday") is not None or lib.find("screen_prep") is not None)
    lib.check("창문 없는 플래시몹은 창문 경고 없음", lib.find("banner_window") is None)
    lib.tap("prep_start")
    lib.check("재생 시작", lib.wait("screen_playing", timeout=8) is not None)
    wipers = set()
    end = time.time() + 60
    while time.time() < end and lib.find("screen_end") is None:
        wipers.add(lib.car()["wiper"])
        time.sleep(1.5)
    lib.check("와이퍼가 움직임(후렴)", len(wipers - {"Off"}) >= 1, str(wipers))
    lib.check("A6 끝 화면", lib.wait("screen_end", timeout=10) is not None)
    code = lib.find(desc="code:")
    lib.check("A6 플래시몹 코드 120 120", code is not None and code.desc == "code:120120", repr(code))
    lib.check("해치 cheer", lib.wait_log(r"HAECHI state=cheer", timeout=3) is not None)
    time.sleep(1.5)
    c = lib.car()
    lib.check("끝 → 복원(와이퍼 끔, 송풍·색 원래대로)", c["wiper"] == before["wiper"] and c["fan"] == before["fan"] and c["color"] == before["color"], str(c))
    lib.screenshot("s2_a6_end")
    lib.tap("result_secondary")
    lib.check("다른 플래시몹 고르기 → 홈", lib.wait("screen_home", timeout=5) is not None)
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
