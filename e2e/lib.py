"""카플래시 e2e 도우미. adb + uiautomator + Pleos 버추얼 콘솔(HTTP 8088).

앱은 에뮬레이터의 사용자 10(운전자)으로 돈다. 화면 요소는 Compose testTag가 resource-id로 나온다.
"""
import json
import os
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "app.carflash"
USER = "10"
CONSOLE = "http://127.0.0.1:8088"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)

FAILS = []


def sh(*args, check=True, timeout=60):
    r = subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip()}")
    return r.stdout


def shell(cmd, **kw):
    return sh("shell", cmd, **kw)


# ---------- 앱 ----------
def install(apk):
    sh("install", "-r", "-g", apk, timeout=300)


def clear():
    shell(f"pm clear --user {USER} {PKG}", check=False)


def logclear():
    sh("logcat", "-c", check=False)


def start(countdown_ms=None, offset_ms=None, offline=None, width_dp=None):
    """디버그 빌드 시험 옵션: 카운트다운 길이, 곡 중간부터 시작, 오프라인 흉내."""
    extra = ""
    if countdown_ms is not None:
        extra += f" --el cf_countdown_ms {countdown_ms}"
    if offset_ms is not None:
        extra += f" --el cf_offset_ms {offset_ms}"
    if offline is not None:
        extra += f" --ez cf_offline {'true' if offline else 'false'}"
    if width_dp is not None:
        extra += f" --ei cf_width_dp {width_dp}"
    shell(f"am start --user {USER} -W -n {PKG}/.MainActivity{extra}")


def stop():
    shell(f"am force-stop --user {USER} {PKG}", check=False)


def logs():
    return sh("logcat", "-d", "-s", "CARFLASH:I", check=False)


def wait_log(pattern, timeout=15, since=None):
    rx = re.compile(pattern)
    end = time.time() + timeout
    while time.time() < end:
        for line in logs().splitlines():
            if rx.search(line):
                return line
        time.sleep(0.5)
    return None


# ---------- 화면 ----------
class Node:
    def __init__(self, e):
        self.e = e
        self.rid = e.get("resource-id", "")
        self.text = e.get("text", "")
        self.desc = e.get("content-desc", "")
        b = [int(x) for x in re.findall(r"\d+", e.get("bounds", "[0,0][0,0]"))]
        self.bounds = b
        self.enabled = e.get("enabled") == "true"

    @property
    def center(self):
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    def __repr__(self):
        return f"<{self.rid or '-'} '{self.text or self.desc}' {self.bounds}>"


def dump():
    for _ in range(3):
        out = shell("uiautomator dump /sdcard/cf.xml", check=False)
        if "dumped" in out:
            xml = shell("cat /sdcard/cf.xml")
            try:
                nodes = []
                for e in ET.fromstring(xml).iter("node"):
                    n = Node(e)
                    # Compose는 탭 가능한 노드의 설명을 같은 크기 자식 노드로 내보내기도 한다. 그 설명을 부모(태그 노드)에 올린다.
                    if n.rid and not n.desc:
                        for c in e.findall("node"):
                            if c.get("content-desc") and c.get("bounds") == e.get("bounds"):
                                n.desc = c.get("content-desc")
                                break
                    nodes.append(n)
                return nodes
            except ET.ParseError:
                pass
        time.sleep(0.5)
    return []


def find(tag=None, text=None, desc=None, nodes=None):
    nodes = nodes if nodes is not None else dump()
    for n in nodes:
        if tag and not (n.rid == tag or n.rid.endswith(":id/" + tag)):
            continue
        if text and text not in n.text:
            continue
        if desc and desc not in n.desc:
            continue
        return n
    return None


