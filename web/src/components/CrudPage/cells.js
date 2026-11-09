// 기준 정보 표의 칸에서 같이 쓰는 작은 표시 · 정렬 함수

// 점 + 글자 상태 (tone: ok · warn · neg · off)
export const Dot = ({ tone, children }) => <span className={`crud-dot ${tone ? `is-${tone}` : ""}`}>{children}</span>;

// 막대 + 글자 (ratio: 0~1)
export const Meter = ({ ratio = 0, tone, text }) => (
  <span className={`crud-meter ${tone ? `is-${tone}` : ""}`}>
    <span className="crud-meter-track">
      <i style={{ width: `${Math.min(Math.max(ratio, 0), 1) * 100}%` }} />
    </span>
    <span>{text}</span>
  </span>
);

export const won = (v) => `${Math.round(Number(v) || 0).toLocaleString()}원`;

// 열 정렬
export const byText = (get) => (a, b) => String(get(a) ?? "").localeCompare(String(get(b) ?? ""));
export const byNum = (get) => (a, b) => (Number(get(a)) || 0) - (Number(get(b)) || 0);
// 정해진 순서(직영점 → 가맹점 …)대로, 목록에 없는 값은 뒤로
export const byOrder = (list, get) => (a, b) => {
  const i = (r) => (list.indexOf(get(r)) < 0 ? list.length : list.indexOf(get(r)));
  return i(a) - i(b);
};
