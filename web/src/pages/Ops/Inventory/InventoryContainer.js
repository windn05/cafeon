import { useMemo, useState } from "react";
import { ConfigProvider, DatePicker, Select, Switch, Table, Tabs } from "antd";
import dayjs from "dayjs";
import { DashHeader, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { Badge } from "../../../components/CrudPage/CrudPage";
import { ERP_API } from "../../../config";
import { ACCENT, StoreSelect, num, todayStr, useFetch, useStoreChoice, won } from "../opsShared";

const TXN_TYPES = ["기초재고", "입고", "판매소진", "폐기", "실사조정"];
const STATUS_TONE = { 부족: "red", 주의: "amber", 정상: "green", 미사용: "gray", 없음: "gray" };
const TYPE_TONE = { 입고: "blue", 기초재고: "blue", 판매소진: "gray", 폐기: "red", 실사조정: "amber" };
const { RangePicker } = DatePicker;

const Qty = ({ v, unit }) => (
  <span className="crud-num">
    {v > 0 ? "+" : ""}
    {num(v)} {unit}
  </span>
);

const InventoryContainer = () => {
  const { stores, storeNo, setStoreNo } = useStoreChoice();
  const [tab, setTab] = useState("stock");
  const [onlyUsed, setOnlyUsed] = useState(true);
  const [range, setRange] = useState([dayjs().subtract(13, "day"), dayjs()]);
  const [txnType, setTxnType] = useState(null);
  const [txnMaterial, setTxnMaterial] = useState(null);

  const stock = useFetch(`${ERP_API}/inventory/stock`, { storeNo }, !!storeNo);
  const wasteFrom = dayjs().subtract(29, "day").format("YYYY-MM-DD");
  const wastes = useFetch(`${ERP_API}/inventory/txns`, { storeNo, from: wasteFrom, to: todayStr(), type: "폐기" }, !!storeNo);
  const stocktakes = useFetch(`${ERP_API}/inventory/stocktakes`, { storeNo }, !!storeNo);
  const txns = useFetch(
    `${ERP_API}/inventory/txns`,
    { storeNo, from: range[0].format("YYYY-MM-DD"), to: range[1].format("YYYY-MM-DD"), type: txnType || undefined, materialNo: txnMaterial || undefined },
    !!storeNo && tab === "txns"
  );


  const stockRows = useMemo(() => stock.data || [], [stock.data]);
  const shown = onlyUsed ? stockRows.filter((r) => r.txnCount > 0) : stockRows;
  const totals = useMemo(() => {
    const value = stockRows.reduce((s, r) => s + Math.max(0, r.value), 0);
    const short = stockRows.filter((r) => r.status === "부족").length;
    const warn = stockRows.filter((r) => r.status === "주의").length;
    const weekFrom = dayjs().subtract(6, "day").format("YYYY-MM-DD");
    const weekWaste = (wastes.data || []).filter((w) => w.txnDate >= weekFrom).reduce((s, w) => s - w.amount, 0);
    const last = (stocktakes.data || [])[0];
    return { value, short, warn, weekWaste, last };
  }, [stockRows, wastes.data, stocktakes.data]);

  const wasteByReason = useMemo(() => {
    const map = {};
    (wastes.data || []).forEach((w) => {
      map[w.reason || "기타"] = (map[w.reason || "기타"] || 0) - w.amount;
    });
    return Object.entries(map)
      .map(([name, amount]) => ({ name, amount }))
      .sort((a, b) => b.amount - a.amount);
  }, [wastes.data]);
  const wasteTotal = wasteByReason.reduce((s, r) => s + r.amount, 0);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          title="재고 · 로스 현황"
          subtitle="매장 시스템에서 받은 입고 확인 · 마감(판매소진) · 폐기 · 실사 기록으로 계산한 매장 재고와 로스입니다."
          extra={
            <div className="ops-toolbar">
              <StoreSelect stores={stores} value={storeNo} onChange={setStoreNo} />
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard label="재고 금액" value={Math.round(totals.value / 10000).toLocaleString()} unit="만 원" hint="식자재 단가 기준" />
          <StatCard
            label="부족 · 주의 품목"
            value={totals.short + totals.warn}
            unit="개"
            tone={totals.short ? "warn" : undefined}
            hint={`2일 치 미만 ${totals.short}개 · 4일 치 미만 ${totals.warn}개`}
          />
          <StatCard label="최근 7일 폐기" value={won(totals.weekWaste).replace("원", "")} unit="원" hint="유통기한 경과 · 미판매 · 파손 등" />
          <StatCard
            label="최근 실사 로스"
            value={totals.last ? won(-Math.min(0, totals.last.diffAmount)).replace("원", "") : "-"}
            unit={totals.last ? "원" : ""}
            tone={totals.last && totals.last.diffAmount < -20000 ? "warn" : undefined}
            hint={totals.last ? `${totals.last.countDate} · 차이 ${totals.last.diffCount}개 품목` : "실사 기록 없음"}
          />
        </div>

        <Panel>
          <Tabs
            activeKey={tab}
            onChange={setTab}
            items={[
              {
                key: "stock",
                label: "현재고",
                children: (
                  <>
                    <div className="ops-toolbar" style={{ justifyContent: "space-between", marginBottom: 10 }}>
                      <span className="ops-note">남은 일수 = 현재고 ÷ 최근 14일 일평균 사용량 · 2일 미만 부족, 4일 미만 주의</span>
                      <span className="ops-note">
                        <Switch size="small" checked={onlyUsed} onChange={setOnlyUsed} /> 이 매장에서 쓰는 식자재만
                      </span>
                    </div>
                    <Table
                      size="small"
                      rowKey="materialNo"
                      loading={stock.loading}
                      dataSource={shown}
                      pagination={{ pageSize: 15, hideOnSinglePage: true }}
                      columns={[
                        {
                          title: "식자재",
                          dataIndex: "materialName",
                          render: (v, r) => (
                            <>
                              <div className="crud-strong">{v}</div>
                              <div className="crud-sub">
                                {r.materialType} · {r.storageType}
                              </div>
                            </>
                          ),
                        },
                        {
                          title: "현재고",
                          dataIndex: "quantity",
                          align: "right",
                          sorter: (a, b) => a.quantity - b.quantity,
                          render: (v, r) => (
                            <span className="stock-qty">
                              {num(v)} {r.unit}
                              <small>
                                약 {num(r.packs)} × {r.packLabel}
                              </small>
                            </span>
                          ),
                        },
                        { title: "재고 금액", dataIndex: "value", align: "right", sorter: (a, b) => a.value - b.value, render: (v) => <span className="crud-num">{won(v)}</span> },
                        {
                          title: "입고 대기",
                          dataIndex: "onOrder",
                          align: "right",
                          render: (v, r) => <span className="crud-num">{v ? `+${num(v, 0)} ${r.unit}` : <span className="crud-sub">-</span>}</span>,
                        },
                        { title: "일평균 사용", dataIndex: "avgDaily", align: "right", render: (v, r) => <span className="crud-num">{v ? `${num(v)} ${r.unit}` : "-"}</span> },
                        {
                          title: "남은 일수",
                          dataIndex: "coverDays",
                          align: "right",
                          sorter: (a, b) => (a.coverDays ?? 999) - (b.coverDays ?? 999),
                          render: (v) => <span className="crud-num">{v === null || v === undefined ? "-" : `${num(v)}일`}</span>,
                        },
                        {
                          title: "상태",
                          dataIndex: "status",
                          filters: Object.keys(STATUS_TONE).map((s) => ({ text: s, value: s })),
                          onFilter: (v, r) => r.status === v,
                          render: (v) => <Badge value={v} tones={STATUS_TONE} />,
                        },
                      ]}
                    />
                  </>
                ),
              },
              {
                key: "waste",
                label: "폐기 내역",
                children: (
                  <div className="ops-grid-2" style={{ gridTemplateColumns: "minmax(0, 2fr) minmax(0, 1fr)" }}>
                    <Table
                      size="small"
                      rowKey="txnNo"
                      loading={wastes.loading}
                      dataSource={wastes.data || []}
                      pagination={{ pageSize: 12, hideOnSinglePage: true }}
                      locale={{ emptyText: "최근 30일 폐기 기록이 없습니다." }}
                      columns={[
                        { title: "날짜", dataIndex: "txnDate", render: (v) => <span className="crud-num">{v}</span> },
                        { title: "식자재", dataIndex: "materialName" },
                        { title: "수량", dataIndex: "quantity", align: "right", render: (v, r) => <span className="crud-num">{num(-v)} {r.unit}</span> },
                        { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(-v)}</span> },
                        { title: "사유", dataIndex: "reason" },
                        { title: "메모", dataIndex: "note", render: (v) => <span className="crud-sub">{v || ""}</span> },
                      ]}
                    />
                    <div>
                      <div className="ops-note" style={{ marginBottom: 10 }}>
                        최근 30일 사유별 · 합계 {won(wasteTotal)}
                      </div>
                      <ul className="ops-bars">
                        {wasteByReason.map((r) => (
                          <li key={r.name}>
                            <span>{r.name}</span>
                            <span className="bar">
                              <i style={{ width: `${wasteTotal ? (r.amount / wasteTotal) * 100 : 0}%`, background: "#d9534f" }} />
                            </span>
                            <span className="val">{won(r.amount)}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                ),
              },
              {
                key: "count",
                label: "실사 기록",
                children: (
                  <Table
                    size="small"
                    rowKey="stocktakeNo"
                    loading={stocktakes.loading}
                    dataSource={stocktakes.data || []}
                    pagination={{ pageSize: 10, hideOnSinglePage: true }}
                    locale={{ emptyText: "실사 기록이 없습니다." }}
                    expandable={{
                      expandedRowRender: (st) => (
                        <Table
                          size="small"
                          rowKey="materialNo"
                          pagination={false}
                          dataSource={[...st.lines].sort((a, b) => a.diffAmount - b.diffAmount)}
                          columns={[
                            { title: "식자재", dataIndex: "materialName" },
                            { title: "장부", dataIndex: "expectedQty", align: "right", render: (v, r) => <span className="crud-num">{num(v)} {r.unit}</span> },
                            { title: "실사", dataIndex: "countedQty", align: "right", render: (v, r) => <span className="crud-num">{num(v)} {r.unit}</span> },
                            {
                              title: "차이",
                              dataIndex: "diffQty",
                              align: "right",
                              render: (v, r) => (
                                <span className={`crud-num count-row-diff ${v < 0 ? "is-minus" : v > 0 ? "is-plus" : ""}`}>
                                  {v ? <Qty v={v} unit={r.unit} /> : "일치"}
                                </span>
                              ),
                            },
                            { title: "금액", dataIndex: "diffAmount", align: "right", render: (v) => <span className="crud-num">{v ? won(v) : "-"}</span> },
                          ]}
                        />
                      ),
                    }}
                    columns={[
                      { title: "실사일", dataIndex: "countDate", render: (v) => <span className="crud-num">{v}</span> },
                      { title: "품목", dataIndex: "lineCount", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
                      { title: "차이 품목", dataIndex: "diffCount", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
                      {
                        title: "차이 금액",
                        dataIndex: "diffAmount",
                        align: "right",
                        render: (v) => <span className={`crud-num count-row-diff ${v < 0 ? "is-minus" : v > 0 ? "is-plus" : ""}`}>{won(v)}</span>,
                      },
                      { title: "메모", dataIndex: "note", render: (v) => <span className="crud-sub">{v || ""}</span> },
                      { title: "담당", dataIndex: "createdBy" },
                    ]}
                  />
                ),
              },
              {
                key: "txns",
                label: "재고 변동",
                children: (
                  <>
                    <div className="ops-toolbar" style={{ marginBottom: 10 }}>
                      <RangePicker value={range} allowClear={false} onChange={(v) => v && setRange(v)} disabledDate={(d) => d.isAfter(dayjs(), "day")} />
                      <Select allowClear placeholder="종류 전체" style={{ width: 130 }} value={txnType} onChange={setTxnType} options={TXN_TYPES.map((t) => ({ value: t, label: t }))} />
                      <Select
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        placeholder="식자재 전체"
                        style={{ width: 200 }}
                        value={txnMaterial}
                        onChange={setTxnMaterial}
                        options={stockRows.map((s) => ({ value: s.materialNo, label: s.materialName }))}
                      />
                    </div>
                    <Table
                      size="small"
                      rowKey="txnNo"
                      loading={txns.loading}
                      dataSource={txns.data || []}
                      pagination={{ pageSize: 15, showSizeChanger: false }}
                      columns={[
                        { title: "날짜", dataIndex: "txnDate", render: (v) => <span className="crud-num">{v}</span> },
                        { title: "종류", dataIndex: "txnType", render: (v) => <Badge value={v} tones={TYPE_TONE} /> },
                        { title: "식자재", dataIndex: "materialName" },
                        { title: "수량", dataIndex: "quantity", align: "right", render: (v, r) => <Qty v={v} unit={r.unit} /> },
                        { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
                        { title: "사유 · 메모", key: "why", render: (_, r) => <span className="crud-sub">{[r.reason, r.note].filter(Boolean).join(" · ")}</span> },
                      ]}
                    />
                  </>
                ),
              },
            ]}
          />
        </Panel>

      </div>
    </ConfigProvider>
  );
};

export default InventoryContainer;
