// 규칙에 맞는 긴급 발주 요청은 n8n이 바로 승인한다
// POS 발주 요청 → ERP 저장 + 웹훅 → n8n 서명 확인 · 규칙 판단 → ERP 승인 API → POS 본사 알림
const { test, expect } = require("@playwright/test");
const { erp, exact, n8nUp, openPos, posTab, loginErp, today, waitFor } = require("../helpers");

const STORE = "강남";

// 물류센터 재고가 넉넉하고 값이 싼 품목 (자동 승인 조건을 만족하도록)
const pickCheapCenterItem = async () => {
  const center = (await erp("/stores")).find((s) => s.storeType === "물류센터");
  const supply = new Map((await erp("/materials")).map((m) => [m.materialNo, m]));
  const rows = (await erp(`/inventory/stock?storeNo=${center.storeNo}`))
    .filter((r) => supply.get(r.materialNo)?.supplyType === "물류센터" && r.packs >= 3 && r.materialPrice <= 50000)
    .sort((a, b) => a.materialPrice - b.materialPrice);
  if (!rows.length) throw new Error("물류센터에 자동 승인 테스트용 재고가 없습니다.");
  return rows[0];
};

test("작은 긴급 발주는 n8n이 자동 승인", async ({ page, browser }) => {
  test.skip(!(await n8nUp()), "n8n이 꺼져 있습니다.");
  const item = await pickCheapCenterItem();
  const note = `E2E 자동 승인 ${Date.now()}`;

  // POS: 1팩 발주 요청
  const store = await openPos(page, STORE);
  await posTab(page, "발주 요청");
  const row = page.getByRole("row", { name: exact(item.materialName) }).first();
  await row.locator("input").fill("1");
  await page.getByPlaceholder("메모 (예: 주말 대비)").fill(note);
  await page.getByRole("button", { name: /품목 발주 요청/ }).click();
  await expect(page.getByText(/발주 요청을 저장했습니다/)).toBeVisible();

  // ERP: 요청이 들어오고 n8n이 승인할 때까지
  const day = today();
  const request = await waitFor(
    async () => {
      const list = await erp(`/order-requests?from=${day}&to=${day}&storeNo=${store.storeNo}`);
      const r = list.find((x) => x.note === note);
      return r && r.status !== "요청" ? r : null;
    },
    { timeout: 90_000, message: "n8n이 발주 요청을 처리하지 않았습니다." },
  );
  expect(request.status).toBe("승인");
  expect(request.decidedBy).toBe("자동화(n8n)");
  expect(request.poNumbers).toMatch(/^(PO|SO)-/);

  // ERP 화면: 자동화 기록에 자동 승인이 남는다
  const hq = await browser.newPage();
  await loginErp(hq);
  await hq.goto("/erp/integration/automation");
  await expect(hq.getByText(`${request.requestNumber} 자동 승인`).first()).toBeVisible();
  await hq.close();

  // POS: 본사 알림에 승인 결과가 온다
  await page.getByRole("button", { name: /지금 동기화/ }).click();
  await posTab(page, "본사 알림");
  await expect(page.getByText(`발주 요청 승인 · ${request.requestNumber}`).first()).toBeVisible({ timeout: 30_000 });
});
