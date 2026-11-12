import { useMemo, useState } from "react";
import { Button, ConfigProvider, Popconfirm, Select, Table, Tooltip, message } from "antd";
import {
  CopyOutlined,
  DatabaseOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  MobileOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { DashHeader, Panel, StatCard } from "../../components/Dashboard/Dashboard";
import { Badge, errorText } from "../../components/CrudPage/CrudPage";
import { ERPapi } from "../../components/api/api";
import { ERP_API } from "../../config";
import { ACCENT, useFetch } from "../Ops/opsShared";
import { useNavigate } from "react-router-dom";
import "./integration.css";

dayjs.extend(relativeTime);

const TYPES = ["판매", "일마감", "폐기", "실사", "발주요청", "입고", "지시회신"];
const ago = (v) => (v ? dayjs(v).fromNow() : "-");

// 매장 POS를 새 탭으로 연다. 연동 키를 넘겨주면 POS가 그 키로 기기 등록을 한다.
export const openPos = (key) => {
  try {
    localStorage.setItem("pos.registerKey", key);
  } catch {
    /* 못 저장하면 POS 화면에서 키를 직접 넣으면 된다 */
  }
  window.open("/pos", "_blank");
};

const KeyCell = ({ value, storeNo, onReissued }) => {
  const [show, setShow] = useState(false);
  const reissue = async () => {
    try {
      await ERPapi.post(`${ERP_API}/integration/stores/${storeNo}/key`);
      message.success("연동 키를 새로 발급했습니다. 매장 시스템에 새 키를 넣어야 합니다.");
      onReissued();
    } catch (err) {
      message.error(errorText(err, "재발급하지 못했습니다."));
    }
  };
  if (!value) return "-";
  return (
    <span className="key-cell">
      <code>{show ? value : `${value.slice(0, 7)}••••••••${value.slice(-4)}`}</code>
      <Tooltip title={show ? "가리기" : "보기"}>
        <Button type="text" size="small" icon={show ? <EyeInvisibleOutlined /> : <EyeOutlined />} onClick={() => setShow(!show)} />
      </Tooltip>
      <Tooltip title="복사">
        <Button
          type="text"
          size="small"
          icon={<CopyOutlined />}
          onClick={() => navigator.clipboard?.writeText(value).then(() => message.success("연동 키를 복사했습니다."))}
        />
      </Tooltip>
      <Popconfirm title="연동 키를 새로 발급할까요?" description="지금 키는 바로 못 쓰게 되어, 매장 시스템 설정을 바꿔야 합니다." okText="재발급" okButtonProps={{ danger: true }} cancelText="닫기" onConfirm={reissue}>
        <Tooltip title="재발급">
          <Button type="text" size="small" icon={<ReloadOutlined />} />
        </Tooltip>
      </Popconfirm>
    </span>
  );
};

const IntegrationContainer = () => {
  const status = useFetch(`${ERP_API}/integration/status`, {});
  const [logStore, setLogStore] = useState(null);
  const [logStatus, setLogStatus] = useState(null);
  const logs = useFetch(`${ERP_API}/integration/logs`, { storeNo: logStore || undefined, status: logStatus || undefined, limit: 200 });
  const navigate = useNavigate();
  const [demoStore, setDemoStore] = useState(null);

  const rows = useMemo(() => status.data || [], [status.data]);
  const stats = useMemo(() => {
    const active = rows.filter((r) => r.expectsData);
    return {
      linked: rows.filter((r) => r.integrationKey).length,
      today: rows.reduce((s, r) => s + r.todayRecords, 0),
      fails: rows.reduce((s, r) => s + r.failures7d, 0),
      missing: active.filter((r) => !r.yesterdayClosingReceived).length,
      activeCount: active.length,
    };
  }, [rows]);

  const reloadAll = () => {
    status.reload();
    logs.reload();
  };
  const shops = rows.filter((r) => r.storeType === "직영점" || r.storeType === "가맹점");

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          subtitle="매장 POS가 연동 키로 본사에 보내는 데이터(판매 · 마감 · 폐기 · 실사 · 발주 요청 · 입고 · 지시 회신)를 확인합니다."
          extra={
            <div className="ops-toolbar">
              <Select
                placeholder="POS로 열 매장"
                style={{ minWidth: 200 }}
                value={demoStore}
                onChange={setDemoStore}
                options={shops.filter((r) => r.status === "영업중").map((r) => ({ value: r.integrationKey, label: r.storeName }))}
              />
              <Button icon={<MobileOutlined />} disabled={!demoStore} onClick={() => openPos(demoStore)}>
                매장 POS 열기
              </Button>
              <Button icon={<DatabaseOutlined />} onClick={() => navigate("/erp/integration/simulator")}>
                매장 영업 시뮬레이터
              </Button>
            </div>
          }
        />

        <div className="stat-grid">
          <StatCard label="연동된 사업장" value={stats.linked} unit="곳" hint="연동 키가 발급된 매장 · 센터" />
          <StatCard label="오늘 받은 데이터" value={stats.today.toLocaleString()} unit="건" hint="영수증 · 품목 줄 수 합계" />
          <StatCard label="어제 마감 미수신" value={stats.missing} unit={`/ ${stats.activeCount}곳`} tone={stats.missing ? "warn" : undefined} hint="영업 중인 판매 매장 기준" />
          <StatCard label="최근 7일 수신 실패" value={stats.fails} unit="건" tone={stats.fails ? "warn" : undefined} hint="형식 오류 · 없는 코드 · 시간 초과 등" />
        </div>

        <Panel title="사업장별 수신 상태" extra="마지막으로 받은 시각" bodyClassName="is-flush">
          <Table
            size="small"
            rowKey="storeNo"
            loading={status.loading}
            dataSource={rows}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              {
                title: "사업장",
                dataIndex: "storeName",
                fixed: "left",
                render: (v, r) => (
                  <>
                    <div className="crud-strong">{v}</div>
                    <div className="crud-sub">
                      {r.storeType}
                      {r.status !== "영업중" ? ` · ${r.status}` : ""}
                    </div>
                  </>
                ),
              },
              {
                title: "상태",
                key: "health",
                render: (_, r) => {
                  if (!r.expectsData) return <Badge value={r.status === "영업중" ? "판매 없음" : r.status} tones={{}} />;
                  if (!r.yesterdayClosingReceived) return <span className="crud-badge red">어제 마감 미수신</span>;
                  if (r.failures7d) return <span className="crud-badge amber">실패 {r.failures7d}건</span>;
                  return <span className="crud-badge green">정상</span>;
                },
              },
              ...TYPES.map((t) => ({
                title: t,
                key: t,
                render: (_, r) => <span className="crud-num crud-sub">{ago(r.lastReceived[t])}</span>,
              })),
              { title: "오늘", dataIndex: "todayRecords", align: "right", render: (v) => <span className="crud-num">{v ? `${v.toLocaleString()}건` : "-"}</span> },
              { title: "연동 키", dataIndex: "integrationKey", render: (v, r) => <KeyCell value={v} storeNo={r.storeNo} onReissued={reloadAll} /> },
            ]}
          />
        </Panel>

        <Panel
          title="최근 수집 기록"
          extra={
            <div className="ops-toolbar">
              <Select allowClear placeholder="사업장 전체" style={{ width: 150 }} value={logStore} onChange={setLogStore} options={rows.map((r) => ({ value: r.storeNo, label: r.storeName }))} />
              <Select allowClear placeholder="결과 전체" style={{ width: 110 }} value={logStatus} onChange={setLogStatus} options={["성공", "실패"].map((s) => ({ value: s, label: s }))} />
            </div>
          }
          bodyClassName="is-flush"
        >
          <Table
            size="small"
            rowKey="logNo"
            loading={logs.loading}
            dataSource={logs.data || []}
            pagination={{ pageSize: 12, showSizeChanger: false }}
            columns={[
              { title: "받은 시각", dataIndex: "receivedAt", render: (v) => <span className="crud-num">{dayjs(v).format("M/D HH:mm:ss")}</span> },
              { title: "사업장", dataIndex: "storeName" },
              { title: "종류", dataIndex: "dataType" },
              { title: "영업일", dataIndex: "businessDate", render: (v) => <span className="crud-num">{v ? dayjs(v).format("M/D") : "-"}</span> },
              { title: "건수", dataIndex: "recordCount", align: "right" },
              { title: "결과", dataIndex: "status", render: (v) => <Badge value={v} tones={{ 성공: "green", 실패: "red" }} /> },
              { title: "메시지", dataIndex: "message", render: (v) => <span className="crud-sub">{v || ""}</span> },
            ]}
          />
        </Panel>

      </div>
    </ConfigProvider>
  );
};

export default IntegrationContainer;
