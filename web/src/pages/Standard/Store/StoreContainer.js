import { useState } from "react";
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
  { key: "cost", title: "월 고정비", description: "직영점 월간 손익 계산에 쓰입니다. 비워 두면 0원으로 계산합니다." },
];

const man = (v) => `${Math.round((Number(v) || 0) / 10000).toLocaleString()}만원`;
const none = <span className="crud-sub">-</span>;

// 사업장 = 매장(직영 · 가맹) + 물류센터. 데이터는 한 표, 화면은 구분해서 본다
const CENTER = "물류센터";
const KIND = {
  shop: { types: SHOPS, entity: "매장", name: "매장명", code: "매장 코드", manager: "점장", phone: "매장 연락처", codeHint: "예: ST-GN01", nameHint: "예: 강남역점" },
  center: { types: [CENTER], entity: "물류센터", name: "센터명", code: "센터 코드", manager: "센터장", phone: "센터 연락처", codeHint: "예: LC-01", nameHint: "예: 수도권 물류센터" },
};

const columns = (k) => [
  { title: "코드", dataIndex: "storeCode", sorter: byText((r) => r.storeCode), render: (v) => <span className="crud-code">{v}</span> },
  { title: k.name, dataIndex: "storeName", sorter: byText((r) => r.storeName), render: (v) => <span className="crud-strong">{v}</span> },
  ...(k.types.length > 1
    ? [
        {
          title: "유형",
          dataIndex: "storeType",
          filter: true,
          filterOptions: k.types,
          sorter: byOrder(STORE_TYPES, (r) => r.storeType),
          defaultSortOrder: "ascend",
        },
      ]
    : []),
  {
    title: "상태",
    dataIndex: "status",
    filter: true,
    filterOptions: STORE_STATUS,
    render: (v) => <Dot tone={STORE_TONE[v]}>{v}</Dot>,
  },
  { title: k.manager, dataIndex: "managerName", render: (v) => v || <span className="crud-sub">미지정</span> },
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
  // 월 고정비는 직영점 손익에만 쓴다 (가맹점은 점주 관리, 물류센터는 매장 손익 밖)
  ...(k.types.includes("직영점")
    ? [
        {
          title: "월 고정비",
          key: "fixed",
          align: "right",
          sorter: byNum(fixedCost),
          render: (_, r) => (r.storeType === "직영점" ? <span className="crud-num">{man(fixedCost(r))}</span> : none),
        },
      ]
    : []),
  { title: "오픈", dataIndex: "openDate", sorter: byText((r) => r.openDate), render: (v) => (v ? <span className="crud-num">{dayjs(v).format("YYYY.MM")}</span> : none) },
];

const StoreContainer = () => {
  const { data, loading, reload } = useList(URL);
  const [kind, setKind] = useState("shop");
  const k = KIND[kind];
  const count = (key) => data.filter((r) => KIND[key].types.includes(r.storeType) && r.status !== "폐점").length;

  return (
    <CrudPage
      key={kind}
      description={
        kind === "shop"
          ? "직영점 · 가맹점을 관리합니다. 행을 누르면 그 매장의 고정비 · 직원 · 본사 자산까지 볼 수 있습니다. 가맹점 직원 · 자산 · 비용은 점주 관리라 본사에 두지 않습니다."
          : "매장 발주를 출고하고 공급업체 발주를 받는 물류센터입니다. 재고는 구매 · 물류 > 재고 관리에서 봅니다."
      }
      entity={k.entity}
      icon={<ShopOutlined />}
      rowKey="storeNo"
      data={data.filter((r) => k.types.includes(r.storeType))}
      loading={loading}
      views={{
        value: kind,
        onChange: setKind,
        options: [
          { value: "shop", label: `매장 ${count("shop")}` },
          { value: "center", label: `물류센터 ${count("center")}` },
        ],
      }}
      searchFields={[
        { key: "storeName", label: k.name },
        { key: "storeCode", label: k.code },
        { key: "address", label: "주소" },
        { key: "managerName", label: k.manager },
        { key: "phone", label: "연락처", normalize: onlyDigits },
      ]}
      columns={columns(k)}
      archived={{ label: kind === "shop" ? "폐점 매장" : "운영 종료 센터", test: (r) => r.status === "폐점" }}
      modalWidth={620}
      renderDetail={({ record, close, edit, DeleteButton }) => (
        <StoreDetail store={record} onClose={close} edit={edit} DeleteButton={DeleteButton} />
      )}
      sections={SECTIONS}
      fields={[
        { name: "storeCode", label: k.code, section: "basic", required: true, half: true, placeholder: k.codeHint, unique: true, maxLength: 30 },
        { name: "storeName", label: k.name, section: "basic", required: true, half: true, placeholder: k.nameHint, maxLength: 50 },
        { name: "storeType", label: "유형", section: "basic", type: "select", options: k.types, required: true, half: true, initialValue: k.types[0] },
        {
          name: "status",
          label: "영업 상태",
          section: "basic",
          type: "select",
          options: STORE_STATUS,
          required: true,
          half: true,
          initialValue: "영업중",
          help: "문을 닫은 곳은 삭제 대신 '폐점'으로 두면 지난 데이터가 유지됩니다.",
        },
        { name: "openDate", label: "오픈일", section: "basic", type: "date", half: true },
        { name: "managerName", label: k.manager, section: "contact", half: true, maxLength: 30 },
        { name: "phone", label: k.phone, section: "contact", type: "phone", half: true, placeholder: "숫자만 입력 (하이픈 자동)" },
        { name: "address", label: "주소", section: "contact", required: true, maxLength: 200, placeholder: "예: 서울특별시 강남구 강남대로 396" },
        { name: "monthlyRent", label: "월 임차료", section: "cost", type: "money", half: true, showWhen: (v) => v.storeType === "직영점" },
        { name: "monthlyFixedCost", label: "월 기타 고정비", section: "cost", type: "money", half: true, showWhen: (v) => v.storeType === "직영점", tooltip: "관리비 · 전기·수도·가스 · 통신 · 보험 · POS 이용료 등" },
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
