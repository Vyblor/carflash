/* 해치 캐릭터 리그 v0.1 (카플래시 마스코트 초안, 2026-11-05)
 *
 * 순수 함수 구조: pose = POSES[state](t, ctx) → 숫자 묶음 → rig.apply(pose).
 * 같은 t면 언제나 같은 모습이 나온다. 그래서 앱 화면(requestAnimationFrame)과
 * HyperFrames 영상(hf-seek로 프레임마다 t를 줌)이 이 파일 하나를 같이 쓴다.
 * Math.random, Date.now를 쓰지 않는다.
 *
 * 상태: idle(홈 대기), hello(인사·플래시몹 준비), dance(플래시몹 재생, BPM 동기),
 *       stop(정지), cheer(플래시몹 끝), wait(모임 대기실), rest(멈춘 이유 설명)
 */
import * as THREE from './three.module.js';

export const PALETTE = {
  fur: '#fff4e6',        // 아이보리 털 (노랑 계열을 피해 서울시 해치와 구별)
  belly: '#fffaf2',
  mane: '#ff4d8d',       // 플래시 핑크 갈기
  maneDeep: '#e63d7a',
  horn: '#f6c453',
  nose: '#e8577f',
  cheek: '#ff86b0',
  eye: '#17181d',
  collar: '#3fd5ff',     // 청록 목걸이(동기화 색)
  bell: '#f6c453',
  pad: '#ffb3cc',
};

export const SHOW_COLORS = ['#ff2d55', '#ff8a00', '#ffd60a', '#30d158', '#00e5ff', '#2f6bff', '#a05cff', '#ff4fd8'];

/* ---------- 작은 수학 도우미 ---------- */
const TAU = Math.PI * 2;
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
const easeOutBack = (x) => { x = clamp01(x); const c = 1.70158, c3 = c + 1; return 1 + c3 * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2); };
const lerp = (a, b, w) => a + (b - a) * w;
const fract = (x) => x - Math.floor(x);
/* 정해진 시드의 해시 — 깜빡임 간격 같은 '불규칙해 보이는' 값을 결정적으로 만든다 */
const hash = (n) => fract(Math.sin(n * 127.1 + 311.7) * 43758.5453);

/* ---------- 기본 자세 ---------- */
export const BASE = {
  rootX: 0, rootY: 0, rootRotY: 0, rootRotZ: 0,
  squash: 1, bodyRotX: 0, bodyRotZ: 0,
  headRotX: 0, headRotY: 0, headRotZ: 0,
  armPRaise: 0.18, armPFwd: 0, armNRaise: 0.18, armNFwd: 0,
  legP: 0, legN: 0, footPLift: 0, footNLift: 0,
  tail: 0, tailUp: 0,
  eyeOpen: 1, happy: 0, mouthOpen: 0.05, lookX: 0, lookY: 0,
  glow: 0.15, maneBounce: 0,
};

export function blendPose(a, b, w) {
  const o = {};
  for (const k in BASE) o[k] = lerp(a[k] ?? BASE[k], b[k] ?? BASE[k], w);
  return o;
}

/* 깜빡임: 2.6~4.4초마다, 가끔 두 번 */
function blink(t) {
  let s = 0.8, i = 0;
  if (t < s) return 1;
  while (s + 2.6 + hash(i) * 1.8 <= t) { s += 2.6 + hash(i) * 1.8; i++; }
  const d = t - s;
  if (d < 0.16) return 1 - Math.sin((d / 0.16) * Math.PI);
  if (hash(i + 99) > 0.7 && d >= 0.22 && d < 0.38) return 1 - Math.sin(((d - 0.22) / 0.16) * Math.PI);
  return 1;
}

