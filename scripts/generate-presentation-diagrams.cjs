const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'docs', 'presentation');
const W = 2560;
const ERD_H = 1400;
const FLOW_H = 1220;

const C = {
  bg: '#F8FAFC', ink: '#0F172A', muted: '#475569', line: '#64748B', white: '#FFFFFF',
  navy: '#1E3A8A', blue: '#2563EB', violet: '#7C3AED', green: '#059669',
  orange: '#EA580C', rose: '#DB2777', teal: '#0F766E', paleBlue: '#EFF6FF',
  paleViolet: '#F5F3FF', paleGreen: '#ECFDF5', paleOrange: '#FFF7ED', paleRose: '#FDF2F8'
};

const xmlEsc = (value) => String(value)
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;');

const svgEsc = xmlEsc;

function shell(title, content, height) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${height}" viewBox="0 0 ${W} ${height}">
  <title>${svgEsc(title)}</title>
  <defs>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="6" stdDeviation="7" flood-color="#0F172A" flood-opacity="0.16"/>
    </filter>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L10,5 L0,10 Z" fill="#64748B"/>
    </marker>
    <pattern id="grid" width="28" height="28" patternUnits="userSpaceOnUse">
      <circle cx="1" cy="1" r="1" fill="#CBD5E1"/>
    </pattern>
  </defs>
  <rect width="${W}" height="${height}" fill="${C.bg}"/>
  <rect width="${W}" height="${height}" fill="url(#grid)" opacity="0.22"/>
  <g font-family="Malgun Gothic, Noto Sans KR, Arial, sans-serif">${content}</g>
