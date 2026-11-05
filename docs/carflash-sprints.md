---
name: carflash-sprints
title: 카플래시 개발 스프린트 계획과 결과
app: carflash
created: 2026-11-05
updated: 2026-11-05
---

# 카플래시 개발 스프린트

창업자 지시(2026-11-05): "우리가 논의한 것, wireframe 기반으로 sprint를 나누고, 각 sprint를 개발 => e2e test => 오류 발견시 수정 => 오류 없을 시 다음 스프린트 이 순서로 해서 개발까지 완료해줘." `EXTRACTED`

기준 문서는 와이어프레임 v0.3(`ideas/carflash-design/wireframes.html`, 위키 `carflash-design`)과 마스코트 초안(`ideas/carflash-mascot/`, 위키 `carflash-mascot`)이다. 루트 위키 페이지는 이 저장소 밖에 있어 경로로만 적는다.

## 정해진 것 (2026-11-05 창업자 답)

- 화면은 Kotlin + Jetpack Compose, 해치 3D는 WebView 안의 three.js(마스코트 리그 재사용).
- 서버는 이번에는 만들지 않는다. 코드 공유·플래시몹 초대는 앱 안 목업 저장소와 가짜 참가 차로 흐름을 완성한다.
- 음원은 Claude가 BPM이 정해진 짧은 곡을 합성한다. 테마는 로맨틱, 캠핑장 댄스파티, 감미로운 밤 등. 각 플래시몹 커버는 테마에 맞춘 해치 그림.
- 커밋 신원은 `Haechi <225294644+Vyblor@users.noreply.github.com>`이다(2026-11-05 창업자). 저장소는 창업자 개인 계정에 둔다. 커밋은 창업자가 지시할 때 한다.

## E2E 시험 방식

에뮬레이터(Pleos Connect v2.1.0, AVD `Pleos_Probe`)에 앱을 설치하고, `e2e/` 의 Python 스크립트가 adb로 화면을 조작한다. 화면 요소는 Compose `testTag`를 resource-id로 내보내(`testTagsAsResourceId`) `uiautomator dump`로 찾는다. 차량 쪽 결과는 버추얼 콘솔 HTTP(`/vehicle/state`)로 확인하고, 기어는 콘솔 `/vehicle/gear`로 바꾼다. 스프린트마다 시험 스크립트 하나(`e2e/sN_*.py`)를 쓰고, 다음 스프린트에서는 앞 스프린트 시험도 다시 돌린다(회귀).

## 스프린트

| # | 범위 (와이어프레임) | 합격선 (미리 고정) |
|---|---|---|
| S1 | 골격, 테마, 앱 상단줄·기어 칩, A1 첫 실행, A2 홈(가로·세로), 해치 WebView(idle·hello), 차량 게이트웨이(Pleos + 가짜) | 첫 실행에 A1이 뜨고 "알겠어요, 시작" 뒤 A2. 재실행하면 A1 생략. 해치 WebView가 WebGL로 그려짐(로그). 기어 칩이 P를 보여 줌 |
| S2 | 음원 4곡과 커버, 플래시몹 데이터·엔진, A3 준비, A4 카운트다운, A5 재생, A6 끝·코드 | 시작 뒤 콘솔 조명 색이 곡 진행에 따라 바뀌고 창문이 열림. 정지 누르면 조명·창문이 시작 전 값으로 돌아감. 끝까지 재생하면 A6 |
| S3 | E1 기어 전환 즉시 정지·복원, E2 멈춘 이유, E3 배터리 보호, E4 오프라인 | 재생 중 콘솔 기어 D → 1초 안에 엔진 정지와 복원 명령. 다시 P → E2 화면. 배터리·오프라인 모의 값에서 E3·E4 화면 |
| S4 | B1 코드 입력, B2 차종 차이, C1 곡 고르기, C2 편집기, C3 저장·코드 | 만들기 → 저장 → 코드 발급 → 그 코드 입력 → 준비 화면에 같은 플래시몹 |
| S5 | D1 플래시몹 초대, D2 방장 대기실, D3 참가, D4 동시 카운트다운 | 방 만들기 → 가짜 차 입장 → 모두 함께 시작 → 카운트다운 → 재생. 코드로 참가 → 자리 고르기 → 준비 |
| S6 | 전체 회귀, 해치 성능 측정, 문서 | S1~S5 시험 전부 통과. 해치 WebView 프레임 측정값 기록 |

