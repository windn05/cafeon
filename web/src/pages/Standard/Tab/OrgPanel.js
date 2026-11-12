// 직원 관리 왼쪽 조직도: 본사 부서 · 사업장을 인원수와 함께 보여 주고, 부서는 여기서 바로 추가 · 이름 변경 · 삭제한다
import { useState } from "react";
import { Button, Input, Popconfirm, Tooltip, message } from "antd";
import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SettingOutlined } from "@ant-design/icons";
import { errorText } from "../../../components/CrudPage/CrudPage";

// 이름 입력 한 줄 (추가 · 이름 변경 공용)
const NameInput = ({ initial = "", onSave, onCancel }) => {
  const [value, setValue] = useState(initial);
  const [saving, setSaving] = useState(false);
  const save = async () => {
    const name = value.trim();
    if (!name) return onCancel();
    if (name === initial) return onCancel();
    setSaving(true);
    try {
      await onSave(name);
    } finally {
      setSaving(false);
    }
  };
  return (
    <div className="org-input">
      <Input
        autoFocus
        size="small"
        maxLength={50}
        value={value}
        placeholder="부서명"
        onChange={(e) => setValue(e.target.value)}
        onPressEnter={save}
        onKeyDown={(e) => e.key === "Escape" && onCancel()}
        disabled={saving}
      />
      <Button type="text" size="small" icon={<CheckOutlined />} onClick={save} loading={saving} aria-label="저장" />
      <Button type="text" size="small" icon={<CloseOutlined />} onClick={onCancel} aria-label="취소" />
    </div>
  );
};

const Node = ({ active, label, count, onClick, muted, children }) => (
  <div className={`org-node ${active ? "is-active" : ""} ${muted ? "is-muted" : ""}`}>
    <button type="button" onClick={onClick}>
      <span className="org-label">{label}</span>
      <span className="org-count">{count}</span>
    </button>
    {children}
  </div>
);

const OrgPanel = ({ users, departments, stores, node, setNode, onCreateDept, onRenameDept, onDeleteDept, onOpenRoles }) => {
  const [adding, setAdding] = useState(false);
  const [renaming, setRenaming] = useState(null);

  const hq = users.filter((u) => !u.storeNo);
  const hqIn = (no) => hq.filter((u) => u.department?.departmentNo === no).length;
  const allIn = (no) => users.filter((u) => u.department?.departmentNo === no).length;
  const storeIn = (no) => users.filter((u) => u.storeNo === no).length;
  const is = (kind, no) => node.kind === kind && (no === undefined || node.no === no);

  // 본사가 직원을 두는 사업장: 직영점 · 물류센터 (가맹점 직원은 점주 관리)
  const shops = stores.filter((s) => s.storeType !== "가맹점");

  const run = async (fn, ok) => {
    try {
      await fn();
      if (ok) message.success(ok);
      return true;
    } catch (err) {
      message.error(errorText(err, "처리하지 못했습니다."));
      return false;
    }
  };

  return (
    <aside className="org">
      <Node active={is("all")} label="전체" count={users.length} onClick={() => setNode({ kind: "all" })} />

      <div className="org-section">
        <Node active={is("hq")} label="본사" count={hq.length} onClick={() => setNode({ kind: "hq" })} />
        <div className="org-children">
          {departments.map((d) =>
            renaming === d.departmentNo ? (
              <NameInput
                key={d.departmentNo}
                initial={d.departmentName}
                onCancel={() => setRenaming(null)}
                onSave={(name) => run(() => onRenameDept(d, name), "부서 이름을 바꿨습니다.").then((ok) => ok && setRenaming(null))}
              />
            ) : (
              <Node
                key={d.departmentNo}
                active={is("dept", d.departmentNo)}
                label={d.departmentName}
                count={hqIn(d.departmentNo)}
                onClick={() => setNode({ kind: "dept", no: d.departmentNo })}
              >
                <span className="org-tools">
                  <Tooltip title="이름 변경">
                    <Button type="text" size="small" icon={<EditOutlined />} onClick={() => setRenaming(d.departmentNo)} />
                  </Tooltip>
                  {allIn(d.departmentNo) > 0 ? (
                    <Tooltip title={`직원 ${allIn(d.departmentNo)}명이 이 부서에 있어 삭제할 수 없습니다`}>
                      <Button type="text" size="small" icon={<DeleteOutlined />} disabled />
                    </Tooltip>
                  ) : (
                    <Popconfirm
                      title="부서를 삭제할까요?"
                      description={`'${d.departmentName}' 부서를 삭제합니다.`}
                      okText="삭제"
                      okButtonProps={{ danger: true }}
                      cancelText="취소"
                      onConfirm={() => run(() => onDeleteDept(d), "부서를 삭제했습니다.").then((ok) => ok && is("dept", d.departmentNo) && setNode({ kind: "hq" }))}
                    >
                      <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="삭제" />
                    </Popconfirm>
                  )}
                </span>
              </Node>
            )
          )}
          {adding ? (
            <NameInput onCancel={() => setAdding(false)} onSave={(name) => run(() => onCreateDept(name), "부서를 추가했습니다.").then((ok) => ok && setAdding(false))} />
          ) : (
            <button type="button" className="org-add" onClick={() => setAdding(true)}>
              <PlusOutlined /> 부서 추가
            </button>
          )}
        </div>
      </div>

      <div className="org-section">
        <p className="org-heading">사업장</p>
        <div className="org-children">
          {shops.map((s) => (
            <Node
              key={s.storeNo}
              active={is("store", s.storeNo)}
              label={s.storeName}
              count={storeIn(s.storeNo)}
              muted={s.status === "폐점"}
              onClick={() => setNode({ kind: "store", no: s.storeNo })}
            />
          ))}
        </div>
        <p className="org-hint">사업장은 [기준 정보 · 사업장]에서 관리합니다. 가맹점 직원은 점주가 관리합니다</p>
      </div>

      <Button className="org-roles" icon={<SettingOutlined />} onClick={onOpenRoles} block>
        직급 설정
      </Button>
    </aside>
  );
};

export default OrgPanel;
