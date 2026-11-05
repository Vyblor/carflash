"""S5 시험: D1 초대 시작, D2 방장 대기실(가짜 차 입장), D3 코드로 참가·자리 고르기, D4 동시 카운트다운.

합격선(미리 고정, docs/carflash-sprints.md):
  방 만들기 → 가짜 차 입장 → 모두 함께 시작 → 카운트다운 → 재생. 코드로 참가 → 자리 고르기 → 준비.
  추가: 시작 시각 오차(서버 시각 − 지연 보정 대비 실제 첫 박) 100 ms 이내.
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402

SKEW = 1234  # MockGroupServer.skewMs


def sync_error():
    st = lib.wait_log(r"GROUP start server=\d+ latency=\d+ms", timeout=1)
    pl = lib.wait_log(r"SHOW playing t=\d+ wall=\d+", timeout=1)
    if not st or not pl:
        return None
    server, lat = map(int, re.search(r"server=(\d+) latency=(\d+)ms", st).groups())
    wall = int(re.search(r"wall=(\d+)", pl).group(1))
    return wall - (server - SKEW - lat)


def main(install=True):
    lib.fresh(install)
    before = lib.car()

    # ---- D1 ----
    lib.tap("home_group")
    lib.check("D1 초대 시작 화면", lib.wait("screen_group", timeout=5) is not None)
    nodes = lib.dump()
    lib.check("D1 두 갈래(방 만들기·코드로 참가)", lib.find("group_host", nodes=nodes) is not None and lib.find("group_join", nodes=nodes) is not None)
    lib.screenshot("s5_d1_start")

    # ---- D2 방장 ----
    lib.logclear()
    lib.tap("group_host")
    lib.check("D2 방장 대기실", lib.wait(desc="lobby:host", timeout=5) is not None)
    nodes = lib.dump()
    rc = lib.find("room_code", nodes=nodes)
    lib.check("D2 방 코드 6자리", rc is not None and re.fullmatch(r"room:\d{6}", rc.desc) is not None, rc and rc.desc)
    s = lib.find("group_start", nodes=nodes)
    lib.check("D2 혼자면 시작 꺼짐", s is not None and not s.enabled)
    lib.check("D2 가짜 차 입장", lib.wait_log(r"GROUP fake join nick=친구 차", timeout=6) is not None)
    lib.check("D2 가짜 차 준비", lib.wait_log(r"GROUP ready nick=친구 차", timeout=6) is not None)
    time.sleep(0.5)
    nodes = lib.dump()
    lib.check("D2 왼쪽 자리 준비됨", lib.find("seat_0", nodes=nodes).desc == "seat:0:친구 차:ready", lib.find("seat_0", nodes=nodes).desc)
    lib.check("D2 가운데 = 나", lib.find("seat_1", nodes=nodes).desc.endswith(":me"))
    lib.check("D2 2대 → 시작 켜짐", lib.find("group_start", nodes=nodes).enabled)
    lib.check("D2 준비 수", any("2대 준비됨" in x.text for x in nodes))
    old = lib.find("lobby_show", nodes=nodes).desc
    lib.tap("lobby_show")
    time.sleep(0.6)
    new = lib.find("lobby_show").desc
    lib.check("D2 플래시몹 바꾸기", new != old, f"{old} → {new}")
    lib.screenshot("s5_d2_host")
    # P가 아니면 시작 못 함
    lib.gear("Drive")
    time.sleep(1.2)
    lib.start()
    time.sleep(1.0)
    gs = lib.find("group_start")
    lib.check("D2 D단이면 시작 꺼짐", gs is not None and not gs.enabled, repr(gs))
    lib.gear("Park")
    time.sleep(1.2)

    # ---- D4 방장 시작 ----
    lib.logclear()
    lib.tap("group_start")
    nodes = lib.dump()
    n = lib.find(desc="countdown:group", nodes=nodes)
    lib.check("D4 함께 카운트다운", n is not None, n and n.desc)
    lib.check("D4 내 차만 멈추기 버튼", any(x.text == "내 차만 멈추기" for x in nodes))
    lib.check("D4 서버 시각 표시", any(x.text.startswith("서버 시각") for x in nodes))
    lib.screenshot("s5_d4_count")
    lib.check("D4 재생 시작", lib.wait_log(r"SHOW playing", timeout=8) is not None)
    err = sync_error()
    lib.check("D4 시작 시각 오차 100ms 이내(방장)", err is not None and abs(err) <= 100, f"{err} ms")
    lib.check("방장 물결 지연 1박(가운데)", lib.wait_log(r"SHOW start .* slot=1 wave=1", timeout=1) is not None)
    looks = set()
    for _ in range(8):
        c = lib.car(); looks.add((c["color"], c["bright"])); time.sleep(0.5)
    lib.check("함께 재생 중 조명 움직임(색·밝기)", len(looks) >= 2 and all(x[0] != before["color"] for x in looks), str(looks))
    lib.tap("stop")
    lib.check("내 차만 멈춤 → 결과", lib.wait(desc="result:USER", timeout=5) is not None)
    lib.tap("result_secondary")
    lib.check("닫기 → 홈, 방 나감", lib.wait("screen_home", timeout=5) is not None and lib.wait_log(r"GROUP leave", timeout=2) is not None)

    # ---- D3 참가 ----
    lib.tap("home_group")
    lib.tap("group_join")
    lib.check("D3 참가 코드 화면", lib.wait("screen_group_code", timeout=5) is not None)
    nodes = lib.dump()
    for d in "246810":
        x, y = lib.find(f"key_{d}", nodes=nodes).center
        lib.shell(f"input tap {x} {y}")
        time.sleep(0.15)
    lib.check("D3 방 찾음", lib.wait_log(r"GROUP lookup code=246810 → 246810", timeout=3) is not None)
    lib.check("D3 자리 고르기", lib.wait("screen_group_seat", timeout=6) is not None)
    nodes = lib.dump()
    lib.check("D3 찬 자리 꺼짐", lib.find("pick_0", nodes=nodes).desc == "pick:0:taken" and lib.find("pick_1", nodes=nodes).desc == "pick:1:taken")
    lib.check("D3 빈 자리 기본 선택", lib.find("pick_2", nodes=nodes).desc == "pick:2:on")
    x, y = lib.find("nick_1", nodes=nodes).center
    lib.shell(f"input tap {x} {y}")
    lib.screenshot("s5_d3_seat")
    lib.logclear()
    lib.tap("group_ready")
    lib.check("D3 준비 → 참가자 대기", lib.wait(desc="lobby:guest", timeout=5) is not None)
    nodes = lib.dump()
    lib.check("D3 내 자리 오른쪽·별명", lib.find("seat_2", nodes=nodes).desc == "seat:2:우리 차:ready:me", lib.find("seat_2", nodes=nodes).desc)
    lib.check("D3 3대 준비", any("3대 준비됨" in x.text for x in nodes))
    lib.screenshot("s5_d3_wait")
    lib.check("D4 방장이 시작", lib.wait_log(r"GROUP host start", timeout=6) is not None)
    n = lib.wait(desc="countdown:group:3:2", timeout=4)
    lib.check("D4 참가자 카운트다운(3대, 오른쪽)", n is not None)
    lib.check("D4 재생 시작(참가자)", lib.wait_log(r"SHOW playing", timeout=8) is not None)
    err = sync_error()
    lib.check("D4 시작 시각 오차 100ms 이내(참가자)", err is not None and abs(err) <= 100, f"{err} ms")
    lib.check("참가자 물결 지연 2박(오른쪽)", lib.wait_log(r"SHOW start .* slot=2 wave=2", timeout=1) is not None)
    time.sleep(2)
    lib.screenshot("s5_d4_playing")
    lib.tap("stop")
    lib.wait(desc="result:USER", timeout=5)
    time.sleep(0.5)
    c = lib.car()
    lib.check("멈춤 → 복원", all(c[k] == before[k] for k in ("color", "bright", "windows", "wiper", "fan")), f"{c} / before {before}")
    lib.back()
    lib.check("뒤로 → 홈, 방 나감", lib.wait("screen_home", timeout=5) is not None and lib.wait_log(r"GROUP leave", timeout=2) is not None)

    # ---- 안전: 플래시몹 도중 앱이 죽으면 다음 실행 때 되돌린다 ----
    lib.logclear()
    lib.start(countdown_ms=1500, offset_ms=31000)
    lib.open_show("camp")
    lib.tap("prep_start")
    lib.wait_log(r"SHOW playing", timeout=8)
    time.sleep(2)
    mid = lib.car()
    lib.check("죽기 전 차가 바뀌어 있음", mid["color"] != before["color"] and max(mid["windows"]) > 0, str(mid))
    lib.stop()  # 강제 종료(복원 기회 없음)
    time.sleep(1)
    lib.logclear()
    lib.start(countdown_ms=0, offset_ms=0)
    lib.check("다음 실행 때 되돌림 로그", lib.wait_log(r"SHOW recover pending restore", timeout=10) is not None)
    time.sleep(1.5)
    c = lib.car()
    lib.check("다음 실행 때 차 복원", all(c[k] == before[k] for k in ("color", "bright", "windows", "wiper", "fan")), str(c))
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
