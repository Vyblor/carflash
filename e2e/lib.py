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


def start():
    shell(f"am start --user {USER} -W -n {PKG}/.MainActivity")


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
                return [Node(e) for e in ET.fromstring(xml).iter("node")]
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