/* ---------- 상태별 자세 (t: 그 상태에 들어온 뒤 흐른 초) ---------- */
export const POSES = {
  idle(t, ctx = {}) {
    const m = ctx.reduced ? 0.4 : 1;
    const br = Math.sin(TAU * t / 3.2);
    const p = { ...BASE };
    p.squash = 1 + 0.018 * br * m;
    p.headRotZ = 0.05 * Math.sin(TAU * t / 5.3) * m;
    p.headRotX = -0.03 * br * m;
    p.headRotY = 0.08 * Math.sin(TAU * t / 7.1) * m;
    p.armPRaise = 0.2 + 0.04 * br * m;
    p.armNRaise = 0.2 + 0.04 * Math.sin(TAU * (t - 0.4) / 3.2) * m;
    p.tail = 0.35 * Math.sin(TAU * t / 1.9) * m;
    p.eyeOpen = blink(t);
    p.lookX = 0.25 * Math.sin(TAU * t / 7.1);
    p.mouthOpen = 0.05;
    p.glow = 0.18 + 0.06 * br;
    // 주의 끌기 루프: 2.5초 뒤 첫 번, 이후 8초마다 짧은 까딱 점프 (choreograph: attention loop)
    if (!ctx.reduced && t > 2.5) {
      const q = (t - 2.5) % 8;
      if (q < 0.7) {
        const a = q / 0.7;
        const hop = a < 0.25 ? 0 : Math.sin(((a - 0.25) / 0.75) * Math.PI);
        p.squash *= a < 0.25 ? 1 - 0.08 * Math.sin((a / 0.25) * Math.PI) : 1 + 0.05 * hop;
        p.rootY = 0.12 * hop;
        p.headRotZ += 0.18 * Math.sin(a * Math.PI);
        p.happy = Math.sin(a * Math.PI);
        p.tailUp = 0.4 * hop;
      }
    }
    return p;
  },

  hello(t, ctx = {}) {
    const p = POSES.idle(t + 3, { ...ctx, reduced: true });
    const inA = easeOutBack(t / 0.45);
    const wave = Math.sin(TAU * t * 2.1);
    p.armNRaise = lerp(0.2, 2.45 + 0.3 * wave, inA);   // 화면 왼쪽 팔(캐릭터 오른팔)로 인사
    p.armNFwd = lerp(0, 0.25, inA);
    p.headRotZ = lerp(0, -0.16, inA) + 0.03 * wave;
    p.headRotY = lerp(0, -0.12, inA);
    p.happy = inA;
    p.mouthOpen = 0.55 * inA;
    p.rootY = 0.03 * Math.abs(Math.sin(TAU * t * 1.05));
    p.tail = 0.6 * Math.sin(TAU * t * 2.1);
    p.glow = 0.35;
    return p;
  },

  dance(t, ctx = {}) {
    // 곡 BPM을 춤 템포 75~150으로 접는다: 170 BPM 곡은 85(반박), 60 BPM 곡은 120(두 배)으로 춘다.
    // 박 위치는 곡과 같게 유지되므로 착지는 여전히 곡의 박에 맞는다.
    let bpm = ctx.bpm || 120;
    while (bpm > 150) bpm /= 2;
    while (bpm < 75) bpm *= 2;
    const m = ctx.reduced ? 0.35 : 1;
    // t: 곡 재생 위치(초). 앱에서는 벽시계가 아니라 플레이어 위치를 넘겨야 끊김·일시정지 뒤에도 박이 맞는다.
    const b = t * bpm / 60 + (ctx.beatOffset || 0);
    const ph = fract(b);
    const beat = Math.floor(b);
    const bar = Math.floor(b / 4) % 4;
    const inBar = b - Math.floor(b / 4) * 4;
    const p = { ...BASE };

    // 매 박: 박에서 눌리고(squash) 박 사이에 뜬다. 머리는 0.1박 늦게 따라온다(겹침).
    const up = Math.sin(Math.PI * ph);
    p.rootY = 0.07 * up * m;
    p.squash = 1 - 0.09 * Math.pow(1 - ph, 5) * m + 0.04 * up * m;
    p.headRotX = 0.13 * Math.sin(TAU * (b - 0.1)) * m;
    p.maneBounce = Math.sin(TAU * (b - 0.18)) * m;
    p.tail = 0.7 * Math.sin(TAU * b) * m;
    p.happy = 1;
    p.mouthOpen = 0.35 + 0.25 * up;
    p.glow = 0.35 + 0.65 * Math.pow(1 - ph, 2);

    if (bar === 0) {
      // 번갈아 손 들기
      const s = smooth((ph) / 0.35);
      const even = beat % 2 === 0;
      p.armNRaise = even ? lerp(0.7, 2.6, s) : lerp(2.6, 0.7, s);
      p.armPRaise = even ? lerp(2.6, 0.7, s) : lerp(0.7, 2.6, s);
      p.bodyRotZ = (even ? 1 : -1) * 0.08 * s * m;
      p.headRotZ = (even ? -1 : 1) * 0.1 * s * m;
    } else if (bar === 1) {
      // 옆으로 스텝 + 양팔 물결
      p.rootX = 0.16 * Math.sin(Math.PI * b) * m;
      p.bodyRotZ = -0.14 * Math.sin(Math.PI * b) * m;
      p.headRotZ = 0.1 * Math.sin(Math.PI * (b - 0.15)) * m;
      p.armPRaise = 1.35 + 0.45 * Math.sin(TAU * b);
      p.armNRaise = 1.35 + 0.45 * Math.sin(TAU * (b - 0.25));
      p.legP = 0.25 * Math.max(0, Math.sin(Math.PI * b));
      p.legN = 0.25 * Math.max(0, -Math.sin(Math.PI * b));
      p.footPLift = 0.06 * Math.max(0, Math.sin(Math.PI * b)) * m;
      p.footNLift = 0.06 * Math.max(0, -Math.sin(Math.PI * b)) * m;
    } else if (bar === 2) {
      // 플래시 포즈: 박마다 V(만세)와 X(가슴 앞 교차)를 번갈아 '찍는다'
      const hit = easeOutBack(ph / 0.22);
      const v = beat % 2 === 0;
      p.armNRaise = v ? lerp(0.9, 2.5, hit) : lerp(2.5, 0.9, hit);
      p.armPRaise = v ? lerp(0.9, 2.5, hit) : lerp(2.5, 0.9, hit);
      p.armNFwd = v ? lerp(0.9, 0, hit) : lerp(0, 0.9, hit);
      p.armPFwd = p.armNFwd;
      p.headRotX += v ? -0.08 : 0.06;
      p.mouthOpen = v ? 0.8 : 0.3;
    } else {
      // 회전과 점프: 0~2박 한 바퀴, 2.6박 웅크림(예비 동작), 3박 점프
      const spin = smooth(inBar / 2);
      p.rootRotY = TAU * spin * (ctx.reduced ? 0 : 1);
      p.armPRaise = 1.2 + 0.6 * Math.sin(Math.PI * spin);
      p.armNRaise = 1.2 + 0.6 * Math.sin(Math.PI * spin);
      if (inBar > 2.4 && inBar < 3.0) {
        const a = (inBar - 2.4) / 0.6;
        p.squash = 1 - 0.14 * Math.sin(a * Math.PI * 0.5) * m;
        p.armPRaise = lerp(p.armPRaise, 0.5, a);
        p.armNRaise = lerp(p.armNRaise, 0.5, a);
      } else if (inBar >= 3.0) {
        const a = inBar - 3.0;
        const j = Math.sin(a * Math.PI);
        p.rootY = 0.42 * j * m;
        p.squash = 1 + 0.08 * j * m - 0.1 * Math.pow(Math.max(0, a - 0.85) / 0.15, 2) * m;
        p.armPRaise = 2.7; p.armNRaise = 2.7;
        p.tailUp = 0.6 * j;
        p.legP = -0.3 * j; p.legN = -0.3 * j;
      }
    }
    return p;
  },

  stop(t, ctx = {}) {
    const p = { ...BASE };
    // 예비 동작(0~0.1초 움찔) → 양 손바닥 앞으로 "멈춰!" → 고정
    const a = clamp01(t / 0.1);
    const pop = easeOutBack((t - 0.1) / 0.3);
    p.squash = t < 0.1 ? 1 - 0.1 * Math.sin(a * Math.PI * 0.5) : lerp(0.9, 1, pop);
    p.armPRaise = lerp(0.2, 0.55, pop); p.armNRaise = lerp(0.2, 0.55, pop);
    p.armPFwd = lerp(0, 1.45, pop); p.armNFwd = lerp(0, 1.45, pop);
    p.bodyRotX = lerp(0, -0.08, pop);
    p.headRotX = lerp(0, -0.06, pop);
    p.eyeOpen = lerp(1, 1.18, pop);
    p.mouthOpen = lerp(0.05, 0.4, pop);
    p.glow = 0;
    p.tail = 0;
    // 멈춘 뒤에도 아주 작은 숨만 남긴다
    if (t > 0.6) p.squash *= 1 + 0.006 * Math.sin(TAU * (t - 0.6) / 3);
    return p;
  },

  cheer(t, ctx = {}) {
    const m = ctx.reduced ? 0.3 : 1;
    const p = { ...BASE };
    const c = fract(t / 0.9);
    const j = Math.sin(Math.min(1, c / 0.7) * Math.PI);
    p.rootY = 0.3 * j * m;
    p.squash = c > 0.7 ? 1 - 0.1 * Math.sin(((c - 0.7) / 0.3) * Math.PI) * m : 1 + 0.05 * j;
    p.armPRaise = 2.55 + 0.15 * Math.sin(TAU * t * 2.2);
    p.armNRaise = 2.55 + 0.15 * Math.sin(TAU * (t * 2.2 - 0.3));
    p.happy = 1; p.mouthOpen = 0.85;
    p.headRotZ = 0.12 * Math.sin(TAU * t / 1.8);
    p.tail = 0.9 * Math.sin(TAU * t * 2.2); p.tailUp = 0.5;
    p.glow = 0.6 + 0.4 * j;
    return p;
  },

  wait(t, ctx = {}) {
    const p = POSES.idle(t, { ...ctx, reduced: true });
    const look = Math.sin(TAU * t / 3.6);
    p.headRotY = 0.45 * look;
    p.lookX = 0.8 * look;
    p.bodyRotZ = 0.03 * look;
    const tap = fract(t / 0.55);
    p.footPLift = 0.05 * Math.max(0, Math.sin(tap * Math.PI * 2));
    p.legP = -0.2 * Math.max(0, Math.sin(tap * Math.PI * 2));
    p.armPRaise = 0.35; p.armNRaise = 0.35; p.armPFwd = -0.35; p.armNFwd = -0.35; // 뒷짐
    p.mouthOpen = 0.08;
    p.glow = 0.25 + 0.15 * Math.sin(TAU * t / 1.2);
    return p;
  },

  rest(t, ctx = {}) {
    const p = { ...BASE };
    const br = Math.sin(TAU * t / 4.6);
    p.squash = 0.97 + 0.02 * br;
    p.headRotX = 0.22 + 0.03 * br;
    p.headRotZ = 0.1;
    p.eyeOpen = 0.06;
    p.mouthOpen = 0.02;
    p.armPRaise = 0.12; p.armNRaise = 0.12; p.armPFwd = 0.3; p.armNFwd = 0.3;
    p.tail = 0.1 * Math.sin(TAU * t / 4.6); p.tailUp = -0.2;
    p.glow = 0;
    return p;
  },
};