</svg>`;
}

function header(title) {
  return `<g filter="url(#shadow)">
    <rect x="64" y="28" width="2432" height="80" rx="12" fill="#0F172A"/>
    <text x="96" y="81" font-size="40" font-weight="700" fill="#FFFFFF">${svgEsc(title)}</text>
  </g>`;
}

const erdTables = [
  { id: 'users', title: 'USERS · 사용자', x: 60, y: 150, w: 330, color: C.navy, rows: [
    ['PK', 'id', 'UUID'], ['', 'nickname', 'VARCHAR(80)'], ['', 'oauth_provider', 'VARCHAR(30)'],
    ['', 'oauth_subject', 'VARCHAR(120)'], ['', 'status', 'VARCHAR(20)'], ['', 'created_at', 'TIMESTAMP']
  ]},
  { id: 'trips', title: 'TRIPS · 여행', x: 480, y: 150, w: 400, color: C.navy, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'owner_id', 'UUID'], ['', 'title', 'VARCHAR(120)'],
    ['', 'departure_mode', 'VARCHAR(30)'], ['', 'departure_at', 'TIMESTAMP'], ['', 'meeting_at', 'TIMESTAMP'],
    ['', 'meeting_location', 'VARCHAR(1000)'], ['', 'status', 'VARCHAR(30)'], ['', 'created_at', 'TIMESTAMP']
  ]},
  { id: 'surveys', title: 'SURVEYS · 설문', x: 970, y: 150, w: 350, color: C.violet, rows: [
    ['PK', 'id', 'UUID'], ['', 'survey_type', 'VARCHAR(40)'], ['', 'version', 'VARCHAR(20)'],
    ['', 'questions', 'JSON'], ['', 'status', 'VARCHAR(20)']
  ]},
  { id: 'places', title: 'PLACES · 장소', x: 1500, y: 150, w: 400, color: C.green, rows: [
    ['PK', 'id', 'UUID'], ['', 'name', 'VARCHAR(160)'], ['', 'category', 'VARCHAR(60)'],
    ['', 'address', 'VARCHAR(300)'], ['', 'latitude', 'DECIMAL'], ['', 'longitude', 'DECIMAL'],
    ['', 'source', 'VARCHAR(30)'], ['', 'source_place_id', 'VARCHAR(120)'], ['', 'basic_info', 'JSON']
  ]},
  { id: 'placeVectors', title: 'PLACE_VECTORS · Milvus', x: 2070, y: 150, w: 430, color: C.teal, dashed: true, rows: [
    ['PK', 'place_id', 'VARCHAR'], ['', 'embedding', 'FLOAT_VECTOR'], ['', 'content', 'VARCHAR'],
    ['', 'category', 'VARCHAR'], ['', 'indoor', 'BOOLEAN'], ['', 'region_code', 'VARCHAR'], ['', 'updated_at', 'TIMESTAMP']
  ]},
  { id: 'members', title: 'TRIP_MEMBERS · 여행 멤버', x: 60, y: 590, w: 400, color: C.navy, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'trip_id', 'UUID'], ['FK', 'user_id', 'UUID'], ['', 'role', 'VARCHAR(20)'],
    ['', 'participation_status', 'VARCHAR(20)'], ['', 'departure_location', 'JSON'],
    ['', 'return_destination', 'JSON'], ['', 'route_preferences', 'JSON'], ['', 'created_at', 'TIMESTAMP']
  ]},
  { id: 'plans', title: 'TRIP_PLANS · 현재 일정', x: 520, y: 590, w: 400, color: C.navy, rows: [
    ['PK', 'id', 'UUID'], ['FK·UQ', 'trip_id', 'UUID'], ['FK', 'survey_response_id', 'UUID'],
    ['', 'revision_no', 'INTEGER'], ['', 'preference_snapshot', 'JSON'], ['', 'status', 'VARCHAR(20)'],
    ['', 'created_at', 'TIMESTAMP'], ['', 'updated_at', 'TIMESTAMP']
  ]},
  { id: 'responses', title: 'SURVEY_RESPONSES · 설문 응답', x: 970, y: 590, w: 400, color: C.violet, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'survey_id', 'UUID'], ['FK', 'user_id', 'UUID'], ['FK', 'trip_id', 'UUID'],
    ['', 'answers', 'JSON'], ['', 'result_code', 'VARCHAR(40)'], ['', 'result_data', 'JSON'], ['', 'created_at', 'TIMESTAMP']
  ]},
  { id: 'items', title: 'TRIP_PLAN_ITEMS · 일정 항목', x: 1420, y: 590, w: 400, color: C.navy, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'plan_id', 'UUID'], ['FK', 'place_id', 'UUID'], ['', 'sequence_no', 'INTEGER'],
    ['', 'planned_start', 'TIMESTAMP'], ['', 'planned_end', 'TIMESTAMP'], ['', 'status', 'VARCHAR(20)']
  ]},
  { id: 'events', title: 'EVENT_OBSERVATIONS · 이벤트 관측', x: 1970, y: 590, w: 460, color: C.orange, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'place_id', 'UUID'], ['', 'event_type', 'VARCHAR(40)'], ['', 'source', 'VARCHAR(40)'],
    ['', 'observed_at', 'TIMESTAMP'], ['', 'valid_to', 'TIMESTAMP'], ['', 'severity', 'VARCHAR(20)'], ['', 'normalized_value', 'JSON']
  ]},
  { id: 'routes', title: 'TRIP_ROUTES · 선택 경로', x: 60, y: 1050, w: 900, color: C.blue, columns: 2, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'trip_id', 'UUID'], ['FK', 'member_id', 'UUID'], ['', 'scope', 'VARCHAR(30)'],
    ['', 'phase', 'VARCHAR(30)'], ['', 'origin', 'JSON'], ['', 'destination', 'JSON'], ['', 'duration_minutes', 'INTEGER'],
    ['', 'transfer_count', 'INTEGER'], ['', 'fare', 'INTEGER'], ['', 'route_data', 'JSON'], ['', 'status', 'VARCHAR(20)']
  ]},
  { id: 'proposals', title: 'CHANGE_PROPOSALS · 변경 제안', x: 1530, y: 1050, w: 900, color: C.rose, columns: 2, rows: [
    ['PK', 'id', 'UUID'], ['FK', 'trip_id', 'UUID'], ['FK', 'plan_id', 'UUID'], ['FK', 'event_id', 'UUID'],
    ['', 'base_revision_no', 'INTEGER'], ['', 'status', 'VARCHAR(20)'], ['', 'reason', 'VARCHAR(500)'], ['', 'options', 'JSON'],
    ['', 'selected_option', 'JSON'], ['', 'before_snapshot', 'JSON'], ['', 'after_snapshot', 'JSON'],
    ['FK', 'decided_by', 'UUID'], ['', 'decided_at', 'TIMESTAMP'], ['', 'created_at', 'TIMESTAMP']
  ]}
];

function tableHeight(table) {
  const count = table.columns === 2 ? Math.ceil(table.rows.length / 2) : table.rows.length;
  return 56 + count * 38 + 12;
}

function svgTable(table) {
  const h = tableHeight(table);
  const dash = table.dashed ? ' stroke-dasharray="10 7"' : '';
  let out = `<g filter="url(#shadow)">
    <rect x="${table.x}" y="${table.y}" width="${table.w}" height="${h}" rx="14" fill="#FFFFFF" stroke="${table.color}" stroke-width="2.2"${dash}/>
    <path d="M${table.x + 14},${table.y} H${table.x + table.w - 14} Q${table.x + table.w},${table.y} ${table.x + table.w},${table.y + 14} V${table.y + 56} H${table.x} V${table.y + 14} Q${table.x},${table.y} ${table.x + 14},${table.y} Z" fill="${table.color}"/>
    <text x="${table.x + 16}" y="${table.y + 38}" font-size="24" font-weight="700" fill="#FFFFFF">${svgEsc(table.title)}</text>`;
  const cols = table.columns === 2 ? 2 : 1;
  const rowsPerCol = Math.ceil(table.rows.length / cols);
  const colW = table.w / cols;
  table.rows.forEach((row, index) => {
    const col = Math.floor(index / rowsPerCol);
    const rowIndex = index % rowsPerCol;
    const x = table.x + col * colW;
    const y = table.y + 56 + rowIndex * 38;
    if (rowIndex % 2 === 1) out += `<rect x="${x + 2}" y="${y}" width="${colW - 4}" height="38" fill="#F1F5F9"/>`;
    if (col === 1) out += `<line x1="${x}" y1="${table.y + 64}" x2="${x}" y2="${table.y + h - 12}" stroke="#CBD5E1"/>`;
    const keyColor = row[0].startsWith('PK') ? '#B42318' : row[0].startsWith('FK') ? '#175CD3' : C.muted;
    out += `<text x="${x + 12}" y="${y + 26}" font-size="14" font-weight="700" fill="${keyColor}">${svgEsc(row[0])}</text>
      <text x="${x + 66}" y="${y + 26}" font-size="17" font-weight="500" fill="${C.ink}">${svgEsc(row[1])}</text>
      <text x="${x + colW - 12}" y="${y + 26}" text-anchor="end" font-size="14" fill="${C.muted}">${svgEsc(row[2])}</text>`;
  });
  return out + '</g>';
}

function svgRelation(points, color = C.line, dashed = false) {
  const p = points.map(([x, y]) => `${x},${y}`).join(' ');
  return `<polyline points="${p}" fill="none" stroke="#FFFFFF" stroke-width="6" stroke-linejoin="round"/>
    <polyline points="${p}" fill="none" stroke="${color}" stroke-width="2.5" stroke-linejoin="round"${dashed ? ' stroke-dasharray="9 7"' : ''}/>`;
}

function erdSvg() {
  let relations = '';
  relations += svgRelation([[390, 230], [480, 230]], C.navy);
  relations += svgRelation([[225, 446], [225, 590]], C.navy);
  relations += svgRelation([[480, 340], [450, 340], [450, 680], [460, 680]], C.navy);
  relations += svgRelation([[1145, 408], [1145, 590]], C.violet);
  relations += svgRelation([[390, 310], [940, 310], [940, 690], [970, 690]], C.violet);
  relations += svgRelation([[880, 310], [930, 310], [930, 730], [970, 730]], C.violet);
  relations += svgRelation([[680, 560], [680, 590]], C.navy);
  relations += svgRelation([[970, 780], [920, 780]], C.violet);
  relations += svgRelation([[920, 720], [1420, 720]], C.navy);
  relations += svgRelation([[1700, 560], [1700, 575], [1620, 575], [1620, 590]], C.green);
  relations += svgRelation([[1900, 260], [2070, 260]], C.teal, true);
  relations += svgRelation([[1700, 560], [1940, 560], [1940, 690], [1970, 690]], C.orange);
  relations += svgRelation([[260, 1000], [260, 1050]], C.blue);
  relations += svgRelation([[680, 560], [900, 560], [900, 1020], [510, 1020], [510, 1050]], C.blue);
  relations += svgRelation([[2200, 962], [2200, 1050]], C.rose);
  relations += svgRelation([[920, 850], [1480, 850], [1480, 1125], [1530, 1125]], C.rose);
  return shell('GAYADI 데이터 모델 · ERD', header('GAYADI 데이터 모델 · ERD') + relations + erdTables.map(svgTable).join(''), ERD_H);
}

const flowNodes = [
  { id: 'create', x: 190, y: 225, w: 490, h: 82, title: '여행 생성 · 멤버 초대', module: '여행 관리', color: C.blue },
  { id: 'mode', x: 275, y: 335, w: 320, h: 110, title: '출발 방식 선택', module: '여행 관리', color: C.blue, decision: true },
  { id: 'meet', x: 60, y: 465, w: 345, h: 86, title: '모여서 출발', module: '집결지 경로', color: C.blue, fill: C.paleBlue },
  { id: 'individual', x: 465, y: 465, w: 345, h: 86, title: '각자 출발', module: '멤버별 경로', color: C.blue, fill: C.paleBlue },
  { id: 'survey', x: 190, y: 570, w: 490, h: 82, title: '성향 설문 제출', module: '유저 관리', color: C.violet },
  { id: 'recommend', x: 145, y: 675, w: 580, h: 90, title: '성향 기반 장소 후보 검색', module: 'AI 서비스 처리 · Milvus', color: C.teal },
  { id: 'plan', x: 190, y: 790, w: 490, h: 82, title: '추천 일정 생성', module: '여행 관리', color: C.blue },
  { id: 'departureRoute', x: 145, y: 895, w: 580, h: 90, title: '출발 경로 추천', module: '외부 API 처리', color: C.blue },
  { id: 'ready', x: 220, y: 1010, w: 430, h: 82, title: '여행 준비 완료', module: '', color: C.green, fill: C.paleGreen },

  { id: 'start', x: 1170, y: 225, w: 480, h: 82, title: '여행 시작', module: '여행 관리', color: C.violet, fill: C.paleViolet },
  { id: 'observe', x: 1100, y: 330, w: 620, h: 90, title: '날씨 · 혼잡 · 교통 확인', module: '외부 API 처리 · 이벤트 DB', color: C.orange },
  { id: 'impact', x: 1245, y: 445, w: 330, h: 110, title: '일정 영향 발생', module: '이벤트 처리', color: C.orange, decision: true },
  { id: 'alternative', x: 1085, y: 580, w: 650, h: 90, title: '대체 장소 · 경로 추천', module: 'AI 서비스 처리 · Milvus · 외부 API 처리', color: C.teal },
  { id: 'notify', x: 1140, y: 695, w: 540, h: 82, title: '변경 제안 알림', module: 'SSE', color: C.rose },
  { id: 'approve', x: 1245, y: 800, w: 330, h: 110, title: '사용자 승인', module: '여행 관리', color: C.rose, decision: true },
  { id: 'apply', x: 1080, y: 1010, w: 660, h: 90, title: '현재 일정 수정 · revision 증가', module: '여행 관리', color: C.green, fill: C.paleGreen },

  { id: 'last', x: 2040, y: 235, w: 430, h: 82, title: '마지막 일정 완료', module: '여행 관리', color: C.green },
  { id: 'returnRoute', x: 2020, y: 500, w: 470, h: 90, title: '멤버별 귀가 경로 추천', module: '외부 API 처리', color: C.blue },
  { id: 'selectRoute', x: 2040, y: 765, w: 430, h: 82, title: '귀가 경로 선택', module: '경로 관리', color: C.blue },
  { id: 'complete', x: 2040, y: 1030, w: 430, h: 82, title: '여행 완료', module: '여행 관리', color: C.green, fill: C.paleGreen }
];

const flowEdges = [
  { from: [435, 307], to: [435, 335] },
  { points: [[355, 410], [232, 465]], label: ['모여서', 270, 440, C.blue] },
  { points: [[515, 410], [638, 465]], label: ['각자', 600, 440, C.blue] },
  { points: [[232, 551], [232, 560], [435, 560], [435, 570]] },
  { points: [[638, 551], [638, 560], [435, 560], [435, 570]] },
  { from: [435, 652], to: [435, 675] },
  { from: [435, 765], to: [435, 790] },
  { from: [435, 872], to: [435, 895] },
  { from: [435, 985], to: [435, 1010] },
  { points: [[650, 1051], [845, 1051], [845, 266], [1170, 266]] },
  { from: [1410, 307], to: [1410, 330] },
  { from: [1410, 420], to: [1410, 445] },
  { points: [[1410, 555], [1410, 580]], label: ['영향 있음', 1480, 575, C.orange] },
  { points: [[1245, 500], [1005, 500], [1005, 375], [1100, 375]], label: ['영향 없음', 1165, 468, C.line] },
  { from: [1410, 670], to: [1410, 695] },
  { from: [1410, 777], to: [1410, 800] },
  { points: [[1410, 910], [1410, 1010]], label: ['승인', 1455, 970, C.green] },
  { points: [[1245, 855], [1035, 855], [1035, 375], [1100, 375]], label: ['거절', 1070, 835, C.rose] },
  { points: [[1080, 1055], [970, 1055], [970, 375], [1100, 375]], label: ['여행 진행', 1015, 1035, C.green] },
  { points: [[1740, 1055], [1940, 1055], [1940, 276], [2040, 276]] },
  { from: [2255, 317], to: [2255, 500] },
  { from: [2255, 590], to: [2255, 765] },
  { from: [2255, 847], to: [2255, 1030] }
];

function phasePanel(x, y, w, h, number, title, color, fill) {
  return `<g><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="22" fill="${fill}" stroke="${color}" stroke-width="2.5"/>
    <path d="M${x + 22},${y} H${x + w - 22} Q${x + w},${y} ${x + w},${y + 22} V${y + 78} H${x} V${y + 22} Q${x},${y} ${x + 22},${y} Z" fill="${color}"/>
    <circle cx="${x + 42}" cy="${y + 39}" r="22" fill="#FFFFFF" fill-opacity="0.18" stroke="#FFFFFF" stroke-opacity="0.6"/>
    <text x="${x + 42}" y="${y + 47}" text-anchor="middle" font-size="20" font-weight="700" fill="#FFFFFF">${number}</text>
    <text x="${x + 78}" y="${y + 52}" font-size="32" font-weight="700" fill="#FFFFFF">${svgEsc(title)}</text></g>`;
}

function flowNodeSvg(node) {
  if (node.decision) {
    const cx = node.x + node.w / 2;
    const cy = node.y + node.h / 2;
    const titleY = node.module ? cy + 12 : cy + 9;
    return `<g filter="url(#shadow)"><polygon points="${cx},${node.y} ${node.x + node.w},${cy} ${cx},${node.y + node.h} ${node.x},${cy}" fill="#FFFFFF" stroke="${node.color}" stroke-width="3"/>
      ${node.module ? `<text x="${cx}" y="${cy - 22}" text-anchor="middle" font-size="16" font-weight="700" fill="${node.color}">${svgEsc(node.module)}</text>` : ''}
      <text x="${cx}" y="${titleY}" text-anchor="middle" font-size="24" font-weight="700" fill="${C.ink}">${svgEsc(node.title)}</text></g>`;
  }
  const fill = node.fill || '#FFFFFF';
  const titleY = node.module ? node.y + 65 : node.y + node.h / 2 + 9;
  return `<g filter="url(#shadow)"><rect x="${node.x}" y="${node.y}" width="${node.w}" height="${node.h}" rx="14" fill="${fill}" stroke="${node.color}" stroke-width="2.2"/>
    ${node.module ? `<text x="${node.x + node.w / 2}" y="${node.y + 29}" text-anchor="middle" font-size="16" font-weight="700" fill="${node.color}">${svgEsc(node.module)}</text>` : ''}
    <text x="${node.x + node.w / 2}" y="${titleY}" text-anchor="middle" font-size="24" font-weight="700" fill="${C.ink}">${svgEsc(node.title)}</text></g>`;
}

function flowEdgeSvg(edge) {
  const points = edge.points || [edge.from, edge.to];
  const p = points.map(([x, y]) => `${x},${y}`).join(' ');
  return `<polyline points="${p}" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round"/>
    <polyline points="${p}" fill="none" stroke="${C.line}" stroke-width="3" stroke-linejoin="round" marker-end="url(#arrow)"/>`;
}

function flowLabelSvg(edge) {
  if (!edge.label) return '';
  const [label, x, y, color] = edge.label;
  const labelWidth = Math.max(72, label.length * 18 + 26);
  return `<g><rect x="${x - labelWidth / 2}" y="${y - 24}" width="${labelWidth}" height="32" rx="8" fill="#FFFFFF" fill-opacity="0.98"/>
    <text x="${x}" y="${y}" text-anchor="middle" font-size="16" font-weight="700" fill="${color}">${svgEsc(label)}</text></g>`;
}

function flowSvg() {
  let content = header('GAYADI 서비스 흐름도');
  content += phasePanel(50, 140, 780, 1040, '01', '여행 전', C.blue, '#F4F7FF');
  content += phasePanel(860, 140, 1100, 1040, '02', '여행 중', C.violet, '#F8F6FF');
  content += phasePanel(1990, 140, 520, 1040, '03', '여행 후', C.green, '#F3FBF8');
  content += flowEdges.map(flowEdgeSvg).join('');
  content += flowNodes.map(flowNodeSvg).join('');
  content += flowEdges.map(flowLabelSvg).join('');
  return shell('GAYADI 서비스 흐름도', content, FLOW_H);
}

function mxFile(name, pageId, cells, pageHeight) {
  return `<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="app.diagrams.net" agent="Codex draw.io plugin" version="26.0.9"><diagram id="${pageId}" name="${xmlEsc(name)}"><mxGraphModel adaptiveColors="auto" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="2560" pageHeight="${pageHeight}" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>${cells.join('')}</root></mxGraphModel></diagram></mxfile>`;
}

function mxVertex(id, value, x, y, w, h, style, parent = '1') {
  return `<mxCell id="${id}" value="${xmlEsc(value)}" style="${style}" vertex="1" parent="${parent}"><mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/></mxCell>`;
}

function mxEdge(id, source, target, color = C.line, dashed = false) {
  return `<mxCell id="${id}" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;strokeWidth=2.5;strokeColor=${color};endArrow=none;${dashed ? 'dashed=1;dashPattern=8 6;' : ''}" edge="1" parent="1" source="${source}" target="${target}"><mxGeometry relative="1" as="geometry"/></mxCell>`;
}

