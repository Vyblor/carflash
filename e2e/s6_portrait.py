"""S6 세로(분할 화면 840) 시험: 앱 영역을 840 dp로 좁혀 주요 화면이 portrait 배치로 그려지고 밖으로 넘치지 않는지 본다.

에뮬레이터 화면은 1 dp = 1 px(160 dpi)이고 앱 영역 왼쪽 끝은 x=853이다. 디버그 옵션 cf_width_dp=840으로 앱 영역을 좁힌다.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402

LEFT, W = 853, 840


def visible(nodes, tag, name):
    n = lib.find(tag, nodes=nodes)
    lib.check(f"{name}: {tag} 화면 안", n is not None and n.bounds[3] <= 1300 and n.bounds[1] >= 90, repr(n))


def no_overflow(name):
    nodes = lib.dump()
    bad = [n for n in nodes if n.rid and not n.rid.startswith("android:") and (n.bounds[2] > LEFT + W + 2) and n.bounds[2] - n.bounds[0] > 0]
    lib.check(f"{name}: 840 안에 그려짐", not bad, str(bad[:3]))
    return nodes


def main(install=True):
    lib.fresh(install, onboard=False)
    lib.start(width_dp=W)
    lib.check("A1 세로", lib.wait("onboard_start", timeout=10) is not None)
    no_overflow("A1")
    lib.screenshot("s6_p_a1")
    lib.tap("onboard_start")
    lib.wait("screen_home", timeout=8)
    lib.wait_log(r"VEH cap fan", timeout=10)
    nodes = no_overflow("A2 홈")
    lib.check("A2 세로 타일 짧은 이름", any(x.text == "코드" for x in nodes) and any(x.text == "만들기" for x in nodes))
    lib.screenshot("s6_p_a2")
    lib.tap("home_today")
    lib.wait("screen_prep", timeout=6)
    visible(no_overflow("A3 준비"), "prep_start", "A3")
    lib.screenshot("s6_p_a3")
    lib.start(countdown_ms=1500)
    lib.tap("prep_start")
    lib.wait_log(r"SHOW playing", timeout=8)
    time.sleep(1)
    nodes = no_overflow("A5 재생")
    lib.check("A5 세로 정지 버튼", lib.find("stop", nodes=nodes) is not None)
    lib.screenshot("s6_p_a5")
    lib.tap("stop")
    lib.wait(desc="result:USER", timeout=5)
    visible(no_overflow("E2 멈춤"), "result_primary", "E2")
    lib.screenshot("s6_p_e2")
    lib.back()
    lib.wait("screen_home", timeout=5)
    lib.tap("home_code")
    lib.wait("screen_code", timeout=5)
    no_overflow("B1 코드")
    lib.screenshot("s6_p_b1")
    lib.back()
    lib.wait("screen_home", timeout=5)
    lib.tap("home_create")
    lib.wait("screen_songs", timeout=5)
    visible(no_overflow("C1 곡"), "songs_next", "C1")
    lib.screenshot("s6_p_c1")
    lib.tap("songs_next")
    lib.wait("screen_editor", timeout=5)
    nodes = no_overflow("C2 편집기")
    visible(nodes, "editor_save", "C2"); visible(nodes, "editor_preview", "C2")
    lib.screenshot("s6_p_c2")
    lib.back()
    lib.wait("screen_songs", timeout=5)
    lib.back()
    lib.wait("screen_home", timeout=5)
    lib.tap("home_group")
    lib.wait("screen_group", timeout=5)
    no_overflow("D1 초대")
    lib.tap("group_host")
    lib.wait(desc="lobby:host", timeout=5)
    visible(no_overflow("D2 대기실"), "group_start", "D2")
    lib.screenshot("s6_p_d2")
    lib.back()
    lib.start(width_dp=0)
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
