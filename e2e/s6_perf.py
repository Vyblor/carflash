"""S6 해치 성능: 홈(idle)과 재생(dance) 중 해치 WebView 프레임(HaechiBridge.onFps, 5초마다)을 모은다."""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402


def fps_samples(seconds):
    lib.logclear()
    time.sleep(seconds)
    return [int(m) for m in re.findall(r"HAECHI fps=(\d+)", lib.logs())]


def main(install=True):
    lib.fresh(install)
    time.sleep(3)
    home = fps_samples(16)
    print("home fps:", home)
    lib.check("홈 해치 30fps 이상", bool(home) and min(home) >= 30, str(home))
    lib.start(countdown_ms=1500, offset_ms=31000)
    lib.open_show("camp")
    lib.tap("prep_start")
    lib.wait_log(r"SHOW playing", timeout=8)
    play = fps_samples(16)
    print("play fps:", play)
    lib.check("재생 중 해치 30fps 이상", bool(play) and min(play) >= 30, str(play))
    mem = lib.shell("dumpsys meminfo app.carflash | grep -E 'TOTAL'", check=False).strip()
    print("meminfo:", mem.splitlines()[:2])
    lib.tap("stop")
    lib.wait(desc="result:USER", timeout=5)
    lib.back()
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