/* ---------- 모델 ---------- */
function mat(color, o = {}) {
  return new THREE.MeshPhysicalMaterial({
    color, roughness: o.roughness ?? 0.55, metalness: o.metalness ?? 0,
    clearcoat: o.clearcoat ?? 0.25, clearcoatRoughness: 0.4,
    sheen: o.sheen ?? 0.6, sheenColor: new THREE.Color(o.sheenColor ?? '#ffd6e6'), sheenRoughness: 0.6,
    emissive: new THREE.Color(o.emissive ?? '#000000'), emissiveIntensity: o.emissiveIntensity ?? 1,
    transparent: o.opacity != null, opacity: o.opacity ?? 1,
  });
}
function ball(r, m, sx = 1, sy = 1, sz = 1, seg = 40) {
  const g = new THREE.SphereGeometry(r, seg, Math.round(seg * 0.75));
  const mesh = new THREE.Mesh(g, m); mesh.scale.set(sx, sy, sz); mesh.castShadow = true; return mesh;
}

export function createHaechi(opts = {}) {
  const P = { ...PALETTE, ...(opts.palette || {}) };
  const M = {
    fur: mat(P.fur, { sheen: 0.9, sheenColor: '#ffe3ee' }),
    belly: mat(P.belly, { sheen: 0.4 }),
    mane: mat(P.mane, { roughness: 0.45, sheen: 1, sheenColor: '#ffc2d9', emissive: P.mane, emissiveIntensity: 0 }),
    horn: mat(P.horn, { roughness: 0.35, metalness: 0.35, clearcoat: 0.8, emissive: '#ffb020', emissiveIntensity: 0 }),
    nose: mat(P.nose, { roughness: 0.3, clearcoat: 0.9 }),
    cheek: mat(P.cheek, { opacity: 0.7, sheen: 0 }),
    eye: mat(P.eye, { roughness: 0.12, clearcoat: 1, sheen: 0 }),
    white: new THREE.MeshBasicMaterial({ color: '#ffffff' }),
    dark: mat('#5a1f33', { roughness: 0.6, sheen: 0 }),
    tongue: mat('#ff7f9f', { sheen: 0 }),
    collar: mat(P.collar, { roughness: 0.3, clearcoat: 0.8, sheen: 0, emissive: P.collar, emissiveIntensity: 0.15 }),
    bell: mat(P.bell, { roughness: 0.25, metalness: 0.6, clearcoat: 1, emissive: '#ffffff', emissiveIntensity: 0 }),
    pad: mat(P.pad, { sheen: 0 }),
  };

  const root = new THREE.Group(); root.name = 'haechi';
  const bodyPivot = new THREE.Group(); bodyPivot.position.y = 0.40; root.add(bodyPivot);
  const squashG = new THREE.Group(); bodyPivot.add(squashG);

  // 몸통
  const body = ball(0.5, M.fur, 1.0, 1.02, 0.9); body.position.y = 0.3; squashG.add(body);
  const belly = ball(0.34, M.belly, 1, 1.12, 0.45); belly.position.set(0, 0.24, 0.3); squashG.add(belly);

  // 목걸이와 방울(차 헤드라이트처럼 빛남)
  const collar = new THREE.Mesh(new THREE.TorusGeometry(0.37, 0.055, 16, 48), M.collar);
  collar.rotation.x = Math.PI / 2; collar.position.y = 0.66; squashG.add(collar);
  const bell = ball(0.085, M.bell); bell.position.set(0, 0.6, 0.39); squashG.add(bell);
  const bellSlit = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.012, 0.02), M.dark); bellSlit.position.set(0, 0.585, 0.47); squashG.add(bellSlit);

  // 머리
  const neck = new THREE.Group(); neck.position.y = 0.7; squashG.add(neck);
  const head = new THREE.Group(); neck.add(head);
  const skull = ball(0.62, M.fur, 1.1, 0.95, 1.0, 56); skull.position.y = 0.52; head.add(skull);
  const muzzle = ball(0.29, M.belly, 1.25, 0.78, 0.75); muzzle.position.set(0, 0.36, 0.48); head.add(muzzle);
  const nose = ball(0.075, M.nose, 1.35, 0.9, 0.9); nose.position.set(0, 0.47, 0.7); head.add(nose);
  const noseHi = ball(0.022, M.white); noseHi.position.set(-0.03, 0.5, 0.77); head.add(noseHi);

  // 입: 웃는 선 + 벌림(어두운 안쪽, 혀) + 송곳니 두 개
  const smile = new THREE.Mesh(new THREE.TorusGeometry(0.075, 0.016, 10, 28, Math.PI), M.dark);
  smile.rotation.z = Math.PI; smile.position.set(0, 0.37, 0.69); head.add(smile);
  const mouth = ball(0.085, M.dark, 1.2, 1, 0.5); mouth.position.set(0, 0.31, 0.66); head.add(mouth);
  const tongue = ball(0.05, M.tongue, 1.3, 0.6, 0.6); tongue.position.set(0, 0.27, 0.68); head.add(tongue);
  const fangs = [-1, 1].map((s) => {
    const f = new THREE.Mesh(new THREE.ConeGeometry(0.022, 0.06, 12), M.white);
    f.rotation.x = Math.PI; f.position.set(s * 0.05, 0.34, 0.715); head.add(f); return f;
  });

  // 눈: 반짝이는 검은 알 + 하이라이트 두 개, 웃는 눈(^^)은 따로
  const eyes = [-1, 1].map((s) => {
    const g = new THREE.Group(); g.position.set(s * 0.25, 0.63, 0.555); g.rotation.y = s * 0.36; head.add(g);
    const ball1 = ball(0.13, M.eye, 0.88, 1.18, 0.42); g.add(ball1);
    const h1 = ball(0.044, M.white); h1.position.set(-0.035, 0.055, 0.05); g.add(h1);
    const h2 = ball(0.017, M.white); h2.position.set(0.032, -0.04, 0.045); g.add(h2);
    const happy = new THREE.Mesh(new THREE.TorusGeometry(0.08, 0.02, 10, 24, Math.PI), M.eye);
    happy.position.set(0, -0.015, 0.03); happy.visible = false; g.add(happy);
    return { g, ball: ball1, h1, h2, happy };
  });
  const cheeks = [-1, 1].map((s) => {
    const c = ball(0.085, M.cheek, 1.35, 0.8, 0.3); c.position.set(s * 0.42, 0.45, 0.47); c.rotation.y = s * 0.55; head.add(c); return c;
  });

  // 귀
  [-1, 1].forEach((s) => {
    const e = ball(0.14, M.fur, 0.7, 1, 0.45); e.position.set(s * 0.52, 0.92, -0.02); e.rotation.z = -s * 0.55; head.add(e);
    const ei = ball(0.08, M.pad, 0.6, 0.9, 0.3); ei.position.set(s * 0.52, 0.91, 0.04); ei.rotation.z = -s * 0.55; head.add(ei);
  });

  // 뿔 하나(해치의 표지): 둥근 원뿔
  const hornProfile = [];
  for (let i = 0; i <= 12; i++) {
    const v = i / 12; const r = 0.11 * Math.pow(1 - v, 0.85) + 0.015 * Math.sin(v * Math.PI);
    hornProfile.push(new THREE.Vector2(Math.max(0.006, r), v * 0.4));
  }
  const horn = new THREE.Mesh(new THREE.LatheGeometry(hornProfile, 32), M.horn);
  horn.position.set(0, 0.98, 0.16); horn.rotation.x = -0.22; horn.castShadow = true; head.add(horn);
  const hornTip = ball(0.026, M.horn); hornTip.position.set(0, 1.37, 0.07); head.add(hornTip);

  // 갈기: 머리 둘레 곱슬 뭉치 + 이마 위 세 가닥 (전통 해태의 곱슬 갈기를 단순화)
  const mane = new THREE.Group(); head.add(mane);
  const maneDeep = mat(P.maneDeep, { roughness: 0.5, sheen: 1, sheenColor: '#ffb0cc', emissive: P.mane, emissiveIntensity: 0 });
  M.maneDeep = maneDeep;
  const curls = [];
  // 앞 고리: 얼굴 둘레를 사자 갈기처럼 감싼다(턱 아래는 비움)
  const NF = 16;
  for (let i = 0; i < NF; i++) {
    const th = (i / NF) * TAU;                       // 0 = 정수리, 시계 방향
    if (Math.abs(th - Math.PI) < 0.62) continue;      // 턱 아래
    if (th < 0.25 || th > TAU - 0.25) continue;       // 뿔 자리
    const r = 0.14 + 0.035 * hash(i + 3);
    const c = ball(r, M.mane);
    c.position.set(Math.sin(th) * 0.7, 0.53 + Math.cos(th) * 0.6, -0.06 - 0.05 * Math.abs(Math.cos(th)));
    mane.add(c); curls.push({ m: c, base: c.position.clone(), k: i });
  }
  // 뒤 고리: 진한 분홍, 머리 뒤를 덮는다
  const NB = 12;
  for (let i = 0; i < NB; i++) {
    const th = (i / NB) * TAU + 0.26;
    const c = ball(0.17 + 0.03 * hash(i + 40), maneDeep);
    c.position.set(Math.sin(th) * 0.5, 0.55 + Math.cos(th) * 0.48, -0.42);
    mane.add(c); curls.push({ m: c, base: c.position.clone(), k: 40 + i });
  }
  // 뒤통수 가운데를 채운다
  [[0, 0.62, -0.62, 0.2], [-0.17, 0.42, -0.58, 0.16], [0.17, 0.42, -0.58, 0.16], [0, 0.85, -0.55, 0.15]].forEach(([x, y, z, r], i) => {
    const c = ball(r, maneDeep); c.position.set(x, y, z); mane.add(c); curls.push({ m: c, base: c.position.clone(), k: 70 + i });
  });
  // 이마 앞머리 두 가닥(뿔 양옆)
  [-1, 1].forEach((s2, i) => {
    const c = ball(0.1, M.mane); c.position.set(s2 * 0.2, 1.02, 0.2);
    mane.add(c); curls.push({ m: c, base: c.position.clone(), k: 60 + i });
  });

  // 팔: 어깨 축에서 매달린 캡슐 + 둥근 손
  function limb(len, r, m) {
    const g = new THREE.Group();
    const cap = new THREE.Mesh(new THREE.CapsuleGeometry(r, len, 8, 20), m); cap.position.y = -len / 2 - r * 0.2; cap.castShadow = true; g.add(cap);
    return g;
  }
  const arms = [1, -1].map((s) => {
    const sh = new THREE.Group(); sh.position.set(s * 0.43, 0.52, 0.04); squashG.add(sh);
    const a = limb(0.22, 0.1, M.fur); sh.add(a);
    const paw = ball(0.125, M.belly); paw.position.y = -0.36; a.add(paw);
    const pad = ball(0.06, M.pad, 1, 1, 0.4); pad.position.set(0, -0.38, 0.1); a.add(pad);
    return { sh, s };
  });

  // 다리와 발
  const legs = [1, -1].map((s) => {
    const hip = new THREE.Group(); hip.position.set(s * 0.22, 0.05, 0.02); bodyPivot.add(hip);
    const l = limb(0.08, 0.13, M.fur); hip.add(l);
    const foot = ball(0.16, M.belly, 1.0, 0.68, 1.25); foot.position.set(0, -0.31, 0.06); l.add(foot);
    const toe = ball(0.06, M.pad, 1.2, 0.5, 0.5); toe.position.set(0, -0.33, 0.24); l.add(toe);
    return { hip, s };
  });

  // 꼬리: 말린 꼬리 + 갈기색 술
  const tailRoot = new THREE.Group(); tailRoot.position.set(0, 0.08, -0.44); squashG.add(tailRoot);
  const tailSegs = [];
  let prev = tailRoot;
  for (let i = 0; i < 5; i++) {
    const seg = new THREE.Group(); seg.position.set(0, i === 0 ? 0 : 0.1, 0); prev.add(seg);
    const b = ball(0.1 - i * 0.008, M.fur); seg.add(b); tailSegs.push(seg); prev = seg;
  }
  const tuft = ball(0.15, M.mane, 1, 1.15, 1); tuft.position.set(0, 0.13, 0); prev.add(tuft);

  /* 바닥 그림자(그림자 맵 없이 부드러운 원판) */
  const sc = document.createElement('canvas'); sc.width = sc.height = 128;
  const sx = sc.getContext('2d'); const gr = sx.createRadialGradient(64, 64, 4, 64, 64, 64);
  gr.addColorStop(0, 'rgba(0,0,0,0.42)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); sx.fillStyle = gr; sx.fillRect(0, 0, 128, 128);
  const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.5, 1.2), new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(sc), transparent: true, depthWrite: false }));
  shadow.rotation.x = -Math.PI / 2; shadow.position.y = 0.002;
  const holder = new THREE.Group(); holder.add(root); holder.add(shadow);

  const glowColor = new THREE.Color(P.mane);

  function apply(p) {
    root.position.set(p.rootX, p.rootY, 0);
    root.rotation.set(0, p.rootRotY, p.rootRotZ);
    const sq = p.squash, xz = 1 / Math.sqrt(sq);
    squashG.scale.set(xz, sq, xz);
    bodyPivot.rotation.set(p.bodyRotX, 0, p.bodyRotZ);
    neck.rotation.set(p.headRotX, p.headRotY, p.headRotZ);
    // 팔: +x 쪽은 z 회전 +가 드는 방향, -x 쪽은 반대
    arms.forEach(({ sh, s }) => {
      const raise = s > 0 ? p.armPRaise : p.armNRaise;
      const fwd = s > 0 ? p.armPFwd : p.armNFwd;
      sh.rotation.set(-fwd, 0, s * raise, 'ZXY');
    });
    legs.forEach(({ hip, s }) => {
      hip.rotation.x = s > 0 ? p.legP : p.legN;
      hip.position.y = 0.05 + (s > 0 ? p.footPLift : p.footNLift);
    });
    tailRoot.rotation.set(-1.75 - p.tailUp * 0.5, p.tail, 0);
    tailSegs.forEach((sg, i) => { sg.rotation.x = (i === 0 ? 0 : 0.36) + p.tailUp * 0.1; sg.rotation.z = p.tail * 0.15 * i; });
    // 눈
    const open = Math.max(0.06, p.eyeOpen);
    eyes.forEach((e, i) => {
      const hp = p.happy > 0.5;
      e.ball.visible = !hp; e.h1.visible = !hp; e.h2.visible = !hp; e.happy.visible = hp;
      e.g.scale.set(1, hp ? 1 : open, 1);
      e.ball.position.set(p.lookX * 0.02, p.lookY * 0.02, 0);
    });
    // 입
    const mo = clamp01(p.mouthOpen);
    smile.visible = mo < 0.25;
    mouth.visible = mo >= 0.25; tongue.visible = mo >= 0.4;
    mouth.scale.set(1.2, 0.4 + 0.9 * mo, 0.5);
    fangs.forEach((f) => { f.visible = true; });
    // 갈기 출렁임(머리보다 늦게)
    curls.forEach(({ m, base, k }) => {
      const w = 0.02 * p.maneBounce * (1 + (k % 3) * 0.3);
      m.position.set(base.x, base.y + w, base.z);
    });
    // 빛: 갈기·방울·뿔 끝이 플래시몹 색으로 빛난다
    M.mane.emissive.copy(glowColor); M.mane.emissiveIntensity = 0.06 + 0.38 * p.glow;
    M.maneDeep.emissive.copy(glowColor); M.maneDeep.emissiveIntensity = 0.04 + 0.35 * p.glow;
    M.bell.emissive.copy(glowColor); M.bell.emissiveIntensity = 0.9 * p.glow;
    M.horn.emissiveIntensity = 0.25 * p.glow;
    // 그림자: 높이 뜰수록 작고 옅게
    const h = Math.max(0, p.rootY);
    shadow.scale.setScalar(1 / (1 + h * 1.6));
    shadow.position.x = p.rootX;
    shadow.material.opacity = 1 / (1 + h * 2.5);
  }

  function setGlow(hex) { glowColor.set(hex); }

  apply(BASE);
  return { object: holder, root, apply, setGlow, materials: M };
}

