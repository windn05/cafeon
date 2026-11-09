import { useEffect, useState } from "react";
import { Button, ConfigProvider, DatePicker, Empty, Input, InputNumber, Modal, Popconfirm, Select, Table, Tabs, message } from "antd";
import { CheckOutlined, CloseOutlined, InboxOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { DashHeader, Loading, Panel } from "../../../components/Dashboard/Dashboard";
import { Badge, errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT, useFetch, won } from "../../Ops/opsShared";
import "../../Weekly/weekly.css";

const STATUS_TONE = { 요청: "amber", 승인: "green", 반려: "gray" };
const { RangePicker } = DatePicker;
const day = (d) => (d ? dayjs(d).format("M/D (dd)") : "-");

// 처리 대기 요청 1건: 수량을 고쳐서 승인하거나 사유를 적어 반려
const RequestCard = ({ req, onDone }) => {
  const [packs, setPacks] = useState({});
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setPacks(Object.fromEntries(req.lines.map((l) => [l.lineNo, l.requestedPacks])));
  }, [req]);

  const amount = req.lines.reduce((s, l) => s + (packs[l.lineNo] ?? 0) * l.packPrice, 0);
  const suppliers = [...new Set(req.lines.filter((l) => (packs[l.lineNo] ?? 0) > 0).map((l) => l.clientName || "미지정"))];

  const approve = async () => {
    setBusy(true);
    try {
      const res = await ERPapi.post(`${ERP_API}/order-requests/${req.requestNo}/approve`, {
        lines: req.lines.map((l) => ({ lineNo: l.lineNo, receivedPacks: packs[l.lineNo] ?? 0 })),
      });
      message.success(`${req.storeName} 요청을 승인했습니다. 발주서: ${res.data.poNumbers}`);
      onDone();
    } catch (err) {
      message.error(errorText(err, "승인하지 못했습니다."));
    } finally {
      setBusy(false);
    }
  };
  const reject = async () => {
    setBusy(true);
    try {
      await ERPapi.post(`${ERP_API}/order-requests/${req.requestNo}/reject`, { reason });
      message.success(`${req.storeName} 요청을 반려했습니다.`);
      setRejecting(false);
      onDone();
    } catch (err) {
      message.error(errorText(err, "반려하지 못했습니다."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="po-group">
      <header className="po-group-head">
        <div>
          <h3>
            {req.storeName} <span className="crud-sub">{req.storeType}</span>
          </h3>
          <div className="meta">
            {req.requestNumber} · {day(req.requestDate)} 요청{req.neededDate ? ` · ${day(req.neededDate)}까지 필요` : ""}
            {req.note ? ` · "${req.note}"` : ""}
          </div>
        </div>
        <span className="ops-note">{req.lineCount}개 품목</span>
      </header>
      <Table
        size="small"
        rowKey="lineNo"
        pagination={false}
        dataSource={req.lines}
        scroll={{ x: "max-content" }}
        columns={[
          { title: "식자재", dataIndex: "materialName", render: (v, l) => <><div className="crud-strong">{v}</div><div className="crud-sub">{l.packLabel} · {won(l.packPrice)}</div></> },
          { title: "공급업체", dataIndex: "clientName", render: (v) => v || <span className="crud-badge red">미지정</span> },
          { title: "요청", dataIndex: "requestedPacks", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
          {
            title: "승인 수량",
            key: "approve",
            width: 150,
            render: (_, l) => (
              <InputNumber size="small" min={0} value={packs[l.lineNo]} onChange={(v) => setPacks((x) => ({ ...x, [l.lineNo]: v ?? 0 }))} addonAfter="개" style={{ width: "100%" }} />
            ),
          },
          { title: "금액", key: "amount", align: "right", render: (_, l) => <span className="crud-num">{won((packs[l.lineNo] ?? 0) * l.packPrice)}</span> },
        ]}
      />
      <footer className="po-group-foot">
        <span className="ops-note">발주서 {suppliers.length}장 생성 예정 ({suppliers.join(", ") || "-"})</span>
        <strong>{won(amount)}</strong>
        <Button icon={<CloseOutlined />} onClick={() => setRejecting(true)} disabled={busy}>
          반려
        </Button>
        <Popconfirm title="이 요청을 승인할까요?" description="공급업체별 발주서가 바로 만들어집니다." okText="승인" cancelText="닫기" onConfirm={approve} disabled={!amount}>
          <Button type="primary" icon={<CheckOutlined />} loading={busy} disabled={!amount}>
            승인 · 발주
          </Button>
        </Popconfirm>
      </footer>
      <Modal
        open={rejecting}
        title={`${req.storeName} 요청 반려`}
        okText="반려"
        okButtonProps={{ danger: true, disabled: !reason.trim(), loading: busy }}
        onOk={reject}
        onCancel={() => setRejecting(false)}
      >
        <Input.TextArea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} placeholder="반려 사유 (매장에 전달됩니다). 예: 재고 충분, 다음 정기 발주에 포함" />
      </Modal>
    </section>
  );
};

const RequestsContainer = () => {
  const [tab, setTab] = useState("pending");
  const [range, setRange] = useState([dayjs().subtract(13, "day"), dayjs()]);
  const [status, setStatus] = useState(null);
  const pending = useFetch(`${ERP_API}/order-requests/pending`, {});
  const history = useFetch(
    `${ERP_API}/order-requests`,
    { from: range[0].format("YYYY-MM-DD"), to: range[1].format("YYYY-MM-DD"), status: status || undefined },
    tab === "history"
  );
  const reload = () => {
    pending.reload();
    if (tab === "history") history.reload();
  };

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          title="매장 발주 요청"
          subtitle="매장 POS에서 올라온 발주 요청을 확인해 승인하면 공급업체별 발주서가 만들어지고, 승인 · 반려 결과와 도착 예정일이 매장 POS로 바로 전달됩니다. 수량은 승인 전에 조정할 수 있습니다."
        />
        <Panel>
          <Tabs
            activeKey={tab}
            onChange={setTab}
            items={[
              {
                key: "pending",
                label: `처리 대기 ${pending.data ? pending.data.length : ""}`,
                children: !pending.data ? (
                  <Loading />
                ) : !pending.data.length ? (
                  <Empty image={<InboxOutlined style={{ fontSize: 40, color: "#c0c4cc" }} />} description="처리할 발주 요청이 없습니다." />
                ) : (
                  <div className="po-groups">
                    {pending.data.map((r) => (
                      <RequestCard key={r.requestNo} req={r} onDone={reload} />
                    ))}
                  </div>
                ),
              },
              {
                key: "history",
                label: "처리 기록",
                children: (
                  <>
                    <div className="ops-toolbar" style={{ marginBottom: 10 }}>
                      <RangePicker value={range} allowClear={false} onChange={(v) => v && setRange(v)} disabledDate={(d) => d.isAfter(dayjs(), "day")} />
                      <Select allowClear placeholder="상태 전체" style={{ width: 130 }} value={status} onChange={setStatus} options={["요청", "승인", "반려"].map((s) => ({ value: s, label: s }))} />
                    </div>
                    <Table
                      size="small"
                      rowKey="requestNo"
                      loading={history.loading}
                      dataSource={history.data || []}
                      pagination={{ pageSize: 15, showSizeChanger: false }}
                      expandable={{
                        expandedRowRender: (r) => (
                          <Table
                            size="small"
                            rowKey="lineNo"
                            pagination={false}
                            dataSource={r.lines}
                            columns={[
                              { title: "식자재", dataIndex: "materialName" },
                              { title: "공급업체", dataIndex: "clientName" },
                              { title: "요청", dataIndex: "requestedPacks", align: "right", render: (v) => `${v}개` },
                              { title: "승인", dataIndex: "approvedPacks", align: "right", render: (v) => (v === null || v === undefined ? "-" : `${v}개`) },
                            ]}
                          />
                        ),
                      }}
                      columns={[
                        { title: "요청 번호", dataIndex: "requestNumber", render: (v) => <span className="crud-code">{v}</span> },
                        { title: "매장", dataIndex: "storeName", render: (v) => <span className="crud-strong">{v}</span> },
                        { title: "요청일", dataIndex: "requestDate", render: (v) => <span className="crud-num">{day(v)}</span> },
                        { title: "품목", dataIndex: "lineCount", align: "right", render: (v) => `${v}개` },
                        { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
                        { title: "상태", dataIndex: "status", render: (v) => <Badge value={v} tones={STATUS_TONE} /> },
                        { title: "발주서 · 사유", key: "result", render: (_, r) => <span className="crud-sub">{r.poNumbers || r.rejectReason || r.note || ""}</span> },
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

export default RequestsContainer;
