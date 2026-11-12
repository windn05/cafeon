// 재고 관리: 본사(물류센터)가 직접 쥐고 있는 재고. 매장 출고로 빠지고, 여기서 만든 발주서가 입고되면 채워진다
import { useEffect, useMemo, useState } from "react";
import { Button, ConfigProvider, DatePicker, Input, InputNumber, Modal, Select, Table, Tabs, message } from "antd";
import { ShoppingCartOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { DashHeader, EmptyState, Loading, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { Badge, errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT, num, todayStr, useFetch, useStoreChoice, won } from "../../Ops/opsShared";
import { StockTable, StocktakeTable, TxnPanel } from "../../Ops/Inventory/InventoryParts";
import OrderModal from "./OrderModal";

const TXN_TYPES = ["기초재고", "입고", "출고", "실사조정"];
const KIND_TONE = { 출고: "gray", 직송: "amber" };
// 매장으로 가는 배송 상태: 발주완료 = 아직 도착 전
const STATE = { 발주완료: "배송 중", 입고완료: "도착", 취소: "취소" };
const STATE_TONE = { "배송 중": "blue", 도착: "green", 취소: "gray" };
const { RangePicker } = DatePicker;
const day = (d) => (d ? dayjs(d).format("M/D (dd)") : "-");

const ADJUST_REASONS = ["실사 차이", "파손", "분실", "유통기한 경과", "기타"];

// 수량 조정: 실제로 센 수량을 넣으면 장부와의 차이만큼 '실사조정'으로 기록된다
const AdjustModal = ({ row, storeNo, onClose, onSaved }) => {
  const [counted, setCounted] = useState(null);
  const [reason, setReason] = useState("실사 차이");
  const [memo, setMemo] = useState("");
  const [busy, setBusy] = useState(false);
  if (!row) return null;
  const diff = counted === null || counted === undefined ? null : Math.round((counted - row.quantity) * 10) / 10;

  const save = async () => {
    setBusy(true);
    try {
      await ERPapi.post(`${ERP_API}/inventory/stocktakes`, {
        storeNo,
        date: null,
        note: memo.trim() ? `${reason} · ${memo.trim()}` : reason,
        lines: [{ materialNo: row.materialNo, countedQty: counted }],
      });
      message.success(`${row.materialName} 수량을 ${num(counted)} ${row.unit}(으)로 맞췄습니다.`);
      onSaved();
    } catch (err) {
      message.error(errorText(err, "조정하지 못했습니다."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      title={`${row.materialName} 수량 조정`}
      okText="조정"
      cancelText="닫기"
      okButtonProps={{ disabled: diff === null || diff === 0, loading: busy }}
      onOk={save}
      onCancel={onClose}
      destroyOnHidden
    >
      <div className="adjust-form">
        <div className="adjust-row">
          <span>장부 수량</span>
          <b className="crud-num">
            {num(row.quantity)} {row.unit}
          </b>
          <span className="crud-sub">
            {num(row.packs)}개 × {row.packLabel}
          </span>
        </div>
        <div className="adjust-row">
          <span>실제 수량</span>
          <InputNumber min={0} value={counted} onChange={setCounted} addonAfter={row.unit} style={{ width: 200 }} autoFocus />
          <span className="crud-sub">{counted ? `${num(counted / row.packQty)}개 × ${row.packLabel}` : ""}</span>
        </div>
        <div className="adjust-row">
          <span>차이</span>
          <b className={`crud-num count-row-diff ${diff < 0 ? "is-minus" : diff > 0 ? "is-plus" : ""}`}>
            {diff === null ? "-" : diff === 0 ? "장부와 같음" : `${diff > 0 ? "+" : ""}${num(diff)} ${row.unit} · ${won(diff * row.unitCost)}`}
          </b>
        </div>
        <div className="adjust-row">
          <span>사유</span>
          <Select value={reason} onChange={setReason} options={ADJUST_REASONS.map((r) => ({ value: r, label: r }))} style={{ width: 200 }} />
        </div>
        <div className="adjust-row">
          <span>메모</span>
          <Input value={memo} onChange={(e) => setMemo(e.target.value)} maxLength={100} placeholder="예: 박스 2개 젖어서 폐기" />
        </div>
      </div>
    </Modal>
  );
};

// 매장 배송: 물류센터 출고 · 공급업체 직송 (매장 POS가 입고 확인하면 도착)
const DeliveriesTab = () => {
  const [range, setRange] = useState([dayjs().subtract(6, "day"), dayjs()]);
  const [kind, setKind] = useState(null);
  const list = useFetch(`${ERP_API}/purchase-orders/deliveries`, { from: range[0].format("YYYY-MM-DD"), to: range[1].format("YYYY-MM-DD") });
  const rows = (list.data || []).filter((p) => !kind || p.kind === kind);
  return (
    <>
      <div className="ops-toolbar" style={{ marginBottom: 10 }}>
        <RangePicker value={range} allowClear={false} onChange={(v) => v && setRange(v)} disabledDate={(d) => d.isAfter(dayjs(), "day")} />
        <Select allowClear placeholder="출고 · 직송 전체" style={{ width: 150 }} value={kind} onChange={setKind} options={["출고", "직송"].map((k) => ({ value: k, label: k }))} />
        <span className="ops-note">배송 중인 건은 기간과 상관없이 모두 보입니다.</span>
      </div>
      <Table
        size="small"
        rowKey="poNo"
        loading={list.loading}
        dataSource={rows}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        expandable={{
          expandedRowRender: (po) => (
            <Table
              size="small"
              rowKey="lineNo"
              pagination={false}
              dataSource={po.lines}
              columns={[
                { title: "식자재", dataIndex: "materialName" },
                { title: "포장", dataIndex: "packLabel", render: (v) => <span className="crud-sub">{v}</span> },
                { title: "수량", dataIndex: "orderPacks", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
                { title: "받은 수량", dataIndex: "receivedPacks", align: "right", render: (v) => <span className="crud-num">{v === null || v === undefined ? "-" : `${v}개`}</span> },
                { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
              ]}
            />
          ),
        }}
        locale={{ emptyText: "이 기간에 매장으로 보낸 배송이 없습니다." }}
        columns={[
          { title: "번호", dataIndex: "poNumber", render: (v) => <span className="crud-code">{v}</span> },
          { title: "종류", dataIndex: "kind", render: (v) => <Badge value={v} tones={KIND_TONE} /> },
          { title: "매장", dataIndex: "storeName", render: (v) => <span className="crud-strong">{v}</span> },
          { title: "보내는 곳", dataIndex: "clientName" },
          { title: "보낸 날", dataIndex: "orderDate", render: (v) => <span className="crud-num">{day(v)}</span> },
          {
            title: "도착 예정",
            dataIndex: "expectedDate",
            render: (v, p) => (
              <>
                <span className="crud-num">{day(v)}</span> {p.overdue && <span className="crud-badge red">지연</span>}
              </>
            ),
          },
          { title: "상태", dataIndex: "status", render: (v) => <Badge value={STATE[v] || v} tones={STATE_TONE} /> },
          { title: "품목", dataIndex: "lineCount", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
          { title: "금액", dataIndex: "orderAmount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
        ]}
      />
    </>
  );
};

const CenterContainer = () => {
  const navigate = useNavigate();
  const { storeNo, store, loaded } = useStoreChoice({ types: ["물류센터"], storageKey: "center.storeNo" });
  const [tab, setTab] = useState("stock");
  const stock = useFetch(`${ERP_API}/inventory/stock`, { storeNo }, !!storeNo);
  const restock = useFetch(`${ERP_API}/purchase-orders/open`, { storeNo }, !!storeNo);
  const stocktakes = useFetch(`${ERP_API}/inventory/stocktakes`, { storeNo }, !!storeNo);
  const [adjusting, setAdjusting] = useState(null);
  // 발주할 품목 고르기: 공급업체가 있는 물류센터 품목만, 처음엔 부족 · 주의(남은 일수 4일 미만) 품목을 골라 둔다
  const suggestion = useFetch(`${ERP_API}/purchase-orders/suggestion`, { storeNo, cycleDays: 7 }, !!storeNo);
  const orderable = useMemo(() => {
    const m = new Map();
    (suggestion.data || []).forEach((g) => g.clientNo && g.lines.forEach((l) => m.set(l.materialNo, l.suggestedPacks)));
    return m;
  }, [suggestion.data]);
  const [picked, setPicked] = useState([]);
  const [ordering, setOrdering] = useState(false);
  const stockRows = stock.data;
  useEffect(() => {
    setPicked((stockRows || []).filter((r) => (r.status === "부족" || r.status === "주의") && orderable.has(r.materialNo)).map((r) => r.materialNo));
  }, [stockRows, orderable]);
  const inTransit = useFetch(`${ERP_API}/purchase-orders/deliveries`, { from: todayStr(), to: todayStr() });

  // 이 물류센터에서 다룬 적 없는 식자재(직송 품목 등)는 뺀다
  const rows = useMemo(() => (stock.data || []).filter((r) => r.txnCount > 0), [stock.data]);
  const totals = useMemo(() => {
    const open = restock.data || [];
    const moving = (inTransit.data || []).filter((p) => p.status === "발주완료");
    return {
      value: rows.reduce((s, r) => s + Math.max(0, r.value), 0),
      short: rows.filter((r) => r.status === "부족").length,
      warn: rows.filter((r) => r.status === "주의").length,
      restockCount: open.length,
      restockToday: open.filter((p) => p.expectedDate <= todayStr()).length,
      shipping: moving.filter((p) => p.kind === "출고").length,
      direct: moving.filter((p) => p.kind === "직송").length,
    };
  }, [rows, restock.data, inTransit.data]);

  if (loaded && !store) {
    return <EmptyState text="영업 중인 물류센터가 없습니다. 기준 정보 > 사업장의 물류센터에서 등록하세요." />;
  }
  if (!store) return <Loading />;

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          subtitle={`${store.storeName}의 재고입니다. 매장 발주 요청을 승인하면 여기서 출고됩니다. 모자란 품목을 골라 발주서를 만들고, 물건이 도착해 입고 처리하면 채워집니다.`}
          extra={<Button onClick={() => navigate("/erp/purchase/orders")}>발주 · 입고 보기</Button>}
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
          <StatCard
            label="입고 대기"
            value={totals.restockCount}
            unit="건"
            tone={totals.restockToday ? "warn" : undefined}
            hint={totals.restockToday ? `오늘까지 도착 ${totals.restockToday}건 · 입고 처리 필요` : "공급업체에 넣은 발주"}
          />
          <StatCard label="매장으로 배송 중" value={totals.shipping + totals.direct} unit="건" hint={`물류센터 출고 ${totals.shipping}건 · 직송 ${totals.direct}건`} />
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
                  <StockTable
                    rows={rows}
                    loading={stock.loading}
                    usage="출고"
                    onAdjust={setAdjusting}
                    rowSelection={{
                      selectedRowKeys: picked,
                      onChange: setPicked,
                      getCheckboxProps: (r) => ({ disabled: !orderable.has(r.materialNo) }),
                    }}
                    toolbar={
                      <Button type="primary" icon={<ShoppingCartOutlined />} disabled={!picked.length} onClick={() => setOrdering(true)}>
                        {picked.length ? `선택한 ${picked.length}개 품목 발주서 만들기` : "발주할 품목을 고르세요"}
                      </Button>
                    }
                  />
                ),
              },
              { key: "deliveries", label: "매장 배송", children: <DeliveriesTab /> },
              { key: "txns", label: "입출고 내역", children: <TxnPanel storeNo={storeNo} materials={rows} types={TXN_TYPES} /> },
              {
                key: "count",
                label: "실사 · 조정 기록",
                children: <StocktakeTable data={stocktakes.data} loading={stocktakes.loading} emptyText="수량을 조정한 기록이 없습니다." />,
              },
            ]}
          />
        </Panel>

        <OrderModal
          open={ordering}
          storeNo={storeNo}
          materialNos={picked}
          onClose={() => setOrdering(false)}
          onOrdered={() => {
            setOrdering(false);
            stock.reload();
            restock.reload();
            suggestion.reload();
          }}
        />

        <AdjustModal
          key={adjusting ? adjusting.materialNo : "none"}
          row={adjusting}
          storeNo={storeNo}
          onClose={() => setAdjusting(null)}
          onSaved={() => {
            setAdjusting(null);
            stock.reload();
            stocktakes.reload();
          }}
        />
      </div>
    </ConfigProvider>
  );
};

export default CenterContainer;
