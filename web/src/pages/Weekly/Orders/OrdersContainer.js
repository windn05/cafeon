import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  ConfigProvider,
  DatePicker,
  Empty,
  InputNumber,
  Popconfirm,
  Segmented,
  Select,
  Switch,
  Table,
  Tabs,
  message,
} from "antd";
import { ShoppingCartOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { DashHeader, Loading, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { Badge, errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT, StoreSelect, num, todayStr, useFetch, useStoreChoice, won } from "../../Ops/opsShared";
import "../weekly.css";

const STATUS = ["발주완료", "입고완료", "취소"];
const STATUS_TONE = { 발주완료: "blue", 입고완료: "green", 취소: "gray" };
const { RangePicker } = DatePicker;

const day = (d) => dayjs(d).format("M/D (dd)");

// 발주서 줄 목록 (펼친 행)
const PoLines = ({ po }) => (
  <Table
    size="small"
    rowKey="lineNo"
    pagination={false}
    dataSource={po.lines}
    columns={[
      { title: "식자재", dataIndex: "materialName" },
      { title: "포장", dataIndex: "packLabel", render: (v) => <span className="crud-sub">{v}</span> },
      { title: "발주", dataIndex: "orderPacks", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
      {
        title: "입고",
        dataIndex: "receivedPacks",
        align: "right",
        render: (v, r) =>
          v === null || v === undefined ? (
            <span className="crud-sub">-</span>
          ) : (
            <span className={`crud-num ${v < r.orderPacks ? "count-row-diff is-minus" : ""}`}>{v}개</span>
          ),
      },
      { title: "단가", dataIndex: "packPrice", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
      { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
    ]}
  />
);

// 발주 제안 탭
const SuggestionTab = ({ store, onOrdered }) => {
  const [cycle, setCycle] = useState(7);
  const [onlySuggested, setOnlySuggested] = useState(true);
  const [packs, setPacks] = useState({});
  const [creating, setCreating] = useState(null);
  const { data, loading, reload } = useFetch(`${ERP_API}/purchase-orders/suggestion`, { storeNo: store?.storeNo, cycleDays: cycle }, !!store);

  useEffect(() => {
    if (!data) return;
    const init = {};
    data.forEach((g) => g.lines.forEach((l) => (init[`${g.clientNo}-${l.materialNo}`] = l.suggestedPacks)));
    setPacks(init);
  }, [data]);

  const order = async (g) => {
    const lines = g.lines.map((l) => ({ materialNo: l.materialNo, packs: packs[`${g.clientNo}-${l.materialNo}`] || 0 })).filter((l) => l.packs > 0);
    setCreating(g.clientNo);
    try {
      const res = await ERPapi.post(`${ERP_API}/purchase-orders`, { storeNo: store.storeNo, clientNo: g.clientNo, lines, note: "주간 발주" });
      message.success(`${res.data.poNumber} 발주서를 만들었습니다. (${g.clientName} · ${won(res.data.orderAmount)} · ${day(res.data.expectedDate)} 도착 예정)`);
      reload();
      onOrdered();
    } catch (err) {
      message.error(errorText(err, "발주서를 만들지 못했습니다."));
    } finally {
      setCreating(null);
    }
  };

  if (!data || loading) return <Loading />;
  if (!data.length) return <Empty description="이 매장은 아직 사용 기록이 없어 제안할 발주가 없습니다." />;

  return (
    <>
      <div className="po-controls">
        <div className="ops-toolbar">
          <span className="ops-note">다음 발주까지</span>
          <Segmented value={cycle} onChange={setCycle} options={[{ label: "3일", value: 3 }, { label: "4일", value: 4 }, { label: "1주", value: 7 }]} />
          <span className="ops-note">
            <Switch size="small" checked={onlySuggested} onChange={setOnlySuggested} /> 발주가 필요한 품목만
          </span>
        </div>
        <span className="ops-note">목표 재고 = 일평균 사용량 × (다음 발주까지 + 납품 소요일 + 안전 2일) · 제안 = 목표 − 현재고 − 입고 대기</span>
      </div>
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message="매장 매출·재고 데이터로 계산한 본사 발주 제안입니다. 직영점이나 매장 요청이 늦을 때 본사가 직접 발주할 때 씁니다. 오늘 판매분은 매장 마감이 들어와야 재고에서 빠집니다." />
      <div className="po-groups">
        {data.map((g) => {
          const rows = g.lines.filter((l) => !onlySuggested || (packs[`${g.clientNo}-${l.materialNo}`] || 0) > 0 || l.suggestedPacks > 0);
          const total = g.lines.reduce((s, l) => s + (packs[`${g.clientNo}-${l.materialNo}`] || 0) * l.packPrice, 0);
          const count = g.lines.filter((l) => (packs[`${g.clientNo}-${l.materialNo}`] || 0) > 0).length;
          if (onlySuggested && !rows.length) return null;
          return (
            <section key={g.clientName} className="po-group">
              <header className="po-group-head">
                <div>
                  <h3>{g.clientName}</h3>
                  <div className="meta">
                    {g.clientType ? `${g.clientType} · ` : ""}납품 소요 {g.leadTimeDays}일 → {day(g.expectedDate)} 도착 예정
                  </div>
                </div>
                <span className="ops-note">발주 {count}개 품목</span>
              </header>
              {!g.clientNo && (
                <Alert type="warning" banner message="공급업체가 지정되지 않은 식자재입니다. 식자재 관리에서 주 공급업체를 지정하면 발주할 수 있습니다." />
              )}
              <Table
                size="small"
                rowKey="materialNo"
                pagination={false}
                dataSource={rows}
                scroll={{ x: "max-content" }}
                rowClassName={(l) => ((packs[`${g.clientNo}-${l.materialNo}`] || 0) === 0 ? "po-row-zero" : "")}
                columns={[
                  { title: "식자재", dataIndex: "materialName", render: (v, l) => <><div className="crud-strong">{v}</div><div className="crud-sub">{l.packLabel} · {won(l.packPrice)}</div></> },
                  { title: "현재고", dataIndex: "stock", align: "right", render: (v, l) => <span className="crud-num">{num(v, 0)} {l.unit}</span> },
                  { title: "입고 대기", dataIndex: "onOrder", align: "right", render: (v, l) => <span className="crud-num">{v ? `${num(v, 0)} ${l.unit}` : "-"}</span> },
                  { title: "일평균 사용", dataIndex: "avgDaily", align: "right", render: (v, l) => <span className="crud-num">{num(v)} {l.unit}</span> },
                  { title: "남은 일수", dataIndex: "coverDays", align: "right", render: (v) => <span className="crud-num">{v === null || v === undefined ? "-" : `${num(v)}일`}</span> },
                  { title: "목표 재고", dataIndex: "targetQty", align: "right", render: (v, l) => <span className="crud-num crud-sub">{num(v, 0)} {l.unit}</span> },
                  {
                    title: "발주 수량",
                    key: "packs",
                    width: 150,
                    render: (_, l) => (
                      <InputNumber
                        size="small"
                        min={0}
                        value={packs[`${g.clientNo}-${l.materialNo}`]}
                        onChange={(v) => setPacks((x) => ({ ...x, [`${g.clientNo}-${l.materialNo}`]: v ?? 0 }))}
                        addonAfter="개"
                        disabled={!g.clientNo}
                        style={{ width: "100%" }}
                      />
                    ),
                  },
                  { title: "금액", key: "amount", align: "right", render: (_, l) => <span className="crud-num">{won((packs[`${g.clientNo}-${l.materialNo}`] || 0) * l.packPrice)}</span> },
                ]}
              />
              <footer className="po-group-foot">
                <span className="ops-note">합계</span>
                <strong>{won(total)}</strong>
                <Popconfirm
                  title={`${g.clientName}에 발주할까요?`}
                  description={`${count}개 품목 · ${won(total)}`}
                  okText="발주"
                  cancelText="닫기"
                  onConfirm={() => order(g)}
                  disabled={!g.clientNo || !count}
                >
                  <Button type="primary" icon={<ShoppingCartOutlined />} disabled={!g.clientNo || !count} loading={creating === g.clientNo}>
                    발주서 만들기
                  </Button>
                </Popconfirm>
              </footer>
            </section>
          );
        })}
      </div>
    </>
  );
};

const OrdersContainer = () => {
  const { stores, storeNo, store, setStoreNo } = useStoreChoice();
  const [tab, setTab] = useState("suggest");
  const [range, setRange] = useState([dayjs().subtract(29, "day"), dayjs()]);
  const [status, setStatus] = useState(null);

  const open = useFetch(`${ERP_API}/purchase-orders/open`, { storeNo }, !!storeNo);
  const history = useFetch(
    `${ERP_API}/purchase-orders`,
    { storeNo, from: range[0].format("YYYY-MM-DD"), to: range[1].format("YYYY-MM-DD"), status: status || undefined },
    !!storeNo
  );
  const reloadAll = () => {
    open.reload();
    history.reload();
  };

  const stats = useMemo(() => {
    const o = open.data || [];
    const today = todayStr();
    const week = dayjs().subtract(6, "day").format("YYYY-MM-DD");
    return {
      openCount: o.length,
      openAmount: o.reduce((s, p) => s + p.orderAmount, 0),
      todayCount: o.filter((p) => p.expectedDate === today).length,
      overdue: o.filter((p) => p.overdue).length,
      weekAmount: (history.data || []).filter((p) => p.orderDate >= week && p.status !== "취소").reduce((s, p) => s + p.orderAmount, 0),
    };
  }, [open.data, history.data]);

  const cancel = async (po) => {
    try {
      await ERPapi.post(`${ERP_API}/purchase-orders/${po.poNo}/cancel`);
      message.success(`${po.poNumber} 발주를 취소했습니다.`);
      reloadAll();
    } catch (err) {
      message.error(errorText(err, "취소하지 못했습니다."));
    }
  };

  const baseColumns = [
    { title: "발주 번호", dataIndex: "poNumber", render: (v) => <span className="crud-code">{v}</span> },
    { title: "공급업체", dataIndex: "clientName", render: (v) => <span className="crud-strong">{v}</span> },
    { title: "발주일", dataIndex: "orderDate", render: (v) => <span className="crud-num">{day(v)}</span> },
    {
      title: "입고 예정",
      dataIndex: "expectedDate",
      render: (v, p) => (
        <>
          <span className="crud-num">{day(v)}</span> {p.overdue && <span className="crud-badge red">지연</span>}
          {p.status === "발주완료" && v === todayStr() && <span className="crud-badge amber">오늘</span>}
        </>
      ),
    },
    { title: "품목", dataIndex: "lineCount", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
    { title: "발주 금액", dataIndex: "orderAmount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
  ];

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          title="발주 관리"
          subtitle="공급업체 발주서를 관리합니다. 매장 발주 요청을 승인하면 여기에 발주서가 생기고, 물건이 도착해 매장이 입고 확인을 보내면 재고에 반영됩니다."
          extra={
            <div className="ops-toolbar">
              <StoreSelect stores={stores} value={storeNo} onChange={setStoreNo} />
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard label="입고 대기" value={stats.openCount} unit="건" hint={`발주 금액 ${won(stats.openAmount)}`} />
          <StatCard label="오늘 도착 예정" value={stats.todayCount} unit="건" tone={stats.todayCount ? "warn" : undefined} hint="매장 입고 확인을 기다리는 중" />
          <StatCard label="입고 지연" value={stats.overdue} unit="건" tone={stats.overdue ? "warn" : undefined} hint="예정일이 지났는데 입고 전" />
          <StatCard label="최근 7일 발주 금액" value={Math.round(stats.weekAmount / 10000).toLocaleString()} unit="만 원" hint="취소 제외" />
        </div>

        <Panel>
          <Tabs
            activeKey={tab}
            onChange={setTab}
            items={[
              { key: "suggest", label: "발주 제안", children: store ? <SuggestionTab store={store} onOrdered={reloadAll} /> : <Loading /> },
              {
                key: "open",
                label: `입고 대기 ${open.data ? open.data.length : ""}`,
                children: (
                  <Table
                    size="small"
                    rowKey="poNo"
                    loading={open.loading}
                    dataSource={open.data || []}
                    pagination={false}
                    locale={{ emptyText: "입고를 기다리는 발주가 없습니다." }}
                    expandable={{ expandedRowRender: (po) => <PoLines po={po} /> }}
                    columns={[
                      ...baseColumns,
                      {
                        title: "",
                        key: "x",
                        align: "right",
                        render: (_, po) => (
                          <div className="ops-toolbar" style={{ justifyContent: "flex-end" }}>
                            <Popconfirm title="이 발주를 취소할까요?" okText="발주 취소" okButtonProps={{ danger: true }} cancelText="닫기" onConfirm={() => cancel(po)}>
                              <Button size="small" danger>
                                취소
                              </Button>
                            </Popconfirm>
                          </div>
                        ),
                      },
                    ]}
                  />
                ),
              },
              {
                key: "history",
                label: "발주 기록",
                children: (
                  <>
                    <div className="ops-toolbar" style={{ marginBottom: 10 }}>
                      <RangePicker value={range} allowClear={false} onChange={(v) => v && setRange(v)} disabledDate={(d) => d.isAfter(dayjs(), "day")} />
                      <Select allowClear placeholder="상태 전체" style={{ width: 130 }} value={status} onChange={setStatus} options={STATUS.map((s) => ({ value: s, label: s }))} />
                      <span className="ops-note">
                        {history.data ? `${history.data.length}건 · ${won(history.data.filter((p) => p.status !== "취소").reduce((s, p) => s + p.orderAmount, 0))}` : ""}
                      </span>
                    </div>
                    <Table
                      size="small"
                      rowKey="poNo"
                      loading={history.loading}
                      dataSource={history.data || []}
                      pagination={{ pageSize: 15, showSizeChanger: false }}
                      expandable={{ expandedRowRender: (po) => <PoLines po={po} /> }}
                      columns={[
                        ...baseColumns,
                        { title: "상태", dataIndex: "status", render: (v, p) => <>{<Badge value={v} tones={STATUS_TONE} />} {p.shortage && <span className="crud-badge amber">일부 부족</span>}</> },
                        { title: "입고일", dataIndex: "receivedDate", render: (v) => <span className="crud-num">{v ? day(v) : "-"}</span> },
                        { title: "메모", key: "memo", render: (_, p) => <span className="crud-sub">{[p.note, p.receiveNote].filter(Boolean).join(" · ")}</span> },
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

export default OrdersContainer;
