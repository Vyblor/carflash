"""전체 회귀: S1~S5 시험과 S6 세로·성능 시험을 차례로 돌린다. 첫 시험만 APK를 설치한다."""
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SUITES = ["s1_onboard_home.py", "s2_show.py", "s3_safety.py", "s4_create_code.py", "s5_group.py", "s6_portrait.py", "s6_perf.py"]


def main():
    results = []
    for i, s in enumerate(SUITES):
        t0 = time.time()
        args = [sys.executable, "-I", os.path.join(HERE, s)] + ([] if i == 0 else ["--no-install"])
        r = subprocess.run(args, capture_output=True, text=True)
        out = r.stdout + r.stderr
        passed = out.count("[PASS]"); failed = out.count("[FAIL]")
        ok = r.returncode == 0
        results.append((s, ok, passed, failed, time.time() - t0))
        print(f"== {s}: {'PASS' if ok else 'FAIL'} ({passed} pass, {failed} fail, {time.time() - t0:.0f}s)", flush=True)
        if not ok:
            print("\n".join(l for l in out.splitlines() if "[FAIL]" in l or "Error" in l or "Traceback" in l)[-2000:], flush=True)
        for l in out.splitlines():
            if l.startswith(("home fps", "play fps", "meminfo")):
                print("   ", l, flush=True)
    total = sum(p for _, _, p, _, _ in results)
    print(f"\nTOTAL {total} checks, suites {sum(1 for r in results if r[1])}/{len(results)} pass")
    sys.exit(0 if all(r[1] for r in results) else 1)


if __name__ == "__main__":
    main()
