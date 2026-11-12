import { useEffect, useState } from "react";
import { Alert, Button, ConfigProvider } from "antd";
import { CheckCircleFilled, ClockCircleOutlined, SolutionOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { DashHeader, EmptyState, Loading, Panel } from "../../../components/Dashboard/Dashboard";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT, dayLabel, num, todayStr, useFetch, useStoreChoice, won } from "../opsShared";
import { ComposeModal } from "../../Messages/MessagesContainer";

const DAYS = 7;

const Row = ({ label, value }) => (
  <li style={{ gridTemplateColumns: "1fr auto" }}>
    <span>{label}</span>
    <span className="val">{value}</span>
  </li>
);

// 전 매장 × 최근 7일 마감 수신 현황
const Matrix = ({ stores, selected, onPick }) => {
  const [rows, setRows] = useState(null);
  const from = dayjs().subtract(DAYS - 1, "day").format("YYYY-MM-DD");
  const to = todayStr();
  const days = Array.from({ length: DAYS }, (_, i) => dayjs(to).subtract(DAYS - 1 - i, "day").format("YYYY-MM-DD"));

  useEffect(() => {
    if (!stores.length) return;
    Promise.all(stores.map((s) => ERPapi.get(`${ERP_API}/closings/status`, { params: { storeNo: s.storeNo, from, to } }).then((r) => [s, r.data])))
      .then((list) => setRows(list.map(([s, data]) => ({ store: s, byDate: Object.fromEntries(data.map((d) => [d.date, d])) }))))
      .catch(() => setRows([]));
  }, [stores, from, to]);

  if (!rows) return <Loading />;
  return (
    <div className="table-scroll">
      <table className="dash-table closing-matrix">
        <thead>
          <tr>
            <th>매장</th>
            {days.map((d) => (
              <th key={d} className="cell-right">
                {dayjs(d).format("M/D (dd)")}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map(({ store, byDate }) => (
            <tr key={store.storeNo}>
              <td>
                <div className="cell-main">{store.storeName}</div>
                <div className="cell-sub">{store.storeType}</div>
              </td>
              {days.map((d) => {
                const x = byDate[d];
                const active = selected.storeNo === store.storeNo && selected.date === d;
                let badge;
                if (!x || !x.receipts) badge = <span className="matrix-none" title="판매 없음">–</span>;
                else if (x.closed && x.cashDiff) badge = <span className="status-badge badge-amber">시재 {x.cashDiff > 0 ? "+" : ""}{x.cashDiff.toLocaleString()}</span>;
                else if (x.closed && x.reopenCount) badge = <span className="status-badge badge-amber">재마감</span>;
                else if (x.closed) badge = <span className="matrix-ok">마감</span>;
                else if (d === to) badge = <span className="status-badge badge-blue">영업 중</span>;
                else badge = <span className="status-badge badge-red">미수신</span>;
                return (
                  <td key={d} className={`cell-right matrix-cell ${active ? "is-active" : ""}`} onClick={() => onPick(store.storeNo, d)}>
                    <div className="cell-num">{x?.receipts ? `${Math.round(x.amount / 10000).toLocaleString()}만` : ""}</div>
                    {badge}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

// 시재 차이 · 마감 미수신을 매장에 확인 요청 (매장 POS로 지시를 보내고 회신을 받는다)
const AskStore = ({ store, date, closing }) => {
  const ref = `closing:${store.storeNo}:${date}`;
  const [tick, setTick] = useState(0);
  const [open, setOpen] = useState(false);
  const sent = useFetch(`${ERP_API}/store-messages/by-ref`, { ref, tick });
  const day = dayjs(date).format("M/D");
  const diff = closing ? `${closing.cashDiff > 0 ? "+" : ""}${closing.cashDiff.toLocaleString()}원` : null;
  const initial = {
    modalTitle: `${store.storeName}에 확인 요청`,
    kind: "지시",
    target: "some",
    storeNos: [store.storeNo],
    title: closing ? `${day} 현금 시재 ${diff} 확인 요청` : `${day} 일 마감 미전송 확인 요청`,
    body: closing
      ? `${day} 마감 시재가 ${diff} 차이 납니다 (있어야 할 현금 ${won(closing.expectedCash ?? 0)} / 센 현금 ${won(closing.cashCounted)}). 원인과 조치를 회신해 주세요.`
      : `${day} 일 마감이 본사에 들어오지 않았습니다. POS 전송 기록을 확인하고 마감을 보내 주세요.`,
    dueDate: dayjs().add(1, "day"),
    refKey: ref,
  };
  const msg = (sent.data || [])[0];
  const t = msg?.targets?.[0];
  return (
    <div className="closing-ask">
      {msg ? (
        <span>
          <SolutionOutlined /> {dayjs(msg.sentAt).format("M/D HH:mm")} 확인 요청 보냄 ·{" "}
          {t?.repliedAt ? <b>매장 회신: {t.reply}</b> : t?.readAt ? "매장이 확인함, 회신 대기" : t?.deliveredAt ? "POS 수신, 확인 전" : "POS 전송 대기"}
        </span>
      ) : (
        <span className="ops-note">{closing ? "시재 차이가 있습니다." : "마감이 들어오지 않았습니다."} 매장 POS로 확인 요청을 보내고 회신을 받을 수 있습니다.</span>
      )}
      {!msg && (
        <Button size="small" icon={<SolutionOutlined />} onClick={() => setOpen(true)}>
          매장에 확인 요청
        </Button>
      )}
      <ComposeModal
        open={open}
        initial={initial}
        stores={[store]}
        onClose={() => setOpen(false)}
        onSent={() => {
          setOpen(false);
          setTick((x) => x + 1);
        }}
      />
    </div>
  );
};

// 선택한 매장 · 날짜의 마감 내용 (조회 전용)
const ClosingDetail = ({ store, storeNo, date }) => {
  const { data, loading } = useFetch(`${ERP_API}/closings/preview`, { storeNo, date }, !!storeNo);
  if (!data || loading) return <Loading />;
  const c = data.closing;
  const isToday = date === todayStr();
  const needsAsk = store && ((c && c.cashDiff) || (!c && !isToday && data.receipts > 0));

  return (
    <div className="closing-sheet">
      {c ? (
        <div className="closing-done">
          <span>
            <CheckCircleFilled /> 매장 마감 수신 · {c.closedBy} · {dayjs(c.closedAt).format("M/D HH:mm")}
            {c.note ? ` · 메모: ${c.note}` : ""}
          </span>
          {c.reopenCount > 0 && (
            <span className="status-badge badge-amber" title="매장에서 마감을 취소하고 다시 마감했습니다">
              마감 취소 후 재마감 {c.reopenCount}회{c.reopenNote ? ` · ${c.reopenNote}` : ""}
            </span>
          )}
        </div>
      ) : (
        <Alert
          type={isToday ? "info" : "warning"}
          showIcon
          icon={<ClockCircleOutlined />}
          message={isToday ? "영업 중입니다. 매장에서 마감을 보내면 확정됩니다." : "매장 마감 데이터가 아직 들어오지 않았습니다."}
          description={isToday ? "아래 숫자는 지금까지 받은 판매 기준입니다." : "매장 POS의 마감 전송 상태를 확인하도록 매장에 연락하세요."}
        />
      )}

      {needsAsk && <AskStore store={store} date={date} closing={c ? { ...c, expectedCash: data.expectedCash } : null} />}

      <div className="closing-figures">
        <div>
          <span>매출</span>
          <strong>{won(data.amount)}</strong>
        </div>
        <div>
          <span>영수증</span>
          <strong>{num(data.receipts)}건</strong>
        </div>
        <div>
          <span>객단가</span>
          <strong>{won(data.avgTicket)}</strong>
        </div>
        <div>
          <span>원가율 · 폐기</span>
          <strong>{data.costRate}%</strong> <span style={{ display: "inline" }}>· 폐기 {won(data.wasteAmount)}</span>
        </div>
      </div>

      <div className="ops-grid-2">
        <div>
          <div className="ops-note" style={{ marginBottom: 8 }}>
            결제수단별
          </div>
          <ul className="ops-bars">
            <Row label="카드" value={won(data.cardAmount)} />
            <Row label="간편결제" value={won(data.easyPayAmount)} />
            <Row label="현금" value={won(data.cashAmount)} />
          </ul>
        </div>
        <div>
          <div className="ops-note" style={{ marginBottom: 8 }}>
            판매 채널별
          </div>
          <ul className="ops-bars">
            <Row label="매장" value={won(data.dineInAmount)} />
            <Row label="포장" value={won(data.takeoutAmount)} />
            <Row label="배달" value={won(data.deliveryAmount)} />
          </ul>
        </div>
      </div>

      <div className="cash-box">
        <div>
          <label>있어야 할 현금</label>
          <div className="cash-value">{won(data.expectedCash)}</div>
          <div className="ops-note">
            준비금 {won(data.cashFloat)} + 현금 매출 {won(data.cashAmount)}
          </div>
        </div>
        <div>
          <label>매장이 센 현금</label>
          <div className="cash-value">{c ? won(c.cashCounted) : "-"}</div>
        </div>
        <div>
          <label>차이</label>
          {c ? (
            <div className={`cash-value cash-diff ${c.cashDiff === 0 ? "is-ok" : "is-bad"}`}>
              {c.cashDiff === 0 ? "일치" : `${c.cashDiff > 0 ? "+" : ""}${c.cashDiff.toLocaleString()}원`}
            </div>
          ) : (
            <div className="cash-value">-</div>
          )}
        </div>
      </div>
    </div>
  );
};

const ClosingContainer = () => {
  const { stores, storeNo, setStoreNo, loaded } = useStoreChoice({ shopsOnly: true });
  const [date, setDate] = useState(dayjs().subtract(1, "day").format("YYYY-MM-DD"));
  const store = stores.find((s) => s.storeNo === storeNo);

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          subtitle="매장 POS에서 보낸 일 마감(매출 확정 · 현금 시재)을 모니터링합니다. 칸을 누르면 그날 마감 내용을 볼 수 있습니다."
        />
        <Panel title={`최근 ${DAYS}일 · 전 매장`} extra="미수신: 영업일이 지났는데 마감이 안 들어옴" bodyClassName="is-flush">
          <Matrix
            stores={stores}
            selected={{ storeNo, date }}
            onPick={(no, d) => {
              setStoreNo(no);
              setDate(d);
            }}
          />
        </Panel>
        <Panel title={store ? `${store.storeName} · ${dayLabel(date)}` : "마감 내용"}>
          {storeNo ? <ClosingDetail store={store} storeNo={storeNo} date={date} /> : loaded ? <EmptyState text="등록된 판매 매장이 없습니다. [기준 정보 · 매장]에서 매장을 등록하세요." /> : <Loading />}
        </Panel>
      </div>
    </ConfigProvider>
  );
};

export default ClosingContainer;
