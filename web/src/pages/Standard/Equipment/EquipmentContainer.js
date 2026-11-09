// 자산 관리: 직영점 · 센트럴키친 · 물류센터에 둔 본사 자산 대장
// 감가상각은 서버가 취득가 · 취득일 · 내용연수로 계산해 준다 (정액법 · 월할 · 잔존가치 0)
import { ToolOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { Dot, Meter, byNum, byText, won } from "../../../components/CrudPage/cells";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";

const URL = `${ERP_API}/equipments`;
const STATUS = ["사용가능", "수리", "폐기"];
const STATUS_TONE = { 사용가능: "ok", 수리: "warn", 폐기: "off" };
const DEFAULT_LIFE = 5;
// 자산 분류. 분류가 비어 있는 자산은 수정할 때 이름을 보고 추천값을 채워 준다.
export const EQUIP_CATEGORIES = {
  "커피 머신": ["에스프레소", "커피 머신", "브루어", "드립"],
  "그라인더": ["그라인더"],
  "제빙·음료 설비": ["제빙", "블렌더", "정수", "온수"],
  "오븐·베이킹": ["오븐", "반죽", "발효", "믹서"],
  "냉장·냉동": ["냉장", "냉동", "쇼케이스"],
  "주방·위생": ["식기세척", "세척", "후드"],
  "포장·기타": ["포장", "실링", "POS", "키오스크"],
};
const CATEGORY_NAMES = Object.keys(EQUIP_CATEGORIES);

const guessCategory = (name = "") =>
  CATEGORY_NAMES.find((cat) => EQUIP_CATEGORIES[cat].some((k) => name.includes(k)));

const none = <span className="crud-sub">-</span>;
const money = (v) => <span className="crud-num">{won(v)}</span>;

// 상각 진행: 지난 달 / 전체 달
const Progress = ({ r }) => {
  if (r.typeLabel === "폐기") return <span className="crud-sub">폐기</span>;
  const total = (r.usefulLifeYears || DEFAULT_LIFE) * 12;
  if (r.fullyDepreciated) return <Meter ratio={1} tone="warn" text="상각 끝남" />;
  return <Meter ratio={r.elapsedMonths / total} text={`${r.elapsedMonths} / ${total}개월`} />;
};

const COLUMNS = [
  { title: "코드", dataIndex: "equipCode", sorter: byText((r) => r.equipCode), render: (v) => <span className="crud-code">{v}</span> },
  { title: "자산명", dataIndex: "equipName", sorter: byText((r) => r.equipName), render: (v) => <span className="crud-strong">{v}</span> },
  { title: "분류", dataIndex: "equipCategory", filter: true, filterOptions: CATEGORY_NAMES },
  {
    title: "위치",
    dataIndex: "storeName",
    filter: true,
    filterValue: (r) => r.storeName || "미지정",
    sorter: byText((r) => r.storeName),
    defaultSortOrder: "ascend",
    render: (v) => v || <span className="crud-sub">미지정</span>,
  },
  { title: "상태", dataIndex: "typeLabel", filter: true, filterOptions: STATUS, render: (v) => <Dot tone={STATUS_TONE[v]}>{v}</Dot> },
  {
    title: "취득일",
    dataIndex: "equipPurchaseDate",
    sorter: byText((r) => r.equipPurchaseDate),
    render: (v) => (v ? <span className="crud-num">{dayjs(v).format("YYYY.MM.DD")}</span> : none),
  },
  { title: "취득가", dataIndex: "equipPrice", align: "right", sorter: byNum((r) => r.equipPrice), render: money },
  { title: "내용연수", dataIndex: "usefulLifeYears", align: "right", sorter: byNum((r) => r.usefulLifeYears), render: (v) => <span className="crud-num">{v}년</span> },
  { title: "상각 진행", key: "progress", sorter: byNum((r) => r.elapsedMonths / ((r.usefulLifeYears || DEFAULT_LIFE) * 12)), render: (_, r) => <Progress r={r} /> },
  { title: "월 상각액", dataIndex: "monthlyDepreciation", align: "right", sorter: byNum((r) => r.monthlyDepreciation), render: money },
  { title: "상각 누계", dataIndex: "accumulatedDepreciation", align: "right", sorter: byNum((r) => r.accumulatedDepreciation), render: money },
  {
    title: "장부가",
    dataIndex: "bookValue",
    align: "right",
    sorter: byNum((r) => r.bookValue),
    render: (v) => <span className="crud-num crud-strong">{won(v)}</span>,
  },
];

const EquipmentContainer = () => {
  const { data, loading, reload } = useList(URL);
  const { data: stores } = useList(`${ERP_API}/stores`);
  // 가맹점 자산은 점주 소유라 본사 자산으로 받지 않는다
  const places = stores.filter((s) => s.storeType !== "가맹점");

  return (
    <CrudPage
      title="자산 관리"
      description="직영점·센트럴키친·물류센터에 둔 본사 자산입니다. 취득가와 내용연수로 감가상각(정액법, 월할)을 자동 계산합니다. 가맹점 설비는 점주 소유라 등록하지 않습니다."
      entity="자산"
      icon={<ToolOutlined />}
      rowKey="equipNo"
      data={data}
      loading={loading}
      searchFields={[
        { key: "equipName", label: "자산명" },
        { key: "equipCode", label: "자산 코드" },
        { key: "storeName", label: "위치" },
      ]}
      columns={COLUMNS}
      archived={{ label: "폐기 자산", test: (r) => r.typeLabel === "폐기" }}
      quickFilters={[
        { key: "repair", label: "수리 중", unit: "개", tone: "warn", test: (r) => r.typeLabel === "수리" },
        { key: "done", label: "상각 끝남 (교체 검토)", unit: "개", tone: "warn", test: (r) => r.typeLabel !== "폐기" && r.fullyDepreciated },
      ]}
      modalWidth={620}
      fields={[
        { name: "equipCode", label: "자산 코드", required: true, half: true, placeholder: "예: EQ-GN01-01", unique: true, maxLength: 30 },
        { name: "equipName", label: "자산명", required: true, half: true, placeholder: "예: 에스프레소 머신 2그룹", maxLength: 50 },
        { name: "equipCategory", label: "분류", type: "select", options: CATEGORY_NAMES, required: true, half: true },
        {
          name: "storeNo",
          label: "위치",
          type: "select",
          half: true,
          options: places.map((s) => ({ value: s.storeNo, label: `${s.storeName} (${s.storeType})` })),
          help: "가맹점은 고를 수 없습니다.",
        },
        { name: "type", label: "상태", type: "select", options: STATUS, required: true, half: true, initialValue: "사용가능" },
        { name: "equipPurchaseDate", label: "취득일", type: "date", required: true, half: true },
        { name: "equipPrice", label: "취득가", type: "money", required: true, half: true },
        {
          name: "usefulLifeYears",
          label: "내용연수",
          type: "number",
          unit: "년",
          min: 1,
          required: true,
          half: true,
          initialValue: DEFAULT_LIFE,
          help: "카페 장비는 보통 5년입니다.",
        },
      ]}
      toFormValues={(r) => ({
        ...r,
        type: r.typeLabel,
        equipCategory: r.equipCategory || guessCategory(r.equipName),
      })}
      itemName={(r) => r.equipName}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.equipNo}`, values);
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.equipNo}`);
        await reload();
      }}
    />
  );
};

export default EquipmentContainer;
