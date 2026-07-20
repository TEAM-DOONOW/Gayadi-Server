const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'docs', 'presentation');
fs.mkdirSync(OUT, { recursive: true });

const W = 2560;
const H = 1440;
const C = {
  bg: '#F8FAFC', ink: '#0F172A', muted: '#475569', border: '#94A3B8', white: '#FFFFFF',
  navy: '#1E3A8A', violet: '#7C3AED', green: '#059669', orange: '#EA580C',
  blue: '#2563EB', rose: '#DB2777', success: '#059669', line: '#64748B',
  cyan: '#0F766E', gold: '#D97706'
};

const esc = (value) => String(value)
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');

function shell(title, description, content) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <title>${esc(title)}</title><desc>${esc(description)}</desc>
  <defs>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="8" stdDeviation="9" flood-color="#071A33" flood-opacity="0.18"/>
    </filter>
    <filter id="softShadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="4" stdDeviation="5" flood-color="#071A33" flood-opacity="0.12"/>
    </filter>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L10,5 L0,10 Z" fill="#52627A"/>
    </marker>
    <linearGradient id="pageBackground" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#DEE7F2"/><stop offset="0.52" stop-color="#F4F7FB"/><stop offset="1" stop-color="#E8EEF6"/>
    </linearGradient>
    <linearGradient id="topHeader" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#071A33"/><stop offset="0.56" stop-color="#123C69"/><stop offset="1" stop-color="#165A77"/>
    </linearGradient>
    <linearGradient id="bottomBand" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#071A33"/><stop offset="0.7" stop-color="#102E50"/><stop offset="1" stop-color="#12485C"/>
    </linearGradient>
    <pattern id="grid" width="28" height="28" patternUnits="userSpaceOnUse">
      <circle cx="1" cy="1" r="1.15" fill="#AAB7C8"/>
    </pattern>
  </defs>
  <rect width="${W}" height="${H}" fill="#FFFFFF"/>
  <rect width="${W}" height="${H}" fill="url(#grid)" opacity="0.08"/>
  <g font-family="Malgun Gothic, Noto Sans KR, Arial, sans-serif">${content}</g>
