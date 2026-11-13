import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, ConfigProvider, Popconfirm, Segmented, Table, Tooltip, message } from "antd";
import { ApiOutlined, CheckOutlined, ExportOutlined, ReloadOutlined, SendOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { DashHeader, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { Badge, errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT } from "../../Ops/opsShared";
import "./automation.css";

const N8N_URL = process.env.REACT_APP_N8N_URL || "http://localhost:5678";
const LEVEL_TONE = { 정보: "gray", 주의: "amber", "승인 필요": "red" };
const EVENT_TONE = { 대기: "amber", 전송: "green", 실패: "red" };
const EVENT_LABEL = {
  "order_request.created": "매장 발주 요청",
  "closing.completed": "일마감",
  "closing.cancelled": "마감 취소",
  ping: "연결 확인",
};
const time = (v) => (v ? dayjs(v).format("M/D HH:mm:ss") : "-");

// 10초마다 조용히 다시 불러온다 (표가 깜빡이지 않게 처음에만 로딩 표시)
const usePoll = (url, params, everyMs = 10000) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const key = JSON.stringify(params);
  const load = useCallback(async () => {
    try {
      const res = await ERPapi.get(url, { params: JSON.parse(key) });
      setData(res.data);
    } catch {
      /* 다음 차례에 다시 시도 */
    } finally {
      setLoading(false);
    }
  }, [url, key]);
  useEffect(() => {
    load();
    const t = setInterval(load, everyMs);
    return () => clearInterval(t);
  }, [load, everyMs]);
  return { data, loading, reload: load };
};

const AutomationPage = () => {
  const summary = usePoll(`${ERP_API}/automation/summary`, {});
  const logs = usePoll(`${ERP_API}/automation/logs`, { limit: 300 });
  const events = usePoll(`${ERP_API}/automation/webhooks`, { limit: 200 });
  const [filter, setFilter] = useState("전체");

  const reloadAll = () => {
    summary.reload();
    logs.reload();
    events.reload();
  };

  const rows = useMemo(() => logs.data || [], [logs.data]);
  const waiting = rows.filter((r) => r.level === "승인 필요" && r.status === "열림");
  const today = dayjs().format("YYYY-MM-DD");
  const autoToday = rows.filter((r) => r.level !== "승인 필요" && dayjs(r.createdAt).format("YYYY-MM-DD") === today).length;
  const shown = filter === "전체" ? rows : rows.filter((r) => r.level === filter);
  const s = summary.data || {};

  const ping = async () => {
    try {
      await ERPapi.post(`${ERP_API}/automation/webhooks/test`);
      message.success("연결 확인 이벤트를 보냈습니다. 잠시 뒤 아래 전송 기록을 확인하세요.");
      setTimeout(reloadAll, 4000);
    } catch (err) {
      message.error(errorText(err, "보내지 못했습니다."));
    }
  };

  const resolve = async (r) => {
    try {
      await ERPapi.post(`${ERP_API}/automation/logs/${r.logNo}/resolve`, { result: "담당자가 직접 처리" });
      message.success("처리됨으로 표시했습니다.");
      reloadAll();
    } catch (err) {
      message.error(errorText(err, "바꾸지 못했습니다."));
    }
  };

  const retry = async (e) => {
    try {
      await ERPapi.post(`${ERP_API}/automation/webhooks/${e.eventNo}/retry`);
      message.success("다시 보내기로 했습니다.");
      reloadAll();
    } catch (err) {
      message.error(errorText(err, "다시 보내지 못했습니다."));
    }
  };

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp automation">
        <DashHeader
          subtitle="자동화(n8n)가 처리한 일과 담당자가 결정할 일을 모읍니다. ERP는 발주 요청 · 일마감이 생기면 웹훅으로 n8n에 바로 알립니다."
          extra={
            <div className="ops-toolbar">
              <Button icon={<ReloadOutlined />} onClick={reloadAll}>
                새로고침
              </Button>
              <Button icon={<SendOutlined />} disabled={!s.webhookEnabled} onClick={ping}>
                연결 확인
              </Button>
              <Button icon={<ExportOutlined />} href={N8N_URL} target="_blank" rel="noreferrer">
                n8n 열기
              </Button>
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard label="승인 대기" value={waiting.length} unit="건" tone={waiting.length ? "warn" : undefined} hint="자동화가 멈추고 담당자 결정을 기다리는 일" keyed />
          <StatCard label="오늘 자동 처리 · 알림" value={autoToday} unit="건" hint="승인 없이 끝난 기록" />
          <StatCard label="전송 대기 이벤트" value={s.pendingEvents ?? "-"} unit="건" hint="n8n이 꺼져 있으면 쌓였다가 다시 보냄" />
          <StatCard
            label="전송 실패"
            value={s.failedEvents ?? "-"}
            unit="건"
            tone={s.failedEvents ? "warn" : undefined}
            hint={s.webhookEnabled ? "재시도를 모두 넘긴 이벤트" : "웹훅이 꺼져 있습니다 (local.properties)"}
          />
        </div>

        {waiting.length > 0 && (
          <Panel title="승인 필요" extra="n8n 승인 화면에서 결정하면 자동화가 이어서 처리합니다" bodyClassName="is-flush">
            <Table
              size="small"
              rowKey="logNo"
              dataSource={waiting}
              pagination={false}
              columns={[
                { title: "올라온 시각", dataIndex: "createdAt", width: 130, render: (v) => <span className="crud-num">{time(v)}</span> },
                { title: "워크플로", dataIndex: "workflow", width: 140 },
                {
                  title: "내용",
                  dataIndex: "title",
                  render: (v, r) => (
                    <>
                      <div className="crud-strong">{v}</div>
                      {r.detail && <div className="automation-detail">{r.detail}</div>}
                    </>
                  ),
                },
                {
                  title: "",
                  key: "act",
                  width: 210,
                  align: "right",
                  render: (_, r) => (
                    <div className="automation-actions">
                      {r.actionUrl && (
                        <Button type="primary" size="small" href={r.actionUrl} target="_blank" rel="noreferrer">
                          승인 화면 열기
                        </Button>
                      )}
                      <Popconfirm title="직접 처리했으면 닫습니다." description="자동화는 이 건을 더 기다리지 않습니다." okText="닫기" cancelText="취소" onConfirm={() => resolve(r)}>
                        <Tooltip title="처리됨으로 표시">
                          <Button size="small" icon={<CheckOutlined />} />
                        </Tooltip>
                      </Popconfirm>
                    </div>
                  ),
                },
              ]}
            />
          </Panel>
        )}

        <Panel
          title="자동화 기록"
          extra={<Segmented size="small" value={filter} onChange={setFilter} options={["전체", "정보", "주의", "승인 필요"]} />}
          bodyClassName="is-flush"
        >
          <Table
            size="small"
            rowKey="logNo"
            loading={logs.loading}
            dataSource={shown}
            pagination={{ pageSize: 15, showSizeChanger: false, hideOnSinglePage: true }}
            locale={{ emptyText: "아직 자동화 기록이 없습니다." }}
            columns={[
              { title: "시각", dataIndex: "createdAt", width: 130, render: (v) => <span className="crud-num">{time(v)}</span> },
              { title: "워크플로", dataIndex: "workflow", width: 140 },
              { title: "구분", dataIndex: "level", width: 96, render: (v) => <Badge value={v} tones={LEVEL_TONE} /> },
              {
                title: "내용",
                dataIndex: "title",
                render: (v, r) => (
                  <>
                    <div className="crud-strong">{v}</div>
                    {r.detail && <div className="automation-detail">{r.detail}</div>}
                  </>
                ),
              },
              { title: "관련 문서", dataIndex: "refNumber", width: 130, render: (v) => <span className="crud-num">{v || "-"}</span> },
              {
                title: "결과",
                key: "result",
                width: 200,
                render: (_, r) =>
                  r.level === "승인 필요" ? (
                    r.status === "열림" ? (
                      <span className="crud-badge amber">결정 대기</span>
                    ) : (
                      <>
                        <div>{r.result || "처리됨"}</div>
                        <div className="crud-sub">
                          {r.resolvedBy} · {time(r.resolvedAt)}
                        </div>
                      </>
                    )
                  ) : (
                    <span className="crud-sub">{r.result || ""}</span>
                  ),
              },
            ]}
          />
        </Panel>

        <Panel
          title="웹훅 전송 기록"
          extra={
            <span className="crud-sub">
              <ApiOutlined /> {s.webhookEnabled ? s.webhookUrl : "꺼짐"} · 서명 HMAC-SHA256 · 실패 시 최대 {events.data?.[0]?.maxAttempts ?? 8}번 시도
            </span>
          }
          bodyClassName="is-flush"
        >
          <Table
            size="small"
            rowKey="eventNo"
            loading={events.loading}
            dataSource={events.data || []}
            pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: true }}
            locale={{ emptyText: "보낸 이벤트가 없습니다." }}
            expandable={{
              expandedRowRender: (e) => <pre className="automation-payload">{JSON.stringify(JSON.parse(e.payload), null, 2)}</pre>,
            }}
            columns={[
              { title: "생성", dataIndex: "createdAt", width: 130, render: (v) => <span className="crud-num">{time(v)}</span> },
              {
                title: "이벤트",
                dataIndex: "eventType",
                render: (v, e) => (
                  <>
                    <div className="crud-strong">{EVENT_LABEL[v] || v}</div>
                    <div className="crud-sub">
                      <code>{v}</code> · {e.eventId.slice(0, 8)}
                    </div>
                  </>
                ),
              },
              { title: "상태", dataIndex: "status", width: 80, render: (v) => <Badge value={v} tones={EVENT_TONE} /> },
              { title: "시도", dataIndex: "attempts", width: 70, align: "right", render: (v, e) => <span className="crud-num">{v} / {e.maxAttempts}</span> },
              {
                title: "보낸 시각 · 다음 시도",
                key: "when",
                width: 150,
                render: (_, e) => <span className="crud-num crud-sub">{e.sentAt ? time(e.sentAt) : e.nextAttemptAt ? `다음 ${time(e.nextAttemptAt)}` : "-"}</span>,
              },
              { title: "마지막 오류", dataIndex: "lastError", render: (v) => <span className="crud-sub">{v || ""}</span> },
              {
                title: "",
                key: "retry",
                width: 90,
                align: "right",
                render: (_, e) =>
                  e.status === "실패" ? (
                    <Button size="small" onClick={() => retry(e)}>
                      다시 보내기
                    </Button>
                  ) : null,
              },
            ]}
          />
        </Panel>
      </div>
    </ConfigProvider>
  );
};

export default AutomationPage;
