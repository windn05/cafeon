// 매장 POS ↔ 본사 ERP ↔ n8n 자동화 통합 E2E 테스트
// 서버 네 개(ERP 8080 · POS 8081 · 웹 3000 · n8n 5678)를 먼저 켠 뒤 실행한다: npm test
// 주의: 개발 DB에 실제로 판매 · 발주 요청 · 마감을 남긴다.
const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  globalSetup: require.resolve("./global-setup"),
  timeout: 180_000,
  expect: { timeout: 20_000 },
  // 같은 매장 · 같은 DB를 쓰므로 한 번에 하나씩
  workers: 1,
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_WEB_URL || "http://localhost:3000",
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