## 결과

(스프린트가 끝날 때마다 아래에 적는다.)

### S1 — 통과 (2026-11-05)

만든 것은 다음과 같다. Gradle 프로젝트(AGP 8.5.0, Kotlin 1.9.24, Compose BOM 2024.06.00, compileSdk 34, minSdk 28, `ai.pleos.playground:Vehicle:2.3.2`)를 세웠다. 디자인 토큰은 `ui/theme/Theme.kt`에 옮겼고 글꼴은 Asta Sans 가변 글꼴이다. 공통 컴포넌트는 상단줄, 기어 칩, 버튼, 타일, 커버, 효과 배지, 해치 뷰다. 화면은 A1 첫 실행(안전 약속 3줄과 이 차에서 쓸 수 있는 효과)과 A2 홈(가로·세로 배치)을 만들었다. 차량 게이트웨이는 Pleos 구현과 가짜 구현 두 가지다. 해치는 WebView 하나에 three.js를 올리고 화면이 바뀌면 그 WebView를 옮겨 붙인다. 시험 도구는 `e2e/lib.py`이고 S1 시험은 `e2e/s1_onboard_home.py`다. S2를 위해 곡 5개(`tools/make_songs.py`, 모두 수식 합성)를 미리 만들어 두었다.

시험 결과는 22개 항목 모두 PASS다(에뮬레이터 Pleos_Probe, 2026-11-05). `EXTRACTED`
- 첫 실행에 A1이 뜨고, "알겠어요, 시작"을 누르면 A2로 간다. 앱을 다시 켜면 A1을 건너뛴다.
- 효과 점검에서 조명·창문·와이퍼·송풍이 모두 지원으로 나온다.
- 기어 칩이 P를 보여 준다. 콘솔에서 D와 P로 바꾸면 앱이 받는다.
- 해치 WebView는 WebGL 2.0 컨텍스트에서 그려지고, hello에서 idle로 바뀐다. 홈에서 해치 프레임은 53 fps다.
- 화면 사진은 `e2e/out/s1_a1_onboard.png`와 `e2e/out/s1_a2_home.png`(git에서 제외)다.

시험 중 고친 것:
- 콘솔 기어 변경이 503 `emulator_inactive`로 막혔다. 원인은 하트비트 스크립트가 꺼져 있던 것이었고, 켠 뒤 통과했다.
- Vehicle SDK 조회 API 이름이 `getWindowPosition`이 아니라 `getCurrentXxx` 꼴이었다. 공식 샘플 앱 가이드의 `getCurrentDoorPosition` 규칙을 보고 맞췄다. SDK 바이너리는 열지 않았다.
- 에뮬레이터는 앰비언트 조명 영역을 1개로 돌려준다. 그래서 효과 점검의 "앰비언트 1구역" 표시를, 영역이 1개일 때는 "앰비언트 조명"으로 바꿨다.

남은 것:
- 세로(840) 배치는 코드로만 있고 에뮬레이터 분할 화면에서는 아직 시험하지 않았다. S6 회귀에서 시험한다.
- 디버그 APK가 65 MB다(아이콘 라이브러리를 줄이지 않았다). 출품 전에 R8로 줄인다.
- 커버 그림은 아직 그라데이션이다. 해치 커버는 S2에서 넣는다.
- `INFERRED` 앰비언트 영역 1개는 SDK가 좌석 5구역을 영역 하나로 묶어 돌려주는 것으로 보인다. 구역별 색 연출이 가능한지는 S2에서 시험한다.

## Open Questions

- `AMBIGUOUS` 실차에서 Vehicle SDK 제어 권한 승인(출시 단계).

*Part of the Hyundai App Dev Challenge wiki. See [[index]] to navigate.*
