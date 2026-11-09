import { Form } from "antd";
import { ShoppingOutlined } from "@ant-design/icons";
import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { MATERIAL_TYPES, STORAGE_TYPES, USAGE_UNITS, formatUnitCost } from "../cafeOptions";
import { byNum, byText, won } from "../../../components/CrudPage/cells";

const URL = `${ERP_API}/materials`;
const SHORT_LIFE = 7; // 개봉 전 유통기한이 이 일수 이하면 주의
const none = <span className="crud-sub">-</span>;
const packLabel = (m) => m.materialSize || `${Number(m.packQty || 0).toLocaleString()} ${m.materialUnit}`;

const COLUMNS = [
  { title: "코드", dataIndex: "materialCode", sorter: byText((r) => r.materialCode), render: (v) => <span className="crud-code">{v}</span> },
  { title: "식자재명", dataIndex: "materialName", sorter: byText((r) => r.materialName), render: (v) => <span className="crud-strong">{v}</span> },
  { title: "분류", dataIndex: "materialType", filter: true, filterOptions: MATERIAL_TYPES },
  { title: "보관", dataIndex: "storageType", filter: true, filterOptions: STORAGE_TYPES },
  {
    title: "공급업체",
    dataIndex: "clientName",
    filter: true,
    filterValue: (r) => r.clientName || "미지정",
    render: (v) => v || <span className="crud-tag is-warn">미지정</span>,
  },
  { title: "구매 단위", key: "pack", render: (_, r) => packLabel(r) },
  { title: "구매 단가", dataIndex: "materialPrice", align: "right", sorter: byNum((r) => r.materialPrice), render: (v) => <span className="crud-num">{won(v)}</span> },
  {
    title: "사용 단위 원가",
    dataIndex: "unitCost",
    align: "right",
    sorter: byNum((r) => r.unitCost),
    render: (v, r) => <span className="crud-num crud-strong">{formatUnitCost(v, r.materialUnit)}</span>,
  },
  {
    title: "유통기한",
    dataIndex: "shelfLifeDays",
    align: "right",
    sorter: byNum((r) => r.shelfLifeDays ?? 9999),
    render: (v) =>
      v === null || v === undefined ? none : v <= SHORT_LIFE ? <span className="crud-tag is-warn">{v}일</span> : <span className="crud-num">{v}일</span>,
  },
  {
    title: "사용 메뉴",
    dataIndex: "menuCount",
    align: "right",
    sorter: byNum((r) => r.menuCount),
    render: (v) => (v ? <span className="crud-num">{v}개</span> : <span className="crud-sub">없음</span>),
  },
];

const SECTIONS = [
  { key: "basic", title: "기본 정보" },
  {
    key: "price",
    title: "구매 · 원가",
    description: "구매는 포장 단위로, 레시피는 사용 단위(g·ml·EA)로 계산합니다. 예: 원두 1kg 봉 = 1000 g",
  },
];

// 입력 중인 값으로 사용 단위 원가를 바로 보여 준다
const UnitCostPreview = ({ form }) => {
  const price = Form.useWatch("materialPrice", form);
  const qty = Form.useWatch("packQty", form);
  const unit = Form.useWatch("materialUnit", form) || "단위";
  const ready = Number(price) > 0 && Number(qty) > 0;
  return (
    <div className="cafe-preview">
      <span>사용 단위 원가</span>
      <strong>{ready ? formatUnitCost(price / qty, unit) : "구매 단가와 포장 용량을 입력하면 계산됩니다."}</strong>
    </div>
  );
};

const MaterialContainer = () => {
  const { data, loading, reload } = useList(URL);
  const { data: clients } = useList(`${ERP_API}/clients`);

  return (
    <CrudPage
      title="식자재 관리"
      description="원두·우유·시럽·포장재 등 매장과 센트럴키친에서 쓰는 식자재입니다. 구매 단가로 계산한 사용 단위 원가가 레시피 원가의 기준이 됩니다."
      entity="식자재"
      icon={<ShoppingOutlined />}
      rowKey="materialNo"
      data={data}
      loading={loading}
      searchFields={[
        { key: "materialName", label: "식자재명" },
        { key: "materialCode", label: "코드" },
        { key: "clientName", label: "공급업체" },
      ]}
      columns={COLUMNS}
      quickFilters={[
        { key: "shortLife", label: `유통기한 ${SHORT_LIFE}일 이하`, unit: "종", tone: "warn", test: (r) => r.shelfLifeDays != null && r.shelfLifeDays <= SHORT_LIFE },
        { key: "unused", label: "레시피에 안 쓰임", unit: "종", tone: "warn", test: (r) => !r.menuCount },
        { key: "noClient", label: "공급업체 없음", unit: "종", tone: "neg", test: (r) => !r.clientNo },
      ]}
      modalWidth={640}
      sections={SECTIONS}
      fields={[
        { name: "materialCode", label: "식자재 코드", section: "basic", required: true, half: true, placeholder: "예: CF-001", unique: true, maxLength: 30 },
        { name: "materialName", label: "식자재명", section: "basic", required: true, half: true, placeholder: "예: 에스프레소 블렌드 원두", maxLength: 50 },
        { name: "materialType", label: "분류", section: "basic", type: "select", options: MATERIAL_TYPES, required: true, half: true },
        { name: "storageType", label: "보관 방법", section: "basic", type: "select", options: STORAGE_TYPES, required: true, half: true, initialValue: "상온" },
        { name: "shelfLifeDays", label: "유통기한 (개봉 전)", section: "basic", type: "number", unit: "일", half: true, min: 1 },
        {
          name: "clientNo",
          label: "주 공급업체",
          section: "basic",
          type: "select",
          half: true,
          options: clients.map((c) => ({ value: c.clientNo, label: `${c.clientName} (${c.typeLabel})` })),
          help: "발주할 때 기본으로 선택됩니다.",
        },
        { name: "materialSize", label: "구매 포장 단위", section: "price", required: true, half: true, placeholder: "예: 1kg 봉, 1L 팩, 100개입 박스" },
        { name: "materialPrice", label: "구매 단가 (포장 1개)", section: "price", type: "money", required: true, half: true },
        { name: "materialUnit", label: "사용 단위", section: "price", type: "select", options: USAGE_UNITS, required: true, half: true, initialValue: "g" },
        {
          name: "packQty",
          label: "포장 1개 용량 (사용 단위 기준)",
          section: "price",
          type: "number",
          required: true,
          half: true,
          min: 0.01,
          placeholder: "예: 1000",
          tooltip: "1kg 봉을 g으로 쓰면 1000, 1L 팩을 ml로 쓰면 1000, 100개입 박스를 EA로 쓰면 100",
        },
      ]}
      renderFormExtra={({ form }) => <UnitCostPreview form={form} />}
      itemName={(r) => r.materialName}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.materialNo}`, values);
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.materialNo}`);
        await reload();
      }}
    />
  );
};

export default MaterialContainer;
