import { useMemo, useState } from "react";
import { ConfigProvider, Select, Table } from "antd";
import { CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis, ZAxis } from "recharts";
import dayjs from "dayjs";
import { DashHeader, Loading, Panel } from "../../../components/Dashboard/Dashboard";
import { Badge } from "../../../components/CrudPage/CrudPage";
import { ERP_API } from "../../../config";
import { ACCENT, useFetch, useStoreChoice, won } from "../../Ops/opsShared";
import { MonthPicker, man, pct, periodLabel } from "../monthlyShared";

// 메뉴 엔지니어링 4분면
const TYPES = [
  { key: "효자 메뉴", color: "#22a06b", tone: "green", desc: "많이 팔리고 1개당 이익도 큼 → 품질 유지, 대표 메뉴로 강조" },
  { key: "박리다매", color: "#57534e", tone: "gray", desc: "많이 팔리지만 1개당 이익이 작음 → 원가 절감·가격 조정 검토" },
  { key: "숨은 보석", color: "#e2a03f", tone: "amber", desc: "1개당 이익은 크지만 덜 팔림 → 메뉴판 위치·추천으로 노출 강화" },
  { key: "정리 검토", color: "#d9534f", tone: "red", desc: "덜 팔리고 이익도 작음 → 레시피 개선 또는 메뉴 정리 검토" },
];
const TYPE_TONE = Object.fromEntries(TYPES.map((t) => [t.key, t.tone]));
const TYPE_COLOR = Object.fromEntries(TYPES.map((t) => [t.key, t.color]));

const ChartTip = ({ active, payload }) => {
  if (!active || !payload?.length) return null;
  const m = payload[0].payload;
  return (
    <div style={{ padding: "8px 10px", background: "#fff", border: "1px solid #e5e7eb", borderRadius: 8, fontSize: 12 }}>
      <b>{m.menuName}</b> · {m.type}
      <div>판매 {m.quantity.toLocaleString()}개 ({pct(m.qtyShare)})</div>
      <div>1개당 이익 {won(m.unitMargin)} · 원가율 {pct(m.costRate)}</div>
    </div>
  );
};

