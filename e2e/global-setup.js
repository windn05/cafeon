// 테스트 전에 서버가 켜져 있는지 확인한다 (꺼져 있으면 무엇을 켜야 하는지 알려 주고 멈춘다)
const { ERP_API, reachable } = require("./helpers");

module.exports = async (config) => {
  const web = config.projects[0].use.baseURL;
  const checks = [
    ["본사 ERP 서버", ERP_API.replace(/\/api\/.*$/, "/api/erp/v1/automation/summary")],
    ["매장 POS 서버", process.env.E2E_POS_URL || "http://localhost:8081/api/pos/v1/me"],
    ["웹 화면", web],
  ];
  const down = [];
  for (const [name, url] of checks) if (!(await reachable(url))) down.push(`${name} (${url})`);
  if (down.length) throw new Error(`꺼져 있는 서버가 있습니다: ${down.join(", ")}. server-control.ps1 -Action start로 켜 주세요.`);
};
