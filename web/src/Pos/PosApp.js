// 매장 POS: 본사 ERP와 별개 시스템. POS 서버(8081)에 판매 · 마감을 먼저 저장하고,
// POS 서버가 본사로 보내고(전송 대기열) 본사 메시지 · 메뉴 · 재고를 받아 온다.
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import axios from "axios";
import { Alert, Badge, Button, ConfigProvider, Input, Modal, Popconfirm, Tabs, Tooltip, message } from "antd";
import { BellOutlined, LogoutOutlined, SyncOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import Brand from "../components/Brand/Brand";
import { POS_API } from "../config";
import { ClosingTab, CountTab, DeliveryTab, OrderTab, OutboxTab, ReceiptsTab, RecipeTab, SaleTab, WasteTab } from "./PosTabs";
import MessagesTab from "./PosMessages";
import { errText, won } from "./posShared";
import { erpTheme } from "../theme/erpTheme";
import "../pages/Ops/ops.css";
import "../components/CrudPage/crudPage.css";
import "../pages/Weekly/weekly.css";
import "./pos.css";

// ERP와 같은 색 · 글꼴, 카운터용으로 글자와 컨트롤을 한 단계 크게
const posTheme = {
  ...erpTheme,
  token: { ...erpTheme.token, fontSize: 15, controlHeight: 40, controlHeightLG: 48, controlHeightSM: 32 },
  components: { ...erpTheme.components, Segmented: { ...erpTheme.components.Segmented, itemSelectedColor: "#171717", trackBg: "#efedea" } },
};

const TOKEN = "pos.token";
// 본사 ERP [데이터 수집]에서 'POS 열기'로 넘겨준 연동 키 (한 번 쓰고 지움)
const HANDOFF = "pos.registerKey";

const store = {
  get: (k) => {
    try {
      return localStorage.getItem(k) || "";
    } catch {
      return "";
    }
  },
  set: (k, v) => {
    try {
      if (v) localStorage.setItem(k, v);
      else localStorage.removeItem(k);
    } catch {
      /* 저장 못 해도 이번 화면에서는 쓴다 */
    }
  },
};

// 기기 토큰은 탭마다 따로 기억한다 (탭마다 다른 매장 POS를 열어 볼 수 있게). 새 탭은 마지막으로 쓴 매장으로 시작
const tokenStore = {
  get: () => {
    try {
      const mine = sessionStorage.getItem(TOKEN);
      if (mine) return mine;
      // 처음 여는 탭: 마지막 매장으로 시작하고, 이 탭에 고정한다 (다른 탭이 매장을 바꿔도 안 따라가게)
      const last = store.get(TOKEN);
      if (last) sessionStorage.setItem(TOKEN, last);
      return last;
    } catch {
      return store.get(TOKEN);
    }
  },
  set: (v) => {
    try {
      if (v) sessionStorage.setItem(TOKEN, v);
      else sessionStorage.removeItem(TOKEN);
    } catch {
      /* 탭 저장소를 못 쓰면 공용 저장소만 쓴다 */
    }
    store.set(TOKEN, v);
  },
};

// ----- 기기 등록 -----
const RegisterScreen = ({ onRegistered, notice }) => {
  const [key, setKey] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const register = useCallback(
    async (value) => {
      if (!value?.trim()) return;
      setLoading(true);
      setError("");
      try {
        const { data } = await axios.post(`${POS_API}/auth/register`, { storeKey: value.trim() });
        onRegistered(data.token);
      } catch (err) {
        setError(errText(err, "POS 서버에 연결하지 못했습니다."));
      } finally {
        setLoading(false);
      }
    },
    [onRegistered]
  );

  // 본사 화면에서 넘겨준 키가 있으면 바로 등록
  useEffect(() => {
    const handed = store.get(HANDOFF);
    if (handed) {
      store.set(HANDOFF, "");
      register(handed);
    }
  }, [register]);

  return (
    <div className="sa-login">
      <h2>POS 기기 등록</h2>
      <p>
        본사에서 받은 <b>매장 연동 키</b>를 넣으면 이 POS가 매장에 연결됩니다. 메뉴 · 가격은 본사에서 받아 오고, 판매 · 마감 · 발주 요청은 본사로 자동 전송됩니다.
      </p>
      {notice && <Alert type="warning" showIcon message={notice} style={{ marginBottom: 12 }} />}
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Input.Search placeholder="sk_…" enterButton="등록" size="large" value={key} loading={loading} onChange={(e) => setKey(e.target.value)} onSearch={register} />
      <p className="pos-hint">연동 키는 본사 ERP의 [데이터 수집] 화면에서 매장별로 발급합니다. 같은 화면의 [매장 POS 열기]로 고른 매장의 POS를 바로 열 수 있습니다.</p>
    </div>
  );
};

// ----- 본사 연결 상태 -----
const SyncBadge = ({ sync, syncing, onSync }) => {
  if (!sync) return null;
  const pending = sync.pending || 0;
  const failed = sync.failed || 0;
  const tip = sync.online
    ? `마지막 연결 ${sync.lastContactAt ? dayjs(sync.lastContactAt).format("HH:mm:ss") : "-"}`
    : `${sync.lastError || "본사 서버에 연결할 수 없습니다."} 판매는 계속할 수 있고, 연결되면 자동으로 보냅니다.`;
  return (
    <div className="pos-sync">
      <Tooltip title={tip}>
        <span className={`pos-conn ${sync.online ? "is-on" : "is-off"}`}>{sync.online ? "본사 연결됨" : "본사 연결 끊김"}</span>
      </Tooltip>
      {pending > 0 && <span className="pos-pill is-wait">전송 대기 {pending}</span>}
      {failed > 0 && <span className="pos-pill is-fail">전송 실패 {failed}</span>}
      <Button type="text" icon={<SyncOutlined spin={syncing} />} onClick={onSync} disabled={syncing}>
        지금 동기화
      </Button>
    </div>
  );
};

const PosApp = () => {
  // 본사 ERP에서 [매장 POS 열기]로 다른 매장 키를 넘겨줬으면, 기존 등록 대신 그 매장으로 새로 등록한다
  const [token, setToken] = useState(() => (store.get(HANDOFF) ? "" : tokenStore.get()));
  const [notice, setNotice] = useState("");
  const [me, setMe] = useState(null);
  const [master, setMaster] = useState(null);
  const [today, setToday] = useState(null);
  const [messages, setMessages] = useState([]);
  const [tab, setTab] = useState("sale");
  const [syncing, setSyncing] = useState(false);
  const [popup, setPopup] = useState(null);
  const seenPopup = useRef(new Set());
  const masterAt = useRef(null);

  const api = useMemo(() => {
    const a = axios.create({ baseURL: POS_API, headers: { Authorization: `Bearer ${token}` } });
    a.interceptors.response.use(
      (r) => r,
      (err) => {
        if (err?.response?.status === 401) {
          tokenStore.set("");
          setToken("");
          setNotice("기기 등록이 풀렸습니다. 연동 키로 다시 등록해 주세요.");
        }
        return Promise.reject(err);
      }
    );
    return a;
  }, [token]);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      const [m, t, msg] = await Promise.all([api.get("/me"), api.get("/today"), api.get("/messages")]);
      setMe(m.data);
      setToday(t.data);
      setMessages(msg.data);
      // 본사가 메뉴를 바꿔서 POS가 새로 받았으면 메뉴판도 다시 읽는다
      if (!master || masterAt.current !== m.data.sync.masterSyncedAt) {
        const ms = await api.get("/master");
        setMaster(ms.data);
        masterAt.current = m.data.sync.masterSyncedAt;
      }
    } catch (err) {
      if (err?.response?.status !== 401) message.error(errText(err, "POS 서버에 연결하지 못했습니다."));
    }
  }, [api, token, master]);

  useEffect(() => {
    load();
    const id = setInterval(load, 8000);
    return () => clearInterval(id);
  }, [load]);

  // 읽지 않은 중요 메시지는 화면에 띄워서 확인받는다
  useEffect(() => {
    if (popup) return;
    const m = messages.find((x) => x.important && !x.readAt && !seenPopup.current.has(x.id));
    if (m) setPopup(m);
  }, [messages, popup]);

  // 저장 뒤: 바로 한 번, 본사 전송이 끝날 즈음 한 번 더 새로 읽기
  const refresh = useCallback(() => {
    load();
    setTimeout(load, 1500);
  }, [load]);

  const syncNow = async () => {
    setSyncing(true);
    try {
      await api.post("/sync");
      await load();
      message.success("본사와 동기화했습니다.");
    } catch (err) {
      message.error(errText(err, "동기화하지 못했습니다."));
    } finally {
      setSyncing(false);
    }
  };

  const readMessage = async (m) => {
    try {
      await api.post(`/messages/${m.id}/read`);
      refresh();
    } catch (err) {
      message.error(errText(err, "확인을 보내지 못했습니다."));
    }
  };

  const onRegistered = (t) => {
    tokenStore.set(t);
    setNotice("");
    setMaster(null);
    masterAt.current = null;
    seenPopup.current = new Set();
    setToken(t);
  };

  const unregister = () => {
    tokenStore.set("");
    setToken("");
    setMe(null);
    setToday(null);
    setMaster(null);
    setMessages([]);
  };

  const unread = messages.filter((m) => !m.readAt).length;
  const ready = token && me && today && master;
  const sync = today?.sync || me?.sync;

  return (
    <ConfigProvider theme={posTheme}>
      <div className="sa-page">
        <header className="sa-head">
          <Brand sub="매장 POS" />
          {ready && (
            <div className="sa-store">
              <div className="sa-store-name">
                <b>{me.storeName}</b>
                <span>
                  {me.storeType} · {dayjs().format("M월 D일 (dd)")}
                </span>
              </div>
              <div className="sa-head-actions">
              <SyncBadge sync={sync} syncing={syncing} onSync={syncNow} />
              <Badge count={unread} size="small" offset={[-6, 6]}>
                <Button type="text" icon={<BellOutlined />} onClick={() => setTab("messages")}>
                  본사 알림
                </Button>
              </Badge>
              <Popconfirm
                title="이 기기의 매장 등록을 풀까요?"
                description="POS에 저장된 판매 기록과 전송 대기열은 남아 있습니다. 다시 등록하면 이어서 보냅니다."
                okText="등록 해제"
                cancelText="닫기"
                onConfirm={unregister}
              >
                <Button type="text" icon={<LogoutOutlined />}>
                  등록 해제
                </Button>
              </Popconfirm>
              </div>
            </div>
          )}
        </header>
        <main className="sa-main">
          <div className="sa-main-inner">
          {!token ? (
            <RegisterScreen onRegistered={onRegistered} notice={notice} />
          ) : !ready ? (
            <div className="sa-login">불러오는 중…</div>
          ) : (
            <>
              {sync?.keyRejected && (
                <Alert
                  type="error"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message="본사가 이 매장의 연동 키를 다시 발급해서 전송이 멈췄습니다."
                  description="새 연동 키로 기기를 다시 등록하면 쌓인 데이터를 이어서 보냅니다."
                  action={
                    <Button size="small" danger onClick={unregister}>
                      다시 등록
                    </Button>
                  }
                />
              )}
              <div className="sa-summary pos-summary">
                <div>
                  <span>오늘 매출 · 이 POS</span>
                  <b>{won(today.sales.amount)}</b>
                </div>
                <div>
                  <span>영수증 · 객단가</span>
                  <b>
                    {today.sales.receipts}건<small>객단가 {won(today.sales.avgTicket)}</small>
                  </b>
                </div>
                <div>
                  <span>일 마감</span>
                  <b>{today.closing ? "완료" : "영업 중"}</b>
                </div>
                <div>
                  <span>입고 예정</span>
                  <b>{today.openOrders.length}건</b>
                </div>
              </div>
              <Tabs
                activeKey={tab}
                onChange={setTab}
                items={[
                  { key: "sale", label: "판매", children: <SaleTab api={api} master={master} today={today} onSaved={refresh} /> },
                  { key: "receipts", label: `영수증 ${today.sales.receipts || ""}`, children: <ReceiptsTab today={today} /> },
                  { key: "recipe", label: "레시피", children: <RecipeTab master={master} /> },
                  { key: "close", label: "일 마감", children: <ClosingTab api={api} today={today} onSaved={refresh} /> },
                  { key: "waste", label: "폐기", children: <WasteTab api={api} master={master} today={today} onSaved={refresh} /> },
                  { key: "count", label: "재고 실사", children: <CountTab api={api} master={master} today={today} onSaved={refresh} /> },
                  { key: "order", label: "발주 요청", children: <OrderTab api={api} master={master} today={today} onSaved={refresh} /> },
                  { key: "delivery", label: `입고 확인 ${today.openOrders.length || ""}`, children: <DeliveryTab api={api} master={master} today={today} onSaved={refresh} /> },
                  {
                    key: "messages",
                    label: (
                      <Badge count={unread} size="small" offset={[8, -2]}>
                        본사 알림
                      </Badge>
                    ),
                    children: <MessagesTab api={api} messages={messages} onRead={readMessage} onSaved={refresh} />,
                  },
                  {
                    key: "outbox",
                    label: (
                      <Badge count={sync?.failed || 0} size="small" offset={[8, -2]}>
                        전송 기록
                      </Badge>
                    ),
                    children: <OutboxTab api={api} sync={sync} onSaved={refresh} />,
                  },
                ]}
              />
            </>
          )}
          </div>
        </main>

        <Modal
          open={!!popup}
          title={popup ? `본사 ${popup.kind} · ${popup.title}` : ""}
          okText="확인했습니다"
          cancelText="나중에"
          onOk={() => {
            // 닫히는 동안 한 번 더 눌릴 수 있어서 popup이 비었으면 무시
            if (!popup) return;
            seenPopup.current.add(popup.id);
            readMessage(popup);
            setPopup(null);
          }}
          onCancel={() => {
            if (popup) seenPopup.current.add(popup.id);
            setPopup(null);
          }}
        >
          {popup && (
            <>
              <p className="pos-msg-body">{popup.body}</p>
              <p className="pos-hint">
                {popup.sentBy} · {dayjs(popup.sentAt).format("M/D HH:mm")}
              </p>
            </>
          )}
        </Modal>
      </div>
    </ConfigProvider>
  );
};

export default PosApp;
