const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'docs', 'presentation');
fs.mkdirSync(OUT, { recursive: true });

const W = 2560;
const H = 1440;
const C = {
  bg: '#F5F7FB', ink: '#162033', muted: '#667085', border: '#CAD3E0', white: '#FFFFFF',
  navy: '#274C77', violet: '#6D4BAE', green: '#287A68', orange: '#C56E2D',
  blue: '#2878B7', rose: '#B24F67', success: '#2D7D5A', line: '#8492A6'
};

const esc = (value) => String(value)
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');

function shell(title, description, content) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <title>${esc(title)}</title><desc>${esc(description)}</desc>
  <defs>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="5" stdDeviation="6" flood-color="#162033" flood-opacity="0.10"/>
    </filter>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L10,5 L0,10 Z" fill="#748197"/>
    </marker>
    <pattern id="grid" width="28" height="28" patternUnits="userSpaceOnUse">
      <circle cx="1" cy="1" r="1" fill="#D9E0EA"/>
    </pattern>
  </defs>
  <rect width="${W}" height="${H}" fill="${C.bg}"/>
  <rect width="${W}" height="${H}" fill="url(#grid)" opacity="0.42"/>
  <g font-family="Malgun Gothic, Noto Sans KR, Arial, sans-serif">${content}</g>
