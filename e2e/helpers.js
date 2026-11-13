// E2E 공통: 설정 읽기 · ERP API(자동화 키) · POS 등록 · ERP 로그인 · 기다리기
const fs = require("fs");
const path = require("path");
const { expect } = require("@playwright/test");

const ROOT = path.resolve(__dirname, "..");

const readProps = (file) => {
  if (!fs.existsSync(file)) return {};
  const out = {};
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = /^\s*([\w.-]+)\s*=\s*(.*)$/.exec(line);
    if (m) out[m[1]] = m[2].trim();
  }
  return out;
};
// ${ENV:기본값} 형태면 환경 변수 → 기본값 순서로 고른다
const springValue = (v) => {
  const m = /^\$\{([A-Z_]+):(.*)\}$/.exec(v || "");
  return m ? process.env[m[1]] || m[2] : v;
};

const erpLocal = readProps(path.join(ROOT, "erp-server", "local.properties"));
const erpApp = readProps(path.join(ROOT, "erp-server", "src", "main", "resources", "application.properties"));

const ERP_API = process.env.E2E_ERP_API || "http://localhost:8080/api/erp/v1";
const N8N_URL = process.env.E2E_N8N_URL || "http://localhost:5678";
const AUTOMATION_KEY = process.env.E2E_AUTOMATION_KEY || erpLocal["automation.api-key"];

// 테스트 계정: 환경 변수(E2E_ERP_ID / E2E_ERP_PASSWORD)가 없으면 개발용 초기 관리자
const erpAccount = () => ({
  id: process.env.E2E_ERP_ID || springValue(erpApp["app.init-admin.user-id"]),
  password: process.env.E2E_ERP_PASSWORD || springValue(erpApp["app.init-admin.password"]),
});

// 본사 데이터 확인은 자동화 키로 (조회 + 정해진 처리만 가능)
const erp = async (pathname, { method = "GET", body } = {}) => {
  const res = await fetch(ERP_API + pathname, {
    method,
    headers: { "X-Automation-Key": AUTOMATION_KEY, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${pathname} → ${res.status} ${text.slice(0, 200)}`);
  return text ? JSON.parse(text) : null;
};

const reachable = async (url) => {
  try {
    const res = await fetch(url);
    return res.status < 500;
  } catch {
    return false;
  }
};
const n8nUp = () => reachable(`${N8N_URL}/healthz`);

// 서울 기준 오늘 (YYYY-MM-DD)
const today = () => new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);

const waitFor = async (fn, { timeout = 60_000, interval = 1_500, message = "조건을 기다리다 시간 초과" } = {}) => {
  const end = Date.now() + timeout;
  let last;
  while (Date.now() < end) {
    last = await fn();
    if (last) return last;
    await new Promise((r) => setTimeout(r, interval));
  }
  throw new Error(message);
};

const storeByName = async (name) => {
  const s = (await erp("/integration/status")).find((x) => x.storeName.includes(name));
  if (!s) throw new Error(`사업장을 찾을 수 없습니다: ${name}`);
  if (!s.integrationKey) throw new Error(`${s.storeName}에 연동 키가 없습니다.`);
  return s;
};

// 새 브라우저 컨텍스트에서 매장 연동 키로 POS 기기 등록
const openPos = async (page, storeName) => {
  const store = await storeByName(storeName);
  // 본사 중요 공지 · 지시(예: 자동화의 마감 요청)는 POS에 확인 창으로 뜬다 → 뜰 때마다 확인하고 이어 간다
  // (안 읽은 중요 알림이 여러 개면 창이 연달아 뜬다)
  await page.addLocatorHandler(
    page.getByRole("button", { name: "확인했습니다" }),
    async (button) => {
      for (let i = 0; i < 30 && (await button.isVisible().catch(() => false)); i++) {
        await button.click();
        await page.waitForTimeout(300);
      }
    },
    { noWaitAfter: true },
  );
  await page.goto("/pos");
  await page.getByPlaceholder(/^sk_/).fill(store.integrationKey);
  await page.getByRole("button", { name: "등록" }).click();
  await expect(page.getByRole("tab", { name: "판매" })).toBeVisible();
  return store;
};

// 화면 글자를 그대로 찾는 정규식 (괄호 등 특수 문자 이스케이프)
const exact = (s) => new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));

const posTab = async (page, name) => {
  await page.getByRole("tab", { name: new RegExp(`^${name}`) }).click();
};

// 본사 ERP 로그인
const loginErp = async (page) => {
  const { id, password } = erpAccount();
  await page.goto("/");
  await page.getByPlaceholder("아이디").fill(id);
  await page.getByPlaceholder("비밀번호").fill(password);
  await page.getByRole("button", { name: "로그인" }).click();
  await page.waitForURL(/\/erp/);
};

module.exports = { ROOT, ERP_API, N8N_URL, exact, erp, n8nUp, reachable, today, waitFor, storeByName, openPos, posTab, loginErp };
