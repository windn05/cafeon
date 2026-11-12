import { useMemo, useState } from "react";
import { ConfigProvider, Table, Tabs } from "antd";
import dayjs from "dayjs";
import { DashHeader, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { ERP_API } from "../../../config";
import { ACCENT, StoreSelect, num, todayStr, useFetch, useStoreChoice, won } from "../opsShared";
import { StockTable, StocktakeTable, TxnPanel } from "./InventoryParts";

const TXN_TYPES = ["기초재고", "입고", "판매소진", "폐기", "실사조정"];

const InventoryContainer = () => {
  // 직영점만: 직영점 재고는 본사 재산이라 본사가 로스를 관리한다 (가맹점 재고는 점주 관리, 물류센터는 구매 · 물류 > 재고 관리)
  const { stores, storeNo, setStoreNo } = useStoreChoice({ types: ["직영점"], storageKey: "inventory.storeNo" });
  const [tab, setTab] = useState("stock");
  const stock = useFetch(`${ERP_API}/inventory/stock`, { storeNo }, !!storeNo);
  const wasteFrom = dayjs().subtract(29, "day").format("YYYY-MM-DD");
  const wastes = useFetch(`${ERP_API}/inventory/txns`, { storeNo, from: wasteFrom, to: todayStr(), type: "폐기" }, !!storeNo);
  const stocktakes = useFetch(`${ERP_API}/inventory/stocktakes`, { storeNo }, !!storeNo);
  const stockRows = useMemo(() => stock.data || [], [stock.data]);
  // 그 매장에서 입고 · 판매 · 폐기 기록이 한 번도 없는 식자재는 보여 주지 않는다
  const shown = useMemo(() => stockRows.filter((r) => r.txnCount > 0), [stockRows]);
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
          subtitle="직영점 POS에서 받은 입고 확인 · 마감(판매소진) · 폐기 · 실사 기록으로 계산한 매장 재고와 로스입니다. 현재고는 아직 마감 안 한 오늘 판매 사용량을 미리 뺀 값입니다. 가맹점 재고는 점주가 관리합니다."
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
                children: <StockTable rows={shown} loading={stock.loading} />,
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
                children: <StocktakeTable data={stocktakes.data} loading={stocktakes.loading} />,
              },
              {
                key: "txns",
                label: "재고 변동",
                children: <TxnPanel storeNo={storeNo} materials={stockRows} types={TXN_TYPES} />,
              },
            ]}
          />
        </Panel>

      </div>
    </ConfigProvider>
  );
};

export default InventoryContainer;
