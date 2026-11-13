// 매장 POS에서 결제하면 본사 ERP 매출에 들어온다 (POS outbox → ERP 수집 API)
const { test, expect } = require("@playwright/test");
const { erp, openPos, posTab, loginErp, today, waitFor } = require("../helpers");

const STORE = "홍대";

test("POS 결제 → 본사 ERP 매출 반영", async ({ page, browser }) => {
  const store = await openPos(page, STORE);
  const day = today();
  const [status] = await erp(`/closings/status?storeNo=${store.storeNo}&from=${day}&to=${day}`);
  test.skip(status.closed, `${store.storeName}은(는) 오늘 이미 마감했습니다.`);

  const before = (await erp(`/sales/summary?storeNo=${store.storeNo}&date=${day}`)).totals;

  // POS: 판매 가능한 첫 메뉴 2개 담고 결제
  await posTab(page, "판매");
  const menu = page.locator(".pos-menu:not(.is-off)").first();
  const menuName = (await menu.locator("b").innerText()).trim();
  await menu.click();
  await menu.click();
  await expect(page.locator(".pos-line")).toContainText(menuName);
  await page.locator(".pos-pay").click();
  await expect(page.getByText(/결제 완료/)).toBeVisible();

  // ERP: 영수증 1장 · 수량 2개가 늘 때까지
  const after = await waitFor(
    async () => {
      const t = (await erp(`/sales/summary?storeNo=${store.storeNo}&date=${day}`)).totals;
      return t.receipts > before.receipts ? t : null;
    },
    { message: "본사 ERP 매출에 영수증이 들어오지 않았습니다." },
  );
  expect(after.receipts).toBe(before.receipts + 1);
  expect(after.items).toBe(before.items + 2);

  // ERP 화면: 수신 현황에서 이 매장 판매 데이터가 방금 들어온 것으로 보인다
  const hq = await browser.newPage();
  await loginErp(hq);
  await hq.goto("/erp/integration/status");
  const row = hq.getByRole("row", { name: new RegExp(store.storeName) }).first();
  await expect(row).toContainText(/초 전|1분 전|몇 초/);
  await hq.close();
});