function tableHtml(table) {
  const rowsPerCol = table.columns === 2 ? Math.ceil(table.rows.length / 2) : table.rows.length;
  const chunks = table.columns === 2 ? [table.rows.slice(0, rowsPerCol), table.rows.slice(rowsPerCol)] : [table.rows];
  const body = chunks.map((chunk) => `<table style="width:${100 / chunks.length}%;border-collapse:collapse;display:inline-table;vertical-align:top;font-size:17px;">${chunk.map((row, index) => {
    const keyColor = row[0].startsWith('PK') ? '#B42318' : row[0].startsWith('FK') ? '#175CD3' : C.muted;
    const bg = index % 2 ? '#F1F5F9' : '#FFFFFF';
    return `<tr style="background:${bg};height:38px"><td style="width:56px;padding-left:12px;color:${keyColor};font-size:14px;font-weight:700">${row[0]}</td><td style="color:${C.ink};font-weight:500">${row[1]}</td><td style="padding-right:12px;text-align:right;color:${C.muted};font-size:14px">${row[2]}</td></tr>`;
  }).join('')}</table>`).join('');
  return `<div style="background:${table.color};color:#FFFFFF;font-size:24px;font-weight:700;padding:14px 16px;text-align:left">${table.title}</div><div style="background:#FFFFFF">${body}</div>`;
}

