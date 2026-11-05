"""S3 시험: E1 기어 D → 1초 안 정지·복원, E2 멈춘 이유·복원 결과, E3 배터리 보호, E4 오프라인.

합격선(미리 고정, docs/carflash-sprints.md):
  재생 중 기어 D → 1초 안에 콘솔의 조명·창문·송풍이 시작 전 값. 카운트다운 중 D도 같다.
  다시 P → 멈춘 이유(GEAR)와 되돌린 내용이 보이고 "처음부터 다시"가 켜짐.
  배터리 20% 미만 → 창문·송풍 꺼진 채 시작, 밝기 70% 이하. 10% 미만 → 시작 버튼 꺼짐. 재생 중 떨어지면 멈춤.
  오프라인 → 홈 배너, 코드·초대 타일 꺼짐, 추천 플래시몹은 그대로.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402

DROP_MS = 31000  # 캠핑장 댄스파티 16마디(124 BPM) = 30.97초


def same(c, b):
    return c["color"] == b["color"] and c["windows"] == b["windows"] and c["fan"] == b["fan"] and c["wiper"] == b["wiper"]


def play_camp_at_drop():
    lib.start(countdown_ms=1500, offset_ms=DROP_MS)
    lib.open_show("camp")
    lib.tap("prep_start")
    lib.wait_log(r"SHOW playing", timeout=8)
    end = time.time() + 8
    while time.time() < end:
        if max(lib.car()["windows"]) > 0:
            return True
        time.sleep(0.3)
    return False


def main(install=True):
    lib.soc(100)
    lib.fresh(install)
    lib.check("배터리 읽기(SDK)", lib.wait_log(r"VEH battery soc=100", timeout=8) is not None)
    before = lib.car()
    print("before:", before)

    # ---- E1: 재생 중 D ----
    lib.logclear()
    lib.check("드롭에서 창문 열림", play_camp_at_drop())
    lib.check("포그라운드 서비스 켜짐", lib.wait_log(r"SVC foreground on", timeout=3) is not None)
    time.sleep(1.0)
    t0 = time.time()
    lib.gear("Drive")
    restored = None
    while time.time() - t0 < 4:
        if same(lib.car(), before):
            restored = time.time() - t0
            break
        time.sleep(0.05)
    lib.check("E1 D 전환 → 1초 안 복원(콘솔)", restored is not None and restored <= 1.0, f"{restored:.2f}s" if restored else str(lib.car()))
    ln = lib.wait_log(r"SHOW stop reason=GEAR restoredIn=", timeout=3)
    lib.check("E1 엔진 정지 로그", ln is not None, (ln or "").split("CARFLASH: ")[-1])
    gl = lib.wait_log(r"VEH gear raw=.* mapped=D", timeout=2)
    lib.check("포그라운드 서비스 꺼짐", lib.wait_log(r"SVC foreground off", timeout=3) is not None)
    time.sleep(1.5)
    lib.screenshot("s3_e1_drive")
    # 2초 더 지나도 다시 움직이지 않음
    lib.check("E1 정지 뒤 차 그대로", same(lib.car(), before), str(lib.car()))

    # ---- E2: 다시 P ----
    lib.gear("Park")
    time.sleep(1.0)
    lib.start()  # 주행 차단 화면 뒤에 있던 앱을 앞으로(사람은 앱 아이콘을 누른다)
    n = lib.wait(desc="result:GEAR", timeout=6)
    lib.check("E2 멈춘 이유 화면(GEAR)", n is not None)
    nodes = lib.dump()
    r = lib.find("banner_restored", nodes=nodes)
    lib.check("E2 되돌린 내용", r is not None and "창문 닫힘" in r.desc and "조명 원래 색" in r.desc, repr(r and r.desc))
    lib.check("E2 멈춘 위치 표시", any("에서 멈췄어요" in x.text for x in nodes))
    p = lib.find("result_primary", nodes=nodes)
    lib.check("E2 처음부터 다시 켜짐(P)", p is not None and p.enabled)
    lib.screenshot("s3_e2_resume")
    lib.tap("result_secondary")
    lib.check("E2 닫기 → 홈", lib.wait("screen_home", timeout=5) is not None)

    # ---- E1: 카운트다운 중 D ----
    lib.logclear()
    lib.start(countdown_ms=9000, offset_ms=0)
    lib.open_show("camp")
    lib.tap("prep_start")
    lib.wait_log(r"SHOW countdown", timeout=5)
    lib.gear("Drive")
    lib.check("카운트다운 중 D → 정지", lib.wait_log(r"SHOW stop reason=GEAR", timeout=3) is not None)
    time.sleep(1.0)
    lib.check("카운트다운 중 D → 차 안 바뀜", same(lib.car(), before), str(lib.car()))
    lib.check("곡이 시작되지 않음", lib.wait_log(r"SHOW playing", timeout=1) is None)
    lib.gear("Park")
    time.sleep(1.0)
    lib.start(countdown_ms=0)
    lib.wait(desc="result:GEAR", timeout=6)
    lib.back()
    lib.wait("screen_home", timeout=5)

    # ---- E3: 배터리 부족(15%) ----
    lib.soc(15)
    lib.check("배터리 15% 읽음", lib.wait_log(r"VEH battery soc=15", timeout=8) is not None)
    lib.open_show("camp")
    nodes = lib.dump()
    lib.check("E3 배터리 배너", lib.find("banner_battery", nodes=nodes) is not None)
    descs = {x.desc for x in nodes if x.desc.startswith("fx_")}
    lib.check("E3 창문·송풍 꺼짐", {"fx_window:unsupported", "fx_fan:unsupported", "fx_light:on"} <= descs, str(sorted(descs)))
    lib.check("E3 창문 경고 없음", lib.find("banner_window", nodes=nodes) is None)
    lib.screenshot("s3_e3_battery")
    lib.back()
    lib.wait("screen_home", timeout=5)
    lib.logclear()
    lib.start(countdown_ms=1500, offset_ms=DROP_MS)
    lib.open_show("camp")
    lib.tap("prep_start")
    lib.wait_log(r"SHOW playing", timeout=8)
    wins, brights = [], []
    for _ in range(10):
        c = lib.car(); wins.append(max(c["windows"])); brights.append(c["bright"]); time.sleep(0.4)
    lib.check("E3 창문 안 열림", max(wins) == 0, str(wins))
    lib.check("E3 밝기 70% 이하", max(brights) <= 70 and max(brights) > 0, str(sorted(set(brights))))
    lib.check("E3 15%에서도 조명만이면 계속", lib.wait_log(r"SHOW stop", timeout=1) is None)
    lib.tap("stop")
    lib.wait(desc="result:USER", timeout=5)
    lib.back()
    lib.wait("screen_home", timeout=5)

    # ---- E3: 아주 부족(5%) → 시작 못 함 ----
    lib.soc(5)
    lib.wait_log(r"VEH battery soc=5$", timeout=8)
    time.sleep(0.5)
    lib.open_show("camp")
    s = lib.find("prep_start")
    lib.check("E3 5% → 시작 버튼 꺼짐", s is not None and not s.enabled, repr(s))
    lib.back()
    lib.wait("screen_home", timeout=5)

    # ---- E3: 재생 중 8%로 떨어짐 → 멈춤 ----
    lib.soc(50)
    lib.wait_log(r"VEH battery soc=50", timeout=8)
    lib.logclear()
    lib.start(countdown_ms=1500, offset_ms=0)
    lib.open_show("night")
    lib.tap("prep_start")
    lib.wait_log(r"SHOW playing", timeout=8)
    time.sleep(1)
    lib.soc(8)
    lib.check("E3 재생 중 8% → 멈춤", lib.wait_log(r"SHOW stop reason=BATTERY", timeout=8) is not None)
    lib.check("E3 멈춘 이유(BATTERY)", lib.wait(desc="result:BATTERY", timeout=5) is not None)
    time.sleep(0.5)
    lib.check("E3 복원", same(lib.car(), before), str(lib.car()))
    lib.screenshot("s3_e3_stopped")
    lib.soc(100)
    lib.wait_log(r"VEH battery soc=100", timeout=8)
    lib.back()
    lib.wait("screen_home", timeout=5)

    # ---- E4: 오프라인 ----
    lib.start(offline=True)
    lib.check("E4 오프라인 배너", lib.wait("banner_offline", timeout=5) is not None)
    nodes = lib.dump()
    code = lib.find("home_code", nodes=nodes); grp = lib.find("home_group", nodes=nodes); mk = lib.find("home_create", nodes=nodes)
    lib.check("E4 코드·초대 꺼짐", code is not None and not code.enabled and grp is not None and not grp.enabled, f"{code} {grp}")
    lib.check("E4 만들기는 켜짐", mk is not None and mk.enabled)
    lib.check("E4 오늘의 플래시몹 열림", lib.find("home_today", nodes=nodes) is not None)
    lib.screenshot("s3_e4_offline")
    lib.start(offline=False)
    time.sleep(1.5)
    lib.check("E4 다시 온라인 → 배너 사라짐", lib.find("banner_offline") is None)
    lib.summary()


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv)
