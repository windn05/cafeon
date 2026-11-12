import { CheckCircleFilled, RightOutlined } from "@ant-design/icons";
import { Bar, CartesianGrid, Cell, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import dayjs from "dayjs";
import { DashHeader, EmptyState, Loading, Panel, StatCard, WeatherPanel, todayLabel } from "../../components/Dashboard/Dashboard";
import { ACCENT, CHART } from "../../theme/erpTheme";
import "./home.css";

const won = (v) => `${Math.round(Number(v) || 0).toLocaleString()}원`;
const man = (v) => `${Math.round((Number(v) || 0) / 10000).toLocaleString()}만`;
const pct = (v) => `${Number(v || 0).toFixed(1)}%`;

// 증감률 표시 (한국식: 오르면 빨강, 내리면 파랑)
const Change = ({ now, before, label }) => {
  if (!before) return <>{label} 기록 없음</>;
  const r = ((now - before) / before) * 100;
  return (
    <>
      <span className={`home-change ${r >= 0 ? "is-up" : "is-down"}`}>
        {r >= 0 ? "+" : "−"}{Math.abs(r).toFixed(1)}%
      </span>{" "}
      {label}
    </>
  );
};

const LEVEL = { danger: "긴급", warn: "확인", info: "안내" };

// 오늘 할 일
const TodoPanel = ({ todos, go }) => (
  <Panel title="오늘 할 일" extra={todos.length ? `${todos.length}건` : ""} bodyClassName="is-flush">
    {todos.length === 0 ? (
      <div className="todo-empty">
        <CheckCircleFilled /> 오늘 챙길 일이 없습니다. 모든 매장이 정상입니다.
      </div>
    ) : (
      <ul className="todo-list">
        {todos.map((t, i) => (
          <li key={i}>
            <button type="button" onClick={() => go(t.link, t.storeNo)}>
              <span className={`todo-level is-${t.level}`}>{LEVEL[t.level]}</span>
              <span className="todo-text">
                <b>{t.title}</b>
                <small>
                  {t.area} · {t.detail}
                </small>
              </span>
              <RightOutlined className="todo-go" />
            </button>
          </li>
        ))}
      </ul>
    )}
  </Panel>
);

const HomePresenter = ({ dash, notices, weather, cityCoordinates, selectedCity, onSelectCity, go }) => {
  const ready = dash && !dash.error;
  const y = ready ? dash.yesterdayTotal : null;
  const lw = ready ? dash.lastWeekTotal : null;
  const mtd = ready ? dash.monthToDate : null;
  const pm = ready ? dash.prevMonthToDate : null;
  const pnl = ready ? dash.monthPnl : null;
  const trend = ready
    ? dash.trend.map((d) => ({ day: dayjs(d.date).format("M/D"), full: dayjs(d.date).format("M월 D일 (dd)"), 매출: d.amount, "지난주 같은 요일": d.lastWeek }))
    : [];
  // 이번 달 매출이 있는 매장만 순위에 (휴점 매장 제외)
  const ranking = ready ? dash.stores.filter((s) => s.monthSales > 0).sort((a, b) => b.monthSales - a.monthSales) : [];
  // 어제 실적: 매출 많은 순, 판매 없는 매장은 맨 아래
  const yesterdayRows = ready ? [...dash.stores].sort((a, b) => b.yesterdayAmount - a.yesterdayAmount) : [];
  const yLabel = ready ? dayjs(dash.yesterday).format("M/D(dd)") : "";

  return (
    <>
      <div className="dash dash-erp">
        <DashHeader subtitle={`${todayLabel()} · 전 매장 운영 현황 (매장 시스템에서 받은 어제 마감 기준)`} />

        <div className="stat-grid">
          <StatCard
            keyed
            label={`어제 매출 · ${yLabel}`}
            value={y ? man(y.amount) : "-"}
            unit="원"
            hint={y ? <Change now={y.amount} before={lw?.amount} label="지난주 같은 요일 대비" /> : " "}
          />
          <StatCard
            label="이번 달 누적 매출"
            value={mtd ? man(mtd.amount) : "-"}
            unit="원"
            hint={mtd ? <Change now={mtd.amount} before={pm?.amount} label={`지난달 같은 기간 대비 · 월말 예상 ${man(dash.monthForecast)}`} /> : " "}
          />
          <StatCard
            label="직영점 영업이익 (이번 달 추정)"
            value={pnl ? man(pnl.operatingProfit) : "-"}
            unit="원"
            tone={pnl && pnl.operatingProfit < 0 ? "warn" : undefined}
            hint={pnl ? `직영 영업이익률 ${pct(pnl.operatingMargin)} · 가맹점 비용은 점주 관리라 제외` : " "}
          />
          <StatCard
            label="직영점 식자재 원가율"
            value={pnl ? pnl.foodCostRate : "-"}
            unit="%"
            tone={pnl && pnl.foodCostRate - pnl.theoreticalRate > 1 ? "warn" : undefined}
            hint={pnl ? `레시피 기준 ${pct(pnl.theoreticalRate)} · 차이 ${pct(pnl.foodCostRate - pnl.theoreticalRate)}` : " "}
          />
        </div>

        <div className="dash-row">
          {ready ? <TodoPanel todos={dash.todos} go={go} /> : <Panel title="오늘 할 일">{dash?.error ? <EmptyState text="불러오지 못했습니다." /> : <Loading />}</Panel>}
          <WeatherPanel weather={weather} cityCoordinates={cityCoordinates} selectedCity={selectedCity} onSelectCity={onSelectCity} />
        </div>

        <div className="dash-row">
          <Panel
            title="최근 2주 매출 · 전 매장"
            extra={
              <span className="chart-legend">
                <i className="lg-bar" />그날 매출
                <i className="lg-line" />지난주 같은 요일
              </span>
            }
          >
            {!ready ? (
              <Loading />
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <ComposedChart data={trend} margin={{ top: 12, right: 4, left: 0, bottom: 0 }} barCategoryGap="28%">
                  <CartesianGrid vertical={false} stroke={CHART.grid} />
                  <XAxis dataKey="day" tick={{ fontSize: 12, fill: CHART.axis }} tickLine={false} axisLine={false} interval={0} tickMargin={8} />
                  <YAxis tick={{ fontSize: 12, fill: CHART.axis }} tickLine={false} axisLine={false} width={52} tickFormatter={(v) => `${Math.round(v / 10000).toLocaleString()}만`} />
                  <Tooltip formatter={(v) => won(v)} labelFormatter={(l, p) => p?.[0]?.payload.full || l} cursor={{ fill: "rgba(23,23,23,0.04)" }} contentStyle={{ borderRadius: 8, border: "1px solid #ebebeb", boxShadow: "0 8px 24px rgba(0,0,0,0.06)", fontSize: 13 }} />
                  <Bar dataKey="매출" radius={[4, 4, 0, 0]} maxBarSize={36}>
                    {trend.map((d, i) => (
                      <Cell key={d.day} fill={i === trend.length - 1 ? ACCENT : CHART.bar} />
                    ))}
                  </Bar>
                  <Line dataKey="지난주 같은 요일" stroke={CHART.line} strokeWidth={1.5} strokeDasharray="4 4" dot={false} activeDot={{ r: 3 }} />
                </ComposedChart>
              </ResponsiveContainer>
            )}
          </Panel>
          <Panel title="이번 달 매장 순위" extra={ready ? `${dash.monthElapsedDays}일 기준` : ""} bodyClassName="is-flush">
            {!ready ? (
              <Loading />
            ) : (
              <ol className="rank-list">
                {ranking.map((s, i) => (
                  <li key={s.storeNo}>
                    <button type="button" onClick={() => go("/erp/analysis/pnl", s.storeNo)}>
                      <span className="rank-no">{i + 1}</span>
                      <span className="rank-name">
                        <b>{s.storeName}</b>
                        <small>
                          {s.franchise ? (
                            <>가맹점 · 본사 공급 {man(s.monthPurchase)}</>
                          ) : (
                            <>
                              이익률 <span className={s.monthOperatingProfit < 0 ? "pnl-neg" : ""}>{pct(s.monthOperatingMargin)}</span> · 원가율 {pct(s.monthFoodCostRate)}
                            </>
                          )}
                        </small>
                      </span>
                      <span className="rank-val">{man(s.monthSales)}</span>
                      <span className="rank-bar">
                        <i style={{ width: `${(s.monthSales / ranking[0].monthSales) * 100}%` }} />
                      </span>
                    </button>
                  </li>
                ))}
              </ol>
            )}
          </Panel>
        </div>

        <div className="dash-row">
          <Panel title={`어제 매장별 실적 · ${yLabel}`} bodyClassName="is-flush">
            {!ready ? (
              <Loading />
            ) : (
              <div className="table-scroll">
                <table className="dash-table">
                  <thead>
                    <tr>
                      <th>매장</th>
                      <th className="cell-right">매출</th>
                      <th className="cell-right">지난주 대비</th>
                      <th className="cell-right">영수증</th>
                      <th className="cell-right">객단가</th>
                      <th className="cell-right">원가율</th>
                      <th>일 마감</th>
                    </tr>
                  </thead>
                  <tbody>
                    {yesterdayRows.map((s) => (
                      <tr key={s.storeNo} className="cmp-row" onClick={() => go("/erp/monitor/sales", s.storeNo)} title="매출 현황 보기">
                        <td>
                          <div className="cell-main">{s.storeName}</div>
                          <div className="cell-sub">
                            {s.storeType}
                            {s.status !== "영업중" ? ` · ${s.status}` : ""}
                          </div>
                        </td>
                        <td className="cell-num cell-right">{s.yesterdayAmount ? won(s.yesterdayAmount) : "-"}</td>
                        <td className="cell-num cell-right">{s.yesterdayAmount ? <Change now={s.yesterdayAmount} before={s.lastWeekAmount} label="" /> : "-"}</td>
                        <td className="cell-num cell-right">{s.yesterdayReceipts ? `${s.yesterdayReceipts}건` : "-"}</td>
                        <td className="cell-num cell-right">{s.yesterdayAvgTicket ? won(s.yesterdayAvgTicket) : "-"}</td>
                        <td className="cell-num cell-right">{s.yesterdayAmount ? pct(s.yesterdayCostRate) : "-"}</td>
                        <td>
                          {!s.yesterdayAmount ? (
                            <span className="status-badge badge-gray">{s.status === "영업중" ? "판매 없음" : s.status}</span>
                          ) : !s.yesterdayClosed ? (
                            <span className="status-badge badge-amber">마감 미수신</span>
                          ) : s.cashDiff ? (
                            <span className="status-badge badge-amber">시재 {s.cashDiff > 0 ? "+" : ""}{s.cashDiff.toLocaleString()}</span>
                          ) : (
                            <span className="status-badge badge-green">마감</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>
          <Panel title="카페 · 외식 업계 뉴스" bodyClassName="is-flush">
            {notices.length === 0 ? (
              <EmptyState text="표시할 뉴스가 없습니다." />
            ) : (
              <ul className="link-list">
                {notices.slice(0, 6).map((item) => (
                  <li key={item.id}>
                    <a href={item.link} target="_blank" rel="noopener noreferrer">
                      <span className="link-title">{item.title}</span>
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      </div>
    </>
  );
};

export default HomePresenter;
