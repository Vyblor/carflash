---
name: carflash
title: 카플래시
app: carflash
status: prototype
owner: founder
created: 2026-11-05
updated: 2026-11-05
---

# 카플래시

## 한 줄

정차(P) 중에 곡에 맞춰 차의 앰비언트 조명, 창문, 와이퍼, 송풍이 움직이는 "내 차 플래시몹" 앱이다. 코드로 공유하고, 여러 대가 함께 시작할 수 있다. 마스코트는 해치다.

## 문서

- 개발 스프린트 계획과 결과: [[carflash-sprints]]
- 기획 원본(루트 위키, 이 저장소 밖): 코파운더 PRD `ideas/2026-11-03-prd-car-flashmob.md`, 디자인 `ideas/carflash-design.md`, 마스코트 `ideas/carflash-mascot.md`
- 글꼴 라이선스: `docs/AstaSans-OFL.txt`

## 지금 상태

- 스프린트 S1~S6을 모두 마쳤다. 에뮬레이터 전체 회귀에서 시험 175개 항목이 모두 통과했다([[carflash-sprints]]).
- S1(`2c69356`)에 이어 S2~S6도 한 커밋으로 묶어 커밋했다(2026-11-05, 창업자 "일단 커밋부터 해줘"). origin은 없다.
- 서버는 앱 안 목업이다. 코드 공유는 `MockShowServer`, 초대는 `MockGroupServer`가 맡는다.

## 다음 행동

| 날짜 | 누가 | 할 일 | 합격선(미리 고정) |
|---|---|---|---|
| 다음 세션 | 창업자 | 수동 시험([[carflash-sprints]] "수동 시험 안내" 1~6) | 흐름별 통과/수정 목록 |
| 창업자 지시 후 | Claude | 수동 시험 수정 반영 | 수정 뒤 `e2e/run_all.py` 전부 PASS |
| 출품 전(11-15 전) | Claude | R8로 APK 줄이기, 시연 영상용 흐름 | APK 30 MB 이하, 회귀 전부 PASS |

## Open Questions

- `AMBIGUOUS` 앰비언트 구역별 제어 가능 여부(에뮬레이터는 영역 1개를 돌려준다).
- `AMBIGUOUS` 배터리 보호 기준값(20%·10%), 12V 배터리 값, 실차 SOC 단위.
- `AMBIGUOUS` 실제 서버로 여러 차를 묶었을 때 동시 시작 오차.

## Log

- 2026-11-05 — S2~S6을 한 커밋으로 커밋했다(작성자 Haechi, 창업자 지시 "일단 커밋부터 해줘").

- 2026-11-05 — S1을 커밋했다(`2c69356`, 창업자 지시). S2~S6을 개발했다. S2는 엔진과 A3~A6, S3은 안전 E1~E4와 포그라운드 서비스, S4는 코드·만들기 B1~C3, S5는 함께 플래시몹 D1~D4와 앱이 죽은 뒤 복원, S6은 세로 840·해치 성능·전체 회귀다. 시험 175개 항목이 모두 통과했다. S2~S6은 커밋하지 않았다. 세션 기록은 [[2026-11-05-carflash-sprints-2-6]].

- 2026-11-05 — 커밋 신원을 `Haechi <225294644+Vyblor@users.noreply.github.com>`으로 설정했다(local, 창업자 지시). 원격은 vyblor 계정에 두고 아직 연결하지 않았다.
- 2026-11-05 — 앱 폴더를 만들고 `git init`(로컬 전용, 커밋 없음)과 `.gitignore`를 마쳤다. 스프린트를 S1~S6으로 나눴다. S1을 구현했고 e2e 22개 항목이 모두 통과했다. 곡 5개를 합성했다(캠핑장 댄스파티 124, 로맨틱 드라이브 100, 감미로운 밤 80, 생일 축하 파티 120, 비 오는 주차장 88 BPM). 창업자 지시로 S1 뒤에 멈췄다.

*Part of the Hyundai App Dev Challenge wiki. See [[index]] to navigate.*
