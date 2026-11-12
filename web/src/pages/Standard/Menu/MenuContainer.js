import { CoffeeOutlined } from "@ant-design/icons";
import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { MENU_CATEGORIES, MENU_STATUS, PRODUCT_TYPES, costRateTone, formatUnitCost, isFinished } from "../cafeOptions";
import { Dot, Meter, byNum, byOrder, byText, won } from "../../../components/CrudPage/cells";
import MenuDetail, { MENU_TONE } from "./MenuDetail";
import "./menu.css";

const URL = `${ERP_API}/menus`;

const RATE_TONE = { red: "neg", amber: "warn", green: "pos" };

const COLUMNS = [
  { title: "코드", dataIndex: "menuCode", sorter: byText((r) => r.menuCode), render: (v) => <span className="crud-code">{v}</span> },
  {
    title: "메뉴명",
    dataIndex: "menuName",
    sorter: byText((r) => r.menuName),
    // 완제품은 표시만, 매장 제조인데 레시피가 비면 원가가 0원으로 계산돼 원가율 · 손익이 부풀려진다
    render: (v, r) => (
      <span className="crud-strong">
        {v} {isFinished(r) ? <span className="crud-tag is-gray">완제품</span> : !r.recipe.length && <span className="crud-tag is-neg">레시피 없음</span>}
      </span>
    ),
  },
  {
    title: "카테고리",
    dataIndex: "category",
    filter: true,
    filterOptions: MENU_CATEGORIES,
    sorter: byOrder(MENU_CATEGORIES, (r) => r.category),
    defaultSortOrder: "ascend",
  },
  { title: "판매가", dataIndex: "price", align: "right", sorter: byNum((r) => r.price), render: (v) => <span className="crud-num">{won(v)}</span> },
  { title: "1개 원가", dataIndex: "cost", align: "right", sorter: byNum((r) => r.cost), render: (v) => <span className="crud-num">{won(v)}</span> },
  {
    title: "원가율",
    dataIndex: "costRate",
    sorter: byNum((r) => r.costRate),
    render: (v) => {
      const rate = Number(v) || 0;
      return <Meter ratio={rate / 100} tone={RATE_TONE[costRateTone(rate)]} text={`${rate.toFixed(1)}%`} />;
    },
  },
  {
    title: "1개 이익",
    key: "profit",
    align: "right",
    sorter: byNum((r) => r.price - r.cost),
    render: (_, r) => <span className="crud-num">{won(r.price - r.cost)}</span>,
  },
  {
    title: "판매 상태",
    dataIndex: "status",
    filter: true,
    filterOptions: MENU_STATUS,
    render: (v) => <Dot tone={MENU_TONE[v]}>{v}</Dot>,
  },
];

const MenuContainer = () => {
  const { data, loading, reload } = useList(URL);
  const { data: materials } = useList(`${ERP_API}/materials`);
  // 완제품 납품 품목: 개(EA) 단위 식자재, 베이커리 · 디저트 먼저
  const productOptions = materials
    .filter((m) => m.materialUnit === "EA")
    .sort((a, b) => (b.materialType === "베이커리·디저트") - (a.materialType === "베이커리·디저트") || a.materialName.localeCompare(b.materialName))
    .map((m) => ({ value: m.materialNo, label: `${m.materialName} (${formatUnitCost(m.unitCost, m.materialUnit)})` }));

  return (
    <CrudPage
      description="매장 POS 메뉴판에 나가는 메뉴와 판매가입니다. 가격 · 판매 상태를 바꾸면 전 매장 POS에 바로 반영됩니다. 원가는 매장 제조 메뉴는 레시피로, 베이커리 · 디저트 같은 완제품은 납품 단가로 자동 계산됩니다. 행을 누르면 원가를 보면서 레시피를 바로 고칠 수 있습니다."
      entity="메뉴"
      icon={<CoffeeOutlined />}
      rowKey="menuNo"
      data={data}
      loading={loading}
      searchFields={[
        { key: "menuName", label: "메뉴명" },
        { key: "menuCode", label: "코드" },
        { key: (r) => r.recipe.map((l) => l.materialName).join(" "), label: "식자재" },
      ]}
      columns={COLUMNS}
      archived={{ label: "단종 메뉴", test: (r) => r.status === "단종" }}
      quickFilters={[
        { key: "noRecipe", label: "레시피 없음", unit: "개", tone: "neg", test: (r) => !isFinished(r) && !r.recipe.length },
        { key: "highCost", label: "원가율 40% 이상", unit: "개", tone: "neg", test: (r) => Number(r.costRate) >= 40 },
      ]}
      renderDetail={({ record, close, edit, DeleteButton }) => (
        <MenuDetail menu={record} materials={materials} onClose={close} edit={edit} DeleteButton={DeleteButton} onSaved={reload} />
      )}
      sections={[{ key: "basic", title: "메뉴 정보" }]}
      fields={[
        { name: "menuCode", label: "메뉴 코드", section: "basic", required: true, half: true, placeholder: "예: CF-AME", unique: true, maxLength: 30 },
        { name: "menuName", label: "메뉴명", section: "basic", required: true, half: true, placeholder: "예: 아메리카노", maxLength: 50 },
        { name: "category", label: "카테고리", section: "basic", type: "select", options: MENU_CATEGORIES, required: true, half: true },
        { name: "price", label: "판매가", section: "basic", type: "money", required: true, half: true },
        { name: "status", label: "판매 상태", section: "basic", type: "select", options: MENU_STATUS, required: true, half: true, initialValue: "판매중" },
        {
          name: "productType",
          label: "제공 방식",
          section: "basic",
          type: "select",
          options: PRODUCT_TYPES,
          required: true,
          half: true,
          initialValue: "매장 제조",
          help: "매장 제조는 저장한 뒤 메뉴 상세에서 레시피를 넣고, 완제품은 납품받는 품목 하나를 고릅니다.",
        },
        {
          name: "productMaterialNo",
          label: "납품 품목",
          section: "basic",
          type: "select",
          options: productOptions,
          required: true,
          half: true,
          showWhen: (v) => v.productType === "완제품",
          help: "1개 팔면 이 품목 1개가 재고에서 빠지고, 납품 단가가 원가가 됩니다.",
        },
        { name: "description", label: "설명", section: "basic", type: "textarea", maxLength: 200 },
      ]}
      toFormValues={(r) => ({ ...r, productMaterialNo: r.productMaterialNo ?? undefined })}
      itemName={(r) => r.menuName}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.menuNo}`, values);
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.menuNo}`);
        await reload();
      }}
    />
  );
};

export default MenuContainer;
