// 매장 POS: 본사 알림 (공지 · 메뉴·가격 변경 · 발주 결과 · 지시)
import { useEffect, useState } from "react";
import { Button, Empty, Input, Segmented, message } from "antd";
import { CheckCircleFilled } from "@ant-design/icons";
import dayjs from "dayjs";
import { KIND, errText } from "./posShared";

const FILTERS = ["전체", "안 읽음", "공지", "메뉴·가격", "발주 결과", "지시"];

const Payload = ({ m }) => {
  const p = m.payload;
  if (!p) return null;
  if (m.kind === "발주 결과") {
    return (
      <div className="pos-msg-extra">
        {p.lines?.length > 0 && (
          <table className="pos-mini">
            <thead>
              <tr>
                <th>식자재</th>
                <th>요청</th>
                <th>승인</th>
              </tr>
            </thead>
            <tbody>
              {p.lines.map((l) => (
                <tr key={l.materialCode}>
                  <td>{l.materialName}</td>
                  <td>{l.requestedPacks}개</td>
                  <td className={l.approvedPacks !== null && l.approvedPacks < l.requestedPacks ? "pos-warn" : ""}>
                    {l.approvedPacks === null || l.approvedPacks === undefined ? "-" : `${l.approvedPacks}개`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {p.orders?.length > 0 && <div className="pos-hint">도착하면 [입고 확인]에서 받은 수량을 확인해 주세요.</div>}
      </div>
    );
  }
  if (m.kind === "메뉴·가격" && p.changes?.length) {
    return <div className="pos-msg-extra pos-hint">POS 메뉴판에 반영됨 · {p.changes.join(", ")}</div>;
  }
  return null;
};

const MessagesTab = ({ api, messages, onRead, onSaved }) => {
  const [filter, setFilter] = useState("전체");
  const [selectedId, setSelectedId] = useState(null);
  const [reply, setReply] = useState("");
  const [sending, setSending] = useState(false);
  const list = messages.filter((m) => (filter === "전체" ? true : filter === "안 읽음" ? !m.readAt : m.kind === filter));
  const selected = messages.find((m) => m.id === selectedId) || list[0];

  useEffect(() => {
    setReply("");
  }, [selectedId]);

  // 펼쳐 본 메시지는 확인 처리 (지시는 회신해야 끝나므로 확인만 먼저)
  useEffect(() => {
    if (selected && !selected.readAt && !selected.important) onRead(selected);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.id]);

  const sendReply = async () => {
    setSending(true);
    try {
      await api.post(`/messages/${selected.id}/reply`, { reply });
      message.success("회신을 저장했습니다. 본사로 보냅니다.");
      setReply("");
      onSaved();
    } catch (err) {
      message.error(errText(err, "회신하지 못했습니다."));
    } finally {
      setSending(false);
    }
  };

  const overdue = (m) => m.needsReply && !m.repliedAt && m.dueDate && dayjs(m.dueDate).isBefore(dayjs(), "day");

  return (
    <div className="pos-inbox">
      <div className="pos-inbox-list">
        <Segmented value={filter} onChange={setFilter} options={FILTERS} />
        {list.length === 0 ? (
          <Empty description="메시지가 없습니다." style={{ marginTop: 30 }} />
        ) : (
          <ul>
            {list.map((m) => (
              <li key={m.id}>
                <button type="button" className={`${selected?.id === m.id ? "is-active" : ""} ${m.readAt ? "" : "is-unread"}`} onClick={() => setSelectedId(m.id)}>
                  <span className="pos-inbox-top">
                    <span className={`crud-badge ${KIND[m.kind] || "gray"}`}>{m.kind}</span>
                    {m.important && <span className="crud-badge red">중요</span>}
                    {m.needsReply && <span className={`crud-badge ${m.repliedAt ? "green" : overdue(m) ? "red" : "amber"}`}>{m.repliedAt ? "회신함" : overdue(m) ? "기한 지남" : "회신 필요"}</span>}
                    <small>{dayjs(m.sentAt).format("M/D HH:mm")}</small>
                  </span>
                  <b>{m.title}</b>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="pos-inbox-detail">
        {!selected ? (
          <Empty description="메시지를 고르세요." />
        ) : (
          <>
            <div className="pos-inbox-top">
              <span className={`crud-badge ${KIND[selected.kind] || "gray"}`}>{selected.kind}</span>
              {selected.important && <span className="crud-badge red">중요</span>}
              <small>
                본사 {selected.sentBy} · {dayjs(selected.sentAt).format("M월 D일 HH:mm")}
              </small>
            </div>
            <h3>{selected.title}</h3>
            <p className="pos-msg-body">{selected.body}</p>
            <Payload m={selected} />
            {selected.needsReply ? (
              selected.repliedAt ? (
                <div className="pos-replied">
                  <CheckCircleFilled /> {dayjs(selected.repliedAt).format("M/D HH:mm")} 회신 · {selected.reply}
                </div>
              ) : (
                <div className="pos-reply">
                  <label>
                    회신 {selected.dueDate ? `· ${dayjs(selected.dueDate).format("M월 D일")}까지` : ""}
                    {overdue(selected) && <span className="pos-warn"> (기한 지남)</span>}
                  </label>
                  <Input.TextArea rows={3} maxLength={500} showCount value={reply} onChange={(e) => setReply(e.target.value)} placeholder="처리 결과를 적어 주세요." />
                  <Button type="primary" disabled={!reply.trim()} loading={sending} onClick={sendReply}>
                    회신 보내기
                  </Button>
                </div>
              )
            ) : selected.readAt ? (
              <div className="pos-hint">{dayjs(selected.readAt).format("M/D HH:mm")} 확인함</div>
            ) : (
              <Button onClick={() => onRead(selected)}>확인했습니다</Button>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default MessagesTab;