/* ---------- 무대(조명, 환경) ---------- */
export function createStage(renderer, scene, { theme = 'dark' } = {}) {
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const key = new THREE.DirectionalLight('#fff1e2', 2.2); key.position.set(-2.2, 3.4, 3); scene.add(key);
  const rim = new THREE.DirectionalLight('#8fdcff', 2.4); rim.position.set(2.6, 2.4, -2.6); scene.add(rim);
  const rim2 = new THREE.DirectionalLight('#ff86b0', 1.2); rim2.position.set(-2.8, 1.6, -2.2); scene.add(rim2);
  const hemi = new THREE.HemisphereLight(theme === 'dark' ? '#5b5f72' : '#ffffff', '#20222a', theme === 'dark' ? 0.55 : 0.9); scene.add(hemi);
  return {
    key, rim, rim2, hemi,
    /* 플래시몹 색이 차 조명처럼 캐릭터 뒤에서 비친다 */
    tint(hex, amt) { rim.color.set('#8fdcff').lerp(new THREE.Color(hex), amt); rim2.color.set('#ff86b0').lerp(new THREE.Color(hex), amt); },
  };
}

export async function applyEnvironment(renderer, scene, intensity = 0.55) {
  const { RoomEnvironment } = await import('./RoomEnvironment.js');
  const pm = new THREE.PMREMGenerator(renderer);
  scene.environment = pm.fromScene(new RoomEnvironment(renderer), 0.04).texture;
  scene.traverse((o) => { if (o.material && 'envMapIntensity' in o.material) o.material.envMapIntensity = intensity; });
}

export const util = { smooth, easeOutBack, lerp, fract, clamp01 };