function erdDrawio() {
  const cells = [];
  const rels = [
    ['r1', 'users', 'trips', C.navy], ['r2', 'users', 'members', C.navy], ['r3', 'trips', 'members', C.navy],
    ['r4', 'surveys', 'responses', C.violet], ['r5', 'users', 'responses', C.violet], ['r6', 'trips', 'responses', C.violet],
    ['r7', 'trips', 'plans', C.navy], ['r8', 'responses', 'plans', C.violet], ['r9', 'plans', 'items', C.navy],
    ['r10', 'places', 'items', C.green], ['r11', 'places', 'placeVectors', C.teal, true], ['r12', 'places', 'events', C.orange],
    ['r13', 'members', 'routes', C.blue], ['r14', 'trips', 'routes', C.blue], ['r15', 'events', 'proposals', C.rose],
    ['r16', 'plans', 'proposals', C.rose]
  ];
  rels.forEach((rel) => cells.push(mxEdge(...rel)));
  erdTables.forEach((table) => {
    const style = `rounded=1;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=${table.color};strokeWidth=2;align=left;verticalAlign=top;spacing=0;shadow=1;${table.dashed ? 'dashed=1;dashPattern=8 6;' : ''}`;
    cells.push(mxVertex(table.id, tableHtml(table), table.x, table.y, table.w, tableHeight(table), style));
  });
  cells.push(mxVertex('title', 'GAYADI 데이터 모델 · ERD', 64, 28, 2432, 80, 'rounded=1;whiteSpace=wrap;html=1;fillColor=#0F172A;strokeColor=#0F172A;fontColor=#FFFFFF;fontSize=40;fontStyle=1;align=left;spacingLeft=30;shadow=1;'));
  return mxFile('ERD', 'gayadi-erd', cells, ERD_H);
}

