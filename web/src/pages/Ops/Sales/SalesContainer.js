import { useMemo, useState } from "react";
import { ConfigProvider, Table } from "antd";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { DashHeader, EmptyState, Loading, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { ERP_API } from "../../../config";
import { ACCENT, DayPicker, StoreSelect, changeRate, dayLabel, todayStr, useFetch, useStoreChoice, won } from "../opsShared";

const HOURS = Array.from({ length: 15 }, (_, i) => i + 7); // 07 ~ 21시

const Change = ({ now, before, suffix = "지난주 같은 요일 대비" }) => {
  const rate = changeRate(now, before);
  if (rate === null) return <>{suffix} 기록 없음</>;
  return (
    <>
      <span className={`ops-change ${rate >= 0 ? "is-up" : "is-down"}`}>
        {rate >= 0 ? "▲" : "▼"} {Math.abs(rate)}%
      </span>{" "}
      {suffix}
    </>
  );
};

const Bars = ({ rows, total }) => (
  <ul className="ops-bars">
    {rows.map((r) => (
      <li key={r.name}>
        <span>{r.name}</span>
        <span className="bar">
          <i style={{ width: `${total ? (r.amount / total) * 100 : 0}%` }} />
        </span>
        <span className="val">
          {won(r.amount)}
          <small>{total ? Math.round((r.amount / total) * 100) : 0}%</small>
        </span>
      </li>
    ))}
  </ul>
);

const SalesContainer = () => {
  const { stores, storeNo, setStoreNo } = useStoreChoice({ shopsOnly: true });
  const [date, setDate] = useState(todayStr());
  const params = { storeNo, date };
  const summary = useFetch(`${ERP_API}/sales/summary`, params, !!storeNo);
  const receipts = useFetch(`${ERP_API}/sales/receipts`, params, !!storeNo);

  const s = summary.data;
  const t = s?.totals;
  const lw = s?.lastWeek;
  const compareLabel = s?.compareUntil ? `지난주 같은 요일 ${s.compareUntil}까지 대비` : "지난주 같은 요일 대비";
  const hourly = useMemo(() => {
    const map = Object.fromEntries((s?.byHour || []).map((h) => [h.hour, h]));
    return HOURS.map((h) => ({ hour: `${h}시`, amount: map[h]?.amount || 0, receipts: map[h]?.receipts || 0 }));
  }, [s]);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          title="매출 현황"
          subtitle={`${dayLabel(date)} · 매장 POS에서 받은 판매 데이터 ${s?.closed ? "· 마감 수신" : date === todayStr() ? "· 영업 중 (실시간 수신)" : "· 마감 미수신"}`}
          extra={
            <div className="ops-toolbar">
              <StoreSelect stores={stores} value={storeNo} onChange={setStoreNo} />
              <DayPicker value={date} onChange={setDate} />
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard
            label="매출"
            value={t ? Math.round(t.amount / 10000).toLocaleString() : "-"}
            unit="만 원"
            hint={t ? <Change now={t.amount} before={lw?.amount} suffix={compareLabel} /> : " "}
          />
          <StatCard
            label="영수증 (손님 수)"
            value={t ? t.receipts.toLocaleString() : "-"}
            unit="건"
            hint={t ? <Change now={t.receipts} before={lw?.receipts} suffix={compareLabel} /> : " "}
          />
          <StatCard
            label="객단가"
            value={t ? t.avgTicket.toLocaleString() : "-"}
            unit="원"
            hint={t ? `판매 ${t.items.toLocaleString()}잔(개)` : " "}
          />
          <StatCard
            label="원가율"
            value={t ? t.costRate : "-"}
            unit="%"
            tone={t?.costRate >= 35 ? "warn" : undefined}
            hint={t ? `식자재 원가 ${won(t.cost)}` : " "}
          />
        </div>

        <div className="dash-row">
          <Panel title="시간대별 매출">
            {!s ? (
              <Loading />
            ) : (
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={hourly} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#ededed" />
                  <XAxis dataKey="hour" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
                  <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} tickFormatter={(v) => (v ? `${Math.round(v / 10000)}만` : 0)} width={40} />
                  <Tooltip formatter={(v, n) => (n === "amount" ? [won(v), "매출"] : [v, n])} />
                  <Bar dataKey="amount" fill={ACCENT} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Panel>
          <Panel title="결제수단 · 판매 채널">
            {!s ? (
              <Loading />
            ) : !t.amount ? (
              <EmptyState text="판매 기록이 없습니다." />
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
                <Bars rows={s.byPayment} total={t.amount} />
                <Bars rows={s.byChannel} total={t.amount} />
              </div>
            )}
          </Panel>
        </div>

        <div className="ops-grid-2">
          <Panel title="메뉴별 판매" bodyClassName="is-flush">
            <Table
              size="small"
              rowKey="menuNo"
              loading={summary.loading}
              dataSource={s?.byMenu || []}
              pagination={{ pageSize: 10, hideOnSinglePage: true }}
              locale={{ emptyText: "판매 기록이 없습니다." }}
              columns={[
                {
                  title: "메뉴",
                  dataIndex: "menuName",
                  render: (v, r) => (
                    <>
                      <div className="crud-strong">{v}</div>
                      <div className="crud-sub">{r.category}</div>
                    </>
                  ),
                },
                { title: "수량", dataIndex: "quantity", align: "right", sorter: (a, b) => a.quantity - b.quantity, render: (v) => <span className="crud-num">{v}</span> },
                { title: "매출", dataIndex: "amount", align: "right", defaultSortOrder: "descend", sorter: (a, b) => a.amount - b.amount, render: (v) => <span className="crud-num">{won(v)}</span> },
                {
                  title: "비중",
                  key: "share",
                  align: "right",
                  render: (_, r) => <span className="crud-num crud-sub">{t?.amount ? `${((r.amount / t.amount) * 100).toFixed(1)}%` : "-"}</span>,
                },
              ]}
            />
          </Panel>
          <Panel title={`영수증 ${receipts.data ? `${receipts.data.length}건` : ""}`} bodyClassName="is-flush">
            <Table
              size="small"
              rowKey="receiptNo"
              loading={receipts.loading}
              dataSource={receipts.data || []}
              pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: true }}
              locale={{ emptyText: "판매 기록이 없습니다." }}
              expandable={{
                expandedRowRender: (r) => (
                  <ul className="ops-bars" style={{ padding: "4px 8px" }}>
                    {r.items.map((it, i) => (
                      <li key={i} style={{ gridTemplateColumns: "1fr auto auto" }}>
                        <span>{it.menuName}</span>
                        <span className="crud-sub">× {it.quantity}</span>
                        <span className="val">{won(it.amount)}</span>
                      </li>
                    ))}
                  </ul>
                ),
              }}
              columns={[
                { title: "시각", dataIndex: "saleTime", render: (v) => <span className="crud-num">{v}</span> },
                { title: "결제", dataIndex: "paymentMethod" },
                { title: "채널", dataIndex: "channel" },
                { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
              ]}
            />
          </Panel>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default SalesContainer;
