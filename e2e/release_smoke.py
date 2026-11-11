"""출시 APK(R8 축소·릴리스 서명) 기본 동작 시험.

디버그 시험 옵션(cf_*)은 릴리스에서 꺼져 있으므로 전체 회귀(run_all.py) 대신 심사위원이 밟을
기본 흐름만 확인한다: 설치 → 첫 실행 → 해치 → 플래시몹 재생(차 값 변화) → 기어 D 정지·복원 →
만들기·저장(코드) → 함께 플래시몹 → 크래시 없음.

  python3 e2e/release_smoke.py [apk 경로]   # 기본: apk/Haechi_CarFlash_release.apk
에뮬레이터와 버추얼 콘솔(포트 8088 + 하트비트)이 켜져 있어야 한다. 기존 앱은 지우고 새로 설치한다.
"""
import os
import re
import sys
import time

import lib

ROOT = os.path.dirname(lib.HERE)
APK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "apk", "Haechi_CarFlash_release.apk")
BASE = "#aa00ff"


def main():
    print("APK:", APK, f"{os.path.getsize(APK) / 1e6:.1f} MB")
    lib.sh("uninstall", lib.PKG, check=False)  # 디버그 빌드와 서명이 달라 덮어쓰기가 안 된다
    lib.install(APK)
    dbg = lib.shell(f"dumpsys package {lib.PKG}")
    lib.check("릴리스 빌드(디버그 불가)", "DEBUGGABLE" not in dbg.split("pkgFlags=")[1].split("\n")[0] if "pkgFlags=" in dbg else False)
    lib.logclear()

    lib.fresh(install_apk=False)
    lib.check("첫 실행 → 홈", lib.find("screen_home") is not None)
    lib.check("차량 SDK 연결(기능 점검)", lib.wait_log(r"VEH cap fan", timeout=5) is not None)
    lib.check("해치 WebView 브리지(R8 뒤)", lib.wait_log(r"HAECHI ready ok=true", timeout=15) is not None)

    # 재생
    lib.open_show("camp")
    lib.tap("prep_start")
    lib.check("플래시몹 재생 시작", lib.wait_log(r"SHOW playing", timeout=12) is not None)
    time.sleep(6)
    c = lib.car()
    lib.check("차 값이 곡에 맞춰 바뀜(콘솔)", c["color"] != BASE or c["fan"] != 3 or c["bright"] != 30, str(c))

    # 안전: 기어 D
    lib.gear("Drive")
    lib.check("기어 D → 정지·복원", lib.wait_log(r"SHOW stop reason=GEAR restoredIn=", timeout=4) is not None)
    time.sleep(1)
    c = lib.car()
    lib.check("시작 전 값으로 되돌림(색·창문·송풍)", c["color"] == BASE and max(c["windows"]) == 0 and c["fan"] == 3, str(c))
    lib.gear("Park")
    time.sleep(1.5)
    lib.back()
    lib.check("홈 복귀", lib.wait("screen_home", timeout=6) is not None)

    # 만들기·저장
    lib.tap("home_create"); lib.wait("screen_songs", timeout=6)
    lib.tap("song_sweet_night"); time.sleep(0.8)
    lib.tap("songs_next"); lib.wait("screen_editor", timeout=6)
    lib.tap("cell_LIGHT_0"); time.sleep(0.6); lib.tap("block_light_solid_cyan"); time.sleep(0.6)
    lib.tap("editor_save")
    ok = lib.wait("screen_saved", timeout=6) is not None
    code = re.search(r"\b\d{3} ?\d{3}\b", " ".join(lib.texts())) if ok else None
    lib.check("저장 → 6자리 코드", code is not None, code.group(0) if code else "")
    lib.back(); lib.wait("screen_home", timeout=6)

    # 함께 플래시몹 (앱 안 시험 서버)
    lib.tap("home_group"); lib.wait("screen_group", timeout=6)
    lib.tap("group_host")
    lib.check("방 만들기 → 다른 차 준비", lib.wait_log(r"GROUP ready nick=", timeout=10) is not None)
    lib.tap("group_start")
    lib.check("함께 재생 시작", lib.wait_log(r"SHOW playing", timeout=12) is not None)
    time.sleep(4)
    lib.tap("stop")
    lib.check("내 차만 멈추기", lib.wait(desc="result:USER", timeout=6) is not None)

    crash = lib.sh("logcat", "-d", "-b", "crash", check=False)
    lib.check("크래시 없음", lib.PKG not in crash, crash[-300:])
    lib.summary()


if __name__ == "__main__":
    main()
