// 본사 → 매장 POS 공지 · 지시. 업무에서 자동으로 나간 알림(발주 결과 · 메뉴·가격 변경)도 같이 본다.
import { useEffect, useMemo, useState } from "react";
import { Button, Checkbox, ConfigProvider, DatePicker, Empty, Form, Input, Modal, Radio, Segmented, Select, Table, message } from "antd";
import { NotificationOutlined, SendOutlined, SolutionOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { DashHeader, Panel, StatCard } from "../../components/Dashboard/Dashboard";
import { errorText } from "../../components/CrudPage/CrudPage";
import { ERPapi } from "../../components/api/api";
import { ERP_API } from "../../config";
import { ACCENT, SHOP_TYPES, useFetch } from "../Ops/opsShared";
import "./messages.css";

const KINDS = ["전체", "공지", "지시", "발주 결과", "메뉴·가격"];
const KIND_TONE = { 공지: "blue", 지시: "amber", "발주 결과": "green", "메뉴·가격": "purple" };
const STATUS_TONE = { "회신 완료": "green", 확인: "green", 수신: "blue", "전송 대기": "gray" };

const Kind = ({ v }) => <span className={`crud-badge ${KIND_TONE[v] || "gray"}`}>{v}</span>;

// 진행 상황: 지시는 회신 수, 그 밖에는 확인 수
const Progress = ({ m }) => {
  const done = m.needsReply ? m.repliedCount : m.readCount;
  const all = m.targetCount;
  const pct = all ? Math.round((done / all) * 100) : 0;
  return (
    <div className="msg-progress">
      <div className="msg-bar">
        <span style={{ width: `${pct}%` }} />
      </div>
      <small>
        {m.needsReply ? "회신" : "확인"} {done}/{all}
        {m.overdueCount ? <b className="msg-late"> · 기한 지남 {m.overdueCount}</b> : null}
      </small>
    </div>
  );
};

// 보내기 창
export const ComposeModal = ({ open, initial, stores, onClose, onSent }) => {
  const [form] = Form.useForm();
  const [sending, setSending] = useState(false);
  const kind = Form.useWatch("kind", form);
  const target = Form.useWatch("target", form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({
      kind: "공지",
      target: "all",
      important: false,
      dueDate: dayjs().add(1, "day"),
      ...initial,
    });
  }, [open, initial, form]);

  const submit = async () => {
    const v = await form.validateFields();
    setSending(true);
    try {
      const body = {
        kind: v.kind,
        title: v.title,
        body: v.body,
        important: v.kind === "공지" ? v.important : true,
        needsReply: v.kind === "지시",
        dueDate: v.kind === "지시" && v.dueDate ? v.dueDate.format("YYYY-MM-DD") : null,
        storeNos: v.target === "all" ? [] : v.storeNos,
        refKey: initial?.refKey || null,
      };
      const { data } = await ERPapi.post(`${ERP_API}/store-messages`, body);
      message.success(`${data.targetLabel}에 ${v.kind}를 보냈습니다. 매장 POS가 20초 안에 받아 갑니다.`);
      onSent(data);
    } catch (err) {
      message.error(errorText(err, "보내지 못했습니다."));
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal
      open={open}
      title={initial?.modalTitle || "매장에 보내기"}
      okText="보내기"
      okButtonProps={{ icon: <SendOutlined />, loading: sending }}
      cancelText="닫기"
      onOk={submit}
      onCancel={onClose}
      width={600}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" requiredMark={false}>
        <Form.Item name="kind" label="종류">
          <Radio.Group
            optionType="button"
            options={[
              { value: "공지", label: "공지 (확인만)" },
              { value: "지시", label: "지시 · 점검 요청 (회신 필요)" },
            ]}
          />
        </Form.Item>
        <Form.Item name="target" label="받는 매장">
          <Radio.Group
            options={[
              { value: "all", label: "전 매장" },
              { value: "some", label: "골라서" },
            ]}
          />
        </Form.Item>
        {target === "some" && (
          <Form.Item name="storeNos" rules={[{ required: true, message: "매장을 1곳 이상 고르세요." }]}>
            <Select mode="multiple" placeholder="매장 선택" options={stores.map((s) => ({ value: s.storeNo, label: `${s.storeName} · ${s.storeType}` }))} optionFilterProp="label" />
          </Form.Item>
        )}
        <Form.Item name="title" label="제목" rules={[{ required: true, whitespace: true, message: "제목을 적어 주세요." }]}>
          <Input maxLength={120} showCount placeholder={kind === "지시" ? "예: 제빙기 필터 교체 후 회신" : "예: 11월 시즌 메뉴 안내"} />
        </Form.Item>
        <Form.Item name="body" label="내용">
          <Input.TextArea rows={5} maxLength={2000} showCount placeholder="매장 POS 화면에 그대로 보입니다." />
        </Form.Item>
        {kind === "지시" ? (
          <Form.Item name="dueDate" label="회신 기한" rules={[{ required: true, message: "회신 기한을 고르세요." }]}>
            <DatePicker disabledDate={(d) => d.isBefore(dayjs(), "day")} />
          </Form.Item>
        ) : (
          <Form.Item name="important" valuePropName="checked">
            <Checkbox>중요 공지 (POS 화면에 팝업으로 띄워 확인받기)</Checkbox>
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
};

const Detail = ({ no, refreshKey }) => {
  const d = useFetch(`${ERP_API}/store-messages/${no}`, { refreshKey }, !!no);
  const m = d.data;
  if (!no) return <Empty description="왼쪽에서 메시지를 고르세요." />;
  if (!m) return null;
  return (
    <div className="msg-detail">
      <div className="msg-head">
        <Kind v={m.kind} />
        {m.important && <span className="crud-badge red">중요</span>}
        <small>
          {m.sentBy} · {dayjs(m.sentAt).format("M월 D일 HH:mm")} · {m.targetLabel}
          {m.dueDate ? ` · 회신 기한 ${dayjs(m.dueDate).format("M/D")}` : ""}
        </small>
      </div>
      <h3>{m.title}</h3>
      {m.body && <p className="msg-body">{m.body}</p>}
      <Table
        size="small"
        rowKey="targetNo"
        pagination={false}
        dataSource={m.targets}
        columns={[
          { title: "매장", dataIndex: "storeName", render: (v, t) => <><div className="crud-strong">{v}</div><div className="crud-sub">{t.storeType}{t.storeStatus && t.storeStatus !== "영업중" ? ` · ${t.storeStatus}` : ""}</div></> },
          {
            // 전송 대기 → 수신 → 확인 → 회신 완료 중 지금 단계와 그 시각
            title: "상태",
            dataIndex: "status",
            width: 140,
            render: (v, t) => {
              const late = m.needsReply && !t.repliedAt && t.storeStatus !== "휴점" && m.dueDate && dayjs(m.dueDate).isBefore(dayjs(), "day");
              const at = t.repliedAt || t.readAt || t.deliveredAt;
              return (
                <>
                  <span className={`crud-badge ${late ? "red" : STATUS_TONE[v] || "gray"}`}>{late ? "기한 지남" : v}</span>
                  {at && <div className="crud-sub">{dayjs(at).format("M/D HH:mm")}</div>}
                </>
              );
            },
          },
          ...(m.needsReply
            ? [{ title: "회신", dataIndex: "reply", render: (v, t) => (v ? <><div>{v}</div><div className="crud-sub">{dayjs(t.repliedAt).format("M/D HH:mm")}</div></> : "-") }]
            : []),
        ]}
      />
      <p className="ops-note" style={{ marginTop: 8 }}>
        전송 대기: 매장 POS가 아직 받아 가지 않음 (POS가 꺼져 있거나 휴점) · 수신: POS에 도착 · 확인: 매장 직원이 열어 봄 · 시각은 마지막 단계 기준
      </p>
    </div>
  );
};

const MessagesContainer = () => {
  const [kind, setKind] = useState("전체");
  const [range, setRange] = useState([dayjs().subtract(29, "day"), dayjs()]);
  const [selected, setSelected] = useState(null);
  const [compose, setCompose] = useState(null);
  const [tick, setTick] = useState(0);
  const list = useFetch(`${ERP_API}/store-messages`, { from: range[0].format("YYYY-MM-DD"), to: range[1].format("YYYY-MM-DD"), kind: kind === "전체" ? undefined : kind, tick });
  const stores = useFetch(`${ERP_API}/stores`, {});
  const shops = useMemo(() => (stores.data || []).filter((s) => SHOP_TYPES.includes(s.storeType) && s.status !== "폐점"), [stores.data]);

  // 매장 확인 · 회신이 들어오는지 15초마다 다시 읽는다
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 15000);
    return () => clearInterval(id);
  }, []);

  const rows = useMemo(() => list.data || [], [list.data]);
  useEffect(() => {
    if (!selected && rows.length) setSelected(rows[0].messageNo);
  }, [rows, selected]);

  const stats = useMemo(() => {
    const unreadImportant = rows.filter((m) => m.important && !m.needsReply).reduce((s, m) => s + (m.targetCount - m.readCount), 0);
    const waiting = rows.filter((m) => m.needsReply).reduce((s, m) => s + (m.targetCount - m.repliedCount), 0);
    const late = rows.reduce((s, m) => s + m.overdueCount, 0);
    return { sent: rows.length, unreadImportant, waiting, late };
  }, [rows]);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          subtitle="본사가 매장 POS로 보내는 공지 · 지시와, 업무에서 자동으로 나간 알림(발주 승인 · 반려 결과, 메뉴 · 가격 변경)을 봅니다. 매장이 확인 · 회신하면 바로 보입니다."
          extra={
            <div className="ops-toolbar">
              <Button icon={<NotificationOutlined />} onClick={() => setCompose({ kind: "공지" })}>
                공지 보내기
              </Button>
              <Button type="primary" icon={<SolutionOutlined />} onClick={() => setCompose({ kind: "지시" })}>
                지시 보내기
              </Button>
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard label="보낸 메시지" value={stats.sent} unit="건" hint="선택한 기간 · 자동 알림 포함" />
          <StatCard label="중요 공지 미확인" value={stats.unreadImportant} unit="곳" tone={stats.unreadImportant ? "warn" : undefined} hint="매장별로 센 수" />
          <StatCard label="지시 회신 대기" value={stats.waiting} unit="곳" hint="아직 답이 없는 매장" />
          <StatCard label="회신 기한 지남" value={stats.late} unit="곳" tone={stats.late ? "warn" : undefined} hint="홈 '오늘 할 일'에도 뜹니다" />
        </div>

        <div className="msg-layout">
          <Panel
            title="보낸 메시지"
            extra={
              <div className="ops-toolbar">
                <DatePicker.RangePicker size="small" value={range} onChange={(v) => v && setRange(v)} allowClear={false} />
              </div>
            }
            bodyClassName="is-flush"
          >
            <div className="msg-filter">
              <Segmented size="small" value={kind} onChange={setKind} options={KINDS} />
            </div>
            <Table
              size="small"
              rowKey="messageNo"
              loading={list.loading && !list.data}
              dataSource={rows}
              pagination={{ pageSize: 12, size: "small", showSizeChanger: false }}
              rowClassName={(m) => (m.messageNo === selected ? "msg-row is-active" : "msg-row")}
              onRow={(m) => ({ onClick: () => setSelected(m.messageNo) })}
              locale={{ emptyText: <Empty description="보낸 메시지가 없습니다." /> }}
              columns={[
                { title: "보낸 시각", dataIndex: "sentAt", width: 92, render: (v) => <span className="crud-num">{dayjs(v).format("M/D HH:mm")}</span> },
                { title: "종류", dataIndex: "kind", width: 88, render: (v) => <Kind v={v} /> },
                {
                  title: "제목",
                  dataIndex: "title",
                  render: (v, m) => (
                    <>
                      <div className="crud-strong">
                        {m.important && <span className="msg-dot" />}
                        {v}
                      </div>
                      <div className="crud-sub">{m.targetLabel}</div>
                    </>
                  ),
                },
                { title: "진행", key: "p", width: 150, render: (_, m) => <Progress m={m} /> },
              ]}
            />
          </Panel>
          <Panel title="매장별 확인 · 회신">
            <Detail no={selected} refreshKey={tick} />
          </Panel>
        </div>

        <ComposeModal
          open={!!compose}
          initial={compose}
          stores={shops}
          onClose={() => setCompose(null)}
          onSent={(m) => {
            setCompose(null);
            setSelected(m.messageNo);
            setTick((t) => t + 1);
          }}
        />
      </div>
    </ConfigProvider>
  );
};

export default MessagesContainer;