</svg>`;
}

function header(title, subtitle, rightText = '') {
  const right = rightText
    ? `\n    <text x="2464" y="72" text-anchor="end" font-size="14" font-weight="600" fill="#CBD5E1">${esc(rightText)}</text>`
    : '';
  return `<g filter="url(#softShadow)">
    <rect x="64" y="28" width="2432" height="80" rx="10" fill="#0F172A"/>
    <text x="94" y="62" font-size="29" font-weight="700" fill="#FFFFFF">${esc(title)}</text>
    <text x="94" y="88" font-size="14.5" fill="#CBD5E1">${esc(subtitle)}</text>${right}
  </g>`;
}

function table({ id, x, y, w, color, note, rows }) {
  const head = 68;
  const rowH = 33;
  const h = head + rows.length * rowH + 10;
  let body = `<g filter="url(#shadow)">
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="#FFFFFF" stroke="${color}" stroke-width="2"/>
    <path d="M${x + 14},${y} H${x + w - 14} Q${x + w},${y} ${x + w},${y + 14} V${y + head} H${x} V${y + 14} Q${x},${y} ${x + 14},${y} Z" fill="${color}"/>
    <rect x="${x + 16}" y="${y + 12}" width="38" height="4" rx="2" fill="#FFFFFF" opacity="0.72"/>
    <text x="${x + 16}" y="${y + 27}" font-size="19" font-weight="700" fill="#FFFFFF">${esc(id)}</text>
    <text x="${x + 16}" y="${y + 50}" font-size="12.5" fill="#FFFFFF" opacity="0.92">${esc(note)}</text>`;
  rows.forEach((row, index) => {
    const top = y + head + index * rowH;
    if (index % 2 === 1) body += `<rect x="${x + 2}" y="${top}" width="${w - 4}" height="${rowH}" fill="#EDF2F7"/>`;
    if (index) body += `<line x1="${x + 10}" y1="${top}" x2="${x + w - 10}" y2="${top}" stroke="#D6DEE8"/>`;
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
  let out = `<polyline points="${p}" fill="none" stroke="#FFFFFF" stroke-opacity="0.92" stroke-width="5.4" stroke-linejoin="round"/>
    <polyline points="${p}" fill="none" stroke="${color}" stroke-width="2.7" stroke-linejoin="round"${dash}/>`;
  if (options.one) out += `<text x="${options.one[0]}" y="${options.one[1]}" font-size="12" font-weight="700" fill="${color}">1</text>`;
  if (options.many) out += `<text x="${options.many[0]}" y="${options.many[1]}" font-size="12" font-weight="700" fill="${color}">N</text>`;
  if (options.label && options.at) {
    const [x, y] = options.at;
    const width = Math.max(68, options.label.length * 12 + 18);
    out += `<rect x="${x - width / 2}" y="${y - 14}" width="${width}" height="26" rx="13" fill="#FFFFFF" stroke="${color}" stroke-width="1.3"/>
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
    ['surveys', 985, 160, 355, C.violet, '성향·만족도 설문 문항과 버전', [
      ['PK', 'id', 'UUID'], ['', 'survey_type', 'VARCHAR'], ['', 'version', 'VARCHAR'], ['', 'questions', 'JSON'], ['', 'status', 'VARCHAR']]],
    ['survey_responses', 985, 455, 390, C.violet, '응답 원본과 계산 결과', [
      ['PK', 'id', 'UUID'], ['FK', 'survey_id', 'UUID'], ['FK', 'user_id / trip_id', 'UUID'], ['', 'answers', 'JSON'], ['', 'result_code', 'VARCHAR'], ['', 'result_data', 'JSON']]],
    ['trip_plans', 545, 620, 405, C.navy, '여행당 하나의 현재 일정', [
      ['PK', 'id', 'UUID'], ['FK', 'trip_id', 'UUID'], ['FK', 'survey_response_id', 'UUID'], ['', 'revision_no', 'INT'], ['', 'preference_snapshot', 'JSON'], ['', 'status', 'VARCHAR']]],
    ['trip_plan_items', 1040, 850, 430, C.navy, '장소별 방문 순서와 예정 시간', [
      ['PK', 'id', 'UUID'], ['FK', 'plan_id', 'UUID'], ['FK', 'place_id', 'UUID'], ['', 'sequence_no', 'INT'], ['', 'planned_start / end', 'TIMESTAMP'], ['', 'status', 'VARCHAR']]],
    ['trip_routes', 55, 960, 455, C.blue, '선택된 출발·이동·귀가 경로', [
      ['PK', 'id', 'UUID'], ['FK', 'trip_id / member_id', 'UUID'], ['', 'scope / phase', 'VARCHAR'], ['', 'origin / destination', 'JSON'], ['', 'duration / transfer / fare', 'INT'], ['', 'route_data', 'JSON'], ['', 'status / valid_until', 'VARCHAR']]],
    ['places', 1745, 160, 430, C.green, '서비스에서 사용하는 장소 정보', [
      ['PK', 'id', 'UUID'], ['', 'name / category', 'VARCHAR'], ['', 'address', 'VARCHAR'], ['', 'latitude / longitude', 'DECIMAL'], ['', 'source / source_place_id', 'VARCHAR'], ['', 'basic_info', 'JSON']]],
    ['event_observations', 1745, 530, 455, C.orange, '수집한 날씨·혼잡·교통 상태', [
      ['PK', 'id', 'UUID'], ['FK', 'place_id', 'UUID'], ['', 'event_type / source', 'VARCHAR'], ['', 'observed_at / valid_to', 'TIMESTAMP'], ['', 'severity', 'VARCHAR'], ['', 'normalized_value', 'JSON']]],
    ['change_proposals', 1600, 960, 610, C.rose, '일정 변경 제안과 사용자 선택 결과', [
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

  const labels = `<g><rect x="200" y="127" width="170" height="25" rx="12.5" fill="${C.navy}"/><text x="285" y="144" text-anchor="middle" font-size="11.5" font-weight="700" fill="#FFFFFF">핵심 여행 데이터</text></g>
    <g><rect x="1130" y="127" width="174" height="25" rx="12.5" fill="${C.violet}"/><text x="1217" y="144" text-anchor="middle" font-size="11.5" font-weight="700" fill="#FFFFFF">설문 · 성향 데이터</text></g>
    <g><rect x="1890" y="127" width="232" height="25" rx="12.5" fill="${C.green}"/><text x="2006" y="144" text-anchor="middle" font-size="11.5" font-weight="700" fill="#FFFFFF">장소 · 실시간 이벤트 데이터</text></g>
    <g transform="translate(2158,35)" filter="url(#softShadow)"><rect width="166" height="52" rx="12" fill="#FFFFFF" fill-opacity="0.94" stroke="#8392A7" stroke-width="1.4"/>
      <line x1="14" y1="18" x2="50" y2="18" stroke="${C.line}" stroke-width="2.5"/><text x="60" y="22" font-size="11" font-weight="700" fill="${C.ink}">물리 FK</text>
      <line x1="14" y1="38" x2="50" y2="38" stroke="${C.green}" stroke-width="2.5" stroke-dasharray="8 7"/><text x="60" y="42" font-size="11" font-weight="700" fill="${C.ink}">논리 참조</text></g>`;

  return shell('GAYADI 데이터 모델 ERD', '여행 서비스의 핵심 데이터 관계',
    header('GAYADI 데이터 모델 · ERD', '핵심 테이블 11개  |  여행 · 설문 · 일정 · 장소 · 이벤트 · 경로') + labels
      + `<g transform="translate(145 0)">${lines}${nodes}</g>`);
}

function panel(x, y, w, h, color, number, title, subtitle) {
  return `<g filter="url(#softShadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="22" fill="#FFFFFF" stroke="${color}" stroke-width="2.4"/>
    <rect x="${x + 2}" y="${y + 78}" width="${w - 4}" height="${h - 80}" rx="20" fill="${color}" fill-opacity="0.045"/>
    <path d="M${x + 18},${y} H${x + w - 18} Q${x + w},${y} ${x + w},${y + 18} V${y + 78} H${x} V${y + 18} Q${x},${y} ${x + 18},${y} Z" fill="${color}"/>
    <circle cx="${x + 42}" cy="${y + 39}" r="23" fill="#FFFFFF" fill-opacity="0.18" stroke="#FFFFFF" stroke-opacity="0.62"/>
    <text x="${x + 42}" y="${y + 45}" text-anchor="middle" font-size="16" font-weight="700" fill="#FFFFFF">${number}</text>
    <text x="${x + 80}" y="${y + 31}" font-size="23" font-weight="700" fill="#FFFFFF">${esc(title)}</text>
    <text x="${x + 80}" y="${y + 57}" font-size="12.5" fill="#FFFFFF" opacity="0.94">${esc(subtitle)}</text></g>`;
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
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="${fill}" stroke="${stroke}" stroke-width="2.2"/>
    <rect x="${x + 10}" y="${y + 13}" width="7" height="${Math.max(18, h - 26)}" rx="3.5" fill="${stroke}"/>${texts}</g>`;
}

function decision(cx, cy, w, h, text, color) {
  return `<g filter="url(#shadow)"><polygon points="${cx},${cy - h / 2} ${cx + w / 2},${cy} ${cx},${cy + h / 2} ${cx - w / 2},${cy}" fill="${color}" fill-opacity="0.12" stroke="${color}" stroke-width="3"/>
    <polygon points="${cx},${cy - h / 2 + 10} ${cx + w / 2 - 18},${cy} ${cx},${cy + h / 2 - 10} ${cx - w / 2 + 18},${cy}" fill="#FFFFFF" fill-opacity="0.75"/>
    <text x="${cx}" y="${cy + 6}" text-anchor="middle" font-size="16" font-weight="700" fill="${C.ink}">${esc(text)}</text></g>`;
}

function flowArrow(points, options = {}) {
  const color = options.color || '#748197';
  const p = points.map(([x, y]) => `${x},${y}`).join(' ');
  let out = `<polyline points="${p}" fill="none" stroke="#FFFFFF" stroke-opacity="0.85" stroke-width="6" stroke-linejoin="round"/>
    <polyline points="${p}" fill="none" stroke="${color}" stroke-width="3" stroke-linejoin="round" marker-end="url(#arrow)"${options.dashed ? ' stroke-dasharray="9 7"' : ''}/>`;
  if (options.label && options.at) {
    const [x, y] = options.at;
    const width = Math.max(62, options.label.length * 12 + 18);
    out += `<rect x="${x - width / 2}" y="${y - 14}" width="${width}" height="26" rx="13" fill="#FFFFFF" stroke="${color}" stroke-width="1.4"/>
      <text x="${x}" y="${y + 3}" text-anchor="middle" font-size="11.5" font-weight="700" fill="${color}">${esc(options.label)}</text>`;
  }
  return out;
}

function serviceFlow() {
  let s = header('GAYADI 서비스 흐름도', '성향 기반 일정 생성부터 실시간 변경 대응과 귀가까지', '여행 전  →  여행 중  →  여행 후');
  s += panel(50, 145, 780, 1125, C.blue, '01', '여행 전', '성향 파악 · 일정 생성 · 출발 경로');
  s += panel(870, 145, 1060, 1125, C.violet, '02', '여행 중', '실시간 변수 감지 · 대안 제시 · 승인');
  s += panel(1970, 145, 540, 1125, C.green, '03', '여행 후', '귀가 경로 · 여행 종료');

  // 여행 전
  s += step(250, 245, 380, 64, '여행 생성 · 멤버 초대', { stroke: C.blue });
  s += decision(440, 405, 310, 100, '출발 방식?', C.blue);
  s += step(85, 515, 310, 88, '모여서 출발', { fill: '#EFF6FF', stroke: C.blue, size: 15 });
  s += step(475, 515, 310, 88, '각자 출발', { fill: '#EFF6FF', stroke: C.blue, size: 15 });
  s += step(250, 690, 380, 70, '성향 설문 제출', { stroke: C.violet });
  s += step(220, 825, 440, 82, '장소 후보 조회 · 맞춤 일정 생성', { stroke: C.green });
  s += step(175, 970, 530, 88, '대중교통 출발 경로 추천\n멤버→집결지 또는 멤버→첫 장소', { stroke: C.blue, size: 15 });
  s += step(250, 1120, 380, 70, '여행 준비 완료', { fill: '#DDF3E9', stroke: C.success, color: '#075A3E' });
  s += flowArrow([[440, 309], [440, 355]]);
  s += flowArrow([[350, 440], [240, 515]], { label: '모여서', at: [270, 473], color: C.blue });
  s += flowArrow([[530, 440], [630, 515]], { label: '각자', at: [610, 473], color: C.blue });
  s += flowArrow([[240, 603], [240, 642], [440, 642], [440, 690]]);
  s += flowArrow([[630, 603], [630, 642], [440, 642], [440, 690]]);
  s += flowArrow([[440, 760], [440, 825]]);
  s += flowArrow([[440, 907], [440, 970]]);
  s += flowArrow([[440, 1058], [440, 1120]]);

  // 여행 중
  s += step(1190, 235, 420, 68, '여행 시작 · 진행 중', { fill: '#F5F3FF', stroke: C.violet });
  s += step(1145, 350, 510, 88, '날씨 · 혼잡 · 교통 상태 확인\n주기적으로 최신 정보 확인', { stroke: C.orange, size: 15 });
  s += decision(1400, 535, 330, 108, '일정 영향 있음?', C.orange);
  s += step(1180, 655, 440, 78, '대체 장소 · 경로 계산', { stroke: C.blue });
  s += step(1150, 785, 500, 82, '변경 이유 · 시간 차이 알림', { stroke: C.rose });
  s += decision(1400, 965, 340, 108, '사용자 승인?', C.rose);
  s += step(1110, 1090, 580, 92, '남은 일정에 새 코스 반영\n이전 일정과 변경 이력 보관', { fill: '#DDF3E9', stroke: C.success, color: '#075A3E', size: 15 });
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
  s += flowArrow([[630, 1155], [850, 1155], [850, 269], [1190, 269]], { label: '출발', at: [850, 228], color: C.blue });

  // 여행 후
  s += step(2045, 300, 390, 76, '마지막 일정 완료', { stroke: C.green });
  s += step(2035, 465, 410, 94, '멤버별 귀가 경로 추천\n마지막 장소 → 각자 귀가지', { stroke: C.blue, size: 15 });
  s += step(2045, 650, 390, 76, '귀가 경로 선택', { stroke: C.blue });
  s += step(2045, 830, 390, 80, '여행 완료', { fill: '#DDF3E9', stroke: C.success, color: '#075A3E' });
  s += flowArrow([[1930, 1136], [1950, 1136], [1950, 338], [2045, 338]], { label: '마지막 일정', at: [1994, 294], color: C.green });
  s += flowArrow([[2240, 376], [2240, 465]]);
  s += flowArrow([[2240, 559], [2240, 650]]);
  s += flowArrow([[2240, 726], [2240, 830]]);

  // 기반 시스템
  s += `<g filter="url(#softShadow)"><rect x="50" y="1295" width="2460" height="100" rx="14" fill="#ECFDF5" stroke="${C.green}" stroke-width="2"/>
    <path d="M64,1295 H2496 Q2510,1295 2510,1309 V1334 H50 V1309 Q50,1295 64,1295 Z" fill="${C.green}"/>
    <text x="82" y="1322" font-size="15" font-weight="700" fill="#FFFFFF">데이터 · 외부 연동</text>
    <text x="82" y="1370" font-size="16" font-weight="600" fill="#065F46">핵심 DB</text>
    <text x="280" y="1370" font-size="16" font-weight="600" fill="#065F46">장소 DB</text>
    <text x="485" y="1370" font-size="16" font-weight="600" fill="#065F46">이벤트 DB</text>
    <text x="690" y="1370" font-size="16" font-weight="600" fill="#475569">Redis 캐시</text>
    <text x="930" y="1370" font-size="16" font-weight="600" fill="#9A3412">관광 API</text>
    <text x="1115" y="1370" font-size="16" font-weight="600" fill="#9A3412">날씨 · 혼잡 API</text>
    <text x="1410" y="1370" font-size="16" font-weight="600" fill="#9A3412">대중교통 · 경로 API</text>
    <text x="1760" y="1370" font-size="16" font-weight="600" fill="#475569">푸시 알림 / SSE</text>
    <text x="2110" y="1370" font-size="16" font-weight="600" fill="#475569">로그 · 지표</text></g>`;

  return shell('GAYADI 서비스 흐름도', '여행 전, 여행 중, 여행 후의 전체 사용자 및 시스템 흐름', s);
}

function architectureBox(x, y, w, h, title, lines, options = {}) {
  const stroke = options.stroke || C.border;
  const fill = options.fill || '#FFFFFF';
  const dash = options.dashed ? ' stroke-dasharray="9 7"' : '';
  let text = `<rect x="${x + 10}" y="${y + 13}" width="7" height="${Math.max(22, h - 26)}" rx="3.5" fill="${stroke}"/>
    <text x="${x + 29}" y="${y + 31}" font-size="17" font-weight="700" fill="${C.ink}">${esc(title)}</text>`;
  lines.forEach((line, index) => {
    text += `<text x="${x + 29}" y="${y + 59 + index * 23}" font-size="13.5" font-weight="500" fill="${C.muted}">${esc(line)}</text>`;
  });
  if (options.badge) {
    const badgeWidth = options.badge.length * 13 + 24;
    text += `<rect x="${x + w - badgeWidth - 14}" y="${y + 13}" width="${badgeWidth}" height="26" rx="13" fill="${options.badgeFill || stroke}"/>
      <text x="${x + w - badgeWidth / 2 - 14}" y="${y + 31}" text-anchor="middle" font-size="11.5" font-weight="700" fill="${options.badgeColor || '#FFFFFF'}">${esc(options.badge)}</text>`;
  }
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="${fill}" stroke="${stroke}" stroke-width="2.1"${dash}/>${text}</g>`;
}

function legacyServiceArchitecture() {
  let s = header('GAYADI 서비스 아키텍처', '현재 실행되는 Spring MVP와 운영 연동 지점을 한눈에 구분');
  s += `<g transform="translate(1990,35)">
    <line x1="0" y1="12" x2="38" y2="12" stroke="${C.cyan}" stroke-width="3"/>
    <text x="48" y="17" font-size="11.5" font-weight="700" fill="#FFFFFF">현재 구현</text>
    <line x1="0" y1="38" x2="38" y2="38" stroke="#FF935C" stroke-width="3" stroke-dasharray="8 6"/>
    <text x="48" y="43" font-size="11.5" font-weight="700" fill="#FFFFFF">운영 연동 예정</text></g>`;

  // 사용자 영역
  s += `<g filter="url(#softShadow)"><rect x="50" y="160" width="330" height="1090" rx="22" fill="#FFFFFF" stroke="${C.navy}" stroke-width="2.4"/>
    <rect x="52" y="238" width="326" height="1010" rx="20" fill="${C.navy}" fill-opacity="0.045"/>
    <rect x="50" y="160" width="330" height="78" rx="22" fill="${C.navy}"/>
    <rect x="50" y="218" width="330" height="20" fill="${C.navy}"/>
    <text x="78" y="193" font-size="23" font-weight="700" fill="#FFFFFF">사용자 채널</text>
    <text x="78" y="219" font-size="12.5" fill="#FFFFFF" opacity="0.94">Android 앱 · HTTPS JSON API</text></g>`;
  s += architectureBox(90, 290, 250, 128, 'Android 앱', ['여행 생성과 멤버 초대', '일정·경로 확인과 승인'], { stroke: C.navy, fill: '#DCEAF7', badge: '사용자' });
  s += architectureBox(90, 500, 250, 110, '여행 전', ['성향 설문', '맞춤 일정·출발 경로'], { stroke: C.violet });
  s += architectureBox(90, 665, 250, 110, '여행 중', ['상황 알림', '변경안 승인·거절'], { stroke: C.orange });
  s += architectureBox(90, 830, 250, 110, '여행 후', ['멤버별 귀가 경로', '여행 완료'], { stroke: C.green });

  // Spring Boot 애플리케이션
  s += `<g filter="url(#softShadow)"><rect x="430" y="160" width="1370" height="1090" rx="22" fill="#FFFFFF" stroke="${C.navy}" stroke-width="2.4"/>
    <rect x="432" y="238" width="1366" height="1010" rx="20" fill="${C.navy}" fill-opacity="0.035"/>
    <rect x="430" y="160" width="1370" height="78" rx="22" fill="${C.navy}"/>
    <rect x="430" y="218" width="1370" height="20" fill="${C.navy}"/>
    <text x="462" y="193" font-size="23" font-weight="700" fill="#FFFFFF">Spring Boot 모듈러 모놀리스</text>
    <text x="462" y="219" font-size="12.5" fill="#FFFFFF" opacity="0.94">하나의 서버 안에서 업무 책임만 모듈로 분리</text></g>`;
  s += architectureBox(500, 275, 1230, 88, 'API 계층', ['Controller · 입력값 검증 · 공통 오류 응답 · Actuator 상태 확인'], { stroke: C.navy, fill: '#E3ECF6', badge: '현재 구현' });

  s += architectureBox(500, 420, 370, 112, '여행 준비 유스케이스', ['여행·멤버 → 그룹 성향', '일정 생성 → 출발 경로'], { stroke: C.violet, fill: '#EEE7FF' });
  s += architectureBox(930, 420, 370, 112, '여행 중 대응 유스케이스', ['이벤트 영향 판단', '대안 생성 → 승인 반영'], { stroke: C.orange, fill: '#FFE9DA' });
  s += architectureBox(1360, 420, 370, 112, '귀가 유스케이스', ['마지막 장소 확인', '멤버별 귀가 경로'], { stroke: C.green, fill: '#DFF3EA' });

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

  s += architectureBox(500, 955, 370, 125, '로컬 어댑터', ['H2 기준 장소 데이터', '결정적 대중교통 경로 스텁'], { stroke: C.success, fill: '#DFF3E9', badge: '바로 실행' });
  s += architectureBox(930, 955, 370, 125, '외부 API 포트', ['장소 · 이벤트 · 경로 공급자를', '구현 교체만으로 연결'], { stroke: C.blue, fill: '#DFEDFA', badge: '교체 가능' });
  s += architectureBox(1360, 955, 370, 125, '알림 포트', ['변경 제안 전달', '로그 → FCM / SSE 전환'], { stroke: C.orange, dashed: true, badge: '연동 예정' });

  // 외부 연동
  s += `<g filter="url(#softShadow)"><rect x="1850" y="160" width="660" height="650" rx="22" fill="#FFFFFF" stroke="${C.orange}" stroke-width="2.4"/>
    <rect x="1852" y="238" width="656" height="570" rx="20" fill="${C.orange}" fill-opacity="0.05"/>
    <rect x="1850" y="160" width="660" height="78" rx="22" fill="${C.orange}"/>
    <rect x="1850" y="218" width="660" height="20" fill="${C.orange}"/>
    <text x="1882" y="193" font-size="23" font-weight="700" fill="#FFFFFF">외부 서비스 연동</text>
    <text x="1882" y="219" font-size="12.5" fill="#FFFFFF" opacity="0.94">운영 환경에서 공급자별 어댑터로 연결</text></g>`;
  s += architectureBox(1900, 285, 560, 82, 'OAuth / OIDC', ['로그인과 토큰 검증'], { stroke: C.orange, dashed: true, badge: '예정' });
  s += architectureBox(1900, 395, 560, 82, '관광 · 지도 API', ['장소 검색과 상세 정보 동기화'], { stroke: C.orange, dashed: true, badge: '예정' });
  s += architectureBox(1900, 505, 560, 82, '날씨 · 혼잡 · 교통 API', ['실시간 관측값 수집과 정규화'], { stroke: C.orange, dashed: true, badge: '예정' });
  s += architectureBox(1900, 615, 560, 82, '대중교통 경로 API · FCM/SSE', ['실제 경로 후보와 변경 알림'], { stroke: C.orange, dashed: true, badge: '예정' });

  // 데이터·운영
  s += `<g filter="url(#softShadow)"><rect x="1850" y="850" width="660" height="400" rx="22" fill="#FFFFFF" stroke="${C.green}" stroke-width="2.4"/>
    <rect x="1852" y="922" width="656" height="326" rx="20" fill="${C.green}" fill-opacity="0.05"/>
    <rect x="1850" y="850" width="660" height="72" rx="22" fill="${C.green}"/>
    <rect x="1850" y="902" width="660" height="20" fill="${C.green}"/>
    <text x="1882" y="893" font-size="23" font-weight="700" fill="#FFFFFF">데이터 · 운영</text></g>`;
  s += architectureBox(1900, 965, 255, 112, 'H2 / PostgreSQL', ['로컬 / 운영 DB', 'Flyway 11개 테이블'], { stroke: C.green, fill: '#DFF3E9', badge: '구현' });
  s += architectureBox(2190, 965, 270, 112, 'Redis', ['경로 후보 TTL', '외부 API 짧은 캐시'], { stroke: C.orange, dashed: true, badge: '예정' });
  s += architectureBox(1900, 1110, 560, 92, '운영 확인', ['Actuator Health · 로그/지표 · GitHub Actions 빌드/테스트'], { stroke: C.navy, fill: '#E3ECF6', badge: '구현' });

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

function diagramContainer(x, y, w, h, title, subtitle, color) {
  return `<g filter="url(#softShadow)">
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="6" fill="#FFFFFF" stroke="${color}" stroke-width="2.4"/>
    <path d="M${x + 6},${y} H${x + w - 6} Q${x + w},${y} ${x + w},${y + 6} V${y + 62} H${x} V${y + 6} Q${x},${y} ${x + 6},${y} Z" fill="${color}"/>
    <text x="${x + 24}" y="${y + 29}" font-size="21" font-weight="700" fill="#FFFFFF">${esc(title)}</text>
    <text x="${x + 24}" y="${y + 50}" font-size="12.5" fill="#FFFFFF" opacity="0.92">${esc(subtitle)}</text>
  </g>`;
}

function diagramLane(x, y, w, h, title, subtitle, color) {
  return `<g>
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="4" fill="#F8FAFC" stroke="#93A4B8" stroke-width="1.8"/>
    <rect x="${x}" y="${y}" width="170" height="${h}" rx="4" fill="${color}" fill-opacity="0.13"/>
    <line x1="${x + 170}" y1="${y}" x2="${x + 170}" y2="${y + h}" stroke="${color}" stroke-width="2"/>
    <rect x="${x + 18}" y="${y + 19}" width="8" height="${h - 38}" rx="4" fill="${color}"/>
    <text x="${x + 42}" y="${y + h / 2 - 5}" font-size="17" font-weight="700" fill="${C.ink}">${esc(title)}</text>
    <text x="${x + 42}" y="${y + h / 2 + 19}" font-size="11.5" fill="${C.muted}">${esc(subtitle)}</text>
  </g>`;
}

function diagramNode(x, y, w, h, title, subtitle, color, options = {}) {
  const dash = options.dashed ? ' stroke-dasharray="9 6"' : '';
  const fill = options.fill || '#FFFFFF';
  const titleY = subtitle ? y + h / 2 - 2 : y + h / 2 + 6;
  return `<g>
    <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="5" fill="${fill}" stroke="${color}" stroke-width="2"${dash}/>
    <rect x="${x}" y="${y}" width="${w}" height="9" rx="4.5" fill="${color}"/>
    <text x="${x + w / 2}" y="${titleY}" text-anchor="middle" font-size="${options.titleSize || 15}" font-weight="700" fill="${C.ink}">${esc(title)}</text>
    ${subtitle ? `<text x="${x + w / 2}" y="${titleY + 24}" text-anchor="middle" font-size="${options.subtitleSize || 11.5}" fill="${C.muted}">${esc(subtitle)}</text>` : ''}
  </g>`;
}

function diagramCylinder(x, y, w, h, title, subtitle, color, options = {}) {
  const dash = options.dashed ? ' stroke-dasharray="9 6"' : '';
  return `<g>
    <path d="M${x},${y + 18} V${y + h - 18} C${x},${y + h + 4} ${x + w},${y + h + 4} ${x + w},${y + h - 18} V${y + 18}" fill="#FFFFFF" stroke="${color}" stroke-width="2"${dash}/>
    <ellipse cx="${x + w / 2}" cy="${y + 18}" rx="${w / 2}" ry="18" fill="${color}" fill-opacity="0.17" stroke="${color}" stroke-width="2"${dash}/>
    <path d="M${x},${y + h - 18} C${x},${y + h + 4} ${x + w},${y + h + 4} ${x + w},${y + h - 18}" fill="none" stroke="${color}" stroke-width="2"${dash}/>
    <text x="${x + w / 2}" y="${y + 62}" text-anchor="middle" font-size="16" font-weight="700" fill="${C.ink}">${esc(title)}</text>
    <text x="${x + w / 2}" y="${y + 88}" text-anchor="middle" font-size="11.5" fill="${C.muted}">${esc(subtitle)}</text>
  </g>`;
}

function diagramEdge(points, options = {}) {
  const color = options.color || '#53657A';
  const p = points.map(([x, y]) => `${x},${y}`).join(' ');
  const dash = options.dashed ? ' stroke-dasharray="10 7"' : '';
  let out = `<polyline points="${p}" fill="none" stroke="${color}" stroke-width="2.6" stroke-linejoin="round" marker-end="url(#arrow)"${dash}/>`;
  if (options.label && options.at) {
    const [x, y] = options.at;
    const width = Math.max(72, options.label.length * 13 + 20);
    out += `<rect x="${x - width / 2}" y="${y - 15}" width="${width}" height="28" rx="4" fill="#FFFFFF" stroke="${color}" stroke-width="1.3"/>
      <text x="${x}" y="${y + 4}" text-anchor="middle" font-size="11.5" font-weight="700" fill="${color}">${esc(options.label)}</text>`;
  }
  return out;
}

function serviceArchitecture() {
  let s = header('GAYADI 서비스 아키텍처', 'draw.io 표준 형태 · 계층형 모듈 구조 · 직교 연결선');
  s += `<g transform="translate(1965,37)">
    <line x1="0" y1="12" x2="42" y2="12" stroke="${C.cyan}" stroke-width="3"/>
    <text x="53" y="17" font-size="11.5" font-weight="700" fill="#FFFFFF">현재 구현</text>
    <line x1="0" y1="39" x2="42" y2="39" stroke="#FF935C" stroke-width="3" stroke-dasharray="9 6"/>
    <text x="53" y="44" font-size="11.5" font-weight="700" fill="#FFFFFF">운영 연동 예정</text></g>`;

  s += diagramContainer(50, 165, 300, 810, '클라이언트', '사용자와 모바일 앱', C.cyan);
  s += diagramNode(90, 300, 220, 150, 'Android 앱', 'REST API 호출 · 알림 수신', C.cyan, { fill: '#E8F7F8', titleSize: 18 });
  s += diagramNode(90, 545, 220, 62, '여행 전', '설문 · 일정 · 출발 경로', C.violet);
  s += diagramNode(90, 650, 220, 62, '여행 중', '상황 감지 · 변경 승인', C.orange);
  s += diagramNode(90, 755, 220, 62, '여행 후', '귀가 경로 · 여행 완료', C.green);

  s += diagramContainer(390, 165, 1540, 810, 'GAYADI Server · Spring Boot', '하나의 애플리케이션 안에서 계층과 업무 책임을 분리', C.navy);
  s += diagramLane(430, 250, 1460, 110, 'API 계층', '요청 진입점', C.navy);
  s += diagramNode(630, 275, 330, 60, 'REST Controller', '여행 전 · 중 · 후 API', C.navy, { fill: '#E5EEF8' });
  s += diagramNode(1040, 275, 330, 60, '인증 · 권한 필터', '사용자와 여행 멤버 확인', C.navy, { fill: '#E5EEF8' });
  s += diagramNode(1450, 275, 330, 60, '검증 · 오류 응답', '입력 검증과 공통 응답 형식', C.navy, { fill: '#E5EEF8' });

  s += diagramLane(430, 390, 1460, 160, '애플리케이션', '유스케이스 조합', C.violet);
  s += diagramNode(630, 430, 350, 80, '여행 준비', '멤버 → 설문 → 일정 → 출발 경로', C.violet, { fill: '#EEE7FF' });
  s += diagramNode(1050, 430, 350, 80, '여행 중 대응', '이벤트 판단 → 대안 → 승인 반영', C.orange, { fill: '#FFE9DA' });
  s += diagramNode(1470, 430, 350, 80, '귀가', '마지막 장소 → 멤버별 귀가 경로', C.green, { fill: '#DFF3EA' });

  s += diagramLane(430, 580, 1460, 180, '도메인 모듈', '업무 규칙과 데이터', C.blue);
  const modules = [
    ['인증·사용자', '사용자 식별', C.navy], ['여행·멤버', '출발 방식', C.navy],
    ['설문·성향', '그룹 성향', C.violet], ['일정·변경', 'revision', C.rose],
    ['장소', '장소 원장', C.green], ['이벤트', '상황 판단', C.orange],
    ['경로', '출발·귀가', C.blue], ['공통', '예외·검증', '#667085']
  ];
  modules.forEach(([title, subtitle, color], index) => {
    s += diagramNode(630 + index * 155, 625, 135, 88, title, subtitle, color, { titleSize: 13.5, subtitleSize: 10.5 });
  });

  s += diagramLane(430, 790, 1460, 150, '인프라 계층', '외부 기술 교체 경계', C.green);
  s += diagramNode(650, 830, 340, 72, '로컬 어댑터', 'H2 장소 데이터 · 경로 스텁', C.green, { fill: '#E3F4EC' });
  s += diagramNode(1050, 830, 340, 72, '알림 어댑터', '로그 → FCM / SSE 교체', C.orange, { dashed: true });
  s += diagramNode(1450, 830, 360, 72, '외부 API 어댑터', '관광 · 날씨 · 혼잡 · 대중교통', C.orange, { dashed: true, fill: '#FFF4EC' });

  s += diagramContainer(1970, 165, 540, 810, '외부 서비스', '운영 환경에서 연결', C.orange);
  s += diagramNode(2010, 275, 460, 74, 'OAuth / OIDC', '로그인과 토큰 검증', C.orange, { dashed: true });
  s += diagramNode(2010, 390, 460, 74, '관광 · 지도 API', '장소 검색과 상세 정보', C.orange, { dashed: true });
  s += diagramNode(2010, 505, 460, 74, '날씨 · 혼잡 API', '실시간 관측값 수집', C.orange, { dashed: true });
  s += diagramNode(2010, 620, 460, 74, '대중교통 경로 API', '실제 경로 후보 계산', C.orange, { dashed: true });
  s += diagramNode(2010, 735, 460, 74, 'FCM · SSE', '변경 제안과 알림 전달', C.orange, { dashed: true });

  s += diagramContainer(390, 1020, 2120, 350, '데이터 · 운영', '현재 저장소와 선택적 운영 구성', C.green);
  s += diagramCylinder(500, 1135, 240, 125, 'Core DB', '사용자 · 여행 · 일정', C.navy);
  s += diagramCylinder(820, 1135, 240, 125, 'Place DB', '장소 기본 정보', C.green);
  s += diagramCylinder(1140, 1135, 240, 125, 'Event DB', '판단에 사용한 이벤트', C.orange);
  s += diagramCylinder(1460, 1135, 240, 125, 'Redis', '짧은 TTL 캐시 · 선택', C.orange, { dashed: true });
  s += diagramNode(1790, 1135, 600, 125, '운영 확인', 'Actuator · 로그/지표 · GitHub Actions', C.navy, { fill: '#E5EEF8', titleSize: 17 });

  s += diagramEdge([[310, 375], [370, 375], [370, 305], [630, 305]], { label: 'HTTPS · JSON', at: [450, 305], color: C.navy });
  s += diagramEdge([[1205, 335], [1205, 430]], { color: C.navy });
  s += diagramEdge([[1205, 510], [1205, 625]], { color: C.violet });
  s += diagramEdge([[1205, 713], [1205, 830]], { color: C.blue });
  s += diagramEdge([[1810, 866], [1950, 866], [1950, 657], [2010, 657]], { label: '운영 연동', at: [1950, 710], color: C.orange, dashed: true });
  s += diagramEdge([[820, 902], [820, 995], [620, 995], [620, 1135]], { label: 'JPA · Flyway', at: [720, 995], color: C.green });

  return shell('GAYADI 서비스 아키텍처', 'draw.io 표준 형태의 계층형 서비스 아키텍처', s);
}

function drawioValue(title, subtitle = '') {
  const html = subtitle
    ? `<b>${title}</b><br><font color="#475569" style="font-size:11px">${subtitle}</font>`
    : `<b>${title}</b>`;
  return esc(html);
}

function serviceArchitectureDrawio() {
  const cells = [];
  const vertex = (id, parent, title, subtitle, style, x, y, w, h) => {
    cells.push(`<mxCell id="${id}" value="${drawioValue(title, subtitle)}" style="${style}" vertex="1" parent="${parent}"><mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/></mxCell>`);
  };
  const edge = (id, source, target, label = '', dashed = false) => {
    const style = `edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;strokeWidth=2;endArrow=classic;${dashed ? 'dashed=1;strokeColor=#D85C22;' : 'strokeColor=#53657A;'}`;
    cells.push(`<mxCell id="${id}" value="${esc(label)}" style="${style}" edge="1" source="${source}" target="${target}" parent="1"><mxGeometry relative="1" as="geometry"/></mxCell>`);
  };
  const swimlane = 'swimlane;startSize=32;rounded=1;html=1;fontStyle=1;fontSize=16;container=1;collapsible=0;pointerEvents=0;';
  const node = 'rounded=1;whiteSpace=wrap;html=1;strokeWidth=2;';
  const planned = `${node}dashed=1;fillColor=#FFF4EC;strokeColor=#D85C22;`;

  vertex('client', '1', '클라이언트', '사용자와 모바일 앱', `${swimlane}fillColor=#E8F7F8;strokeColor=#0D9FA5;`, 20, 40, 220, 760);
  vertex('app', 'client', 'Android 앱', '여행 전·중·후 화면', `${node}fillColor=#E8F7F8;strokeColor=#0D9FA5;`, 30, 80, 160, 80);
  vertex('before', 'client', '여행 전', '설문·일정·출발 경로', `${node}fillColor=#EEE7FF;strokeColor=#6637D9;`, 30, 230, 160, 65);
  vertex('during', 'client', '여행 중', '상황 감지·변경 승인', `${node}fillColor=#FFE9DA;strokeColor=#D85C22;`, 30, 340, 160, 65);
  vertex('after', 'client', '여행 후', '귀가 경로·완료', `${node}fillColor=#DFF3EA;strokeColor=#087E62;`, 30, 450, 160, 65);

  vertex('server', '1', 'GAYADI Server · Spring Boot', '모듈러 모놀리스', `${swimlane}fillColor=#E5EEF8;strokeColor=#123C69;`, 270, 40, 1200, 760);
  vertex('apiLane', 'server', 'API 계층', '요청 진입점', `${swimlane}fillColor=#E5EEF8;strokeColor=#123C69;startSize=26;fontSize=14;`, 20, 50, 1160, 105);
  vertex('rest', 'apiLane', 'REST Controller', '여행 전·중·후 API', `${node}fillColor=#E5EEF8;strokeColor=#123C69;`, 160, 34, 250, 52);
  vertex('authFilter', 'apiLane', '인증·권한 필터', '사용자와 멤버 확인', `${node}fillColor=#E5EEF8;strokeColor=#123C69;`, 455, 34, 250, 52);
  vertex('validation', 'apiLane', '검증·오류 응답', '공통 응답 형식', `${node}fillColor=#E5EEF8;strokeColor=#123C69;`, 750, 34, 250, 52);

  vertex('appLane', 'server', '애플리케이션 계층', '유스케이스 조합', `${swimlane}fillColor=#EEE7FF;strokeColor=#6637D9;startSize=26;fontSize=14;`, 20, 175, 1160, 130);
  vertex('prepareUsecase', 'appLane', '여행 준비', '멤버→설문→일정→출발 경로', `${node}fillColor=#EEE7FF;strokeColor=#6637D9;`, 150, 38, 270, 68);
  vertex('duringUsecase', 'appLane', '여행 중 대응', '이벤트→대안→승인 반영', `${node}fillColor=#FFE9DA;strokeColor=#D85C22;`, 445, 38, 270, 68);
  vertex('returnUsecase', 'appLane', '귀가', '멤버별 귀가 경로', `${node}fillColor=#DFF3EA;strokeColor=#087E62;`, 740, 38, 270, 68);

  vertex('domainLane', 'server', '도메인 모듈', '업무 규칙과 데이터', `${swimlane}fillColor=#EAF3FC;strokeColor=#0568C2;startSize=26;fontSize=14;`, 20, 325, 1160, 160);
  const drawioModules = [
    ['auth', '인증·사용자', '#123C69'], ['trip', '여행·멤버', '#123C69'], ['survey', '설문·성향', '#6637D9'], ['plan', '일정·변경', '#C52B5A'],
    ['place', '장소', '#087E62'], ['event', '이벤트', '#D85C22'], ['route', '경로', '#0568C2'], ['common', '공통', '#667085']
  ];
  drawioModules.forEach(([id, title, color], index) => vertex(id, 'domainLane', title, '', `${node}fillColor=#FFFFFF;strokeColor=${color};fontSize=12;`, 115 + index * 128, 55, 110, 62));

  vertex('infraLane', 'server', '인프라 계층', '외부 기술 교체 경계', `${swimlane}fillColor=#E5F4EC;strokeColor=#087E62;startSize=26;fontSize=14;`, 20, 505, 1160, 145);
  vertex('localAdapter', 'infraLane', '로컬 어댑터', 'H2 데이터·경로 스텁', `${node}fillColor=#E5F4EC;strokeColor=#087E62;`, 170, 45, 250, 70);
  vertex('notifyAdapter', 'infraLane', '알림 어댑터', '로그→FCM/SSE', planned, 455, 45, 250, 70);
  vertex('externalAdapter', 'infraLane', '외부 API 어댑터', '장소·이벤트·경로', planned, 740, 45, 250, 70);

  vertex('external', '1', '외부 서비스', '운영 환경 연동', `${swimlane}fillColor=#FFF4EC;strokeColor=#D85C22;`, 1500, 40, 420, 760);
  vertex('oauth', 'external', 'OAuth / OIDC', '로그인과 토큰 검증', planned, 35, 70, 350, 70);
  vertex('placeApi', 'external', '관광·지도 API', '장소 검색과 상세 정보', planned, 35, 180, 350, 70);
  vertex('eventApi', 'external', '날씨·혼잡 API', '실시간 관측값 수집', planned, 35, 290, 350, 70);
  vertex('routeApi', 'external', '대중교통 경로 API', '실제 경로 후보 계산', planned, 35, 400, 350, 70);
  vertex('pushApi', 'external', 'FCM · SSE', '변경 제안과 알림', planned, 35, 510, 350, 70);

  vertex('data', '1', '데이터 · 운영', '현재 저장소와 선택 구성', `${swimlane}fillColor=#E5F4EC;strokeColor=#087E62;`, 270, 840, 1650, 230);
  vertex('coreDb', 'data', 'Core DB', '사용자·여행·일정', 'shape=cylinder3;whiteSpace=wrap;html=1;strokeWidth=2;fillColor=#E5EEF8;strokeColor=#123C69;', 120, 80, 180, 95);
  vertex('placeDb', 'data', 'Place DB', '장소 기본 정보', 'shape=cylinder3;whiteSpace=wrap;html=1;strokeWidth=2;fillColor=#E5F4EC;strokeColor=#087E62;', 380, 80, 180, 95);
  vertex('eventDb', 'data', 'Event DB', '사용한 이벤트', 'shape=cylinder3;whiteSpace=wrap;html=1;strokeWidth=2;fillColor=#FFF1E8;strokeColor=#D85C22;', 640, 80, 180, 95);
  vertex('redis', 'data', 'Redis', '짧은 TTL 캐시·선택', 'shape=cylinder3;whiteSpace=wrap;html=1;strokeWidth=2;dashed=1;fillColor=#FFF4EC;strokeColor=#D85C22;', 900, 80, 180, 95);
  vertex('ops', 'data', '운영 확인', 'Actuator·로그·GitHub Actions', `${node}fillColor=#E5EEF8;strokeColor=#123C69;`, 1160, 80, 360, 95);

  edge('e1', 'app', 'rest', 'HTTPS');
  edge('e2', 'rest', 'prepareUsecase');
  edge('e3', 'prepareUsecase', 'trip');
  edge('e4', 'prepareUsecase', 'survey');
  edge('e5', 'duringUsecase', 'event');
  edge('e6', 'duringUsecase', 'plan');
  edge('e7', 'returnUsecase', 'route');
  edge('e8', 'externalAdapter', 'placeApi', '운영 연동', true);
  edge('e9', 'externalAdapter', 'eventApi', '', true);
  edge('e10', 'externalAdapter', 'routeApi', '', true);
  edge('e11', 'notifyAdapter', 'pushApi', '', true);
  edge('e12', 'localAdapter', 'coreDb', 'JPA');
  edge('e13', 'place', 'placeDb');
  edge('e14', 'event', 'eventDb');

  return `<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net" agent="Codex draw.io plugin" version="26.0.9"><diagram id="gayadi-architecture" name="서비스 아키텍처"><mxGraphModel adaptiveColors="auto" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1920" pageHeight="1080" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>${cells.join('')}</root></mxGraphModel></diagram></mxfile>`;
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
  output.push(await render('gayadi-service-flow-presentation', serviceFlow()));
  output.flatMap((item) => [item.svgPath, item.pngPath]).forEach((file) => console.log(file));
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
