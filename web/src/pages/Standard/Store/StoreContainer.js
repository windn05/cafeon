import { ShopOutlined } from "@ant-design/icons";
import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { Dot, byNum, byOrder, byText } from "../../../components/CrudPage/cells";
import { formatPhone, onlyDigits } from "../../../components/CrudPage/formatters";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { STORE_STATUS, STORE_TYPES } from "../cafeOptions";
import dayjs from "dayjs";
import StoreDetail, { SHOPS, STORE_TONE, fixedCost } from "./StoreDetail";

const URL = `${ERP_API}/stores`;

const SECTIONS = [
  { key: "basic", title: "기본 정보" },
  { key: "contact", title: "위치 · 연락처" },
  { key: "cost", title: "월 고정비", description: "월간 손익 계산에 쓰입니다. 비워 두면 0원으로 계산합니다." },
];

const man = (v) => `${Math.round((Number(v) || 0) / 10000).toLocaleString()}만원`;
const none = <span className="crud-sub">-</span>;

const COLUMNS = [
  { title: "코드", dataIndex: "storeCode", sorter: byText((r) => r.storeCode), render: (v) => <span className="crud-code">{v}</span> },
  { title: "매장명", dataIndex: "storeName", sorter: byText((r) => r.storeName), render: (v) => <span className="crud-strong">{v}</span> },
  {
    title: "유형",
    dataIndex: "storeType",
    filter: true,
    filterOptions: STORE_TYPES,
    sorter: byOrder(STORE_TYPES, (r) => r.storeType),
    defaultSortOrder: "ascend",
  },
  {
    title: "상태",
    dataIndex: "status",
    filter: true,
    filterOptions: STORE_STATUS,
    render: (v) => <Dot tone={STORE_TONE[v]}>{v}</Dot>,
  },
  { title: "점장", dataIndex: "managerName", render: (v) => v || <span className="crud-sub">미지정</span> },
  { title: "연락처", dataIndex: "phone", render: (v) => (v ? <span className="crud-num">{formatPhone(v)}</span> : none) },
  { title: "주소", dataIndex: "address", className: "is-wrap" },
  { title: "직원", dataIndex: "staffCount", align: "right", sorter: byNum((r) => r.staffCount), render: (v) => <span className="crud-num">{v}명</span> },
  {
    title: "자산",
    dataIndex: "equipmentCount",
    align: "right",
    sorter: byNum((r) => r.equipmentCount),
    render: (v, r) => (r.storeType === "가맹점" ? none : <span className="crud-num">{v}개</span>),
  },
  {
    title: "월 고정비",
    key: "fixed",
    align: "right",
    sorter: byNum(fixedCost),
    render: (_, r) => (SHOPS.includes(r.storeType) ? <span className="crud-num">{man(fixedCost(r))}</span> : none),
  },
  { title: "오픈", dataIndex: "openDate", sorter: byText((r) => r.openDate), render: (v) => (v ? <span className="crud-num">{dayjs(v).format("YYYY.MM")}</span> : none) },
];

const StoreContainer = () => {
  const { data, loading, reload } = useList(URL);

  return (
    <CrudPage
      title="매장 관리"
      description="직영점·가맹점과 물류센터·센트럴키친을 관리합니다. 행을 누르면 그 매장의 고정비 · 직원 · 본사 자산까지 볼 수 있습니다."
      entity="매장"
      icon={<ShopOutlined />}
      rowKey="storeNo"
      data={data}
      loading={loading}
      searchFields={[
        { key: "storeName", label: "매장명" },
        { key: "storeCode", label: "매장 코드" },
        { key: "address", label: "주소" },
        { key: "managerName", label: "점장" },
        { key: "phone", label: "연락처", normalize: onlyDigits },
      ]}
      columns={COLUMNS}
      archived={{ label: "폐점 매장", test: (r) => r.status === "폐점" }}
      modalWidth={620}
      renderDetail={({ record, close, edit, DeleteButton }) => (
        <StoreDetail store={record} onClose={close} edit={edit} DeleteButton={DeleteButton} />
      )}
      sections={SECTIONS}
      fields={[
        { name: "storeCode", label: "매장 코드", section: "basic", required: true, half: true, placeholder: "예: ST-GN01", unique: true, maxLength: 30 },
        { name: "storeName", label: "매장명", section: "basic", required: true, half: true, placeholder: "예: 강남역점", maxLength: 50 },
        { name: "storeType", label: "유형", section: "basic", type: "select", options: STORE_TYPES, required: true, half: true, initialValue: "직영점" },
        {
          name: "status",
          label: "영업 상태",
          section: "basic",
          type: "select",
          options: STORE_STATUS,
          required: true,
          half: true,
          initialValue: "영업중",
          help: "문을 닫은 매장은 삭제 대신 '폐점'으로 두면 지난 데이터가 유지됩니다.",
        },
        { name: "openDate", label: "오픈일", section: "basic", type: "date", half: true },
        { name: "managerName", label: "점장", section: "contact", half: true, maxLength: 30 },
        { name: "phone", label: "매장 연락처", section: "contact", type: "phone", half: true, placeholder: "숫자만 입력 (하이픈 자동)" },
        { name: "address", label: "주소", section: "contact", required: true, maxLength: 200, placeholder: "예: 서울특별시 강남구 강남대로 396" },
        { name: "monthlyRent", label: "월 임차료", section: "cost", type: "money", half: true },
        { name: "monthlyFixedCost", label: "월 기타 고정비", section: "cost", type: "money", half: true, tooltip: "관리비 · 전기·수도·가스 · 통신 · 보험 · POS 이용료 등" },
      ]}
      itemName={(r) => r.storeName}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.storeNo}`, values);
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.storeNo}`);
        await reload();
      }}
    />
  );
};

export default StoreContainer;
