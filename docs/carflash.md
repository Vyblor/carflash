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

- 2026-11-05에 앱 폴더를 만들었다. 로컬 git 저장소이고 origin은 없으며 아직 커밋하지 않았다.
- S1(골격, A1, A2, 해치 WebView)은 에뮬레이터 e2e 22개 항목 모두 통과했다.
- 창업자 지시("첫 스프린트 한 후에 커밋하지 말고 잠시 스탑해줘")에 따라 S2를 시작하지 않고 멈춰 있다.

## 다음 행동

| 날짜 | 누가 | 할 일 | 합격선(미리 고정) |
|---|---|---|---|
| 다음 세션 | 창업자 | S1 화면 확인, 첫 커밋 지시 | 진행/수정 기록 |
| 창업자 재개 지시 후 | Claude | S2: 플래시몹 엔진, A3~A6, 해치 커버 | P단에서 1곡 끝까지 재생, 정지하면 원래 상태로 복원 |

## Open Questions

- `AMBIGUOUS` 앰비언트 구역별 제어 가능 여부(에뮬레이터는 영역 1개를 돌려준다).

## Log

- 2026-11-05 — 커밋 신원을 `Haechi <225294644+Vyblor@users.noreply.github.com>`으로 설정했다(local, 창업자 지시). 원격은 vyblor 계정에 두고 아직 연결하지 않았다.
- 2026-11-05 — 앱 폴더를 만들고 `git init`(로컬 전용, 커밋 없음)과 `.gitignore`를 마쳤다. 스프린트를 S1~S6으로 나눴다. S1을 구현했고 e2e 22개 항목이 모두 통과했다. 곡 5개를 합성했다(캠핑장 댄스파티 124, 로맨틱 드라이브 100, 감미로운 밤 80, 생일 축하 파티 120, 비 오는 주차장 88 BPM). 창업자 지시로 S1 뒤에 멈췄다.

*Part of the Hyundai App Dev Challenge wiki. See [[index]] to navigate.*
