// 발주 · 입고: 재고 관리에서 만든 물류센터 발주서의 입고 처리 · 취소 · 기록
import { useMemo, useState } from "react";
import { Button, ConfigProvider, DatePicker, Popconfirm, Select, Table, Tabs, message } from "antd";
import { ShoppingCartOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { DashHeader, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { Badge, errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT, todayStr, useFetch, useStoreChoice, won } from "../../Ops/opsShared";
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

const OrdersContainer = () => {
  // 물류센터 발주 전용 (매장 배송은 재고 관리 > 매장 배송에서 본다)
  const navigate = useNavigate();
  const { storeNo, store } = useStoreChoice({ types: ["물류센터"], storageKey: "center.storeNo" });
  const [tab, setTab] = useState("open");
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

  // 물류센터 보충 발주 입고: 물류센터에는 POS가 없어 본사가 직접 처리한다 (발주 수량대로 입고)
  const receive = async (po) => {
    try {
      await ERPapi.post(`${ERP_API}/purchase-orders/${po.poNo}/receive`, { receivedDate: null, note: null, lines: [] });
      message.success(`${po.poNumber} 입고를 처리했습니다. 물류센터 재고에 반영됐습니다.`);
      reloadAll();
    } catch (err) {
      message.error(errorText(err, "입고 처리하지 못했습니다."));
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
          subtitle={`${store ? store.storeName : "물류센터"}로 들어올 공급업체 발주입니다. 발주서는 재고 관리에서 모자란 품목을 골라 만들고, 여기서는 물건이 도착하면 입고를 처리합니다.`}
          extra={
            <Button icon={<ShoppingCartOutlined />} onClick={() => navigate("/erp/purchase/center")}>
              재고 관리에서 발주
            </Button>
          }
        />

        <div className="stat-grid">
          <StatCard label="입고 대기" value={stats.openCount} unit="건" hint={`발주 금액 ${won(stats.openAmount)}`} />
          <StatCard label="오늘 도착 예정" value={stats.todayCount} unit="건" tone={stats.todayCount ? "warn" : undefined} hint="도착하면 입고 처리" />
          <StatCard label="입고 지연" value={stats.overdue} unit="건" tone={stats.overdue ? "warn" : undefined} hint="예정일이 지났는데 입고 전" />
          <StatCard label="최근 7일 발주 금액" value={Math.round(stats.weekAmount / 10000).toLocaleString()} unit="만 원" hint="취소 제외" />
        </div>

        <Panel>
          <Tabs
            activeKey={tab}
            onChange={setTab}
            items={[
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
                            <Popconfirm title="발주 수량대로 입고할까요?" description="물류센터 재고에 더해집니다." okText="입고" cancelText="닫기" onConfirm={() => receive(po)}>
                              <Button size="small" type="primary">
                                입고 처리
                              </Button>
                            </Popconfirm>
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
