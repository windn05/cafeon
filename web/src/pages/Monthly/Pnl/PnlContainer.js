import { useState } from "react";
import { Alert, Button, ConfigProvider, Input, Popconfirm, Segmented, message } from "antd";
import { LockOutlined, UndoOutlined } from "@ant-design/icons";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import dayjs from "dayjs";
import { DashHeader, Loading, Panel, StatCard } from "../../../components/Dashboard/Dashboard";
import { errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { ACCENT, StoreSelect, useFetch, useStoreChoice, won } from "../../Ops/opsShared";
import { Delta, MonthPicker, man, pct, periodLabel, thisMonth } from "../monthlyShared";

// 손익계산서 한 줄
const Line = ({ label, cur, prev, net, kind, sub }) => (
  <tr className={kind ? `is-${kind}` : sub ? "is-sub" : ""}>
    <td>{label}</td>
    {/* 비용 줄은 원래 음수라서, 합계·결과 줄이 적자일 때만 빨갛게 */}
    <td className={kind && cur < 0 ? "pnl-neg" : ""}>{won(cur)}</td>
    <td className="pct">{net ? pct((cur / net) * 100) : "-"}</td>
    <td>{prev === undefined ? "" : won(prev)}</td>
    <td>{prev === undefined ? "" : <Delta now={cur} before={prev} />}</td>
  </tr>
);

// 매장 1곳 손익계산서
const StoreDetail = ({ storeNo, month }) => {
  const { data, loading, reload } = useFetch(`${ERP_API}/monthly/pnl`, { storeNo, month }, !!storeNo);
  const daily = useFetch(`${ERP_API}/monthly/daily`, { storeNo, month }, !!storeNo);
  const [note, setNote] = useState("");

  if (!data || loading) return <Loading />;
  const p = data.previous;
  const n = data.netSales;
  const monthEnded = !data.inProgress;

  const closeMonth = async () => {
    try {
      await ERPapi.post(`${ERP_API}/monthly/closings`, { storeNo, month, note: note.trim() || null });
      message.success(`${data.storeName} ${month} 월 마감을 확정했습니다. 이 달의 판매·재고 기록은 이제 바꿀 수 없습니다.`);
      reload();
    } catch (err) {
      message.error(errorText(err, "월 마감하지 못했습니다."));
    }
  };
  const cancelMonth = async () => {
    try {
      await ERPapi.delete(`${ERP_API}/monthly/closings/${data.closing.monthlyNo}`);
      message.success("월 마감을 취소했습니다.");
      reload();
    } catch (err) {
      message.error(errorText(err, "월 마감을 취소하지 못했습니다."));
    }
  };

  const chart = (daily.data || []).map((d) => ({ day: dayjs(d.date).format("D"), 매출: d.amount, 원가: d.cost }));

  return (
    <div className="dash" style={{ padding: 0 }}>
      {/* 월 마감 상태 */}
      {data.closing ? (
        <div className="month-close is-done">
          <span>
            <LockOutlined /> {month} 월 마감 완료 · {data.closing.closedBy} · {dayjs(data.closing.closedAt).format("M/D HH:mm")}
            {data.closing.note ? ` · ${data.closing.note}` : ""}
          </span>
          <Popconfirm title="월 마감을 취소할까요?" description="이 달의 일 마감 취소·실사·입고 수정이 다시 가능해집니다." okText="월 마감 취소" okButtonProps={{ danger: true }} cancelText="닫기" onConfirm={cancelMonth}>
            <Button size="small" icon={<UndoOutlined />}>
              월 마감 취소
            </Button>
          </Popconfirm>
        </div>
      ) : (
        <div className="month-close is-open">
          <div>
            <b>{monthEnded ? `${month} 월 마감 전` : `${month}은(는) 아직 진행 중입니다`}</b>
            <ul>
              {data.unclosedDays > 0 && <li>일 마감하지 않은 날이 {data.unclosedDays}일 있습니다. 모두 마감해야 월 마감할 수 있습니다.</li>}
              {!data.stocktakeCount && <li>이 달에 재고 실사 기록이 없습니다. 실제 원가가 정확하지 않을 수 있습니다.</li>}
              {monthEnded && data.unclosedDays === 0 && <li>월 마감하면 숫자가 확정되고, 이 달의 판매·폐기·실사·입고를 고칠 수 없게 됩니다.</li>}
              {!monthEnded && <li>달이 끝난 뒤에 월 마감할 수 있습니다.</li>}
            </ul>
          </div>
          {monthEnded && (
            <div className="ops-toolbar">
              <Input size="small" placeholder="메모 (선택)" value={note} onChange={(e) => setNote(e.target.value)} style={{ width: 200 }} maxLength={300} />
              <Popconfirm title={`${data.storeName} ${month} 월 마감을 확정할까요?`} okText="월 마감" cancelText="닫기" onConfirm={closeMonth} disabled={data.unclosedDays > 0}>
                <Button type="primary" icon={<LockOutlined />} disabled={data.unclosedDays > 0}>
                  월 마감 확정
                </Button>
              </Popconfirm>
            </div>
          )}
        </div>
      )}

      <div className="dash-row">
        <Panel title={`${data.franchise ? "가맹점 실적" : "손익계산서"} · ${data.storeName}`} extra={periodLabel(data)} bodyClassName="is-flush">
          <div className="table-scroll">
            <table className="pnl-table">
              <thead>
                <tr>
                  <th>항목</th>
                  <th>{month}</th>
                  <th>순매출 대비</th>
                  <th>전월</th>
                  <th>전월 대비</th>
                </tr>
              </thead>
              <tbody>
                <Line label="매출 (부가세 포함)" cur={data.salesAmount} prev={p.salesAmount} net={n} />
                <Line label="부가세 (매출 ÷ 11)" cur={-data.vat} prev={-p.vat} net={n} sub />
                <Line label="순매출" cur={n} prev={p.netSales} net={n} kind="total" />
                <Line label="식자재 원가 (실제)" cur={-data.foodCost} prev={-p.foodCost} net={n} />
                <Line label="판매소진 (레시피 기준)" cur={-data.consumeCost} prev={-p.consumeCost} net={n} sub />
                <Line label="폐기" cur={-data.wasteAmount} prev={-p.wasteAmount} net={n} sub />
                <Line label="재고 실사 로스" cur={-data.lossAmount} prev={-p.lossAmount} net={n} sub />
                <Line label="매출총이익" cur={data.grossProfit} prev={p.grossProfit} net={n} kind="total" />
                {!data.franchise && (<>
                <Line label="인건비" cur={-data.laborCost} prev={-p.laborCost} net={n} />
                <Line label={`정규직·계약직 급여 (${data.labor.fullTimeCount}명)`} cur={-data.labor.fullTime} prev={-p.labor.fullTime} net={n} sub />
                <Line label={`파트타임 시급 (${data.labor.partTimeHours}시간)`} cur={-data.labor.partTime} prev={-p.labor.partTime} net={n} sub />
                <Line label="주휴수당" cur={-data.labor.holidayPay} prev={-p.labor.holidayPay} net={n} sub />
                <Line label="임차료" cur={-data.rent} prev={-p.rent} net={n} />
                <Line label="기타 고정비 (관리비·공과금 등)" cur={-data.fixedCost} prev={-p.fixedCost} net={n} />
                <Line label="결제 수수료 (카드·간편 1.5%)" cur={-data.cardFee} prev={-p.cardFee} net={n} />
                <Line label="배달앱 수수료 (배달 매출 12%)" cur={-data.deliveryFee} prev={-p.deliveryFee} net={n} />
                <Line label="영업이익" cur={data.operatingProfit} prev={p.operatingProfit} net={n} kind="result" />
                </>)}
              </tbody>
            </table>
          </div>
        </Panel>

        <div className="home-side" style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <Panel title="핵심 지표">
            <div className="kpi-list">
              <div>
                <span>식자재 원가율</span>
                <strong>{pct(data.foodCostRate)}</strong>
                <small>레시피 기준 {pct(data.theoreticalRate)} · 차이 {pct(data.foodCostRate - data.theoreticalRate)}</small>
              </div>
              {data.franchise ? (
                <>
                  <div>
                    <span>로스율 (폐기 + 실사)</span>
                    <strong>{pct(data.lossRate)}</strong>
                    <small>순매출 대비</small>
                  </div>
                  <div>
                    <span>입고(매입) 금액</span>
                    <strong>{man(data.purchaseAmount)}원</strong>
                    <small>본사 승인 발주 기준</small>
                  </div>
                </>
              ) : (
              <>
              <div>
                <span>인건비율</span>
                <strong>{pct(data.laborRate)}</strong>
                <small>전월 {pct(p.laborRate)}</small>
              </div>
              <div>
                <span>FL 비율 (원가+인건비)</span>
                <strong className={data.flRate > 60 ? "pnl-neg" : ""}>{pct(data.flRate)}</strong>
                <small>60% 이하면 양호</small>
              </div>
              <div>
                <span>영업이익률</span>
                <strong className={data.operatingProfit < 0 ? "pnl-neg" : ""}>{pct(data.operatingMargin)}</strong>
                <small>전월 {pct(p.operatingMargin)}</small>
              </div>
              </>
              )}
              <div>
                <span>객단가 · 영수증</span>
                <strong>{won(data.avgTicket)}</strong>
                <small>{data.receipts.toLocaleString()}건</small>
              </div>
              <div>
                <span>{data.inProgress ? "월말 예상 매출" : "일평균 매출"}</span>
                <strong>{data.inProgress ? man(data.forecastSales) : won(data.dailyAvgSales)}</strong>
                <small>{data.inProgress ? `지금까지 일평균 ${won(data.dailyAvgSales)}` : data.franchise ? `영업일 ${data.days}일` : `입고(매입) ${man(data.purchaseAmount)}원`}</small>
              </div>
            </div>
          </Panel>
          <Panel title="일별 매출">
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={chart} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke="#ededed" />
                <XAxis dataKey="day" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} interval={2} />
                <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} width={36} tickFormatter={(v) => `${Math.round(v / 10000)}만`} />
                <Tooltip formatter={(v) => won(v)} labelFormatter={(l) => `${month.slice(5)}월 ${l}일`} />
                <Bar dataKey="매출" fill={ACCENT} radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Panel>
        </div>
      </div>
      <p className="ops-note">
        식자재 원가(실제) = 마감 때 레시피로 빠진 양 + 폐기 + 실사에서 모자란 양. 레시피 기준과의 차이가 클수록 로스·과다 사용이 많다는 뜻입니다.{" "}
        {data.franchise ? "가맹점의 인건비 · 임차료 · 고정비는 점주의 사업 정보라 본사 손익에 넣지 않습니다." : "4대보험 사업주 부담금은 포함하지 않았습니다."}
      </p>
    </div>
  );
};