</svg>`;
}

function header(title, subtitle) {
  return `<rect width="${W}" height="118" fill="#FFFFFF"/>
  <line x1="0" y1="117" x2="${W}" y2="117" stroke="#DDE3EC" stroke-width="2"/>
  <text x="64" y="51" font-size="34" font-weight="700" fill="${C.ink}">${esc(title)}</text>
  <text x="64" y="85" font-size="17" fill="${C.muted}">${esc(subtitle)}</text>
  <rect x="2350" y="35" width="146" height="48" rx="24" fill="#EEF3F8"/>
  <text x="2423" y="65" text-anchor="middle" font-size="15" font-weight="700" fill="${C.navy}">GAYADI</text>`;
}

function table({ id, x, y, w, color, note, rows }) {
  const head = 68;
  const rowH = 33;
  const h = head + rows.length * rowH + 10;
  let body = `<g filter="url(#shadow)">
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="8" fill="#FFFFFF" stroke="${C.border}" stroke-width="1.5"/>
    <path d="M${x + 8},${y} H${x + w - 8} Q${x + w},${y} ${x + w},${y + 8} V${y + head} H${x} V${y + 8} Q${x},${y} ${x + 8},${y} Z" fill="${color}"/>
    <text x="${x + 16}" y="${y + 27}" font-size="19" font-weight="700" fill="#FFFFFF">${esc(id)}</text>
    <text x="${x + 16}" y="${y + 50}" font-size="12.5" fill="#FFFFFF" opacity="0.86">${esc(note)}</text>`;
  rows.forEach((row, index) => {
    const top = y + head + index * rowH;
    if (index) body += `<line x1="${x + 10}" y1="${top}" x2="${x + w - 10}" y2="${top}" stroke="#E7EBF1"/>`;
    const keyColor = row[0] === 'PK' ? '#B42318' : row[0] === 'FK' ? '#175CD3' : C.muted;
    body += `<text x="${x + 14}" y="${top + 21}" font-size="11.5" font-weight="700" fill="${keyColor}">${esc(row[0])}</text>
      <text x="${x + 49}" y="${top + 21}" font-size="13" font-weight="500" fill="${C.ink}">${esc(row[1])}</text>
      <text x="${x + w - 13}" y="${top + 21}" text-anchor="end" font-size="11.5" fill="${C.muted}">${esc(row[2])}</text>`;
  });
  return { x, y, w, h, svg: body + '</g>' };
}

function relation(points, options = {}) {
  const color = options.color || C.line;
  const dash = options.dashed ? ' stroke-dasharray="8 7"' : '';
  const p = points.map(([x, y]) => `${x},${y}`).join(' ');
  let out = `<polyline points="${p}" fill="none" stroke="${color}" stroke-width="2" stroke-linejoin="round"${dash}/>`;
  if (options.one) out += `<text x="${options.one[0]}" y="${options.one[1]}" font-size="12" font-weight="700" fill="${color}">1</text>`;
  if (options.many) out += `<text x="${options.many[0]}" y="${options.many[1]}" font-size="12" font-weight="700" fill="${color}">N</text>`;
  if (options.label && options.at) {
    const [x, y] = options.at;
    const width = Math.max(68, options.label.length * 12 + 18);
    out += `<rect x="${x - width / 2}" y="${y - 14}" width="${width}" height="26" rx="13" fill="#FFFFFF" stroke="#D8DFE9"/>
      <text x="${x}" y="${y + 3}" text-anchor="middle" font-size="11.5" font-weight="700" fill="${color}">${esc(options.label)}</text>`;
  }
  return out;
}

function erd() {
  const boxes = {};
  const displayNames = {
    users: 'USERS · 사용자', trips: 'TRIPS · 여행', trip_members: 'TRIP_MEMBERS · 여행 멤버',
    surveys: 'SURVEYS · 설문', survey_responses: 'SURVEY_RESPONSES · 설문 응답',
    trip_plans: 'TRIP_PLANS · 현재 일정', trip_plan_items: 'TRIP_PLAN_ITEMS · 일정 항목',
    trip_routes: 'TRIP_ROUTES · 선택 경로', places: 'PLACES · 장소',
    event_observations: 'EVENT_OBSERVATIONS · 이벤트 관측',
    change_proposals: 'CHANGE_PROPOSALS · 변경 제안'
  };
  const defs = [
    ['users', 55, 170, 335, C.navy, '사용자 및 소셜 로그인 계정', [
      ['PK', 'id', 'UUID'], ['', 'nickname', 'VARCHAR'], ['', 'oauth_provider', 'VARCHAR'], ['', 'oauth_subject', 'VARCHAR'], ['', 'status', 'VARCHAR']]],
    ['trips', 475, 160, 400, C.navy, '여행과 출발 방식의 기준 정보', [
      ['PK', 'id', 'UUID'], ['FK', 'owner_id', 'UUID'], ['', 'departure_mode', 'VARCHAR'], ['', 'departure_at / meeting_at', 'TIMESTAMP'], ['', 'meeting_location', 'JSON'], ['', 'status', 'VARCHAR']]],
    ['trip_members', 55, 505, 415, C.navy, '참가자별 출발지·귀가지를 관리', [
      ['PK', 'id', 'UUID'], ['FK', 'trip_id', 'UUID'], ['FK', 'user_id', 'UUID'], ['', 'role / participation_status', 'VARCHAR'], ['', 'departure_location', 'JSON'], ['', 'return_destination', 'JSON'], ['', 'route_preferences', 'JSON']]],
    ['surveys', 985, 160, 355, C.violet, '성향·만족도 등 범용 설문 정의', [
      ['PK', 'id', 'UUID'], ['', 'survey_type', 'VARCHAR'], ['', 'version', 'VARCHAR'], ['', 'questions', 'JSON'], ['', 'status', 'VARCHAR']]],
    ['survey_responses', 985, 455, 390, C.violet, '응답 원본과 계산 결과', [
      ['PK', 'id', 'UUID'], ['FK', 'survey_id', 'UUID'], ['FK', 'user_id / trip_id', 'UUID'], ['', 'answers', 'JSON'], ['', 'result_code', 'VARCHAR'], ['', 'result_data', 'JSON']]],
    ['trip_plans', 545, 620, 405, C.navy, '여행당 하나의 현재 일정', [
      ['PK', 'id', 'UUID'], ['FK', 'trip_id', 'UUID'], ['FK', 'survey_response_id', 'UUID'], ['', 'revision_no', 'INT'], ['', 'preference_snapshot', 'JSON'], ['', 'status', 'VARCHAR']]],
    ['trip_plan_items', 1040, 850, 430, C.navy, '장소별 방문 순서와 예정 시간', [
      ['PK', 'id', 'UUID'], ['FK', 'plan_id', 'UUID'], ['FK', 'place_id', 'UUID'], ['', 'sequence_no', 'INT'], ['', 'planned_start / end', 'TIMESTAMP'], ['', 'status', 'VARCHAR']]],
    ['trip_routes', 55, 960, 455, C.blue, '선택된 출발·이동·귀가 경로', [
      ['PK', 'id', 'UUID'], ['FK', 'trip_id / member_id', 'UUID'], ['', 'scope / phase', 'VARCHAR'], ['', 'origin / destination', 'JSON'], ['', 'duration / transfer / fare', 'INT'], ['', 'route_data', 'JSON'], ['', 'status / valid_until', 'VARCHAR']]],
    ['places', 1745, 160, 430, C.green, '외부 장소 API를 보완하는 장소 원장', [
      ['PK', 'id', 'UUID'], ['', 'name / category', 'VARCHAR'], ['', 'address', 'VARCHAR'], ['', 'latitude / longitude', 'DECIMAL'], ['', 'source / source_place_id', 'VARCHAR'], ['', 'basic_info', 'JSON']]],
    ['event_observations', 1745, 530, 455, C.orange, '정규화한 날씨·혼잡·교통 관측값', [
      ['PK', 'id', 'UUID'], ['FK', 'place_id', 'UUID'], ['', 'event_type / source', 'VARCHAR'], ['', 'observed_at / valid_to', 'TIMESTAMP'], ['', 'severity', 'VARCHAR'], ['', 'normalized_value', 'JSON']]],
    ['change_proposals', 1600, 960, 610, C.rose, '일정 변경 제안·승인·감사 기록', [
      ['PK', 'id', 'UUID'], ['FK', 'trip_id / plan_id / event_id', 'UUID'], ['', 'base_revision_no', 'INT'], ['', 'status / reason', 'VARCHAR'], ['', 'options / selected_option', 'JSON'], ['', 'before / after_snapshot', 'JSON'], ['', 'decided_by / decided_at', 'UUID / TIME']]],
  ];

  let nodes = '';
  for (const [key, x, y, w, color, note, rows] of defs) {
    boxes[key] = table({ id: displayNames[key], x, y, w, color, note, rows });
    nodes += boxes[key].svg;
  }

  let lines = '';
  lines += relation([[390, 265], [475, 265]], { label: '소유', at: [432, 238], one: [403, 255], many: [458, 255] });
  lines += relation([[225, 413], [225, 505]], { label: '참여', at: [278, 459], one: [237, 430], many: [237, 494] });
  lines += relation([[475, 330], [445, 330], [445, 575], [470, 575]], { one: [460, 319], many: [454, 565] });
  lines += relation([[1160, 403], [1160, 455]], { label: '응답', at: [1210, 430], one: [1172, 418], many: [1172, 448] });
  lines += relation([[390, 330], [430, 330], [430, 435], [960, 435], [960, 570], [985, 570]], { dashed: true, color: C.violet, label: '사용자 응답', at: [710, 417], one: [402, 320], many: [970, 559] });
  lines += relation([[675, 426], [675, 620]], { label: '현재 일정', at: [738, 525], one: [687, 444], many: [687, 608] });
  lines += relation([[985, 635], [950, 635]], { dashed: true, color: C.violet, label: '성향 반영', at: [928, 603], one: [973, 625], many: [960, 653] });
  lines += relation([[950, 760], [1015, 760], [1015, 955], [1040, 955]], { label: '일정 항목', at: [1012, 808], one: [965, 750], many: [1028, 945] });
  lines += relation([[1745, 295], [1570, 295], [1570, 950], [1470, 950]], { dashed: true, color: C.green, label: '장소 참조', at: [1570, 785], one: [1728, 285], many: [1480, 940] });
  lines += relation([[265, 746], [265, 960]], { label: '개인 경로', at: [330, 850], one: [277, 765], many: [277, 948] });
  lines += relation([[545, 760], [510, 760], [510, 1090]], { label: '여행 경로', at: [462, 885], one: [530, 750], many: [520, 1078] });
  lines += relation([[1960, 426], [1960, 530]], { label: '상태 관측', at: [2025, 478], one: [1972, 443], many: [1972, 518] });
  lines += relation([[1975, 806], [1975, 960]], { label: '변경 근거', at: [2042, 880], one: [1987, 822], many: [1987, 948] });
  lines += relation([[950, 800], [1530, 800], [1530, 1110], [1600, 1110]], { label: '일정 변경', at: [1260, 782], one: [965, 790], many: [1585, 1100] });
  lines += relation([[875, 295], [1505, 295], [1505, 1035], [1600, 1035]], { label: '여행 제안', at: [1450, 330], one: [890, 285], many: [1585, 1025] });

  const labels = `<text x="55" y="145" font-size="12" font-weight="700" fill="${C.navy}">핵심 여행 데이터</text>
    <text x="985" y="145" font-size="12" font-weight="700" fill="${C.violet}">설문 · 성향 데이터</text>
    <text x="1745" y="145" font-size="12" font-weight="700" fill="${C.green}">장소 · 실시간 이벤트 데이터</text>
    <g transform="translate(2190,34)"><rect width="150" height="52" rx="8" fill="#F8FAFC" stroke="#D7DEE8"/>
      <line x1="14" y1="18" x2="50" y2="18" stroke="${C.line}" stroke-width="2"/><text x="60" y="22" font-size="11" fill="${C.muted}">물리 FK</text>
      <line x1="14" y1="38" x2="50" y2="38" stroke="${C.green}" stroke-width="2" stroke-dasharray="8 7"/><text x="60" y="42" font-size="11" fill="${C.muted}">논리 참조</text></g>`;

  return shell('GAYADI 데이터 모델 ERD', '여행 서비스의 핵심 데이터 관계',
    header('GAYADI 데이터 모델 · ERD', '핵심 테이블 11개  |  여행 · 설문 · 일정 · 장소 · 이벤트 · 경로') + labels + lines + nodes);
}

function panel(x, y, w, h, color, number, title, subtitle) {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="18" fill="#FFFFFF" fill-opacity="0.78" stroke="#D5DDE8" stroke-width="1.5"/>
    <path d="M${x + 18},${y} H${x + w - 18} Q${x + w},${y} ${x + w},${y + 18} V${y + 78} H${x} V${y + 18} Q${x},${y} ${x + 18},${y} Z" fill="${color}"/>
    <circle cx="${x + 42}" cy="${y + 39}" r="22" fill="#FFFFFF" fill-opacity="0.18"/>
    <text x="${x + 42}" y="${y + 45}" text-anchor="middle" font-size="16" font-weight="700" fill="#FFFFFF">${number}</text>
    <text x="${x + 80}" y="${y + 31}" font-size="23" font-weight="700" fill="#FFFFFF">${esc(title)}</text>
    <text x="${x + 80}" y="${y + 57}" font-size="12.5" fill="#FFFFFF" opacity="0.88">${esc(subtitle)}</text>`;
}

