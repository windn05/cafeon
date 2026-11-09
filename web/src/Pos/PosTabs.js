// 매장 POS 업무 탭: 판매 · 영수증 · 일 마감 · 폐기 · 재고 실사 · 발주 요청 · 입고 확인 · 전송 기록
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, DatePicker, Empty, Input, InputNumber, Popconfirm, Segmented, Select, Table, message } from "antd";
import { CheckCircleFilled, ShoppingCartOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { SendBadge, errText, num, won } from "./posShared";

const PAYMENTS = ["카드", "간편결제", "현금"];
const CHANNELS = ["매장", "포장", "배달"];
const WASTE_REASONS = ["유통기한 경과", "당일 미판매", "제조 실수", "파손", "품질 불량", "기타"];

// 본사 기준 재고에 POS 식자재 정보(포장 단위)를 붙인다. 본사 재고를 아직 못 받았으면 식자재 목록만.
const stockRows = (master, today) => {
  const byCode = Object.fromEntries((today.stock || []).map((s) => [s.materialCode, s]));
  return master.materials.map((m) => ({ ...m, ...(byCode[m.materialCode] || {}), unit: m.unit, materialName: m.materialName }));
};

const SnapshotNote = ({ today }) => (
  <div className="ops-note">
    본사 기준 재고 · {today.snapshotAt ? `${dayjs(today.snapshotAt).format("HH:mm")}에 받음` : "아직 받지 못함"} (본사 연결 시 20초마다 갱신)
  </div>
);

// ----- 판매 -----
export const SaleTab = ({ api, master, today, onSaved }) => {
  const [cat, setCat] = useState("전체");
  const [cart, setCart] = useState([]);
  const [payment, setPayment] = useState("카드");
  const [channel, setChannel] = useState("매장");
  const [sending, setSending] = useState(false);
  const selling = master.menus.filter((m) => m.status !== "단종");
  const cats = ["전체", ...new Set(selling.map((m) => m.category))];
  const list = selling.filter((m) => cat === "전체" || m.category === cat);
  const total = cart.reduce((s, l) => s + l.menu.price * l.qty, 0);
  const add = (menu) =>
    setCart((c) => (c.some((l) => l.menu.menuCode === menu.menuCode) ? c.map((l) => (l.menu.menuCode === menu.menuCode ? { ...l, qty: l.qty + 1 } : l)) : [...c, { menu, qty: 1 }]));
  const setQty = (code, qty) => setCart((c) => (qty <= 0 ? c.filter((l) => l.menu.menuCode !== code) : c.map((l) => (l.menu.menuCode === code ? { ...l, qty } : l))));

  // 본사가 메뉴를 내리거나 품절로 바꾸면 장바구니에서도 뺀다
  useEffect(() => {
    const ok = new Set(master.menus.filter((m) => m.status === "판매중").map((m) => m.menuCode));
    setCart((c) => c.filter((l) => ok.has(l.menu.menuCode)));
  }, [master]);

  const pay = async () => {
    setSending(true);
    try {
      const { data } = await api.post("/sales", { paymentMethod: payment, channel, items: cart.map((l) => ({ menuCode: l.menu.menuCode, quantity: l.qty })) });
      message.success(`결제 완료 · ${won(data.totalAmount)} · ${data.receiptNo}`);
      setCart([]);
      onSaved();
    } catch (err) {
      message.error(errText(err, "결제를 저장하지 못했습니다."));
    } finally {
      setSending(false);
    }
  };

  if (today.closing) return <Alert type="info" showIcon message="오늘은 마감했습니다. 판매를 더 받을 수 없습니다." />;
  return (
    <div className="pos">
      <div>
        <div className="pos-cats">
          <Segmented size="large" value={cat} onChange={setCat} options={cats} />
        </div>
        <div className="pos-menus">
          {list.map((m) => {
            const off = m.status !== "판매중";
            return (
              <button key={m.menuCode} type="button" className={`pos-menu ${off ? "is-off" : ""}`} onClick={() => !off && add(m)} disabled={off}>
                <b>{m.menuName}</b>
                <span>{off ? m.status : won(m.price)}</span>
              </button>
            );
          })}
        </div>
      </div>
      <div className="pos-cart">
        <div className="pos-cart-lines">
        {cart.length === 0 ? (
          <div className="pos-cart-empty">메뉴를 눌러 담으세요</div>
        ) : (
          cart.map((l) => (
            <div key={l.menu.menuCode} className="pos-line">
              <span>{l.menu.menuName}</span>
              <span className="pos-qty">
                <button type="button" onClick={() => setQty(l.menu.menuCode, l.qty - 1)} aria-label="하나 빼기">
                  −
                </button>
                <b>{l.qty}</b>
                <button type="button" onClick={() => setQty(l.menu.menuCode, l.qty + 1)} aria-label="하나 더">
                  +
                </button>
              </span>
              <span className="crud-num">{won(l.menu.price * l.qty)}</span>
            </div>
          ))
        )}
        </div>
        <Segmented block value={payment} onChange={setPayment} options={PAYMENTS} />
        <Segmented block value={channel} onChange={setChannel} options={CHANNELS} />
        <div className="pos-total">
          <span>합계 {cart.length > 0 && <small className="pos-hint">· {cart.reduce((n, l) => n + l.qty, 0)}개</small>}</span>
          <strong>{won(total)}</strong>
        </div>
        <Button type="primary" size="large" className="pos-pay" icon={<ShoppingCartOutlined />} disabled={!cart.length} loading={sending} onClick={pay}>
          {cart.length ? `${won(total)} 결제` : "결제"}
        </Button>
      </div>
    </div>
  );
};

// ----- 오늘 영수증 -----
export const ReceiptsTab = ({ today }) => (
  <Table
    size="middle"
    rowKey="receiptNo"
    pagination={false}
    scroll={{ y: 420 }}
    dataSource={today.recentReceipts}
    locale={{ emptyText: <Empty description="오늘 결제한 영수증이 없습니다." /> }}
    columns={[
      { title: "시각", dataIndex: "saleTime", width: 80, render: (v) => String(v).slice(0, 5) },
      { title: "영수증", dataIndex: "receiptNo", width: 190 },
      { title: "메뉴", key: "lines", render: (_, r) => r.lines.map((l) => `${l.menuName}×${l.quantity}`).join(", ") },
      { title: "결제 · 채널", key: "pay", width: 120, render: (_, r) => `${r.paymentMethod} · ${r.channel}` },
      { title: "금액", dataIndex: "totalAmount", align: "right", width: 100, render: (v) => <span className="crud-num">{won(v)}</span> },
      { title: "본사", dataIndex: "sendStatus", width: 90, render: (v) => <SendBadge status={v} /> },
    ]}
    footer={() => (today.sales.receipts > 30 ? `최근 30건만 보입니다 (오늘 ${today.sales.receipts}건)` : null)}
  />
);

// ----- 일 마감 -----
export const ClosingTab = ({ api, today, onSaved }) => {
  const s = today.sales;
  const [cash, setCash] = useState(null);
  const [note, setNote] = useState("");
  const [sending, setSending] = useState(false);
  const diff = cash === null || cash === undefined ? null : cash - s.expectedCash;
  const send = async () => {
    setSending(true);
    try {
      await api.post("/closings", { cashCounted: cash, note: note.trim() || null });
      message.success("오늘 마감을 저장했습니다. 본사로 보냅니다.");
      onSaved();
    } catch (err) {
      message.error(errText(err, "마감하지 못했습니다."));
    } finally {
      setSending(false);
    }
  };
  const c = today.closing;
  if (c)
    return (
      <div className="closing-done">
        <span>
          <CheckCircleFilled /> {dayjs(c.closedAt).format("HH:mm")} 마감 · 매출 {won(c.totalAmount)} · 시재 {c.cashDiff ? `${c.cashDiff > 0 ? "+" : ""}${c.cashDiff.toLocaleString()}원` : "일치"}
          {c.note ? ` · ${c.note}` : ""}
        </span>
        <SendBadge status={c.sendStatus} />
      </div>
    );
  return (
    <div className="closing-sheet">
      <div className="closing-figures">
        <div>
          <span>오늘 매출</span>
          <strong>{won(s.amount)}</strong>
        </div>
        <div>
          <span>카드 · 간편결제</span>
          <strong>{won(s.cardAmount + s.easyPayAmount)}</strong>
        </div>
        <div>
          <span>현금 매출</span>
          <strong>{won(s.cashAmount)}</strong>
        </div>
        <div>
          <span>있어야 할 현금</span>
          <strong>{won(s.expectedCash)}</strong>
          <small className="pos-hint">준비금 {won(today.cashFloat)} + 현금 매출</small>
        </div>
      </div>
      <div className="cash-box">
        <div>
          <label>금고에서 센 현금</label>
          <InputNumber size="large" min={0} step={1000} value={cash} onChange={setCash} style={{ width: "100%" }} addonAfter="원" />
        </div>
        <div>
          <label>차이</label>
          <div className={`cash-value cash-diff ${diff === 0 ? "is-ok" : diff ? "is-bad" : ""}`}>{diff === null ? "-" : diff === 0 ? "일치" : `${diff > 0 ? "+" : ""}${diff.toLocaleString()}원`}</div>
        </div>
        <div>
          <Button size="small" onClick={() => setCash(s.expectedCash)}>
            예상 금액 넣기
          </Button>
        </div>
      </div>
      <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder={diff ? "시재 차이 사유 (필수)" : "메모 (선택)"} status={diff && !note.trim() ? "warning" : undefined} />
      <Popconfirm title="오늘 마감할까요?" description="마감하면 오늘은 더 판매할 수 없습니다." okText="마감" cancelText="닫기" onConfirm={send} disabled={cash === null || cash === undefined || (!!diff && !note.trim())}>
        <Button type="primary" size="large" disabled={cash === null || cash === undefined || (!!diff && !note.trim())} loading={sending}>
          마감하고 본사로 보내기
        </Button>
      </Popconfirm>
    </div>
  );
};

// ----- 폐기 -----
export const WasteTab = ({ api, master, onSaved }) => {
  const [code, setCode] = useState(null);
  const [qty, setQty] = useState(null);
  const [reason, setReason] = useState("유통기한 경과");
  const [note, setNote] = useState("");
  const [sending, setSending] = useState(false);
  const m = master.materials.find((s) => s.materialCode === code);
  const send = async () => {
    setSending(true);
    try {
      await api.post("/wastes", { materialCode: code, quantity: qty, reason, note: note || null });
      message.success("폐기를 저장했습니다. 본사로 보냅니다.");
      setCode(null);
      setQty(null);
      setNote("");
      onSaved();
    } catch (err) {
      message.error(errText(err, "폐기를 저장하지 못했습니다."));
    } finally {
      setSending(false);
    }
  };
  return (
    <div className="sa-form">
      <Select showSearch optionFilterProp="label" placeholder="폐기할 식자재" value={code} onChange={setCode} options={master.materials.map((s) => ({ value: s.materialCode, label: s.materialName }))} />
      <InputNumber min={0.1} value={qty} onChange={setQty} addonAfter={m?.unit || "단위"} placeholder="수량" style={{ width: "100%" }} />
      <Select value={reason} onChange={setReason} options={WASTE_REASONS.map((r) => ({ value: r, label: r }))} />
      <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="메모 (선택)" />
      <Button type="primary" danger disabled={!code || !qty} loading={sending} onClick={send}>
        폐기 저장
      </Button>
    </div>
  );
};

// ----- 재고 실사 -----
export const CountTab = ({ api, master, today, onSaved }) => {
  const [counts, setCounts] = useState({});
  const [sending, setSending] = useState(false);
  const rows = stockRows(master, today);
  const entered = rows.filter((s) => counts[s.materialCode] !== undefined && counts[s.materialCode] !== null);
  const send = async () => {
    setSending(true);
    try {
      await api.post("/stocktakes", { note: "매장 실사", lines: entered.map((s) => ({ materialCode: s.materialCode, countedQty: counts[s.materialCode] })) });
      message.success(`실사 ${entered.length}개 품목을 저장했습니다. 본사로 보냅니다.`);
      setCounts({});
      onSaved();
    } catch (err) {
      message.error(errText(err, "실사를 저장하지 못했습니다."));
    } finally {
      setSending(false);
    }
  };
  return (
    <>
      <SnapshotNote today={today} />
      <Table
        size="middle"
        rowKey="materialCode"
        pagination={false}
        dataSource={rows}
        scroll={{ y: 380 }}
        columns={[
          { title: "식자재", dataIndex: "materialName" },
          { title: "본사 기준", dataIndex: "quantity", align: "right", width: 120, render: (v, s) => (v === undefined ? "-" : `${num(v)} ${s.unit}`) },
          {
            title: "센 수량",
            key: "count",
            width: 170,
            render: (_, s) => <InputNumber min={0} value={counts[s.materialCode]} onChange={(v) => setCounts((c) => ({ ...c, [s.materialCode]: v }))} addonAfter={s.unit} style={{ width: "100%" }} />,
          },
        ]}
      />
      <div style={{ marginTop: 12, textAlign: "right" }}>
        <Button type="primary" disabled={!entered.length} loading={sending} onClick={send}>
          실사 {entered.length}개 품목 저장
        </Button>
      </div>
    </>
  );
};

// ----- 발주 요청 -----
const REQ_BADGE = { 승인: "green", 반려: "gray", 요청: "amber" };

export const OrderTab = ({ api, master, today, onSaved }) => {
  const [packs, setPacks] = useState({});
  const [note, setNote] = useState("");
  const [needed, setNeeded] = useState(null);
  const [sending, setSending] = useState(false);
  const rows = [...stockRows(master, today)].sort((a, b) => (a.coverDays ?? 99) - (b.coverDays ?? 99));
  const lines = Object.entries(packs).filter(([, v]) => v > 0);
  const amount = lines.reduce((s, [code, p]) => s + (master.materials.find((m) => m.materialCode === code)?.packPrice || 0) * p, 0);
  const send = async () => {
    setSending(true);
    try {
      await api.post("/order-requests", { neededDate: needed ? needed.format("YYYY-MM-DD") : null, note: note || null, lines: lines.map(([materialCode, p]) => ({ materialCode, packs: p })) });
      message.success("발주 요청을 저장했습니다. 본사가 승인하면 [본사 알림]으로 결과가 옵니다.");
      setPacks({});
      setNote("");
      setNeeded(null);
      onSaved();
    } catch (err) {
      message.error(errText(err, "요청을 저장하지 못했습니다."));
    } finally {
      setSending(false);
    }
  };
  return (
    <div className="ops-grid-2" style={{ gridTemplateColumns: "minmax(0, 1.4fr) minmax(0, 1fr)" }}>
      <div>
        <SnapshotNote today={today} />
        <Table
          size="middle"
          rowKey="materialCode"
          pagination={false}
          scroll={{ y: 360 }}
          dataSource={rows}
          columns={[
            { title: "식자재", dataIndex: "materialName", render: (v, s) => <><div>{v}</div><div className="crud-sub">{s.packLabel}</div></> },
            { title: "남은 양", key: "qty", align: "right", render: (_, s) => (s.quantity === undefined ? "-" : <>{num(s.quantity)} {s.unit}{s.onOrder > 0 && <div className="crud-sub">+{num(s.onOrder)} 입고 예정</div>}</>) },
            { title: "남은 일수", dataIndex: "coverDays", align: "right", width: 80, render: (v) => (v === null || v === undefined ? "-" : <span className={v < 2 ? "pos-warn" : ""}>{v}일</span>) },
            {
              title: "요청",
              key: "packs",
              width: 120,
              render: (_, s) => <InputNumber min={0} value={packs[s.materialCode]} onChange={(v) => setPacks((p) => ({ ...p, [s.materialCode]: v ?? 0 }))} addonAfter="개" style={{ width: "100%" }} />,
            },
          ]}
        />
        <div className="sa-form" style={{ marginTop: 10, maxWidth: "none" }}>
          <div className="pos-row">
            <DatePicker value={needed} onChange={setNeeded} placeholder="필요한 날 (선택)" disabledDate={(d) => d.isBefore(dayjs(), "day")} />
            <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="메모 (예: 주말 대비)" />
          </div>
          <Button type="primary" disabled={!lines.length} loading={sending} onClick={send}>
            {lines.length}개 품목 발주 요청 {amount ? `· 약 ${won(amount)}` : ""}
          </Button>
        </div>
      </div>
      <div>
        <div className="ops-note" style={{ marginBottom: 6 }}>
          요청 현황 (최근 2주)
        </div>
        {today.queuedRequests.length === 0 && today.requests.length === 0 ? (
          <Empty description="최근 요청이 없습니다." />
        ) : (
          <ul className="sa-list">
            {today.queuedRequests.map((q) => (
              <li key={`q${q.id}`}>
                <b>{q.summary}</b> <SendBadge status={q.status} />
                <div className="crud-sub">
                  {dayjs(q.createdAt).format("M/D HH:mm")} · 본사에 아직 안 들어감{q.error ? ` · ${q.error}` : ""}
                </div>
              </li>
            ))}
            {today.requests.map((r) => (
              <li key={r.requestNo}>
                <b>{r.requestNumber}</b> <span className={`crud-badge ${REQ_BADGE[r.status] || "gray"}`}>{r.status}</span>
                <div className="crud-sub">
                  {dayjs(r.requestDate).format("M/D")} · {r.lineCount}개 품목 · {won(r.amount)}
                  {r.rejectReason ? ` · 반려: ${r.rejectReason}` : ""}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

// ----- 입고 확인 -----
export const DeliveryTab = ({ api, today, onSaved }) => {
  const [got, setGot] = useState({});
  const [notes, setNotes] = useState({});
  const [sending, setSending] = useState(null);
  const send = async (po) => {
    setSending(po.poNumber);
    try {
      const lines = po.lines.map((l) => ({ materialCode: l.materialCode, packs: got[`${po.poNo}-${l.lineNo}`] ?? l.orderPacks }));
      await api.post("/deliveries", { poNumber: po.poNumber, note: notes[po.poNo] || null, lines });
      message.success(`${po.poNumber} 입고 확인을 저장했습니다. 본사 재고에 반영됩니다.`);
      onSaved();
    } catch (err) {
      message.error(errText(err, "입고 확인을 저장하지 못했습니다."));
    } finally {
      setSending(null);
    }
  };
  if (!today.openOrders.length) return <Empty description="도착 예정인 발주가 없습니다." />;
  return (
    <div className="po-groups">
      {today.openOrders.map((po) => {
        const short = po.lines.some((l) => (got[`${po.poNo}-${l.lineNo}`] ?? l.orderPacks) < l.orderPacks);
        return (
          <section key={po.poNo} className="po-group">
            <header className="po-group-head">
              <div>
                <h3>{po.clientName}</h3>
                <div className="meta">
                  {po.poNumber} · {dayjs(po.expectedDate).format("M/D")} 도착 예정
                  {po.overdue ? " · 지연" : ""}
                </div>
              </div>
            </header>
            <Table
              size="middle"
              rowKey="lineNo"
              pagination={false}
              dataSource={po.lines}
              columns={[
                { title: "식자재", dataIndex: "materialName", render: (v, l) => <><div>{v}</div><div className="crud-sub">{l.packLabel}</div></> },
                { title: "발주", dataIndex: "orderPacks", align: "right", width: 80, render: (v) => `${v}개` },
                {
                  title: "받은 수량",
                  key: "got",
                  width: 140,
                  render: (_, l) => (
                    <InputNumber min={0} value={got[`${po.poNo}-${l.lineNo}`] ?? l.orderPacks} onChange={(v) => setGot((g) => ({ ...g, [`${po.poNo}-${l.lineNo}`]: v ?? 0 }))} addonAfter="개" style={{ width: "100%" }} />
                  ),
                },
              ]}
            />
            <footer className="po-group-foot">
              <Input value={notes[po.poNo] || ""} onChange={(e) => setNotes((n) => ({ ...n, [po.poNo]: e.target.value }))} placeholder={short ? "덜 받은 사유 (필수)" : "메모 (선택)"} status={short && !notes[po.poNo] ? "warning" : undefined} style={{ maxWidth: 280 }} />
              <Button type="primary" disabled={short && !notes[po.poNo]} loading={sending === po.poNumber} onClick={() => send(po)}>
                입고 확인
              </Button>
            </footer>
          </section>
        );
      })}
    </div>
  );
};

// ----- 전송 기록 -----
export const OutboxTab = ({ api, sync, onSaved }) => {
  const [rows, setRows] = useState([]);
  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/outbox");
      setRows(data);
    } catch {
      /* 다음 새로고침 때 다시 */
    }
  }, [api]);
  useEffect(() => {
    load();
  }, [load, sync?.pending, sync?.failed, sync?.lastContactAt]);
  const act = async (o, what) => {
    try {
      await api.post(`/outbox/${o.id}/${what}`);
      message.success(what === "retry" ? "다시 보냅니다." : "보내지 않기로 했습니다.");
      load();
      onSaved();
    } catch (err) {
      message.error(errText(err, "처리하지 못했습니다."));
    }
  };
  return (
    <>
      <div className="ops-note">
        POS에 저장한 데이터는 쌓인 순서대로 본사에 보냅니다. 본사 연결이 끊겨도 판매는 계속할 수 있고, 연결되면 자동으로 이어서 보냅니다. 본사가 거절한 건은 [실패]로 남습니다.
      </div>
      <Table
        size="middle"
        rowKey="id"
        pagination={{ pageSize: 15, size: "small" }}
        dataSource={rows}
        columns={[
          { title: "저장 시각", dataIndex: "createdAt", width: 140, render: (v) => dayjs(v).format("M/D HH:mm:ss") },
          { title: "종류", dataIndex: "dataType", width: 96 },
          { title: "내용", dataIndex: "summary" },
          {
            title: "상태",
            dataIndex: "status",
            width: 200,
            render: (v, o) => (
              <>
                <SendBadge status={v} />
                {o.attempts > 1 && <span className="crud-sub"> · {o.attempts}번 시도</span>}
                {o.lastError && <div className="crud-sub pos-err">{o.lastError}</div>}
              </>
            ),
          },
          { title: "본사 반영", dataIndex: "sentAt", width: 100, render: (v) => (v ? dayjs(v).format("HH:mm:ss") : "-") },
          {
            title: "",
            key: "act",
            width: 140,
            render: (_, o) =>
              o.status === "실패" ? (
                <span className="pos-row">
                  <Button size="small" onClick={() => act(o, "retry")}>
                    다시 보내기
                  </Button>
                  <Popconfirm title="이 건은 본사에 보내지 않을까요?" okText="제외" cancelText="닫기" onConfirm={() => act(o, "skip")}>
                    <Button size="small" type="text" danger>
                      제외
                    </Button>
                  </Popconfirm>
                </span>
              ) : null,
          },
        ]}
      />
    </>
  );
};