// 전 매장 비교
const CompareView = ({ month, onPick }) => {
  const { data, loading } = useFetch(`${ERP_API}/monthly/compare`, { month });
  if (!data || loading) return <Loading />;
  const t = data.total;
  const dt = data.directTotal;
  const chart = data.stores.map((s) => ({ name: s.storeName.replace("점", ""), 매출: s.salesAmount, 영업이익: s.franchise ? null : s.operatingProfit }));

  return (
    <>
      <div className="stat-grid">
        <StatCard label="전 매장 매출" value={man(t.salesAmount)} unit="원" hint={<>전월 대비 <Delta now={t.salesAmount} before={t.prevSalesAmount} /></>} />
        <StatCard label="직영점 영업이익" value={man(dt.operatingProfit)} unit="원" tone={dt.operatingProfit < 0 ? "warn" : undefined} hint={`직영 영업이익률 ${pct(dt.operatingMargin)} · 가맹점은 제외`} />
        <StatCard label="식자재 원가율" value={t.foodCostRate} unit="%" hint={`레시피 기준 ${pct(t.theoreticalRate)}`} />
        <StatCard label="직영점 FL 비율" value={dt.flRate} unit="%" tone={dt.flRate > 60 ? "warn" : undefined} hint={`인건비율 ${pct(dt.laborRate)}`} />
      </div>

      <div className="dash-row">
        <Panel title="매장별 손익" extra={periodLabel(data)} bodyClassName="is-flush">
          <div className="table-scroll">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>매장</th>
                  <th className="cell-right">매출</th>
                  <th className="cell-right">전월 대비</th>
                  <th className="cell-right">원가율</th>
                  <th className="cell-right">인건비율</th>
                  <th className="cell-right">FL</th>
                  <th className="cell-right">영업이익</th>
                  <th className="cell-right">이익률</th>
                  <th>월 마감</th>
                </tr>
              </thead>
              <tbody>
                {data.stores.map((s) => (
                  <tr key={s.storeNo} className="cmp-row" onClick={() => onPick(s.storeNo)} title="눌러서 손익계산서 보기">
                    <td>
                      <div className="cell-main">{s.storeName}</div>
                      <div className="cell-sub">
                        {s.storeType}
                        {s.storeStatus !== "영업중" ? ` · ${s.storeStatus}` : ""}
                      </div>
                    </td>
                    <td className="cell-num cell-right">{man(s.salesAmount)}</td>
                    <td className="cell-num cell-right">
                      <Delta now={s.salesAmount} before={s.prevSalesAmount} />
                    </td>
                    <td className="cell-num cell-right">{pct(s.foodCostRate)}</td>
                    {s.franchise ? (
                      <td colSpan={4} className="cell-right cell-sub">가맹점 · 비용은 점주 관리 (로스율 {pct(s.lossRate)})</td>
                    ) : (
                      <>
                        <td className="cell-num cell-right">{pct(s.laborRate)}</td>
                        <td className={`cell-num cell-right ${s.flRate > 60 ? "pnl-neg" : ""}`}>{pct(s.flRate)}</td>
                        <td className={`cell-num cell-right ${s.operatingProfit < 0 ? "pnl-neg" : ""}`}>{man(s.operatingProfit)}</td>
                        <td className={`cell-num cell-right ${s.operatingProfit < 0 ? "pnl-neg" : ""}`}>{pct(s.operatingMargin)}</td>
                      </>
                    )}
                    <td>
                      {s.closing ? (
                        <span className="status-badge badge-green">마감</span>
                      ) : s.inProgress ? (
                        <span className="status-badge badge-gray">진행 중</span>
                      ) : s.unclosedDays ? (
                        <span className="status-badge badge-amber">일 마감 {s.unclosedDays}일 남음</span>
                      ) : (
                        <span className="status-badge badge-blue">마감 가능</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
        <Panel title="매출 · 영업이익 (영업이익은 직영점만)">
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={chart} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke="#ededed" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={false} width={44} tickFormatter={(v) => `${Math.round(v / 10000).toLocaleString()}만`} />
              <Tooltip formatter={(v) => won(v)} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar dataKey="매출" fill="#d6d3d1" radius={[3, 3, 0, 0]} />
              <Bar dataKey="영업이익" fill={ACCENT} radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Panel>
      </div>
      {data.inProgress && <Alert type="info" showIcon message="진행 중인 달은 어제까지 마감한 기록으로 계산하고, 월급·임차료·고정비는 지난 날 수만큼만 반영합니다." />}
    </>
  );
};

const PnlContainer = () => {
  const { stores, storeNo, setStoreNo } = useStoreChoice({ shopsOnly: true });
  const [month, setMonth] = useState(dayjs().subtract(1, "month").format("YYYY-MM")); // 기본: 지난달
  const [view, setView] = useState("compare");

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className="dash dash-erp">
        <DashHeader
          title="월간 손익"
          subtitle="매장별 매출 · 원가 · 인건비 · 고정비로 영업이익을 계산하고, 달이 끝나면 월 마감으로 숫자를 확정합니다."
          extra={
            <div className="ops-toolbar">
              <MonthPicker value={month} onChange={setMonth} />
              <Segmented value={view} onChange={setView} options={[{ label: "매장 비교", value: "compare" }, { label: "매장별 손익 · 실적", value: "store" }]} />
              {view === "store" && <StoreSelect stores={stores} value={storeNo} onChange={setStoreNo} />}
              {month !== thisMonth() && (
                <Button onClick={() => setMonth(thisMonth())} size="middle">
                  이번 달
                </Button>
              )}
            </div>
          }
        />
        {view === "compare" ? (
          <CompareView
            month={month}
            onPick={(no) => {
              setStoreNo(no);
              setView("store");
            }}
          />
        ) : (
          <StoreDetail storeNo={storeNo} month={month} />
        )}
      </div>
    </ConfigProvider>
  );
};

export default PnlContainer;
