// 매장 POS 화면 공용
export const won = (v) => `${Math.round(Number(v) || 0).toLocaleString()}원`;
export const num = (v) => (v === null || v === undefined ? "-" : Number(v).toLocaleString(undefined, { maximumFractionDigits: 1 }));
export const errText = (err, fallback) => err?.response?.data?.message || fallback;

// 본사 전송 상태 표시
export const SEND = {
  대기: { cls: "amber", label: "전송 대기" },
  완료: { cls: "green", label: "본사 반영" },
  실패: { cls: "red", label: "전송 실패" },
  제외: { cls: "gray", label: "제외" },
};

export const SendBadge = ({ status }) => {
  const s = SEND[status] || { cls: "gray", label: status || "-" };
  return <span className={`crud-badge ${s.cls}`}>{s.label}</span>;
};

// 본사 메시지 종류
export const KIND = {
  공지: "blue",
  "메뉴·가격": "purple",
  "발주 결과": "green",
  지시: "amber",
};
