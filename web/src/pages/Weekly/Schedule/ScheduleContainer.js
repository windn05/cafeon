import { useState } from "react";
import { ConfigProvider, Empty } from "antd";
import dayjs from "dayjs";
import { DashHeader, Loading, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { ERP_API } from "../../../config";
import { ACCENT, StoreSelect, num, todayStr, useFetch, useStoreChoice, won } from "../../Ops/opsShared";
import "../weekly.css";

const WARN_TONE = { "주 52시간 초과": "red", "주 1회 휴일 없음": "red", "주휴수당 대상": "amber" };

const ScheduleContainer = () => {
  const { stores, storeNo, setStoreNo } = useStoreChoice({ types: ["직영점"] });
  const [week, setWeek] = useState(todayStr()); // 그 주의 아무 날짜 (월요일은 아래에서 계산)
  const { data, loading } = useFetch(`${ERP_API}/shifts/week`, { storeNo, week }, !!storeNo);

  const monday = dayjs(week).day() === 0 ? dayjs(week).subtract(6, "day") : dayjs(week).day(1);
  const days = Array.from({ length: 7 }, (_, i) => monday.add(i, "day").format("YYYY-MM-DD"));
  const thisMonday = dayjs().day() === 0 ? dayjs().subtract(6, "day") : dayjs().day(1);
  const isThisWeek = monday.isSame(thisMonday, "day");
  const warnCount = (data?.staff || []).reduce((s, p) => s + p.warnings.filter((w) => w !== "주휴수당 대상").length, 0);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          subtitle="직영점이 매장 시스템에 등록한 근무표입니다. 본사는 근무시간 · 52시간 · 주휴수당 · 파트타임 인건비를 확인합니다. (가맹점 근무는 점주 관리)"
          extra={
            <div className="ops-toolbar">
              <StoreSelect stores={stores} value={storeNo} onChange={setStoreNo} />
              <div className="ops-day">
                <button type="button" onClick={() => setWeek(monday.subtract(7, "day").format("YYYY-MM-DD"))} aria-label="지난주">
                  ‹
                </button>
                <span className="sch-week-label">
                  {monday.format("M.D")} ~ {monday.add(6, "day").format("M.D")}
                  {isThisWeek ? " (이번 주)" : ""}
                </span>
                <button type="button" onClick={() => setWeek(monday.add(7, "day").format("YYYY-MM-DD"))} aria-label="다음 주">
                  ›
                </button>
                {!isThisWeek && (
                  <button type="button" className="ops-day-today" onClick={() => setWeek(thisMonday.format("YYYY-MM-DD"))}>
                    이번 주
                  </button>
                )}
              </div>
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard label="근무 인원" value={data ? data.staff.length : "-"} unit="명" hint={data ? `이 주 근무 ${data.staff.filter((s) => s.days).length}명` : " "} />
          <StatCard label="총 근무시간" value={data ? num(data.totalHours) : "-"} unit="시간" hint="휴게 시간 제외" />
          <StatCard
            label="파트타임 인건비 (예상)"
            value={data ? Math.round(data.partTimeCost / 10000).toLocaleString() : "-"}
            unit="만 원"
            hint={data ? `시급 × 근무시간 + 주휴수당 · ${won(data.partTimeCost)}` : " "}
          />
          <StatCard label="근무 주의" value={warnCount} unit="건" tone={warnCount ? "warn" : undefined} hint="주 52시간 초과 · 휴일 없음" />
        </div>

        <Panel bodyClassName="is-flush">
          {!data || loading ? (
            <Loading />
          ) : !data.staff.length ? (
            <div style={{ padding: 32 }}>
              <Empty description="이 매장 소속 직원이 없습니다. 직원 관리에서 소속 사업장을 지정하세요." />
            </div>
          ) : (
            <div className="sch-wrap">
              <table className="sch-table">
                <thead>
                  <tr>
                    <th className="sch-person" style={{ textAlign: "left" }}>
                      직원
                    </th>
                    {days.map((d) => {
                      const dd = dayjs(d);
                      return (
                        <th key={d} className={`${d === todayStr() ? "is-today" : ""} ${dd.day() === 0 || dd.day() === 6 ? "is-weekend" : ""}`}>
                          {dd.format("M/D (dd)")}
                        </th>
                      );
                    })}
                    <th className="sch-total">주간 합계</th>
                  </tr>
                </thead>
                <tbody>
                  {data.staff.map((p) => {
                    const byDate = Object.fromEntries(p.shifts.map((s) => [s.workDate, s]));
                    return (
                      <tr key={p.userNo}>
                        <td className="sch-person">
                          <div className="name">{p.userName}</div>
                          <div className="sub">
                            {p.roleName} · {p.employmentType || "-"}
                            {p.hourlyWage ? ` · 시급 ${won(p.hourlyWage)}` : ""}
                          </div>
                        </td>
                        {days.map((d) => {
                          const s = byDate[d];
                          return (
                            <td key={d} className={`sch-cell ${d === todayStr() ? "is-today" : ""}`}>
                              {s ? (
                                <div className={`sch-chip is-readonly pos-${s.position}`}>
                                  <b>
                                    {s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)}
                                  </b>
                                  <span>
                                    {s.position} · {num(s.hours)}h{s.note ? " ·" : ""}
                                  </span>
                                  {s.note && <span>{s.note}</span>}
                                </div>
                              ) : (
                                <span className="sch-off">휴무</span>
                              )}
                            </td>
                          );
                        })}
                        <td className="sch-total">
                          <div className="hours">{num(p.hours)}h</div>
                          <div className="sub">
                            {p.days}일{p.employmentType === "파트타임" ? ` · ${won(p.wage + p.holidayPay)}` : ""}
                          </div>
                          {p.holidayPay > 0 && <div className="sub">주휴 {won(p.holidayPay)} 포함</div>}
                          <div className="sch-warn">
                            {p.warnings.map((w) => (
                              <span key={w} className={`crud-badge ${WARN_TONE[w] || "gray"}`}>
                                {w}
                              </span>
                            ))}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot>
                  <tr>
                    <td style={{ textAlign: "left" }}>하루 합계</td>
                    {data.days.map((d) => (
                      <td key={d.date}>
                        {d.people}명 · {num(d.hours)}h
                      </td>
                    ))}
                    <td style={{ textAlign: "right", fontWeight: 700 }}>{num(data.totalHours)}h</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </Panel>

      </div>
    </ConfigProvider>
  );
};

export default ScheduleContainer;