function flowNodeHtml(node) {
  if (!node.module) return `<div style="font-size:24px;font-weight:700;color:${C.ink}">${node.title}</div>`;
  return `<div style="font-size:16px;font-weight:700;color:${node.color};margin-bottom:8px">${node.module}</div><div style="font-size:24px;font-weight:700;color:${C.ink}">${node.title}</div>`;
}

function flowDrawio() {
  const cells = [];
  const panelStyle = (color, fill) => `swimlane;horizontal=1;startSize=78;rounded=1;whiteSpace=wrap;html=1;fillColor=${color};swimlaneFillColor=${fill};strokeColor=${color};strokeWidth=2.5;fontColor=#FFFFFF;fontSize=32;fontStyle=1;align=left;spacingLeft=28;`;
  cells.push(mxVertex('beforePanel', '01   여행 전', 50, 140, 780, 1040, panelStyle(C.blue, '#F4F7FF')));
  cells.push(mxVertex('duringPanel', '02   여행 중', 860, 140, 1100, 1040, panelStyle(C.violet, '#F8F6FF')));
  cells.push(mxVertex('afterPanel', '03   여행 후', 1990, 140, 520, 1040, panelStyle(C.green, '#F3FBF8')));
  const edges = [
    ['e1', 'create', 'mode'], ['e2', 'mode', 'meet'], ['e3', 'mode', 'individual'], ['e4', 'meet', 'survey'],
    ['e5', 'individual', 'survey'], ['e6', 'survey', 'recommend'], ['e7', 'recommend', 'plan'],
    ['e8', 'plan', 'departureRoute'], ['e9', 'departureRoute', 'ready'], ['e10', 'ready', 'start'],
    ['e11', 'start', 'observe'], ['e12', 'observe', 'impact'], ['e13', 'impact', 'alternative'],
    ['e14', 'impact', 'observe'], ['e15', 'alternative', 'notify'], ['e16', 'notify', 'approve'],
    ['e17', 'approve', 'apply'], ['e18', 'approve', 'observe'], ['e19', 'apply', 'observe'],
    ['e20', 'apply', 'last'], ['e21', 'last', 'returnRoute'], ['e22', 'returnRoute', 'selectRoute'], ['e23', 'selectRoute', 'complete']
  ];
  edges.forEach((edge) => cells.push(mxEdge(edge[0], edge[1], edge[2])));
  flowNodes.forEach((node) => {
    const shape = node.decision ? 'rhombus;' : 'rounded=1;arcSize=18;';
    const style = `${shape}whiteSpace=wrap;html=1;fillColor=${node.fill || '#FFFFFF'};strokeColor=${node.color};strokeWidth=2.2;fontColor=${C.ink};fontSize=24;fontStyle=1;shadow=1;align=center;verticalAlign=middle;spacing=8;`;
    cells.push(mxVertex(node.id, flowNodeHtml(node), node.x, node.y, node.w, node.h, style));
  });
  const labels = [
    ['l1', '모여서', 210, 422, C.blue], ['l2', '각자', 540, 422, C.blue],
    ['l3', '영향 없음', 1105, 442, C.line], ['l4', '영향 있음', 1420, 557, C.orange],
    ['l5', '거절', 1010, 817, C.rose], ['l6', '승인', 1395, 952, C.green],
    ['l7', '여행 진행', 955, 1017, C.green]
  ];
  labels.forEach(([id, value, x, y, color]) => cells.push(mxVertex(id, value, x, y, 120, 30, `rounded=1;html=1;strokeColor=none;fillColor=#FFFFFF;opacity=90;fontColor=${color};fontSize=16;fontStyle=1;align=center;verticalAlign=middle;`)));
  cells.push(mxVertex('title', 'GAYADI 서비스 흐름도', 64, 28, 2432, 80, 'rounded=1;whiteSpace=wrap;html=1;fillColor=#0F172A;strokeColor=#0F172A;fontColor=#FFFFFF;fontSize=40;fontStyle=1;align=left;spacingLeft=30;shadow=1;'));
  return mxFile('서비스 흐름도', 'gayadi-service-flow', cells, FLOW_H);
}

function renderSvg(name, svg, height) {
  const svgPath = path.join(OUT, `${name}.svg`);
  const pngPath = path.join(OUT, `${name}.png`);
  const cleanSvg = svg.replace(/[ \t]+\r?\n/g, '\n');
  fs.writeFileSync(svgPath, cleanSvg, 'utf8');
  const chrome = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  const target = `file:///${svgPath.replaceAll('\\', '/')}`;
  execFileSync(chrome, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
    `--window-size=${W},${height}`, `--screenshot=${pngPath}`, target
  ], { stdio: 'ignore' });
  return [svgPath, pngPath];
}

function writeDrawio(name, xml) {
  const file = path.join(OUT, `${name}.drawio`);
  fs.writeFileSync(file, xml, 'utf8');
  return file;
}

fs.mkdirSync(OUT, { recursive: true });
const output = [
  ...renderSvg('gayadi-erd-presentation', erdSvg(), ERD_H),
  ...renderSvg('gayadi-service-flow-presentation', flowSvg(), FLOW_H),
  writeDrawio('gayadi-erd-presentation', erdDrawio()),
  writeDrawio('gayadi-service-flow-presentation', flowDrawio())
];
output.forEach((file) => console.log(file));