def wait(tag=None, text=None, desc=None, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        n = find(tag, text, desc)
        if n:
            return n
        time.sleep(0.4)
    return None


def tap(tag=None, text=None, desc=None, timeout=10):
    n = wait(tag, text, desc, timeout)
    if not n:
        raise AssertionError(f"tap: not found tag={tag} text={text} desc={desc}")
    x, y = n.center
    shell(f"input tap {x} {y}")
    return n


def texts():
    return [n.text for n in dump() if n.text]


def screenshot(name):
    path = os.path.join(OUT, f"{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
    return path


def back():
    shell("input keyevent 4")


# ---------- 차(버추얼 콘솔) ----------
def console(method, path):
    req = urllib.request.Request(CONSOLE + path, method=method)
    with urllib.request.urlopen(req, timeout=5) as r:
        body = r.read().decode()
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


def gear(g):
    """g: Park | Drive | Reverse | Neutral"""
    return console("PUT", f"/vehicle/gear?gear={g}")


def state():
    return console("GET", "/vehicle/state")


def soc(v):
    """배터리 잔량(%)을 콘솔로 바꾼다."""
    return console("PUT", f"/vehicle/ev-battery?field=soc&value={v}")


# ---------- 판정 ----------
def check(name, ok, detail=""):
    mark = "PASS" if ok else "FAIL"
    print(f"[{mark}] {name}" + (f" — {detail}" if detail else ""), flush=True)
    if not ok:
        FAILS.append(name)
    return ok


def summary():
    if FAILS:
        print(f"\n{len(FAILS)} FAIL: {FAILS}")
        raise SystemExit(1)
    print("\nALL PASS")


# ---------- 공통 흐름 ----------
APK = os.path.join(os.path.dirname(HERE), "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def fresh(install_apk=True, onboard=True):
    """앱을 지우고 새로 켠 뒤 홈까지 간다."""
    if install_apk:
        install(APK)
    gear("Park")
    # 기준 상태: 흰색이 아닌 색으로 두어야 "원래 색 복원"을 가려낼 수 있다
    baseline()
    stop()
    clear()
    logclear()
    start()
    if onboard:
        n = wait("onboard_start", timeout=15)
        if n:
            tap("onboard_start")
        wait("screen_home", timeout=10)
        # 차량 기능 점검(SDK 초기화 약 2.5초) 끝날 때까지
        wait_log(r"VEH cap fan", timeout=10)


def baseline():
    """시험 전 차를 정해진 상태로: 보라 30%, 창문 닫힘, 와이퍼 끔, 송풍 3단."""
    console("PUT", "/vehicle/body?ambient_color=%23aa00ff")
    console("PUT", "/vehicle/body?ambient_brightness=30")
    for k in ("window_fl", "window_fr", "window_rl", "window_rr"):
        console("PUT", f"/vehicle/body?{k}=0")
    console("PUT", "/vehicle/body?front_wiper=Off")
    console("PUT", "/vehicle/hvac?fan_speed=3")
    time.sleep(0.5)


def car():
    """콘솔에서 읽은 차 상태 요약."""
    s = state()
    b = s["body"]
    return {
        "color": b["ambient_light"]["color"].lower(),
        "bright": b["ambient_light"]["brightness_pct"],
        "windows": [b["windows"][k] for k in ("front_left", "front_right", "rear_left", "rear_right")],
        "wiper": b["wipers"]["front"]["mode"],
        "fan": s["hvac"]["fan_speed"],
        "gear": s["gear"]["actual"]["value"],
        "soc": s.get("ev_battery", {}).get("state_of_charge"),
    }


def open_show(show_id):
    """홈에서 플래시몹을 연다(오늘의 플래시몹이면 큰 타일)."""
    show_id = show_id if show_id.startswith("p_") or show_id.startswith("m_") or show_id.startswith("f_") else "p_" + show_id
    n = find(f"show_{show_id}")
    if n is None:
        # 오른쪽 목록을 아래로 밀어 본다
        shell("input swipe 1900 1100 1900 500 300")
        time.sleep(0.6)
        n = find(f"show_{show_id}")
    if n is None:
        tap("home_today")  # 오늘의 플래시몹이 바로 그것

    else:
        x, y = n.center
        shell(f"input tap {x} {y}")
    return wait("screen_prep", timeout=6)
