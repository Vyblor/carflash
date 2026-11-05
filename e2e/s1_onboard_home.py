"""S1 시험: 첫 실행(A1) → 홈(A2), 재실행 시 A1 생략, 해치 WebView WebGL, 기어 칩, 차량 기능 점검.

합격선(미리 고정, docs/carflash-sprints.md):
  첫 실행에 A1이 뜨고 "알겠어요, 시작" 뒤 A2. 재실행하면 A1 생략.
  해치 WebView가 WebGL로 그려짐(로그). 기어 칩이 P를 보여 줌.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402

APK = os.path.join(os.path.dirname(lib.HERE), "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def main(install=True):
    if install:
        lib.install(APK)
    lib.gear("Park")
    lib.stop()
    lib.clear()
    lib.logclear()
    lib.start()

    # A1
    n = lib.wait("screen_onboard", timeout=15)
    lib.check("A1 첫 실행 화면", n is not None)
    lib.check("A1 안전 약속 문구", lib.wait(text="정차(P) 중에만 플래시몹을 틀어요", timeout=3) is not None)
    lib.check("A1 시작 버튼", lib.find("onboard_start") is not None)

    # 차량 기능 점검(SDK check 후 패널 갱신; 초기화 2.5초 지연)
    caps = None
    end = time.time() + 12
    while time.time() < end:
        caps = lib.find(desc="cap:LIGHT:")
        if caps:
            break
        time.sleep(0.5)
    lib.check("A1 효과 점검 결과 표시", caps is not None, repr(caps))
    nodes = lib.dump()
    on = [n.desc for n in nodes if n.desc.startswith("cap:")]
    lib.check("A1 조명 지원(에뮬레이터)", any(d == "cap:LIGHT:on" for d in on), str(on))

    # 기어 칩
    chip = lib.find(desc="gear:")
    lib.check("기어 칩 P", chip is not None and chip.desc == "gear:P", repr(chip))

    # 해치 WebGL
    line = lib.wait_log(r"HAECHI ready ok=true", timeout=20)
    lib.check("해치 WebView 준비", line is not None)
    gl = lib.wait_log(r"HAECHI js .* HAECHI ready webgl=WebGL", timeout=5)
    lib.check("해치 WebGL 컨텍스트", gl is not None, (gl or "").split("HAECHI ready")[-1].strip())
    lib.check("해치 hello 상태", lib.wait_log(r"HAECHI state=hello", timeout=3) is not None)
    lib.screenshot("s1_a1_onboard")

    # → A2
    lib.tap("onboard_start")
    lib.check("A2 홈 화면", lib.wait("screen_home", timeout=8) is not None)
    lib.check("A2 오늘의 플래시몹", lib.find("home_today") is not None)
    for t in ("home_code", "home_group", "home_create"):
        lib.check(f"A2 타일 {t}", lib.find(t) is not None)
    lib.check("A2 추천 플래시몹 목록", lib.find(text="추천 플래시몹") is not None)
    lib.check("A2 해치 idle", lib.wait_log(r"HAECHI state=idle", timeout=5) is not None)
    time.sleep(6)
    fps = lib.wait_log(r"HAECHI fps=\d+", timeout=8)
    lib.check("해치 프레임 측정", fps is not None, (fps or "").split("CARFLASH:")[-1].strip())
    lib.screenshot("s1_a2_home")

    # 오늘의 플래시몹 → 다음 화면, 뒤로 → 홈
    lib.tap("home_today")
    lib.check("오늘의 플래시몹 누르면 준비 화면으로", lib.wait("screen_todo", timeout=5) is not None or lib.wait("screen_prep", timeout=1) is not None)
    lib.back()
    lib.check("뒤로 → 홈", lib.wait("screen_home", timeout=5) is not None)

    # 재실행: A1 생략
    lib.stop()
    lib.logclear()
    lib.start()
    lib.check("재실행 시 A1 생략(바로 A2)", lib.wait("screen_home", timeout=12) is not None and lib.find("screen_onboard") is None)

    # 기어 칩이 콘솔 기어를 따라감(D → P)
    lib.wait_log(r"VEH gear raw", timeout=10)
    lib.gear("Drive")
    time.sleep(2)
    # D에서는 정차 전용 앱이 시스템에 의해 가려질 수 있다. 로그로 확인.
    lib.check("기어 D 수신", lib.wait_log(r"VEH gear raw=.* mapped=D", timeout=5) is not None)
    lib.gear("Park")
    lib.check("기어 P 수신", lib.wait_log(r"VEH gear raw=.* mapped=P", timeout=5) is not None)
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