function step(x, y, w, h, text, options = {}) {
  const fill = options.fill || '#FFFFFF';
  const stroke = options.stroke || C.border;
  const color = options.color || C.ink;
  const lines = String(text).split('\n');
  const gap = 25;
  const first = y + h / 2 - ((lines.length - 1) * gap / 2) + 5;
  let texts = '';
  lines.forEach((line, index) => {
    texts += `<text x="${x + w / 2}" y="${first + index * gap}" text-anchor="middle" font-size="${options.size || 16}" font-weight="${options.weight || 600}" fill="${color}">${esc(line)}</text>`;
  });
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="10" fill="${fill}" stroke="${stroke}" stroke-width="1.6"/>${texts}</g>`;
}

function decision(cx, cy, w, h, text, color) {
  return `<g filter="url(#shadow)"><polygon points="${cx},${cy - h / 2} ${cx + w / 2},${cy} ${cx},${cy + h / 2} ${cx - w / 2},${cy}" fill="#FFFFFF" stroke="${color}" stroke-width="2"/>
    <text x="${cx}" y="${cy + 6}" text-anchor="middle" font-size="16" font-weight="700" fill="${C.ink}">${esc(text)}</text></g>`;
}

function flowArrow(points, options = {}) {
  const color = options.color || '#748197';
  const p = points.map(([x, y]) => `${x},${y}`).join(' ');
  let out = `<polyline points="${p}" fill="none" stroke="${color}" stroke-width="2.3" stroke-linejoin="round" marker-end="url(#arrow)"${options.dashed ? ' stroke-dasharray="8 7"' : ''}/>`;
  if (options.label && options.at) {
    const [x, y] = options.at;
    const width = Math.max(62, options.label.length * 12 + 18);
    out += `<rect x="${x - width / 2}" y="${y - 14}" width="${width}" height="26" rx="13" fill="#FFFFFF" stroke="#D7DEE8"/>
      <text x="${x}" y="${y + 3}" text-anchor="middle" font-size="11.5" font-weight="700" fill="${color}">${esc(options.label)}</text>`;
  }
  return out;
}

function serviceFlow() {
  let s = header('GAYADI 서비스 흐름도', '여행 전 · 여행 중 · 여행 후  |  성향 기반 일정과 상황 대응');
  s += panel(50, 145, 780, 1125, C.navy, '01', '여행 전', '성향 파악 · 일정 생성 · 출발 경로');
  s += panel(870, 145, 1060, 1125, C.orange, '02', '여행 중', '실시간 변수 감지 · 대안 제시 · 승인');
  s += panel(1970, 145, 540, 1125, C.green, '03', '여행 후', '귀가 경로 · 여행 종료');

  // 여행 전
  s += step(250, 245, 380, 64, '여행 생성 · 멤버 초대', { stroke: C.navy });
  s += decision(440, 405, 310, 100, '출발 방식?', C.navy);
  s += step(85, 515, 310, 88, '모여서 출발\nGROUP_MEETING', { fill: '#EEF4FA', stroke: C.navy, size: 15 });
  s += step(475, 515, 310, 88, '각자 출발\nINDIVIDUAL', { fill: '#EEF4FA', stroke: C.navy, size: 15 });
  s += step(250, 690, 380, 70, '성향 설문 제출', { stroke: C.violet });
  s += step(220, 825, 440, 82, '장소 후보 조회 · 맞춤 일정 생성', { stroke: C.green });
  s += step(175, 970, 530, 88, '대중교통 출발 경로 추천\n멤버→집결지 또는 멤버→첫 장소', { stroke: C.blue, size: 15 });
  s += step(250, 1120, 380, 70, '여행 준비 완료 · READY', { fill: '#EAF7F0', stroke: C.success, color: '#185C3F' });
  s += flowArrow([[440, 309], [440, 355]]);
  s += flowArrow([[350, 440], [240, 515]], { label: '모여서', at: [270, 473], color: C.navy });
  s += flowArrow([[530, 440], [630, 515]], { label: '각자', at: [610, 473], color: C.navy });
  s += flowArrow([[240, 603], [240, 642], [440, 642], [440, 690]]);
  s += flowArrow([[630, 603], [630, 642], [440, 642], [440, 690]]);
  s += flowArrow([[440, 760], [440, 825]]);
  s += flowArrow([[440, 907], [440, 970]]);
  s += flowArrow([[440, 1058], [440, 1120]]);

  // 여행 중
  s += step(1190, 235, 420, 68, '여행 시작 · 진행 중', { fill: '#FFF4E8', stroke: C.orange });
  s += step(1145, 350, 510, 88, '날씨 · 혼잡 · 교통 상태 확인\n주기 조회 + 짧은 캐시', { stroke: C.orange, size: 15 });
  s += decision(1400, 535, 330, 108, '일정 영향 있음?', C.orange);
  s += step(1180, 655, 440, 78, '대체 장소 · 경로 계산', { stroke: C.blue });
  s += step(1150, 785, 500, 82, '변경 이유 · 시간 차이 알림', { stroke: C.rose });
  s += decision(1400, 965, 340, 108, '사용자 승인?', C.rose);
  s += step(1110, 1090, 580, 92, '미래 일정 항목 수정\nrevision_no 증가 · 변경 이력 저장', { fill: '#EAF7F0', stroke: C.success, color: '#185C3F', size: 15 });
  s += flowArrow([[1400, 303], [1400, 350]]);
  s += flowArrow([[1400, 438], [1400, 481]]);
  s += flowArrow([[1400, 589], [1400, 655]], { label: '예', at: [1436, 620], color: C.orange });
  s += flowArrow([[1235, 535], [1025, 535], [1025, 394], [1145, 394]], { label: '아니오', at: [1067, 510] });
  s += flowArrow([[1400, 733], [1400, 785]]);
  s += flowArrow([[1400, 867], [1400, 911]]);
  s += flowArrow([[1400, 1019], [1400, 1090]], { label: '승인', at: [1444, 1054], color: C.success });
  s += flowArrow([[1230, 965], [995, 965], [995, 394], [1145, 394]], { label: '거절', at: [1034, 940], color: C.rose });
  s += flowArrow([[1110, 1136], [965, 1136], [965, 394], [1145, 394]], { label: '여행 계속', at: [1013, 1110], color: C.success });

  // 단계 연결
  s += flowArrow([[630, 1155], [850, 1155], [850, 269], [1190, 269]], { label: '출발', at: [850, 228], color: C.navy });

  // 여행 후
  s += step(2045, 300, 390, 76, '마지막 일정 완료', { stroke: C.green });
  s += step(2035, 465, 410, 94, '멤버별 귀가 경로 추천\n마지막 장소 → 각자 귀가지', { stroke: C.blue, size: 15 });
  s += step(2045, 650, 390, 76, '귀가 경로 선택', { stroke: C.blue });
  s += step(2045, 830, 390, 80, '여행 완료', { fill: '#EAF7F0', stroke: C.success, color: '#185C3F' });
  s += flowArrow([[1930, 1136], [1950, 1136], [1950, 338], [2045, 338]], { label: '마지막 일정', at: [1994, 294], color: C.green });
  s += flowArrow([[2240, 376], [2240, 465]]);
  s += flowArrow([[2240, 559], [2240, 650]]);
  s += flowArrow([[2240, 726], [2240, 830]]);

  // 기반 시스템
  s += `<rect x="50" y="1295" width="2460" height="100" rx="16" fill="#162033"/>
    <text x="82" y="1327" font-size="12" font-weight="700" fill="#AFC2D8">데이터 및 외부 연동</text>
    <text x="82" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">핵심 DB</text>
    <text x="280" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">장소 DB</text>
    <text x="485" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">이벤트 DB</text>
    <text x="690" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">Redis 캐시</text>
    <text x="930" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">관광 API</text>
    <text x="1115" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">날씨 · 혼잡 API</text>
    <text x="1410" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">대중교통 · 경로 API</text>
    <text x="1760" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">푸시 알림 / SSE</text>
    <text x="2110" y="1370" font-size="16" font-weight="600" fill="#FFFFFF">로그 · 지표</text>`;

  return shell('GAYADI 서비스 흐름도', '여행 전, 여행 중, 여행 후의 전체 사용자 및 시스템 흐름', s);
}

function architectureBox(x, y, w, h, title, lines, options = {}) {
  const stroke = options.stroke || C.border;
  const fill = options.fill || '#FFFFFF';
  const dash = options.dashed ? ' stroke-dasharray="9 7"' : '';
  let text = `<text x="${x + 18}" y="${y + 30}" font-size="17" font-weight="700" fill="${C.ink}">${esc(title)}</text>`;
  lines.forEach((line, index) => {
    text += `<text x="${x + 18}" y="${y + 58 + index * 23}" font-size="13.5" fill="${C.muted}">${esc(line)}</text>`;
  });
  if (options.badge) {
    const badgeWidth = options.badge.length * 13 + 24;
    text += `<rect x="${x + w - badgeWidth - 14}" y="${y + 13}" width="${badgeWidth}" height="26" rx="13" fill="${options.badgeFill || '#EEF4FA'}"/>
      <text x="${x + w - badgeWidth / 2 - 14}" y="${y + 31}" text-anchor="middle" font-size="11.5" font-weight="700" fill="${options.badgeColor || C.navy}">${esc(options.badge)}</text>`;
  }
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="10" fill="${fill}" stroke="${stroke}" stroke-width="1.7"${dash}/>${text}</g>`;
}