const MenusContainer = () => {
  const { stores } = useStoreChoice({ shopsOnly: true });
  const [storeNo, setStoreNo] = useState(null); // null = 전체 매장
  const [month, setMonth] = useState(dayjs().subtract(1, "month").format("YYYY-MM"));
  const [type, setType] = useState(null);
  const { data, loading } = useFetch(`${ERP_API}/monthly/menus`, { month, storeNo: storeNo || undefined });

  const counts = useMemo(() => {
    const c = {};
    (data?.menus || []).forEach((m) => (c[m.type] = (c[m.type] || 0) + 1));
    return c;
  }, [data]);
  const rows = (data?.menus || []).filter((m) => !type || m.type === type);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          title="메뉴 분석"
          subtitle="한 달 판매량(인기도)과 1개당 공헌이익(수익성)으로 메뉴를 4가지로 나눕니다. 메뉴판 구성·가격·프로모션을 정할 때 씁니다."
          extra={
            <div className="ops-toolbar">
              <MonthPicker value={month} onChange={setMonth} />
              <Select
                style={{ minWidth: 180 }}
                value={storeNo}
                onChange={setStoreNo}
                options={[{ value: null, label: "전체 매장" }, ...stores.map((s) => ({ value: s.storeNo, label: s.storeName }))]}
              />
            </div>
          }
        />

        {!data || loading ? (
          <Loading />
        ) : (
          <>
            <div className="me-types">
              {TYPES.map((t) => (
                <button key={t.key} type="button" className={`me-type ${type === t.key ? "is-active" : ""}`} style={{ borderLeftColor: t.color }} onClick={() => setType(type === t.key ? null : t.key)}>
                  <h4>
                    {t.key} <span>{counts[t.key] || 0}개</span>
                  </h4>
                  <p>{t.desc}</p>
                </button>
              ))}
            </div>

            <div className="dash-row">
              <Panel title="인기도 × 수익성" extra={periodLabel(data)}>
                <ResponsiveContainer width="100%" height={340}>
                  <ScatterChart margin={{ top: 10, right: 16, bottom: 18, left: 4 }}>
                    <CartesianGrid stroke="#ededed" />
                    <XAxis type="number" dataKey="qtyShare" name="판매량 비중" unit="%" tick={{ fontSize: 12 }}
                      label={{ value: "판매량 비중 (인기도) →", position: "insideBottom", offset: -10, fontSize: 12, fill: "#666666" }} />
                    <YAxis type="number" dataKey="unitMargin" name="1개당 이익" tick={{ fontSize: 12 }} width={52} tickFormatter={(v) => `${v.toLocaleString()}`}
                      label={{ value: "1개당 이익 (원) →", angle: -90, position: "insideLeft", fontSize: 12, fill: "#666666" }} />
                    <ZAxis range={[160, 160]} />
                    <ReferenceLine x={data.popularityLine} stroke="#9ca3af" strokeDasharray="4 4" />
                    <ReferenceLine y={data.marginLine} stroke="#9ca3af" strokeDasharray="4 4" />
                    <Tooltip content={<ChartTip />} />
                    <Scatter data={rows} isAnimationActive={false}>
                      {rows.map((m) => (
                        <Cell key={m.menuNo} fill={TYPE_COLOR[m.type]} />
                      ))}
                    </Scatter>
                  </ScatterChart>
                </ResponsiveContainer>
                <p className="ops-note">
                  점선 기준: 인기 = 판매량 비중 {pct(data.popularityLine)} 이상 (메뉴 수 기준 평균의 70%), 수익 = 1개당 이익 {won(data.marginLine)} 이상 (판매량 가중 평균)
                </p>
              </Panel>
              <Panel title="요약">
                <div className="kpi-list">
                  <div>
                    <span>판매량</span>
                    <strong>{data.totalQuantity.toLocaleString()}개</strong>
                  </div>
                  <div>
                    <span>공헌이익 합계</span>
                    <strong>{man(data.totalMargin)}원</strong>
                    <small>순매출 − 식자재 원가</small>
                  </div>
                  <div>
                    <span>이익 1위</span>
                    <strong style={{ fontSize: 15 }}>{data.menus[0]?.menuName || "-"}</strong>
                    <small>{data.menus[0] ? `${man(data.menus[0].margin)}원` : ""}</small>
                  </div>
                  <div>
                    <span>원가율 가장 높은 메뉴</span>
                    {(() => {
                      const top = [...data.menus].sort((a, b) => b.costRate - a.costRate)[0];
                      return (
                        <>
                          <strong style={{ fontSize: 15 }}>{top?.menuName || "-"}</strong>
                          <small>{top ? pct(top.costRate) : ""}</small>
                        </>
                      );
                    })()}
                  </div>
                </div>
              </Panel>
            </div>

            <Panel title={`메뉴별 ${type ? `· ${type}` : ""}`} bodyClassName="is-flush">
              <Table
                size="small"
                rowKey="menuNo"
                dataSource={rows}
                pagination={false}
                scroll={{ x: "max-content" }}
                columns={[
                  {
                    title: "메뉴",
                    dataIndex: "menuName",
                    render: (v, m) => (
                      <>
                        <div className="crud-strong">{v}</div>
                        <div className="crud-sub">
                          {m.category}
                          {m.cost === 0 && <span className="crud-badge red" style={{ marginLeft: 6 }}>레시피 없음 · 원가 0원으로 계산</span>}
                        </div>
                      </>
                    ),
                  },
                  { title: "유형", dataIndex: "type", filters: TYPES.map((t) => ({ text: t.key, value: t.key })), onFilter: (v, m) => m.type === v, render: (v) => <Badge value={v} tones={TYPE_TONE} /> },
                  { title: "판매량", dataIndex: "quantity", align: "right", sorter: (a, b) => a.quantity - b.quantity, render: (v) => <span className="crud-num">{v.toLocaleString()}</span> },
                  { title: "판매량 비중", dataIndex: "qtyShare", align: "right", sorter: (a, b) => a.qtyShare - b.qtyShare, render: (v) => <span className="crud-num">{pct(v)}</span> },
                  { title: "평균 판매가", dataIndex: "unitPrice", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
                  { title: "원가율", dataIndex: "costRate", align: "right", sorter: (a, b) => a.costRate - b.costRate, render: (v) => <span className="crud-num">{pct(v)}</span> },
                  { title: "1개당 이익", dataIndex: "unitMargin", align: "right", sorter: (a, b) => a.unitMargin - b.unitMargin, render: (v) => <span className="crud-num">{won(v)}</span> },
                  { title: "매출", dataIndex: "amount", align: "right", sorter: (a, b) => a.amount - b.amount, render: (v) => <span className="crud-num">{man(v)}</span> },
                  { title: "공헌이익", dataIndex: "margin", align: "right", defaultSortOrder: "descend", sorter: (a, b) => a.margin - b.margin, render: (v) => <span className="crud-num crud-strong">{man(v)}</span> },
                ]}
              />
            </Panel>
          </>
        )}
      </div>
    </ConfigProvider>
  );
};

export default MenusContainer;
