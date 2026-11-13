// n8n 워크플로 JSON을 만든다: node automation/n8n/build-workflows.mjs
// 비밀값은 넣지 않는다 — ERP 키는 n8n 자격 증명(ERP 자동화 키), 웹훅 서명 키 · ERP 주소는 환경 변수로 받는다.
import { writeFileSync, mkdirSync } from "node:fs";
import { createHash } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const OUT = join(dirname(fileURLToPath(import.meta.url)), "workflows");
const CRED = { httpHeaderAuth: { id: "cafeonErpKey0001", name: "ERP 자동화 키" } };

// 이름에서 고정 ID를 만든다 (다시 만들어도 같은 ID)
const uid = (s) => {
  const h = createHash("sha1").update(s).digest("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
};

// Code 노드 공통 함수
const LIB = `
const won = (n) => Math.round(Number(n) || 0).toLocaleString('ko-KR') + '원';
// HTTP 노드가 '오류여도 계속'으로 넘긴 응답에서 ERP 오류 메시지 꺼내기
const errText = (r) => {
  const e = r && r.error;
  if (!e) return null;
  try { const b = typeof e.description === 'string' ? JSON.parse(e.description) : null; if (b && b.message) return b.message; } catch (x) {}
  return e.message || JSON.stringify(e);
};
`.trim();
const js = (body) => `${LIB}\n\n${body.trim()}\n`;

const workflow = (id, name, build) => {
  const nodes = [];
  const connections = {};
  const add = (name, type, typeVersion, position, parameters, extra = {}) => {
    nodes.push({ id: uid(`${id}/${name}`), name, type, typeVersion, position, parameters, ...extra });
    return name;
  };
  const link = (from, to, output = 0) => {
    const c = (connections[from] ??= { main: [] });
    while (c.main.length <= output) c.main.push([]);
    c.main[output].push({ node: to, type: "main", index: 0 });
  };
  const chain = (...names) => names.slice(1).forEach((n, i) => link(names[i], n));

  const h = {
    add,
    link,
    chain,
    code: (name, pos, body) => add(name, "n8n-nodes-base.code", 2, pos, { jsCode: js(body) }),
    // ERP API 호출 (자동화 키 자격 증명)
    erp: (name, pos, method, path, { query, body, once, always, softFail } = {}) => {
      const p = { method, url: `={{ $env.ERP_API_URL }}${path}`, authentication: "genericCredentialType", genericAuthType: "httpHeaderAuth" };
      if (query) {
        p.sendQuery = true;
        p.queryParameters = { parameters: Object.entries(query).map(([n, value]) => ({ name: n, value })) };
      }
      if (body) {
        p.sendBody = true;
        p.specifyBody = "json";
        p.jsonBody = body;
      }
      p.options = {};
      const extra = { credentials: CRED };
      if (once) extra.executeOnce = true;
      if (always) extra.alwaysOutputData = true;
      if (softFail) extra.onError = "continueRegularOutput";
      return add(name, "n8n-nodes-base.httpRequest", 4.2, pos, p, extra);
    },
    // $json.route 값으로 갈래 나누기 (출력 순서 = routes 순서)
    route: (name, pos, routes) =>
      add(name, "n8n-nodes-base.switch", 3.2, pos, {
        rules: {
          values: routes.map((r) => ({
            conditions: {
              options: { caseSensitive: true, leftValue: "", typeValidation: "strict", version: 2 },
              conditions: [{ id: uid(`${id}/${name}/${r}`), leftValue: "={{ $json.route }}", rightValue: r, operator: { type: "string", operation: "equals" } }],
              combinator: "and",
            },
            renameOutput: true,
            outputKey: r,
          })),
        },
        options: {},
      }),
    manual: (pos) => add("수동 실행", "n8n-nodes-base.manualTrigger", 1, pos, {}),
    schedule: (name, pos, cron) => add(name, "n8n-nodes-base.scheduleTrigger", 1.2, pos, { rule: { interval: [{ field: "cronExpression", expression: cron }] } }),
    // 담당자 결정을 받는 n8n 폼. 주소는 $execution.resumeFormUrl → 자동화 기록의 '승인 화면 열기'
    form: (name, pos, title, description, fields) =>
      add(
        name,
        "n8n-nodes-base.wait",
        1.1,
        pos,
        {
          resume: "form",
          formTitle: title,
          formDescription: description,
          formFields: { values: fields },
          limitWaitTime: true,
          limitType: "afterTimeInterval",
          resumeAmount: 24,
          resumeUnit: "hours",
          options: {},
        },
        { webhookId: uid(`${id}/${name}/webhook`) },
      ),
    log: (name, pos) => h.erp(name, pos, "POST", "/automation/logs", { body: "={{ JSON.stringify($json.log) }}" }),
    resolve: (name, pos) =>
      h.erp(name, pos, "POST", "/automation/logs/{{ $json.logNo }}/resolve", { body: "={{ JSON.stringify({ result: $json.result }) }}" }),
  };

  build(h);
  return {
    id,
    name,
    nodes,
    connections,
    active: false, // 가져온 뒤 start-n8n.ps1이 update:workflow로 켠다
    settings: { executionOrder: "v1", timezone: "Asia/Seoul", saveManualExecutions: true, saveDataSuccessExecution: "all", saveDataErrorExecution: "all" },
    pinData: {},
    tags: [],
  };
};

const dropdown = (label, options) => ({ fieldLabel: label, fieldType: "dropdown", fieldOptions: { values: options.map((option) => ({ option })) }, requiredField: true });
const text = (label) => ({ fieldLabel: label, fieldType: "text" });

// ───────────── 1. ERP 이벤트 수신: 서명 확인 → 중복 거르기 → 종류별 처리 ─────────────
const events = workflow("cafeonErpEvents1", "ERP 이벤트 수신", (h) => {
  const hook = h.add(
    "ERP 이벤트",
    "n8n-nodes-base.webhook",
    2.1,
    [0, 300],
    { httpMethod: "POST", path: "erp-events", responseMode: "responseNode", options: { rawBody: true } },
    { webhookId: uid("cafeonErpEvents1/webhook") },
  );

  const verify = h.code(
    "서명 · 중복 확인",
    [220, 300],
    `
// ERP가 보낸 그대로의 본문으로 HMAC-SHA256 서명을 다시 계산해 비교한다
const crypto = require('crypto');
const item = $input.first();
const headers = item.json.headers || {};
const secret = $env.ERP_WEBHOOK_SECRET || '';
const raw = item.binary && item.binary.data ? Buffer.from(item.binary.data.data, 'base64').toString('utf8') : '';
const ts = String(headers['x-timestamp'] || '');
const sig = String(headers['x-signature'] || '').replace(/^sha256=/, '');

let reason = '';
if (!secret) reason = 'n8n에 ERP_WEBHOOK_SECRET이 없습니다.';
else if (!raw) reason = '본문이 없습니다.';
else if (!/^\\d+$/.test(ts) || Math.abs(Date.now() / 1000 - Number(ts)) > 300) reason = '서명 시각이 5분 넘게 차이 납니다 (재전송 공격 방지).';
else {
  const expected = Buffer.from(crypto.createHmac('sha256', secret).update(ts + '.' + raw).digest('hex'));
  const got = Buffer.from(sig);
  if (expected.length !== got.length || !crypto.timingSafeEqual(expected, got)) reason = '서명이 맞지 않습니다.';
}
if (reason) return [{ json: { route: 'invalid', reason } }];

// 같은 이벤트가 다시 와도(재전송) 한 번만 처리한다
const event = JSON.parse(raw);
const memory = $getWorkflowStaticData('global');
memory.seen = memory.seen || [];
if (memory.seen.includes(event.eventId)) return [{ json: { route: 'duplicate', eventId: event.eventId } }];
memory.seen.push(event.eventId);
if (memory.seen.length > 1000) memory.seen.splice(0, memory.seen.length - 1000);
return [{ json: { route: 'ok', ...event } }];
`,
  );
  const check = h.route("검증 결과", [440, 300], ["ok", "duplicate", "invalid"]);
  const ok = h.add("응답 200", "n8n-nodes-base.respondToWebhook", 1.1, [660, 200], {
    respondWith: "json",
    responseBody: "={{ JSON.stringify({ received: $json.eventId }) }}",
    options: { responseCode: 200 },
  });
  const dup = h.add("응답 200 (중복)", "n8n-nodes-base.respondToWebhook", 1.1, [660, 380], {
    respondWith: "json",
    responseBody: "={{ JSON.stringify({ duplicate: $json.eventId }) }}",
    options: { responseCode: 200 },
  });
  const bad = h.add("응답 401", "n8n-nodes-base.respondToWebhook", 1.1, [660, 540], {
    respondWith: "json",
    responseBody: "={{ JSON.stringify({ message: $json.reason }) }}",
    options: { responseCode: 401 },
  });
  h.chain(hook, verify, check);
  h.link(check, ok, 0);
  h.link(check, dup, 1);
  h.link(check, bad, 2);

  const kind = h.code(
    "이벤트 종류",
    [880, 200],
    `
const e = $('서명 · 중복 확인').first().json;
const map = { 'order_request.created': 'order', 'closing.completed': 'closed', 'closing.cancelled': 'cancelled', 'ping': 'ping' };
return [{ json: { route: map[e.type] || 'other', type: e.type, data: e.data || {}, eventId: e.eventId } }];
`,
  );
  const byKind = h.route("종류별", [1100, 200], ["order", "closed", "cancelled", "ping"]);
  h.chain(ok, kind, byKind);

  const writeLog = h.log("기록 남기기", [2860, 420]);

  // ── 매장 발주 요청: 규칙에 맞으면 자동 승인, 아니면 담당자 승인 ──
  const pending = h.erp("대기 요청 조회", [1320, 0], "GET", "/order-requests/pending", { always: true });
  const judge = h.code(
    "자동 승인 판단",
    [1540, 0],
    `
// 자동 승인 규칙: ① 요청 금액이 한도 이하 ② 물류센터 공급 품목은 물류센터 재고로 모두 출고 가능
const e = $('이벤트 종류').first().json.data;
const limit = Number($env.AUTO_APPROVE_LIMIT || 300000);
const r = $input.all().map((i) => i.json).find((x) => x.requestNo === e.requestNo);
if (!r) return [{ json: { route: 'skip' } }]; // 이미 사람이 처리한 요청

const reasons = [];
if (r.amount > limit) reasons.push('요청 금액 ' + won(r.amount) + '이 자동 승인 한도 ' + won(limit) + '을 넘습니다.');
for (const l of r.lines) {
  if (l.supplyType === '물류센터' && (l.centerPacks || 0) < l.requestedPacks) {
    reasons.push(l.materialName + ': 물류센터 재고 ' + (l.centerPacks || 0) + '팩 < 요청 ' + l.requestedPacks + '팩 (모자라는 만큼 직송 발주가 생깁니다)');
  }
}
const lines = r.lines
  .map((l) => '· ' + l.materialName + ' ' + l.requestedPacks + '팩 — ' + (l.supplyType === '물류센터' ? '물류센터 출고 (보유 ' + (l.centerPacks || 0) + '팩)' : '공급업체 직송'))
  .join('\\n');
return [{ json: { route: reasons.length ? 'manual' : 'auto', request: r, reasons, lines, limit } }];
`,
  );
  const how = h.route("승인 방식", [1760, 0], ["auto", "manual"]);
  h.link(byKind, pending, 0);
  h.chain(pending, judge, how);

  const approveAuto = h.erp("자동 승인", [1980, -120], "POST", "/order-requests/{{ $json.request.requestNo }}/approve", { body: "{}", softFail: true });
  const autoLog = h.code(
    "자동 승인 결과",
    [2200, -120],
    `
const d = $('자동 승인 판단').first().json;
const res = $input.first().json;
const err = errText(res);
const r = d.request;
return [{ json: { log: {
  workflow: '긴급 발주 처리',
  level: err ? '주의' : '정보',
  title: err ? r.storeName + ' ' + r.requestNumber + ' 자동 승인 실패' : r.storeName + ' ' + r.requestNumber + ' 자동 승인',
  detail: err
    ? '오류: ' + err + '\\n' + d.lines
    : '규칙 통과: 금액 ' + won(r.amount) + ' ≤ 한도 ' + won(d.limit) + ', 물류센터 재고 충분\\n' + d.lines + '\\n발주서: ' + (res.poNumbers || '-'),
  refNumber: r.requestNumber,
  executionId: $execution.id,
} } }];
`,
  );
  h.link(how, approveAuto, 0);
  h.chain(approveAuto, autoLog, writeLog);

  const askLog = h.code(
    "승인 요청 내용",
    [1980, 120],
    `
const d = $json;
const r = d.request;
return [{ json: { log: {
  workflow: '긴급 발주 처리',
  level: '승인 필요',
  title: r.storeName + ' ' + r.requestNumber + ' 승인 필요 (' + won(r.amount) + ')',
  detail: '자동 승인하지 않은 이유:\\n' + d.reasons.map((x) => '· ' + x).join('\\n') + '\\n\\n요청 품목:\\n' + d.lines,
  actionUrl: $execution.resumeFormUrl,
  refNumber: r.requestNumber,
  executionId: $execution.id,
} } }];
`,
  );
  const askSave = h.log("승인 요청 기록", [2200, 120]);
  const decide = h.form(
    "담당자 결정",
    [2420, 120],
    "매장 긴급 발주 요청 승인",
    "={{ $json.title }}\n\n{{ $json.detail }}", // 앞 노드(방금 저장한 승인 기록)의 제목 · 내용
    [dropdown("결정", ["승인", "반려"]), text("반려 사유")],
  );
  const decision = h.code(
    "결정 정리",
    [2640, 120],
    `
const f = $input.first().json;
const choice = f['결정'];
const route = choice === '승인' ? 'approve' : choice === '반려' ? 'reject' : 'timeout';
return [{ json: { route, reason: f['반려 사유'] || '본사 담당자 반려', request: $('자동 승인 판단').first().json.request } }];
`,
  );
  const decided = h.route("결정별", [2860, 120], ["approve", "reject", "timeout"]);
  const approve = h.erp("승인 처리", [3080, 0], "POST", "/order-requests/{{ $json.request.requestNo }}/approve", { body: "{}", softFail: true });
  const reject = h.erp("반려 처리", [3080, 160], "POST", "/order-requests/{{ $json.request.requestNo }}/reject", {
    body: "={{ JSON.stringify({ reason: $json.reason }) }}",
    softFail: true,
  });
  const outcome = h.code(
    "처리 결과",
    [3300, 120],
    `
const d = $('결정 정리').first().json;
const res = $input.first().json;
const logNo = $('승인 요청 기록').first().json.logNo;
let result;
if (d.route === 'timeout') result = '24시간 동안 결정이 없어 자동화 종료 (요청은 대기 상태로 남음)';
else if (errText(res)) result = (d.route === 'approve' ? '승인' : '반려') + ' 실패: ' + errText(res);
else if (d.route === 'approve') result = '승인 (n8n 승인 화면) · 발주서 ' + (res.poNumbers || '-');
else result = '반려 (n8n 승인 화면) · ' + d.reason;
return [{ json: { logNo, result } }];
`,
  );
  const close = h.resolve("승인 기록 닫기", [3520, 120]);
  h.link(how, askLog, 1);
  h.chain(askLog, askSave, decide, decision, decided);
  h.link(decided, approve, 0);
  h.link(decided, reject, 1);
  h.link(decided, outcome, 2);
  h.link(approve, outcome);
  h.link(reject, outcome);
  h.link(outcome, close);

  // ── 일마감: 이상한 마감만 기록 ──
  const closed = h.code(
    "마감 이상 확인",
    [1320, 300],
    `
const e = $('이벤트 종류').first().json.data;
const notes = [];
if (Math.abs(e.cashDiff || 0) >= 10000) notes.push('시재 차이 ' + won(e.cashDiff) + ' (기준 ±10,000원)');
if ((e.reopenCount || 0) > 0) notes.push('마감 취소 후 다시 마감 ' + e.reopenCount + '회');
if ((e.receiptCount || 0) === 0) notes.push('영수증 0건으로 마감');
if (!notes.length) return []; // 정상 마감은 기록하지 않는다
return [{ json: { log: {
  workflow: '일마감 점검',
  level: '주의',
  title: e.storeName + ' ' + e.date + ' 마감 확인 필요',
  detail: notes.map((x) => '· ' + x).join('\\n') + '\\n매출 ' + won(e.salesAmount) + ' · 영수증 ' + e.receiptCount + '건 · 폐기 ' + won(e.wasteAmount),
  executionId: $execution.id,
} } }];
`,
  );
  h.link(byKind, closed, 1);
  h.link(closed, writeLog);

  const cancelled = h.code(
    "마감 취소 기록",
    [1320, 460],
    `
const e = $('이벤트 종류').first().json.data;
return [{ json: { log: {
  workflow: '일마감 점검',
  level: '주의',
  title: e.storeName + ' ' + e.date + ' 마감 취소',
  detail: '사유: ' + (e.reason || '-') + '\\n취소한 곳: ' + (e.by || '-') + '\\n취소 전 매출 ' + won(e.salesAmount) + ' · 영수증 ' + e.receiptCount + '건\\n다시 마감이 들어오는지 확인하세요.',
  executionId: $execution.id,
} } }];
`,
  );
  h.link(byKind, cancelled, 2);
  h.link(cancelled, writeLog);

  const ping = h.code(
    "연결 확인 기록",
    [1320, 620],
    `
const e = $('이벤트 종류').first().json;
return [{ json: { log: {
  workflow: 'ERP 이벤트 수신',
  level: '정보',
  title: '웹훅 연결 확인',
  detail: 'ERP → n8n 웹훅 수신, HMAC 서명 확인 통과\\n이벤트 ID ' + e.eventId + ' · 보낸 사람 ' + (e.data.by || '-'),
  executionId: $execution.id,
} } }];
`,
  );
  h.link(byKind, ping, 3);
  h.link(ping, writeLog);
});

// ───────────── 2. 아침 점검: 어제 마감 · 수신 실패 · 대기 요청 ─────────────
const morning = workflow("cafeonMorning001", "아침 점검", (h) => {
  const cron = h.schedule("매일 08:30", [0, 200], "30 8 * * *");
  const manual = h.manual([0, 380]);
  const status = h.erp("수신 현황", [240, 300], "GET", "/integration/status");
  const pending = h.erp("대기 발주 요청", [460, 300], "GET", "/order-requests/pending", { once: true, always: true });
  const sent = h.erp("최근 지시", [680, 300], "GET", "/store-messages", {
    query: { from: "={{ $now.setZone('Asia/Seoul').minus({ days: 1 }).toISODate() }}", to: "={{ $now.setZone('Asia/Seoul').toISODate() }}", kind: "지시" },
    once: true,
    always: true,
  });
  const summary = h.code(
    "점검 정리",
    [900, 300],
    `
const today = $now.setZone('Asia/Seoul');
const date = today.minus({ days: 1 }).toISODate();
const stores = $('수신 현황').all().map((i) => i.json).filter((s) => s.storeNo);
const pending = $('대기 발주 요청').all().map((i) => i.json).filter((x) => x.requestNo);
const sentKeys = new Set($('최근 지시').all().map((i) => i.json.refKey).filter(Boolean));

const unclosed = stores.filter((s) => s.expectsData && !s.yesterdayClosingReceived);
const failing = stores.filter((s) => s.failures7d > 0);
const lines = [];
lines.push('어제(' + date + ') 마감 미수신: ' + (unclosed.length ? unclosed.map((s) => s.storeName).join(', ') : '없음'));
lines.push('최근 7일 수신 실패: ' + (failing.length ? failing.map((s) => s.storeName + ' ' + s.failures7d + '건').join(', ') : '없음'));
lines.push('처리 대기 발주 요청: ' + pending.length + '건' + (pending.length ? ' (가장 오래된 것 ' + pending[0].requestNumber + ', ' + pending[0].storeName + ')' : ''));

const notify = unclosed
  .map((s) => ({ storeNo: s.storeNo, storeName: s.storeName, refKey: 'auto-closing:' + s.storeNo + ':' + date }))
  .filter((s) => !sentKeys.has(s.refKey)); // 이미 보낸 매장은 다시 보내지 않는다
if (notify.length) lines.push('마감 요청 지시 보냄: ' + notify.map((s) => s.storeName).join(', '));

const issues = unclosed.length + failing.length + pending.length;
return [{ json: {
  date,
  today: today.toISODate(),
  notify,
  log: {
    workflow: '아침 점검',
    level: issues ? '주의' : '정보',
    title: '아침 점검: 미마감 ' + unclosed.length + '곳 · 수신 실패 ' + failing.length + '곳 · 대기 요청 ' + pending.length + '건',
    detail: lines.join('\\n'),
    executionId: $execution.id,
  },
} }];
`,
  );
  const save = h.log("점검 기록", [1120, 300]);
  const targets = h.code(
    "마감 요청 대상",
    [1340, 300],
    `
const s = $('점검 정리').first().json;
return s.notify.map((n) => ({ json: { ...n, date: s.date, today: s.today } }));
`,
  );
  const message = h.erp("마감 요청 보내기", [1560, 300], "POST", "/store-messages", {
    body: `={{ JSON.stringify({ kind: '지시', title: $json.date + ' 일마감을 보내 주세요', body: '어제 영업 마감이 본사에 들어오지 않았습니다. POS에서 일마감을 진행하고, 이미 했다면 POS 연결 상태(보내기 대기)를 확인해 주세요.', important: true, needsReply: true, dueDate: $json.today, storeNos: [$json.storeNo], refKey: $json.refKey }) }}`,
    softFail: true,
  });
  h.link(cron, status);
  h.link(manual, status);
  h.chain(status, pending, sent, summary, save, targets, message);
});

// ───────────── 3. 물류센터 보충 발주: 제안 → 담당자 승인 → 발주서 ─────────────
const reorder = workflow("cafeonReorder001", "물류센터 보충 발주", (h) => {
  const cron = h.schedule("매일 09:00", [0, 200], "0 9 * * *");
  const manual = h.manual([0, 380]);
  const stores = h.erp("사업장", [240, 300], "GET", "/stores");
  const center = h.code(
    "물류센터 찾기",
    [460, 300],
    `
const c = $input.all().map((i) => i.json).find((s) => s.storeType === '물류센터' && s.status !== '폐점');
if (!c) throw new Error('물류센터 사업장이 없습니다.');
return [{ json: { centerNo: c.storeNo, centerName: c.storeName } }];
`,
  );
  // 매개변수 식에서는 바로 앞 노드 값($json)만 쓴다 (다른 노드 참조는 Code 노드에서)
  const open = h.erp("입고 대기 발주", [680, 300], "GET", "/purchase-orders/open", {
    query: { storeNo: "={{ $json.centerNo }}" },
    always: true,
  });
  const again = h.code("물류센터 다시", [790, 460], `return [{ json: $('물류센터 찾기').first().json }];`);
  const suggestion = h.erp("보충 제안", [900, 300], "GET", "/purchase-orders/suggestion", {
    query: { storeNo: "={{ $json.centerNo }}", cycleDays: "7" },
    always: true,
  });
  const plan = h.code(
    "발주안 정리",
    [1120, 300],
    `
// 7일치 기준 제안 수량. 오늘 이미 자동화로 발주한 공급업체는 빼서 두 번 발주하지 않는다
const center = $('물류센터 찾기').first().json;
const today = $now.setZone('Asia/Seoul').toISODate();
const groups = $('보충 제안').all().map((i) => i.json).filter((g) => g.clientNo && Array.isArray(g.lines));
const open = $('입고 대기 발주').all().map((i) => i.json).filter((p) => p.poNo);
const already = new Set(open.filter((p) => (p.note || '').includes('자동화 보충 발주') && p.orderDate === today).map((p) => p.clientNo));

const orders = groups
  .filter((g) => !already.has(g.clientNo))
  .map((g) => {
    const lines = g.lines.filter((l) => l.suggestedPacks > 0).map((l) => ({
      materialNo: l.materialNo, materialName: l.materialName, packs: l.suggestedPacks, packPrice: l.packPrice,
      note: '재고 ' + l.stock + l.unit + ' · 일평균 ' + l.avgDaily + l.unit,
    }));
    return { clientNo: g.clientNo, clientName: g.clientName, expectedDate: g.expectedDate, lines, amount: lines.reduce((s, l) => s + l.packs * l.packPrice, 0) };
  })
  .filter((o) => o.lines.length);

if (!orders.length) {
  return [{ json: { route: 'none', log: {
    workflow: '물류센터 보충 발주', level: '정보', title: '물류센터 보충 발주: 필요한 품목 없음',
    detail: center.centerName + ' 재고와 입고 예정으로 7일치가 충분합니다.' + (already.size ? ' (오늘 자동화 발주 ' + already.size + '건 진행 중)' : ''),
    executionId: $execution.id,
  } } }];
}
const total = orders.reduce((s, o) => s + o.amount, 0);
const detail = orders
  .map((o) => '[' + o.clientName + '] ' + won(o.amount) + ' · 도착 예정 ' + o.expectedDate + '\\n' + o.lines.map((l) => '  · ' + l.materialName + ' ' + l.packs + '팩 (' + l.note + ')').join('\\n'))
  .join('\\n');
return [{ json: { route: 'ask', center, orders, log: {
  workflow: '물류센터 보충 발주', level: '승인 필요',
  title: '물류센터 보충 발주 ' + orders.length + '건 · ' + won(total) + ' 승인 필요',
  detail, actionUrl: $execution.resumeFormUrl, executionId: $execution.id,
} } }];
`,
  );
  const need = h.route("발주 필요?", [1340, 300], ["none", "ask"]);
  const noneLog = h.log("기록 남기기", [1560, 160]);
  const askSave = h.log("승인 요청 기록", [1560, 400]);
  const decide = h.form(
    "담당자 결정",
    [1780, 400],
    "물류센터 보충 발주 승인",
    "={{ $json.title }}\n\n{{ $json.detail }}", // 앞 노드(방금 저장한 승인 기록)의 제목 · 내용
    [dropdown("결정", ["발주", "보류"]), text("메모")],
  );
  const decision = h.code(
    "결정 정리",
    [2000, 400],
    `
const f = $input.first().json;
const route = f['결정'] === '발주' ? 'order' : f['결정'] === '보류' ? 'hold' : 'timeout';
return [{ json: { route, memo: f['메모'] || '' } }];
`,
  );
  const decided = h.route("결정별", [2220, 400], ["order", "hold", "timeout"]);
  const split = h.code(
    "공급업체별 발주서",
    [2440, 300],
    `
const p = $('발주안 정리').first().json;
const memo = $('결정 정리').first().json.memo;
return p.orders.map((o) => ({ json: { body: {
  storeNo: p.center.centerNo, clientNo: o.clientNo, expectedDate: o.expectedDate,
  note: '자동화 보충 발주 (n8n 승인)' + (memo ? ' · ' + memo : ''),
  lines: o.lines.map((l) => ({ materialNo: l.materialNo, packs: l.packs })),
} } }));
`,
  );
  const create = h.erp("발주서 만들기", [2660, 300], "POST", "/purchase-orders", { body: "={{ JSON.stringify($json.body) }}", softFail: true });
  const result = h.code(
    "발주 결과",
    [2880, 400],
    `
const d = $('결정 정리').first().json;
const logNo = $('승인 요청 기록').first().json.logNo;
let result;
if (d.route === 'order') {
  const res = $input.all().map((i) => i.json);
  const ok = res.filter((r) => r.poNumber).map((r) => r.poNumber);
  const fail = res.map(errText).filter(Boolean);
  result = '발주 ' + ok.length + '건: ' + (ok.join(', ') || '-') + (fail.length ? ' · 실패: ' + fail.join(' / ') : '');
} else if (d.route === 'hold') result = '보류 (n8n 승인 화면)' + (d.memo ? ' · ' + d.memo : '');
else result = '24시간 동안 결정이 없어 자동화 종료';
return [{ json: { logNo, result } }];
`,
  );
  const close = h.resolve("승인 기록 닫기", [3100, 400]);
  h.link(cron, stores);
  h.link(manual, stores);
  h.chain(stores, center, open, again, suggestion, plan, need);
  h.link(need, noneLog, 0);
  h.link(need, askSave, 1);
  h.chain(askSave, decide, decision, decided);
  h.link(decided, split, 0);
  h.chain(split, create, result);
  h.link(decided, result, 1);
  h.link(decided, result, 2);
  h.link(result, close);
});

// ───────────── 4. 월간 리포트: 지난달 매장 손익 요약 ─────────────
const monthly = workflow("cafeonMonthly001", "월간 손익 리포트", (h) => {
  const cron = h.schedule("매월 1일 09:00", [0, 200], "0 9 1 * *");
  const manual = h.manual([0, 380]);
  const month = h.code("대상 월", [240, 300], `return [{ json: { month: $now.setZone('Asia/Seoul').minus({ months: 1 }).toFormat('yyyy-MM') } }];`);
  const compare = h.erp("매장 손익 비교", [460, 300], "GET", "/monthly/compare", { query: { month: "={{ $json.month }}" } });
  const report = h.code(
    "리포트 정리",
    [680, 300],
    `
const r = $input.first().json;
const rows = (r.stores || []).filter((s) => s.storeType !== '물류센터');
const pct = (a, b) => (b ? Math.round((a / b) * 1000) / 10 : 0);
const warn = [];
const lines = rows.map((s) => {
  const delta = s.prevSalesAmount ? ' (전월 대비 ' + (pct(s.salesAmount - s.prevSalesAmount, s.prevSalesAmount) >= 0 ? '+' : '') + pct(s.salesAmount - s.prevSalesAmount, s.prevSalesAmount) + '%)' : '';
  if (s.franchise) return '· ' + s.storeName + ' (가맹) 매출 ' + won(s.salesAmount) + delta + ' · 본사 공급 ' + won(s.purchaseAmount);
  const cost = pct(s.foodCost, s.netSales);
  if (cost > 35) warn.push(s.storeName + ' 원가율 ' + cost + '%');
  if ((s.operatingProfit || 0) < 0) warn.push(s.storeName + ' 영업손실 ' + won(s.operatingProfit));
  return '· ' + s.storeName + ' 매출 ' + won(s.salesAmount) + delta + ' · 원가율 ' + cost + '% · 영업이익 ' + won(s.operatingProfit);
});
const direct = r.directTotal || {};
lines.push('직영 합계 매출 ' + won(direct.salesAmount) + ' · 원가율 ' + pct(direct.foodCost, direct.netSales) + '% · 영업이익 ' + won(direct.operatingProfit));
if (r.inProgress) lines.push('※ 아직 마감 전 기간이 포함되어 있습니다 (' + r.from + ' ~ ' + r.to + ')');
if (warn.length) lines.push('', '확인 필요: ' + warn.join(', '));
return [{ json: { log: {
  workflow: '월간 손익 리포트',
  level: warn.length ? '주의' : '정보',
  title: r.month + ' 매장 손익 요약',
  detail: lines.join('\\n'),
  executionId: $execution.id,
} } }];
`,
  );
  const save = h.log("기록 남기기", [900, 300]);
  h.link(cron, month);
  h.link(manual, month);
  h.chain(month, compare, report, save);
});

mkdirSync(OUT, { recursive: true });
const files = { "erp-events.json": events, "morning-check.json": morning, "center-reorder.json": reorder, "monthly-report.json": monthly };
for (const [file, wf] of Object.entries(files)) {
  writeFileSync(join(OUT, file), JSON.stringify(wf, null, 2) + "\n");
  console.log(`${file}: ${wf.name} (노드 ${wf.nodes.length}개)`);
}
