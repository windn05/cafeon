// 매장 영업 시뮬레이터: 달력에서 날짜(또는 기간)를 고르고, 매장 · 영업 시간 · 시나리오 · 매장 업무 · 이상 상황을 정해
// 매장 직원처럼 POS로 그 날 영업을 돌린다. 데이터는 실제 경로(POS → 본사 수집 API)로 쌓인다
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Checkbox, ConfigProvider, InputNumber, Popconfirm, Segmented, Select, Slider, Tooltip, message } from "antd";
import { LeftOutlined, PauseCircleOutlined, PlayCircleOutlined, RightOutlined, SaveOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { DashHeader, Panel } from "../../../components/Dashboard/Dashboard";
import { ERPapi } from "../../../components/api/api";
import { errorText } from "../../../components/CrudPage/CrudPage";
import { ERP_API } from "../../../config";
import { ACCENT, won } from "../../Ops/opsShared";
import "./simulator.css";

const URL = `${ERP_API}/simulation/pos-run`;
const SAVE_KEY = "sim.settings";
const WEEK = ["일", "월", "화", "수", "목", "금", "토"];
// 날씨 · 상황을 고르면 손님 수 · 배달 비중의 기본값이 따라 바뀐다 (그 뒤 직접 조정 가능)
const WEATHER = {
  평소: { rate: 1, delivery: 0, hint: "요일 · 계절 패턴 그대로" },
  비: { rate: 0.9, delivery: 0.1, hint: "매장 손님 ↓ · 배달 ↑ · 따뜻한 메뉴 ↑" },
  폭염: { rate: 1.05, delivery: 0.03, hint: "아이스 메뉴 크게 ↑" },
  한파: { rate: 0.92, delivery: 0.05, hint: "따뜻한 메뉴 ↑ · 아이스 ↓" },
  행사: { rate: 1.25, delivery: 0, hint: "주변 행사로 18시 이후 손님 ↑" },
  공휴일: { rate: 1, delivery: 0, hint: "평일이어도 주말 패턴" },
};
const SPEEDS = [
  { value: "instant", label: "즉시" },
  { value: "x60", label: "60배속" },
  { value: "realtime", label: "실시간" },
];

const DEFAULTS = {
  storeNos: [],
  hours: [7, 22],
  close: true,
  speed: "instant",
  weather: "평소",
  customerRate: 1,
  deliveryBoost: 0,
  promos: [],
  deliveries: true,
  shortRate: 0.03,
  evening: true,
  stocktake: false,
  cashDiffRate: 0.04,
  offline: false,
  offlineHours: [14, 15],
  lowStock: [],
  hq: false,
};

const loadSaved = () => {
  try {
    return { ...DEFAULTS, ...(JSON.parse(localStorage.getItem(SAVE_KEY)) || {}) };
  } catch {
    return DEFAULTS;
  }
};
const hh = (h) => `${String(h).padStart(2, "0")}:00`;
const ymd = (d) => d.format("YYYY-MM-DD");

// ----- 달력 -----
const Calendar = ({ month, setMonth, cal, storeNos, from, to, onPick }) => {
  const today = cal ? dayjs(cal.today) : dayjs();
  const first = month.startOf("month");
  const cells = [];
  for (let i = 0; i < first.day(); i++) cells.push(null);
  for (let d = 1; d <= month.daysInMonth(); d++) cells.push(first.date(d));
  const picked = (cal?.stores || []).filter((s) => storeNos.includes(s.storeNo));
  const closedCount = (d) => picked.filter((s) => s.closedDates.includes(ymd(d))).length;
  return (
    <div className="sim-cal">
      <div className="sim-cal-head">
        <Button type="text" size="small" icon={<LeftOutlined />} onClick={() => setMonth(month.subtract(1, "month"))} aria-label="이전 달" />
        <b>{month.format("YYYY년 M월")}</b>
        <Button type="text" size="small" icon={<RightOutlined />} disabled={!month.isBefore(today, "month")} onClick={() => setMonth(month.add(1, "month"))} aria-label="다음 달" />
      </div>
      <div className="sim-cal-grid">
        {WEEK.map((w) => (
          <span key={w} className="dw">
            {w}
          </span>
        ))}
        {cells.map((d, i) => {
          if (!d) return <span key={`e${i}`} />;
          const future = d.isAfter(today, "day");
          const n = picked.length ? closedCount(d) : 0;
          const inRange = from && (to ? !d.isBefore(from, "day") && !d.isAfter(to, "day") : d.isSame(from, "day"));
          const cls = [
            future ? "is-off" : n && n === picked.length ? "is-done" : n ? "is-part" : "",
            inRange ? "is-sel" : "",
            d.isSame(today, "day") ? "is-today" : "",
          ].join(" ");
          return (
            <button key={ymd(d)} type="button" className={cls} disabled={future} onClick={() => onPick(d)}>
              {d.date()}
            </button>
          );
        })}
      </div>
      <div className="sim-legend">
        <span>
          <i className="is-done" />
          마감 완료
        </span>
        <span>
          <i className="is-part" />
          일부 매장 마감
        </span>
        <span>
          <i className="is-sel" />
          선택
        </span>
        <span>
          <i className="is-today" />
          오늘
        </span>
      </div>
    </div>
  );
};

// ----- 시간대별 막대: 계획(연한 막대) · 결제된 손님(진한 막대) -----
const HourBars = ({ hours, live }) => {
  const max = Math.max(1, ...hours.map((h) => h.planned ?? h.count));
  return (
    <>
      <div className="sim-bars">
        {hours.map((h) => {
          const plan = h.planned ?? h.count;
          return (
            <Tooltip key={h.hour} title={`${h.hour}시 · ${live ? `${h.posted} / ${plan}명` : `${plan}명`}`}>
              <span className="sim-bar">
                <i className="plan" style={{ height: `${(plan / max) * 100}%` }} />
                {live && <i className="done" style={{ height: `${(h.posted / max) * 100}%` }} />}
              </span>
            </Tooltip>
          );
        })}
      </div>
      <div className="sim-axis">
        <span>07</span>
        <span>11</span>
        <span>15</span>
        <span>19</span>
        <span>22</span>
      </div>
    </>
  );
};

const SimulatorPage = () => {
  const [s, setS] = useState(loadSaved);
  const set = (patch) => setS((x) => ({ ...x, ...patch }));
  const [month, setMonth] = useState(dayjs().startOf("month"));
  const [cal, setCal] = useState(null);
  const [from, setFrom] = useState(null);
  const [to, setTo] = useState(null);
  const [rangeMode, setRangeMode] = useState(false);
  const [menus, setMenus] = useState([]);
  const [materials, setMaterials] = useState([]);
  const [preview, setPreview] = useState(null);
  const [status, setStatus] = useState(null);
  const [replayDays, setReplayDays] = useState(70);

  const loadCal = useCallback(async () => {
    try {
      const res = await ERPapi.get(`${URL}/calendar`, { params: { month: month.format("YYYY-MM") } });
      setCal(res.data);
    } catch (err) {
      message.error(errorText(err, "달력을 불러오지 못했습니다."));
    }
  }, [month]);
  useEffect(() => {
    loadCal();
  }, [loadCal]);

  useEffect(() => {
    ERPapi.get(`${ERP_API}/menus`).then((r) => setMenus(r.data.filter((m) => m.status === "판매중")));
    ERPapi.get(`${ERP_API}/materials`).then((r) => setMaterials(r.data));
  }, []);

  // 처음: 매장은 저장된 것 또는 전체, 날짜는 매장들의 다음 영업일
  useEffect(() => {
    if (!cal || from) return;
    const ids = s.storeNos.length ? s.storeNos.filter((no) => cal.stores.some((x) => x.storeNo === no)) : cal.stores.map((x) => x.storeNo);
    if (!s.storeNos.length || ids.length !== s.storeNos.length) set({ storeNos: ids });
    const lasts = cal.stores.filter((x) => ids.includes(x.storeNo)).map((x) => (x.lastClosing ? dayjs(x.lastClosing) : null));
    const next = lasts.length && lasts.every(Boolean) ? lasts.reduce((a, b) => (a.isBefore(b) ? a : b)).add(1, "day") : dayjs(cal.today);
    const d = next.isAfter(dayjs(cal.today), "day") ? dayjs(cal.today) : next;
    setFrom(d);
    setTo(d);
    setMonth(d.startOf("month"));
  }, [cal]); // eslint-disable-line react-hooks/exhaustive-deps

  // 진행 상황
  const loadStatus = useCallback(async () => {
    try {
      const res = await ERPapi.get(`${URL}/status`);
      setStatus(res.data);
    } catch {
      /* 다음에 다시 */
    }
  }, []);
  const wasRunning = useRef(false);
  useEffect(() => {
    loadStatus();
    const t = setInterval(loadStatus, 1500);
    return () => clearInterval(t);
  }, [loadStatus]);
  useEffect(() => {
    if (status?.running) wasRunning.current = true;
    else if (wasRunning.current) {
      wasRunning.current = false;
      loadCal();
      const last = status?.history?.[0];
      if (last) message.success(`${last.dates} 영업을 마쳤습니다. 영수증 ${last.receipts.toLocaleString()}장 · 마감 ${last.closings}건${last.errors ? ` · 오류 ${last.errors}건` : ""}`);
    }
  }, [status, loadCal]);

  const body = useMemo(() => {
    if (!from) return null;
    return {
      from: ymd(from),
      to: ymd(to || from),
      storeNos: s.storeNos,
      openAt: hh(s.hours[0]),
      closeAt: hh(s.hours[1]),
      close: s.close && s.hours[1] === 22,
      speed: s.speed,
      weather: s.weather,
      customerRate: s.customerRate,
      deliveryBoost: s.deliveryBoost,
      promos: Object.fromEntries(s.promos.filter((p) => p.menuCode).map((p) => [p.menuCode, p.factor])),
      deliveries: s.deliveries,
      shortRate: s.shortRate,
      evening: s.evening,
      stocktake: s.stocktake,
      cashDiffRate: s.cashDiffRate,
      offlineFrom: s.offline ? hh(s.offlineHours[0]) : null,
      offlineTo: s.offline ? hh(s.offlineHours[1]) : null,
      lowStock: s.lowStock,
      hq: s.hq,
    };
  }, [s, from, to]);

  // 미리보기 (설정이 바뀌면 잠시 뒤 다시 계산)
  useEffect(() => {
    if (!body || !body.storeNos.length) {
      setPreview(null);
      return undefined;
    }
    const t = setTimeout(async () => {
      try {
        const res = await ERPapi.post(`${URL}/preview`, body);
        setPreview(res.data);
      } catch {
        setPreview(null);
      }
    }, 350);
    return () => clearTimeout(t);
  }, [body]);

  const pick = (d) => {
    if (!rangeMode) {
      setFrom(d);
      setTo(d);
    } else if (!from || (to && !from.isSame(to, "day")) || d.isBefore(from, "day")) {
      setFrom(d);
      setTo(null);
    } else setTo(d);
  };

  const run = async (rewind) => {
    try {
      await ERPapi.post(`${URL}/days`, { ...body, rewind });
      loadStatus();
    } catch (err) {
      message.error(errorText(err, "시작하지 못했습니다."));
    }
  };
  const stop = async () => {
    await ERPapi.post(`${URL}/stop`);
    loadStatus();
  };
  const replay = async () => {
    try {
      await ERPapi.post(`${URL}/replay`, null, { params: { days: replayDays } });
      loadStatus();
    } catch (err) {
      message.error(errorText(err, "시작하지 못했습니다."));
    }
  };
  const save = () => {
    try {
      localStorage.setItem(SAVE_KEY, JSON.stringify(s));
      message.success("설정을 저장했습니다. 다음에 열면 이 설정으로 시작합니다.");
    } catch {
      message.error("저장하지 못했습니다.");
    }
  };

  const running = !!status?.running;
  const today = cal ? dayjs(cal.today) : dayjs();
  const isTodayOnly = from && from.isSame(today, "day") && (!to || to.isSame(today, "day"));
  const days = from && to ? to.diff(from, "day") + 1 : 1;
  const stores = cal?.stores || [];
  const dateLabel = !from ? "날짜를 고르세요" : !to || from.isSame(to, "day") ? from.format("M/D (dd)") : `${from.format("M/D")} ~ ${to.format("M/D")} (${days}일)`;
  const live = running && status?.mode === "days";
  const hours = live ? status.hours : preview?.hours || [];
  const canRun = from && to && s.storeNos.length && !running && (s.speed !== "realtime" || isTodayOnly);
  const w = WEATHER[s.weather] || WEATHER.평소;

  const runButton = (
    <Button type="primary" icon={<PlayCircleOutlined />} disabled={!canRun} onClick={preview?.needsRewind ? undefined : () => run(false)}>
      {dateLabel} 영업 시작
    </Button>
  );

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp sim-page">
        <DashHeader subtitle="달력에서 날짜를 고르고 매장 · 시나리오를 정하면, 매장 직원처럼 POS로 그 날 영업을 돌립니다. 데이터는 실제 매장과 같은 경로(POS → 본사 수집 API)로 쌓입니다." />

        <div className="sim-grid">
          <Panel title="영업일" extra={<Segmented size="small" value={rangeMode ? "range" : "day"} onChange={(v) => setRangeMode(v === "range")} options={[{ value: "day", label: "하루" }, { value: "range", label: "여러 날" }]} />}>
            <Calendar month={month} setMonth={setMonth} cal={cal} storeNos={s.storeNos} from={from} to={to} onPick={pick} />
            <div className="sim-row">
              <label>영업 시간</label>
              <Slider range min={7} max={22} value={s.hours} onChange={(v) => set({ hours: v })} marks={{ 7: "07", 12: "12", 17: "17", 22: "22" }} style={{ flex: 1 }} />
            </div>
            <div className="sim-row">
              <label>일 마감</label>
              <Checkbox checked={s.close && s.hours[1] === 22} disabled={s.hours[1] !== 22} onChange={(e) => set({ close: e.target.checked })}>
                22시에 마감까지
              </Checkbox>
              {s.hours[1] !== 22 && <span className="ops-note">{hh(s.hours[1])}까지만 판매 (마감 없이 이어서 쌓기)</span>}
            </div>
            {isTodayOnly && s.speed !== "realtime" && <div className="ops-note">오늘은 지금 시각까지의 손님만 받습니다. 그 뒤는 실시간으로 돌리세요.</div>}
          </Panel>

          <Panel title="매장" extra={<span className="ops-note">{s.storeNos.length}곳</span>}>
            {stores.map((x) => (
              <label key={x.storeNo} className={`sim-store ${s.storeNos.includes(x.storeNo) ? "is-on" : ""}`}>
                <Checkbox
                  checked={s.storeNos.includes(x.storeNo)}
                  onChange={(e) => set({ storeNos: e.target.checked ? [...s.storeNos, x.storeNo] : s.storeNos.filter((n) => n !== x.storeNo) })}
                />
                <span>
                  <b>{x.storeName}</b>
                  <small>
                    {x.storeType} · {x.area} · 기본 {x.baseCustomers}명
                  </small>
                </span>
                <small className="sim-last">{x.lastClosing ? `${dayjs(x.lastClosing).format("M/D")}까지 마감` : "기록 없음"}</small>
              </label>
            ))}
            <div className="sim-row">
              <label>실행 속도</label>
              <Segmented value={s.speed} onChange={(v) => set({ speed: v })} options={SPEEDS.map((o) => ({ ...o, disabled: o.value === "realtime" && !isTodayOnly }))} />
            </div>
            <div className="ops-note">즉시: 몇 초~몇십 초 · 60배속: 1시간을 1분에 · 실시간: 오늘, 실제 시각대로</div>
          </Panel>
        </div>

        <Panel title="시나리오">
          <div className="sim-row">
            <label>날씨 · 상황</label>
            <Segmented
              value={s.weather}
              onChange={(v) => set({ weather: v, customerRate: WEATHER[v].rate, deliveryBoost: WEATHER[v].delivery })}
              options={Object.keys(WEATHER)}
            />
            <span className="ops-note">{w.hint}</span>
          </div>
          <div className="sim-row">
            <label>손님 수</label>
            <Slider min={0.5} max={2} step={0.05} value={s.customerRate} onChange={(v) => set({ customerRate: v })} tooltip={{ formatter: (v) => `${Math.round(v * 100)}%` }} style={{ flex: 1, maxWidth: 360 }} />
            <b className="sim-val">{Math.round(s.customerRate * 100)}%</b>
          </div>
          <div className="sim-row">
            <label>배달 비중</label>
            <Slider min={-0.1} max={0.3} step={0.01} value={s.deliveryBoost} onChange={(v) => set({ deliveryBoost: v })} tooltip={{ formatter: (v) => `${v >= 0 ? "+" : ""}${Math.round(v * 100)}%p` }} style={{ flex: 1, maxWidth: 360 }} />
            <b className="sim-val">
              {s.deliveryBoost >= 0 ? "+" : ""}
              {Math.round(s.deliveryBoost * 100)}%p
            </b>
          </div>
          <div className="sim-row is-top">
            <label>프로모션</label>
            <div className="sim-promos">
              {s.promos.map((p, i) => (
                <span key={i} className="sim-promo">
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="메뉴"
                    value={p.menuCode}
                    onChange={(v) => set({ promos: s.promos.map((x, j) => (j === i ? { ...x, menuCode: v } : x)) })}
                    options={menus.map((m) => ({ value: m.menuCode, label: m.menuName }))}
                    style={{ width: 170 }}
                  />
                  <InputNumber min={1.1} max={5} step={0.1} value={p.factor} onChange={(v) => set({ promos: s.promos.map((x, j) => (j === i ? { ...x, factor: v || 1.5 } : x)) })} addonBefore="×" style={{ width: 110 }} />
                  <Button type="text" size="small" onClick={() => set({ promos: s.promos.filter((_, j) => j !== i) })}>
                    빼기
                  </Button>
                </span>
              ))}
              <Button size="small" onClick={() => set({ promos: [...s.promos, { menuCode: undefined, factor: 1.8 }] })}>
                + 프로모션 메뉴
              </Button>
              <span className="ops-note">그 메뉴가 그만큼 더 팔리고, 손님도 조금 늘어납니다 (1+1이면 ×1.8 정도)</span>
            </div>
          </div>
        </Panel>

        <div className="sim-grid">
          <Panel title="매장 업무 · 이상 상황">
            <div className="sim-check">
              <Checkbox checked={s.deliveries} onChange={(e) => set({ deliveries: e.target.checked })}>
                08:00 입고 확인
              </Checkbox>
              <span className="ops-note">덜 옴</span>
              <InputNumber size="small" min={0} max={50} value={Math.round(s.shortRate * 100)} onChange={(v) => set({ shortRate: (v || 0) / 100 })} addonAfter="%" style={{ width: 90 }} disabled={!s.deliveries} />
            </div>
            <div className="sim-check">
              <Checkbox checked={s.evening} onChange={(e) => set({ evening: e.target.checked })}>
                21:30 폐기 · 발주 요청
              </Checkbox>
            </div>
            <div className="sim-check">
              <Checkbox checked={s.stocktake} onChange={(e) => set({ stocktake: e.target.checked })}>
                재고 실사
              </Checkbox>
              <span className="ops-note">평소엔 일요일만, 켜면 이 날도</span>
            </div>
            <div className="sim-check">
              <span className="ops-note">일 마감 시재 차이</span>
              <InputNumber size="small" min={0} max={50} value={Math.round(s.cashDiffRate * 100)} onChange={(v) => set({ cashDiffRate: (v || 0) / 100 })} addonAfter="%" style={{ width: 90 }} />
            </div>
            <div className="sim-sub">이상 상황</div>
            <div className="sim-check">
              <Checkbox checked={s.offline} onChange={(e) => set({ offline: e.target.checked })}>
                본사 연결 끊김
              </Checkbox>
              <Slider range min={7} max={22} value={s.offlineHours} disabled={!s.offline} onChange={(v) => set({ offlineHours: v })} style={{ width: 160 }} />
              <span className="ops-note">{s.offline ? `${hh(s.offlineHours[0])}~${hh(s.offlineHours[1])} 전송이 쌓였다가 한꺼번에` : ""}</span>
            </div>
            <div className="sim-check">
              <span className="ops-note">재고 낮게 시작</span>
              <Select
                mode="multiple"
                allowClear
                placeholder="식자재 (개점 때 하루 치만 있음)"
                value={s.lowStock}
                onChange={(v) => set({ lowStock: v })}
                options={materials.filter((m) => m.materialType !== "포장재·소모품").map((m) => ({ value: m.materialCode, label: m.materialName }))}
                style={{ minWidth: 240, flex: 1 }}
                maxTagCount={2}
              />
            </div>
            <div className="sim-sub">본사 업무</div>
            <div className="sim-check">
              <Segmented value={s.hq ? "on" : "off"} onChange={(v) => set({ hq: v === "on" })} options={[{ value: "off", label: "하지 않음" }, { value: "on", label: "대신 처리" }]} />
              <span className="ops-note">{s.hq ? "아침 요청 승인 · 월 · 목 보충 발주 · 물류센터 입고를 시뮬레이터가 합니다" : "발주 요청 · 입고 예정이 본사 처리 대기로 쌓입니다"}</span>
            </div>
          </Panel>

          <Panel title={live ? `진행 중 · ${status.currentDate ? dayjs(status.currentDate).format("M/D") : ""} ${status.simTime ? status.simTime.slice(0, 5) : ""}` : `예상 결과 · ${from ? from.format("M/D (dd)") : ""}`}>
            {live ? (
              <div className="sim-kpi">
                <div>
                  <span>결제된 손님</span>
                  <b>{status.receipts.toLocaleString()}명</b>
                </div>
                <div>
                  <span>진행</span>
                  <b>
                    {status.day} / {status.totalDays}일
                  </b>
                </div>
                <div className={status.errors ? "is-neg" : ""}>
                  <span>오류</span>
                  <b>{status.errors}건</b>
                </div>
              </div>
            ) : (
              <div className="sim-kpi">
                <div>
                  <span>손님</span>
                  <b>{preview ? `${preview.customers.toLocaleString()}명` : "-"}</b>
                </div>
                <div>
                  <span>매출</span>
                  <b>{preview ? won(preview.amount) : "-"}</b>
                </div>
                <div>
                  <span>배달</span>
                  <b>{preview ? `${preview.deliveryShare}%` : "-"}</b>
                </div>
              </div>
            )}
            <HourBars hours={hours} live={live} />
            <div className="ops-note" style={{ marginTop: 8 }}>
              {live ? status.phase : preview ? `시간대별 손님 (${preview.stores.map((x) => `${x.storeName} ${x.customers}명`).join(" · ")})${days > 1 ? ` · 첫날 기준, ${days}일 이어서` : ""}` : "매장을 고르면 예상 손님이 보입니다."}
            </div>
            {live && (status.requests || status.wastes || status.closings) ? (
              <div className="ops-note">
                발주 요청 {status.requests} · 폐기 {status.wastes} · 실사 {status.stocktakes} · 마감 {status.closings}
              </div>
            ) : null}
          </Panel>
        </div>

        {preview?.needsRewind && !running && (
          <Alert
            type="warning"
            showIcon
            message={`${from.format("M/D")}에 이미 마감한 매장이 있습니다.`}
            description="이 날부터 이후의 모든 매장 · 물류센터 기록(판매 · 마감 · 재고 · 발주 · 수집 기록)을 지우고 다시 쌓습니다. 그 전 날짜의 기록은 그대로입니다."
          />
        )}
        {status?.lastError && !running && <Alert type="error" showIcon message="마지막 오류" description={status.lastError} closable />}

        <div className="sim-foot">
          <div className="sim-history">
            {(status?.history || []).slice(0, 3).map((h, i) => (
              <span key={i}>
                {dayjs(h.at).format("M/D HH:mm")} · {h.dates} · {h.stores.join(", ")} · {h.weather} · 영수증 {Number(h.receipts).toLocaleString()} · {h.result}
                {h.errors ? ` · 오류 ${h.errors}` : ""}
              </span>
            ))}
          </div>
          <div className="sim-actions">
            <Button icon={<SaveOutlined />} onClick={save}>
              설정 저장
            </Button>
            {running ? (
              <Button danger icon={<PauseCircleOutlined />} onClick={stop}>
                멈추기
              </Button>
            ) : preview?.needsRewind ? (
              <Popconfirm title="이 날부터 이후 기록을 지우고 다시 쌓을까요?" okText="지우고 시작" okButtonProps={{ danger: true }} cancelText="닫기" onConfirm={() => run(true)} disabled={!canRun}>
                {runButton}
              </Popconfirm>
            ) : (
              runButton
            )}
          </div>
        </div>

        <details className="sim-replay">
          <summary>처음부터 다시 만들기 (빠른 재생)</summary>
          <div className="sim-row">
            <span className="ops-note">모든 운영 기록을 지우고 오늘 전날까지 N일 치를 본사 업무까지 대신하며 쌓습니다 (70일 약 15분).</span>
            <InputNumber min={7} max={120} value={replayDays} onChange={(v) => setReplayDays(v || 70)} addonAfter="일" style={{ width: 120 }} />
            <Popconfirm title="모든 운영 기록을 지우고 다시 쌓을까요?" okText="지우고 시작" okButtonProps={{ danger: true }} cancelText="닫기" onConfirm={replay} disabled={running}>
              <Button danger disabled={running}>
                지우고 다시 쌓기
              </Button>
            </Popconfirm>
            {running && status?.mode === "replay" && <span className="ops-note">{status.phase} · {status.day} / {status.totalDays}일</span>}
          </div>
        </details>
      </div>
    </ConfigProvider>
  );
};

export default SimulatorPage;
