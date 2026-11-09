// 월간 화면 공통: 월 고르기, 금액 표시
import { DatePicker } from "antd";
import dayjs from "dayjs";
import "./monthly.css";

export const thisMonth = () => dayjs().format("YYYY-MM");

export const MonthPicker = ({ value, onChange }) => (
  <DatePicker
    picker="month"
    value={dayjs(`${value}-01`)}
    allowClear={false}
    disabledDate={(d) => d.isAfter(dayjs(), "month")}
    onChange={(d) => d && onChange(d.format("YYYY-MM"))}
    format="YYYY년 M월"
  />
);

// 만 원 단위 (예: 5,707만)
export const man = (v) => `${Math.round((Number(v) || 0) / 10000).toLocaleString()}만`;

export const pct = (v) => `${Number(v || 0).toFixed(1)}%`;

// 전월 대비 증감 (%). 비용처럼 둘 다 음수로 표시하는 값은 크기(절댓값)로 비교한다.
export const Delta = ({ now, before }) => {
  if (!before) return <span className="crud-sub">-</span>;
  let a = now;
  let b = before;
  if (a <= 0 && b < 0) {
    a = -a;
    b = -b;
  }
  const r = ((a - b) / Math.abs(b)) * 100;
  return (
    <span className={r >= 0 ? "pnl-up" : "pnl-down"}>
      {r >= 0 ? "▲" : "▼"} {Math.abs(r).toFixed(1)}%
    </span>
  );
};

export const periodLabel = (d) =>
  d ? `${dayjs(d.from).format("M/D")} ~ ${dayjs(d.to).format("M/D")}${d.inProgress ? " · 진행 중 (어제 마감분까지)" : " · 한 달 전체"}` : "";
