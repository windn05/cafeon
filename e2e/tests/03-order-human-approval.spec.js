// 자동 승인 한도를 넘는 요청은 n8n이 멈추고 담당자 결정을 기다린다 (Human-in-the-loop)
// POS 큰 발주 요청 → n8n '승인 필요' 기록 → ERP 자동화 기록 → n8n 승인 폼에서 반려 → ERP 반려 · 기록 닫힘
const { test, expect } = require("@playwright/test");
const { erp, exact, n8nUp, openPos, posTab, loginErp, today, waitFor } = require("../helpers");

const STORE = "강남";
const LIMIT = Number(process.env.AUTO_APPROVE_LIMIT || 300000);

test("한도를 넘는 발주는 담당자가 n8n 승인 화면에서 결정", async ({ page, browser }) => {
  test.skip(!(await n8nUp()), "n8n이 꺼져 있습니다.");
  // 한 팩 값이 가장 비싼 품목으로 한도를 넘긴다
  const material = (await erp("/materials")).sort((a, b) => b.materialPrice - a.materialPrice)[0];
  const packs = Math.ceil((LIMIT + 1) / material.materialPrice) + 1;
  const note = `E2E 승인 필요 ${Date.now()}`;

  const store = await openPos(page, STORE);
  await posTab(page, "발주 요청");
  const row = page.getByRole("row", { name: exact(material.materialName) }).first();
  await row.locator("input").fill(String(packs));
  await page.getByPlaceholder("메모 (예: 주말 대비)").fill(note);
  await page.getByRole("button", { name: /품목 발주 요청/ }).click();
  await expect(page.getByText(/발주 요청을 저장했습니다/)).toBeVisible();

  // n8n이 '승인 필요' 기록을 남길 때까지 (요청은 그대로 대기)
  const day = today();
  const request = await waitFor(
    async () => (await erp(`/order-requests?from=${day}&to=${day}&storeNo=${store.storeNo}`)).find((x) => x.note === note),
    { message: "발주 요청이 본사에 들어오지 않았습니다." },
  );
  const ask = await waitFor(
    async () => (await erp("/automation/logs?limit=50")).find((l) => l.refNumber === request.requestNumber && l.level === "승인 필요"),
    { timeout: 90_000, message: "n8n이 승인 요청 기록을 남기지 않았습니다." },
  );
  expect(ask.status).toBe("열림");
  expect(ask.detail).toContain("자동 승인 한도");
  expect((await erp(`/order-requests?from=${day}&to=${day}&storeNo=${store.storeNo}`)).find((x) => x.note === note).status).toBe("요청");

  // ERP 화면: 승인 필요 목록 → '승인 화면 열기' → n8n 폼에서 반려
  const hq = await browser.newPage();
  await loginErp(hq);
  await hq.goto("/erp/integration/automation");
  const waiting = hq.getByRole("row", { name: new RegExp(request.requestNumber) }).filter({ has: hq.getByRole("link", { name: "승인 화면 열기" }) });
  await expect(waiting).toBeVisible();
  const [form] = await Promise.all([hq.waitForEvent("popup"), waiting.getByRole("link", { name: "승인 화면 열기" }).click()]);
  await form.waitForLoadState();
  await expect(form.getByText("매장 긴급 발주 요청 승인")).toBeVisible();
  await form.locator("select").first().selectOption("반려");
  await form.locator("input[type=text]").first().fill("E2E 테스트 반려");
  await form.getByRole("button", { name: /submit|제출/i }).click();

  // ERP: 요청 반려 + 승인 기록 닫힘
  const decided = await waitFor(
    async () => {
      const r = (await erp(`/order-requests?from=${day}&to=${day}&storeNo=${store.storeNo}`)).find((x) => x.note === note);
      return r.status !== "요청" ? r : null;
    },
    { message: "폼 결정이 ERP에 반영되지 않았습니다." },
  );
  expect(decided.status).toBe("반려");
  expect(decided.rejectReason).toBe("E2E 테스트 반려");
  const closed = await waitFor(async () => {
    const l = (await erp("/automation/logs?limit=50")).find((x) => x.logNo === ask.logNo);
    return l.status === "처리됨" ? l : null;
  });
  expect(closed.result).toContain("반려");

  await hq.reload();
  await expect(hq.getByText(closed.result).first()).toBeVisible();
  await hq.close();
});
