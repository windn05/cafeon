// POS 일마감 → 본사 마감 기록 → POS 마감 취소 → 본사 마감 삭제 + n8n '마감 취소' 주의 기록
const { test, expect } = require("@playwright/test");
const { erp, n8nUp, openPos, posTab, loginErp, today, waitFor } = require("../helpers");

const STORE = "홍대";

test("POS 마감 취소가 본사 마감과 자동화 기록에 반영", async ({ page, browser }) => {
  const store = await openPos(page, STORE);
  const day = today();
  const closing = () => erp(`/closings/status?storeNo=${store.storeNo}&from=${day}&to=${day}`).then((r) => r[0]);
  test.skip((await closing()).closed, `${store.storeName}은(는) 오늘 이미 마감했습니다.`);

  // POS: 예상 시재 그대로 마감
  await posTab(page, "일 마감");
  await page.getByRole("button", { name: "예상 금액 넣기" }).click();
  await page.getByRole("button", { name: "마감하고 본사로 보내기" }).click();
  await page.getByRole("button", { name: "마감", exact: true }).click();
  await expect(page.getByText(/오늘 마감을 저장했습니다/)).toBeVisible();
  await waitFor(async () => (await closing()).closed, { message: "본사에 마감이 들어오지 않았습니다." });

  // POS: 마감 취소 (사유: 마감 후 추가 손님)
  await page.getByRole("button", { name: "마감 취소" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByPlaceholder("메모 (선택)").fill("E2E 테스트");
  await dialog.getByRole("button", { name: "마감 취소" }).click();
  await expect(page.getByText(/마감을 취소했습니다/)).toBeVisible();
  await waitFor(async () => !(await closing()).closed, { message: "본사 마감이 취소되지 않았습니다." });

  // n8n: 마감 취소 이벤트를 '주의'로 기록
  if (await n8nUp()) {
    const log = await waitFor(
      async () =>
        (await erp("/automation/logs?limit=50")).find((l) => l.title === `${store.storeName} ${day} 마감 취소` && (l.detail || "").includes("E2E 테스트")),
      { timeout: 60_000, message: "n8n이 마감 취소를 기록하지 않았습니다." },
    );
    expect(log.level).toBe("주의");
    const hq = await browser.newPage();
    await loginErp(hq);
    await hq.goto("/erp/integration/automation");
    await expect(hq.getByText(`${store.storeName} ${day} 마감 취소`).first()).toBeVisible();
    await hq.close();
  }
});
