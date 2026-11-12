// 기준정보 화면 공통 틀: 검색 · 분류 필터 · 표 · 등록/수정 팝업 · 삭제 확인 · 결과 안내
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Col,
  ConfigProvider,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Select,
  Space,
  Table,
  Tooltip,
  message,
} from "antd";
import {
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FormOutlined,
  PlusOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import { ERPapi } from "../api/api";
import { BrnInput, PhoneInput } from "./formatters";
import "./crudPage.css";
import { ACCENT } from "../../theme/erpTheme";

export const UNCATEGORIZED = "미분류";

// ----- 표시용 도우미 -----
// 상태 배지. tones: { 사용가능: "green", 수리: "amber" … }
export const Badge = ({ value, tones = {} }) =>
  value ? <span className={`crud-badge ${tones[value] || "gray"}`}>{value}</span> : "-";

// 서버 오류 메시지를 사람이 읽을 수 있게
export const errorText = (err, fallback) => {
  const data = err?.response?.data;
  if (typeof data === "string" && data.length < 200) return data;
  return data?.message || fallback;
};

// 목록 불러오기 (data, loading, reload)
export const useList = (url) => {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const res = await ERPapi.get(url);
      setData(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      message.error(errorText(err, "목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [url]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { data, loading, reload };
};

const toOptions = (options = []) =>
  options.map((o) => (typeof o === "object" ? o : { value: o, label: o }));

// 값 비교용 (날짜·빈 값 통일)
const comparable = (v) => {
  if (dayjs.isDayjs(v)) return v.format("YYYY-MM-DD");
  if (v === undefined || v === null) return "";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
};

const defaultNormalize = (v) => String(v ?? "").trim().toLowerCase();

// ----- 폼 입력칸 하나 -----
// Form.Item이 넘겨주는 value/onChange(...rest)를 실제 입력칸에 그대로 전달해야 값이 저장된다.
const FieldInput = ({ field, isEdit, ...rest }) => {
  const disabled = field.disabledOnEdit && isEdit;
  const placeholder = field.placeholder ?? `${field.label}${field.type === "select" ? " 선택" : " 입력"}`;
  const common = { ...rest, placeholder, disabled };
  switch (field.type) {
    case "select":
      return (
        <Select
          {...common}
          options={toOptions(field.options)}
          showSearch
          optionFilterProp="label"
          allowClear={!field.required}
        />
      );
    case "money":
      return (
        <InputNumber
          {...common}
          style={{ width: "100%" }}
          min={0}
          addonAfter="원"
          formatter={(v) => (v === undefined || v === null || v === "" ? "" : `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ","))}
          parser={(v) => (v ? v.replace(/[^\d]/g, "") : "")}
        />
      );
    case "number":
      return <InputNumber {...common} style={{ width: "100%" }} min={field.min ?? 0} addonAfter={field.unit} />;
    case "date":
      return <DatePicker {...common} style={{ width: "100%" }} />;
    case "textarea":
      return <Input.TextArea {...common} rows={3} maxLength={field.maxLength} showCount={!!field.maxLength} />;
    case "password":
      return <Input.Password {...common} autoComplete="new-password" />;
    case "brn":
      return <BrnInput {...common} />;
    case "phone":
      return <PhoneInput {...common} />;
    default:
      return <Input {...common} maxLength={field.maxLength} showCount={!!field.maxLength} />;
  }
};

// ----- 등록/수정 팝업 -----
const FormModal = ({
  editing,
  setEditing,
  entity,
  icon,
  rowKey,
  data,
  fields,
  sections,
  toFormValues,
  onCreate,
  onUpdate,
  onDelete,
  itemName,
  width,
  renderFormExtra,
  extraFields = [],
  onDeleted,
}) => {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState("");
  const [changed, setChanged] = useState(new Set());
  const snapshot = useRef({});
  // 닫히는 애니메이션 동안에도 제목·내용이 '등록'으로 바뀌지 않게 마지막 대상을 기억한다
  const lastShown = useRef(null);
  if (editing) lastShown.current = editing;
  const shown = editing || lastShown.current;
  const isEdit = !!shown && !shown.__new;
  const open = !!editing;

  // showWhen(입력 중인 값): 다른 칸 값에 따라 보이는 칸 (숨은 칸은 검사 · 저장에서 빠진다)
  const watched = Form.useWatch([], form) || {};
  const visibleFields = fields.filter(
    (f) => !(f.hideOnEdit && isEdit) && !(f.hideOnCreate && !isEdit) && (!f.showWhen || f.showWhen(watched))
  );
  const dateFields = fields.filter((f) => f.type === "date").map((f) => f.name);

  const fillForm = useCallback(() => {
    form.resetFields();
    setServerError("");
    if (isEdit) {
      const values = toFormValues ? toFormValues(editing) : { ...editing };
      dateFields.forEach((name) => {
        values[name] = values[name] ? dayjs(values[name]) : null;
      });
      form.setFieldsValue(values);
    } else {
      const initial = {};
      fields.forEach((f) => {
        if (f.initialValue !== undefined) initial[f.name] = f.initialValue;
      });
      form.setFieldsValue(initial);
    }
    snapshot.current = form.getFieldsValue(true);
    setChanged(new Set());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing]);

  useEffect(() => {
    if (open) fillForm();
  }, [open, fillForm]);

  // 처음 값과 달라진 칸 추적
  const onValuesChange = (_, all) => {
    const next = new Set();
    [...fields.map((f) => f.name), ...extraFields].forEach((name) => {
      if (comparable(all[name]) !== comparable(snapshot.current[name])) next.add(name);
    });
    setChanged(next);
  };

  const close = () => setEditing(null);

  // 입력 중인 내용이 있으면 한 번 묻고 닫는다
  const requestClose = () => {
    if (saving) return;
    if (changed.size === 0) return close();
    Modal.confirm({
      title: "작성 중인 내용이 있습니다",
      content: "저장하지 않고 닫으면 입력한 내용이 사라집니다.",
      okText: "닫기",
      okButtonProps: { danger: true },
      cancelText: "계속 작성",
      onOk: close,
    });
  };

  const submit = async (keepOpen = false) => {
    if (isEdit && changed.size === 0) return;
    let values;
    try {
      values = await form.validateFields();
    } catch {
      return; // 입력 오류는 각 칸 아래에 표시됨
    }
    dateFields.forEach((name) => {
      values[name] = values[name] ? values[name].format("YYYY-MM-DD") : null;
    });
    setSaving(true);
    setServerError("");
    try {
      if (isEdit) {
        await onUpdate(editing, values);
        message.success(`${entity} 정보를 저장했습니다.`);
        close();
      } else {
        await onCreate(values);
        if (keepOpen) {
          message.success(`${entity}을(를) 등록했습니다. 이어서 입력하세요.`);
          fillForm();
          const first = visibleFields[0]?.name;
          if (first) setTimeout(() => form.focusField?.(first), 50);
        } else {
          message.success(`${entity}을(를) 등록했습니다.`);
          close();
        }
      }
    } catch (err) {
      setServerError(
        errorText(err, `${isEdit ? "저장" : "등록"}하지 못했습니다. 코드·번호가 이미 등록돼 있는지 확인해 주세요.`)
      );
    } finally {
      setSaving(false);
    }
  };

  const deleteInModal = async () => {
    try {
      await onDelete(editing);
      message.success("삭제했습니다.");
      onDeleted?.();
      close();
    } catch (err) {
      setServerError(errorText(err, "삭제하지 못했습니다. 다른 데이터에서 사용 중인지 확인해 주세요."));
    }
  };

  // 같은 값이 이미 있는지 확인 (자기 자신 제외)
  const uniqueRule = (f) => ({
    validator: async (_, value) => {
      if (value === undefined || value === null || value === "") return;
      const norm = f.normalize || defaultNormalize;
      const dup = data.some(
        (r) => (!isEdit || r[rowKey] !== editing[rowKey]) && norm(r[f.name]) === norm(value)
      );
      if (dup) throw new Error(f.uniqueMessage || `이미 등록된 ${f.label}입니다.`);
    },
  });

  const renderField = (f) => {
    const required = f.required && !(f.optionalOnEdit && isEdit);
    const verb = f.type === "select" || f.type === "date" ? "선택" : "입력";
    return (
      <Col key={f.name} span={f.half ? 12 : 24}>
        <Form.Item
          name={f.name}
          label={
            <>
              {f.label}
              {isEdit && changed.has(f.name) && <span className="fm-changed-tag">변경</span>}
            </>
          }
          className={changed.has(f.name) && isEdit ? "is-changed" : ""}
          validateTrigger={["onChange", "onBlur"]}
          extra={isEdit ? f.editHelp ?? f.help : f.help}
          tooltip={f.tooltip}
          rules={[
            ...(required ? [{ required: true, message: `${f.label}을(를) ${verb}하세요.` }] : []),
            ...(f.rules || []),
            ...(f.unique ? [uniqueRule(f)] : []),
          ]}
        >
          <FieldInput field={f} isEdit={isEdit} />
        </Form.Item>
      </Col>
    );
  };

  // 섹션별로 묶기 (섹션이 없으면 한 덩어리)
  const groups = sections?.length
    ? sections.map((s) => ({ ...s, items: visibleFields.filter((f) => f.section === s.key) }))
    : [{ key: "_", items: visibleFields }];

  const subtitle = isEdit
    ? [itemName(shown), shown?.[rowKey] != null ? `No.${shown[rowKey]}` : null].filter(Boolean).join(" · ")
    : `새 ${entity} 정보를 입력하세요.`;

  return (
    <Modal
      open={open}
      width={width}
      centered
      maskClosable={false}
      onCancel={requestClose}
      forceRender
      className="fm-modal"
      title={
        <div className="fm-head">
          <span className="fm-icon">{isEdit ? <EditOutlined /> : icon || <FormOutlined />}</span>
          <div className="fm-head-text">
            <div className="fm-title">
              {entity} {isEdit ? "정보 수정" : "등록"}
            </div>
            <div className="fm-sub">{subtitle}</div>
          </div>
        </div>
      }
      footer={
        <div className="fm-foot">
          <div className="fm-foot-left">
            {isEdit && onDelete ? (
              <Popconfirm
                title={`${entity}을(를) 삭제할까요?`}
                description="삭제한 데이터는 되돌릴 수 없습니다."
                okText="삭제"
                okButtonProps={{ danger: true }}
                cancelText="취소"
                onConfirm={deleteInModal}
              >
                <Button type="text" danger icon={<DeleteOutlined />} disabled={saving}>
                  삭제
                </Button>
              </Popconfirm>
            ) : (
              <span className="fm-required">
                <b>*</b> 필수 입력 항목
              </span>
            )}
          </div>
          <div className="fm-foot-right">
            {isEdit && (
              <span className={`fm-changes ${changed.size ? "is-dirty" : ""}`}>
                {changed.size ? `변경 ${changed.size}건` : "변경 사항 없음"}
              </span>
            )}
            <Button onClick={requestClose} disabled={saving}>
              취소
            </Button>
            {!isEdit && (
              <Button onClick={() => submit(true)} loading={saving}>
                저장 후 계속 등록
              </Button>
            )}
            <Button type="primary" loading={saving} disabled={isEdit && changed.size === 0} onClick={() => submit(false)}>
              {isEdit ? "변경 저장" : "등록"}
            </Button>
          </div>
        </div>
      }
    >
      <div
        className="fm-body"
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            submit(false);
          }
        }}
      >
        {serverError && (
          <Alert
            className="fm-error"
            type="error"
            showIcon
            message={serverError}
            closable
            onClose={() => setServerError("")}
          />
        )}
        <Form form={form} layout="vertical" requiredMark className="crud-form" onValuesChange={onValuesChange}>
          {groups.map((g) =>
            g.items.length === 0 ? null : (
              <section key={g.key} className="fm-section">
                {g.title && (
                  <div className="fm-section-head">
                    <h3>{g.title}</h3>
                    {g.description && <p>{g.description}</p>}
                  </div>
                )}
                <Row gutter={16}>{g.items.map(renderField)}</Row>
              </section>
            )
          )}
          {renderFormExtra && renderFormExtra({ form, isEdit })}
        </Form>
        <p className="fm-shortcut">Ctrl + Enter 로 저장</p>
      </div>
    </Modal>
  );
};

// ----- 목록 화면 -----
const CrudPage = ({
  title,
  description,
  embedded = false, // 탭 안에 들어갈 때 (제목 작게)
  entity, // 팝업 제목에 쓸 이름 (기본: 제목에서 ' 관리'를 뺀 것)
  icon, // 등록 팝업 아이콘
  rowKey,
  data = [],
  loading = false,
  columns = [],
  searchFields = [], // [{ key: "필드명" | (record) => 값, label, normalize? }] — 입력칸 하나로 모두 찾는다
  // 종료된 항목(폐점 · 단종 · 폐기)은 기본으로 숨기고, 검색칸 옆 링크로 켠다: { label: "폐점 매장", test(record) }
  archived,
  // 확인할 항목: 목록 위 한 줄 안내에서 누르면 그 항목만 본다. [{ key, label, unit, tone, test(record) }]
  quickFilters = [],
  fields = [],
  sections, // [{ key, title, description }] — 필드의 section 값으로 묶는다
  toFormValues,
  onCreate,
  onUpdate,
  onDelete,
  itemName = () => "",
  addLabel,
  modalWidth = 600,
  expandable,
  renderFormExtra,
  extraFields, // renderFormExtra 안 입력값 중 변경 추적할 이름들
  rowActions,
  // 행을 누르면 여는 상세 화면: ({ record, close, edit, DeleteButton }) => 노드. 없으면 행을 누를 때 수정 창을 연다
  renderDetail,
  // 검색칸 왼쪽 구분 (예: 매장 / 물류센터): { value, onChange, options: [{ value, label }] }. 거르기는 부르는 쪽이 data로 한다
  views,
}) => {
  const entityName = entity || (title || "").replace(/ 관리$/, "");
  const [search, setSearch] = useState("");
  const [searchBy, setSearchBy] = useState("all"); // 검색 조건: "all" 또는 searchFields 순번
  const [colFilters, setColFilters] = useState({}); // 열 제목에서 고른 값 { [열 key]: [값...] }
  const [detailKey, setDetailKey] = useState(null);
  const [showArchived, setShowArchived] = useState(false);
  const [activeQuick, setActiveQuick] = useState(null);
  const [editing, setEditing] = useState(null); // null: 닫힘, { __new: true }: 등록, record: 수정

  // ----- 종료된 항목 숨김 -----
  const archivedCount = useMemo(() => (archived ? data.filter(archived.test).length : 0), [data, archived]);
  const base = useMemo(
    () => (archived && !showArchived ? data.filter((r) => !archived.test(r)) : data),
    [data, archived, showArchived]
  );

  // ----- 확인할 항목 개수 -----
  const quickCounts = useMemo(
    () => quickFilters.map((q) => ({ ...q, count: base.filter(q.test).length })),
    [quickFilters, base]
  );
  const quick = quickFilters.find((q) => q.key === activeQuick);

  // ----- 열 제목 필터: 열에 filter: true 를 주면 그 열의 값들로 고르는 목록을 만든다 -----
  const filterCols = useMemo(
    () =>
      columns
        .filter((col) => col.filter)
        .map((col) => {
          const key = col.key || col.dataIndex;
          const get = (r) => (col.filterValue ? col.filterValue(r) : r[col.dataIndex]) || UNCATEGORIZED;
          const order = toOptions(col.filterOptions || []).map((o) => o.value);
          const seen = [...new Set(base.map(get))].sort((a, b) => {
            const ia = order.indexOf(a);
            const ib = order.indexOf(b);
            if (ia >= 0 || ib >= 0) return (ia < 0 ? 999 : ia) - (ib < 0 ? 999 : ib);
            return String(a).localeCompare(String(b));
          });
          const counts = {};
          base.forEach((r) => (counts[get(r)] = (counts[get(r)] || 0) + 1));
          return { key, get, options: seen.map((v) => ({ value: v, text: `${v} (${counts[v]})` })) };
        }),
    [columns, base]
  );

  // ----- 검색(고른 조건, '전체'면 검색 항목 중 하나라도 맞으면) + 열 필터 + 확인할 항목 -----
  const searchIn = useMemo(() => (searchBy === "all" ? searchFields : searchFields.slice(searchBy, searchBy + 1)), [searchBy, searchFields]);
  const rows = useMemo(() => {
    const matches = (r) =>
      searchIn.some((f) => {
        const norm = f.normalize || defaultNormalize;
        const q = norm(search);
        if (!q) return false; // 숫자만 보는 항목(연락처)에 글자를 치면 빈 값이 되어 모두 맞는 일이 없게
        const v = typeof f.key === "function" ? f.key(r) : r[f.key];
        return v !== null && v !== undefined && norm(v).includes(q);
      });
    return base.filter((r) => {
      for (const col of filterCols) {
        const vals = colFilters[col.key];
        if (vals?.length && !vals.includes(col.get(r))) return false;
      }
      if (quick && !quick.test(r)) return false;
      if (!search.trim() || !searchIn.length) return true;
      return matches(r);
    });
  }, [base, search, colFilters, filterCols, quick, searchIn]);

  const filtering = Object.values(colFilters).some((v) => v?.length);
  const detailRecord = detailKey === null ? null : data.find((r) => r[rowKey] === detailKey) || null;
  const openRow = (record) => {
    if (renderDetail) setDetailKey(record[rowKey]);
    else if (onUpdate) setEditing(record);
  };

  const remove = async (record) => {
    try {
      await onDelete(record);
      message.success("삭제했습니다.");
    } catch (err) {
      message.error(errorText(err, "삭제하지 못했습니다. 다른 데이터에서 사용 중인지 확인해 주세요."));
    }
  };

  // 상세 화면에서 쓰는 삭제 버튼 (확인 창 포함)
  const DeleteButton = ({ record, children, ...props }) =>
    onDelete ? (
      <Popconfirm
        title="삭제할까요?"
        description={itemName(record) ? `'${itemName(record)}'을(를) 삭제합니다.` : "삭제한 데이터는 되돌릴 수 없습니다."}
        okText="삭제"
        okButtonProps={{ danger: true }}
        cancelText="취소"
        onConfirm={() => remove(record)}
      >
        <Button danger icon={<DeleteOutlined />} aria-label="삭제" {...props}>
          {children}
        </Button>
      </Popconfirm>
    ) : null;
  const emptyText = search || filtering || quick ? "조건에 맞는 데이터가 없습니다." : "등록된 데이터가 없습니다.";
  // "매장명 · 매장 코드 · 주소" 처럼 검색 대상 앞쪽 몇 개만 안내
  const searchHint = searchFields.length
    ? `${searchFields.slice(0, 3).map((f) => f.label).join(" · ")}${searchFields.length > 3 ? " 등" : ""}(으)로 검색`
    : "검색";

  const tableColumns = [
    {
      title: "No",
      key: "__no",
      width: 64,
      align: "center",
      render: (_, __, index) => <span className="crud-no">{index + 1}</span>,
    },
    ...columns.map(({ filter, filterValue, filterOptions, ...col }) => {
      if (!filter) return col;
      const key = col.key || col.dataIndex;
      return {
        ...col,
        key,
        filters: filterCols.find((x) => x.key === key)?.options || [],
        filteredValue: colFilters[key] || null,
      };
    }),
    {
      title: "",
      key: "__actions",
      width: rowActions ? 150 : 96,
      align: "right",
      fixed: "right",
      render: (_, record) => (
        <div className="crud-row-actions" onClick={(e) => e.stopPropagation()}>
          {rowActions && rowActions(record)}
          {onUpdate && (
            <Tooltip title="수정">
              <Button type="text" size="small" icon={<EditOutlined />} onClick={() => setEditing(record)} />
            </Tooltip>
          )}
          {onDelete && (
            <Popconfirm
              title="삭제할까요?"
              description={itemName(record) ? `'${itemName(record)}'을(를) 삭제합니다.` : "삭제한 데이터는 되돌릴 수 없습니다."}
              okText="삭제"
              okButtonProps={{ danger: true }}
              cancelText="취소"
              onConfirm={() => remove(record)}
            >
              {/* 툴팁을 달면 확인 창의 '삭제' 버튼을 가려서 aria-label만 둔다 */}
              <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="삭제" />
            </Popconfirm>
          )}
        </div>
      ),
    },
  ];

  return (
    <ConfigProvider theme={{ token: { colorPrimary: ACCENT } }}>
      <div className={`crud-page ${embedded ? "is-embedded" : ""}`}>
        <div className="crud-head">
          <div>
            {embedded && <h2 className="crud-title is-small">{title}</h2>}
            {description && <p className="crud-desc">{description}</p>}
          </div>
          {onCreate && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ __new: true })}>
              {addLabel || `${entityName} 등록`}
            </Button>
          )}
        </div>

        <div className="crud-card">
          <div className="crud-toolbar">
            {views && <Segmented className="crud-views" value={views.value} onChange={views.onChange} options={views.options} />}
            <Space.Compact className="crud-search">
              {searchFields.length > 1 && (
                <Select
                  className="crud-search-by"
                  value={searchBy}
                  onChange={setSearchBy}
                  aria-label="검색 조건"
                  popupMatchSelectWidth={false}
                  options={[{ value: "all", label: "전체" }, ...searchFields.map((f, i) => ({ value: i, label: f.label }))]}
                />
              )}
              <Input
                allowClear
                prefix={<SearchOutlined className="crud-search-icon" />}
                placeholder={searchBy === "all" ? searchHint : `${searchFields[searchBy].label}(으)로 검색`}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </Space.Compact>
            <span className="crud-total">
              {rows.length === base.length ? `전체 ${base.length}건` : `${rows.length}건 / 전체 ${base.length}건`}
            </span>
            {archived && archivedCount > 0 && (
              <button type="button" className="crud-link crud-archived-link" onClick={() => setShowArchived((v) => !v)}>
                {showArchived ? `${archived.label} 숨기기` : `${archived.label} ${archivedCount}건 보기`}
              </button>
            )}
          </div>

          {quick ? (
            <div className="crud-notice is-active">
              <span>
                <b>{quick.label}</b> 항목만 보고 있습니다.
              </span>
              <button type="button" className="crud-link" onClick={() => setActiveQuick(null)}>
                모두 보기
              </button>
            </div>
          ) : (
            quickCounts.some((q) => q.count) && (
              <div className="crud-notice">
                <ExclamationCircleOutlined className="crud-notice-icon" />
                <span className="crud-notice-label">확인할 항목</span>
                {quickCounts
                  .filter((q) => q.count)
                  .map((q) => (
                    <button key={q.key} type="button" className={`crud-link is-${q.tone || "plain"}`} onClick={() => setActiveQuick(q.key)}>
                      {q.label} {q.count}
                      {q.unit}
                    </button>
                  ))}
              </div>
            )
          )}

          <Table
            className="crud-table"
            size="middle"
            bordered
            rowKey={rowKey}
            loading={loading}
            columns={tableColumns}
            dataSource={rows}
            expandable={expandable}
            scroll={{ x: "max-content" }}
            onChange={(_, filters) => setColFilters(filters)}
            rowClassName={() => (renderDetail || onUpdate ? "is-clickable" : "")}
            onRow={(record) => ({ onClick: () => openRow(record) })}
            // 처음부터 같은 설정을 줘야 20개씩이 적용된다 (불러오기 전 false였다가 바뀌면 10개로 굳는다)
            pagination={{
              defaultPageSize: 20,
              hideOnSinglePage: true,
              showSizeChanger: rows.length > 20,
              pageSizeOptions: [20, 50, 100],
              showTotal: (total, range) => `${range[0]}-${range[1]} / ${total}건`,
            }}
            locale={{ emptyText }}
          />
        </div>

        {renderDetail &&
          renderDetail({
            record: detailRecord,
            close: () => setDetailKey(null),
            edit: (record) => {
              setDetailKey(null);
              setEditing(record);
            },
            DeleteButton,
          })}

        <FormModal
          editing={editing}
          setEditing={setEditing}
          entity={entityName}
          icon={icon}
          rowKey={rowKey}
          data={data}
          fields={fields}
          sections={sections}
          toFormValues={toFormValues}
          onCreate={onCreate}
          onUpdate={onUpdate}
          onDelete={onDelete}
          itemName={itemName}
          width={modalWidth}
          renderFormExtra={renderFormExtra}
          extraFields={extraFields}
        />
      </div>
    </ConfigProvider>
  );
};

export default CrudPage;