function serviceArchitecture() {
  let s = header('GAYADI 서비스 아키텍처', '현재 실행되는 Spring MVP와 운영 연동 지점을 한눈에 구분');
  s += `<g transform="translate(1990,35)">
    <line x1="0" y1="12" x2="38" y2="12" stroke="${C.navy}" stroke-width="2.3"/>
    <text x="48" y="17" font-size="11.5" fill="${C.muted}">현재 구현</text>
    <line x1="0" y1="38" x2="38" y2="38" stroke="${C.orange}" stroke-width="2.3" stroke-dasharray="8 6"/>
    <text x="48" y="43" font-size="11.5" fill="${C.muted}">운영 연동 예정</text></g>`;

  // 사용자 영역
  s += `<rect x="50" y="160" width="330" height="1090" rx="18" fill="#FFFFFF" fill-opacity="0.80" stroke="#D5DDE8" stroke-width="1.5"/>
    <rect x="50" y="160" width="330" height="78" rx="18" fill="${C.navy}"/>
    <rect x="50" y="218" width="330" height="20" fill="${C.navy}"/>
    <text x="78" y="193" font-size="23" font-weight="700" fill="#FFFFFF">사용자 채널</text>
    <text x="78" y="219" font-size="12.5" fill="#FFFFFF" opacity="0.86">Android 앱 · HTTPS JSON API</text>`;
  s += architectureBox(90, 290, 250, 128, 'Android 앱', ['여행 생성과 멤버 초대', '일정·경로 확인과 승인'], { stroke: C.navy, fill: '#EEF4FA', badge: '사용자' });
  s += architectureBox(90, 500, 250, 110, '여행 전', ['성향 설문', '맞춤 일정·출발 경로'], { stroke: C.violet });
  s += architectureBox(90, 665, 250, 110, '여행 중', ['상황 알림', '변경안 승인·거절'], { stroke: C.orange });
  s += architectureBox(90, 830, 250, 110, '여행 후', ['멤버별 귀가 경로', '여행 완료'], { stroke: C.green });

  // Spring Boot 애플리케이션
  s += `<rect x="430" y="160" width="1370" height="1090" rx="18" fill="#FFFFFF" fill-opacity="0.80" stroke="#B8C8DC" stroke-width="1.8"/>
    <rect x="430" y="160" width="1370" height="78" rx="18" fill="${C.navy}"/>
    <rect x="430" y="218" width="1370" height="20" fill="${C.navy}"/>
    <text x="462" y="193" font-size="23" font-weight="700" fill="#FFFFFF">Spring Boot 모듈러 모놀리스</text>
    <text x="462" y="219" font-size="12.5" fill="#FFFFFF" opacity="0.86">하나의 서버 안에서 업무 책임만 모듈로 분리</text>`;
  s += architectureBox(500, 275, 1230, 88, 'API 계층', ['Controller · 입력값 검증 · 공통 오류 응답 · Actuator 상태 확인'], { stroke: C.navy, fill: '#F4F7FB', badge: '현재 구현' });

  s += architectureBox(500, 420, 370, 112, '여행 준비 유스케이스', ['여행·멤버 → 그룹 성향', '일정 생성 → 출발 경로'], { stroke: C.violet, fill: '#FAF7FF' });
  s += architectureBox(930, 420, 370, 112, '여행 중 대응 유스케이스', ['이벤트 영향 판단', '대안 생성 → 승인 반영'], { stroke: C.orange, fill: '#FFF8F2' });
  s += architectureBox(1360, 420, 370, 112, '귀가 유스케이스', ['마지막 장소 확인', '멤버별 귀가 경로'], { stroke: C.green, fill: '#F2FAF7' });

  const moduleY1 = 625;
  const moduleY2 = 770;
  s += architectureBox(500, moduleY1, 280, 100, '인증 · 사용자', ['개발 사용자', 'OAuth 교체 경계'], { stroke: C.navy });
  s += architectureBox(810, moduleY1, 280, 100, '여행 · 멤버', ['출발 방식', '출발지·귀가지'], { stroke: C.navy });
  s += architectureBox(1120, moduleY1, 280, 100, '설문 · 성향', ['범용 설문', '그룹 성향 집계'], { stroke: C.violet });
  s += architectureBox(1430, moduleY1, 280, 100, '일정 · 변경', ['현재 일정', 'revision·승인 이력'], { stroke: C.rose });
  s += architectureBox(500, moduleY2, 280, 100, '장소', ['장소 원장', '성향별 후보 조회'], { stroke: C.green });
  s += architectureBox(810, moduleY2, 280, 100, '이벤트', ['날씨·혼잡·교통', '영향 판단'], { stroke: C.orange });
  s += architectureBox(1120, moduleY2, 280, 100, '경로', ['출발·이동·귀가', 'RouteProvider'], { stroke: C.blue });
  s += architectureBox(1430, moduleY2, 280, 100, '공통', ['오류 형식·JSON', '트랜잭션·검증'], { stroke: C.border });

  s += architectureBox(500, 955, 370, 125, '로컬 어댑터', ['H2 기준 장소 데이터', '결정적 대중교통 경로 스텁'], { stroke: C.success, fill: '#F2FAF7', badge: '바로 실행' });
  s += architectureBox(930, 955, 370, 125, '외부 API 포트', ['장소 · 이벤트 · 경로 공급자를', '구현 교체만으로 연결'], { stroke: C.blue, fill: '#F4F9FD', badge: '교체 가능' });
  s += architectureBox(1360, 955, 370, 125, '알림 포트', ['변경 제안 전달', '로그 → FCM / SSE 전환'], { stroke: C.orange, dashed: true, badge: '연동 예정', badgeFill: '#FFF3E8', badgeColor: C.orange });

  // 외부 연동
  s += `<rect x="1850" y="160" width="660" height="650" rx="18" fill="#FFFFFF" fill-opacity="0.80" stroke="#D5DDE8" stroke-width="1.5"/>
    <rect x="1850" y="160" width="660" height="78" rx="18" fill="${C.orange}"/>
    <rect x="1850" y="218" width="660" height="20" fill="${C.orange}"/>
    <text x="1882" y="193" font-size="23" font-weight="700" fill="#FFFFFF">외부 서비스 연동</text>
    <text x="1882" y="219" font-size="12.5" fill="#FFFFFF" opacity="0.88">운영 환경에서 공급자별 어댑터로 연결</text>`;
  s += architectureBox(1900, 285, 560, 82, 'OAuth / OIDC', ['로그인과 토큰 검증'], { stroke: C.orange, dashed: true, badge: '예정', badgeFill: '#FFF3E8', badgeColor: C.orange });
  s += architectureBox(1900, 395, 560, 82, '관광 · 지도 API', ['장소 검색과 상세 정보 동기화'], { stroke: C.orange, dashed: true, badge: '예정', badgeFill: '#FFF3E8', badgeColor: C.orange });
  s += architectureBox(1900, 505, 560, 82, '날씨 · 혼잡 · 교통 API', ['실시간 관측값 수집과 정규화'], { stroke: C.orange, dashed: true, badge: '예정', badgeFill: '#FFF3E8', badgeColor: C.orange });
  s += architectureBox(1900, 615, 560, 82, '대중교통 경로 API · FCM/SSE', ['실제 경로 후보와 변경 알림'], { stroke: C.orange, dashed: true, badge: '예정', badgeFill: '#FFF3E8', badgeColor: C.orange });

  // 데이터·운영
  s += `<rect x="1850" y="850" width="660" height="400" rx="18" fill="#FFFFFF" fill-opacity="0.80" stroke="#D5DDE8" stroke-width="1.5"/>
    <rect x="1850" y="850" width="660" height="72" rx="18" fill="${C.green}"/>
    <rect x="1850" y="902" width="660" height="20" fill="${C.green}"/>
    <text x="1882" y="893" font-size="23" font-weight="700" fill="#FFFFFF">데이터 · 운영</text>`;
  s += architectureBox(1900, 965, 255, 112, 'H2 / PostgreSQL', ['로컬 / 운영 DB', 'Flyway 11개 테이블'], { stroke: C.green, fill: '#F2FAF7', badge: '구현' });
  s += architectureBox(2190, 965, 270, 112, 'Redis', ['경로 후보 TTL', '외부 API 짧은 캐시'], { stroke: C.orange, dashed: true, badge: '예정', badgeFill: '#FFF3E8', badgeColor: C.orange });
  s += architectureBox(1900, 1110, 560, 92, '운영 확인', ['Actuator Health · 로그/지표 · GitHub Actions 빌드/테스트'], { stroke: C.navy, fill: '#F4F7FB', badge: '구현' });

  // 주요 연결선
  s += flowArrow([[340, 354], [420, 354], [420, 319], [500, 319]], { label: 'HTTPS', at: [420, 330], color: C.navy });
  s += flowArrow([[1115, 363], [1115, 410]], { color: C.navy });
  s += `<line x1="685" y1="565" x2="1545" y2="565" stroke="${C.line}" stroke-width="2"/>
    <line x1="685" y1="532" x2="685" y2="565" stroke="${C.line}" stroke-width="2"/>
    <line x1="1115" y1="532" x2="1115" y2="565" stroke="${C.line}" stroke-width="2"/>
    <line x1="1545" y1="532" x2="1545" y2="565" stroke="${C.line}" stroke-width="2"/>
    <line x1="1115" y1="565" x2="1115" y2="610" stroke="${C.line}" stroke-width="2" marker-end="url(#arrow)"/>`;
  s += flowArrow([[1115, 870], [1115, 930]], { label: '어댑터', at: [1160, 910], color: C.blue });
  s += flowArrow([[1730, 1018], [1815, 1018], [1815, 655], [1900, 655]], { label: '운영 연동', at: [1815, 830], color: C.orange, dashed: true });
  s += flowArrow([[1710, 820], [1785, 820], [1785, 1020], [1900, 1020]], { label: '저장', at: [1785, 940], color: C.green });

  return shell('GAYADI 서비스 아키텍처', '현재 구현된 Spring 모듈과 향후 외부 연동 경계', s);
}

async function render(name, svg) {
  const svgPath = path.join(OUT, `${name}.svg`);
  const pngPath = path.join(OUT, `${name}.png`);
  fs.writeFileSync(svgPath, svg, 'utf8');
  const chrome = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const target = `file:///${svgPath.replaceAll('\\', '/')}`;
  execFileSync(chrome, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
    `--window-size=${W},${H}`, `--screenshot=${pngPath}`, target
  ], { stdio: 'ignore' });
  return { svgPath, pngPath };
}

(async () => {
  const output = [];
  output.push(await render('gayadi-erd-presentation', erd()));
  output.push(await render('gayadi-service-architecture-presentation', serviceArchitecture()));
  output.push(await render('gayadi-service-flow-presentation', serviceFlow()));
  fs.copyFileSync(
    path.join(OUT, 'gayadi-service-architecture-presentation.png'),
    path.join(ROOT, 'docs', 'architecture', 'travel-realtime-architecture.png')
  );
  output.flatMap((item) => [item.svgPath, item.pngPath]).forEach((file) => console.log(file));
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
