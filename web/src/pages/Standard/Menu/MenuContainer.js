import { useMemo } from "react";
import { Button, Form, InputNumber, Select } from "antd";
import { CoffeeOutlined, MinusCircleOutlined, PlusOutlined } from "@ant-design/icons";
import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { MENU_CATEGORIES, MENU_STATUS, costRateTone, formatUnitCost } from "../cafeOptions";
import { Dot, Meter, byNum, byOrder, byText, won } from "../../../components/CrudPage/cells";
import MenuDetail, { MENU_TONE } from "./MenuDetail";
import "./menu.css";

const URL = `${ERP_API}/menus`;

// 레시피 입력 + 입력 중 원가 미리보기
const RecipeEditor = ({ form, materials }) => {
  const recipe = Form.useWatch("recipe", form) || [];
  const price = Form.useWatch("price", form);
  const byNo = useMemo(() => Object.fromEntries(materials.map((m) => [m.materialNo, m])), [materials]);

  const lineCost = (line) => {
    const m = byNo[line?.materialNo];
    return m && line?.quantity ? m.unitCost * line.quantity : 0;
  };
  const total = recipe.reduce((sum, line) => sum + lineCost(line), 0);
  const rate = price > 0 ? (total / price) * 100 : 0;
  const used = recipe.map((l) => l?.materialNo).filter(Boolean);

  return (
    <section className="fm-section">
      <div className="fm-section-head">
        <h3>레시피</h3>
        <p>메뉴 1개(1잔)에 들어가는 식자재와 사용량입니다. 원가는 식자재 단가로 자동 계산됩니다.</p>
      </div>
      <Form.List name="recipe">
        {(items, { add, remove }) => (
          <>
            {items.length > 0 && (
              <div className="recipe-row recipe-head">
                <span>식자재</span>
                <span>사용량</span>
                <span className="recipe-cost">원가</span>
                <span />
              </div>
            )}
            {items.map(({ key, name }) => {
              const line = recipe[name];
              const m = byNo[line?.materialNo];
              return (
                <div key={key} className="recipe-row">
                  <Form.Item name={[name, "materialNo"]} rules={[{ required: true, message: "식자재를 고르세요." }]}>
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="식자재 선택"
                      options={materials.map((mat) => ({
                        value: mat.materialNo,
                        label: `${mat.materialName} (${formatUnitCost(mat.unitCost, mat.materialUnit)})`,
                        disabled: used.includes(mat.materialNo) && mat.materialNo !== line?.materialNo,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item name={[name, "quantity"]} rules={[{ required: true, message: "사용량" }]}>
                    <InputNumber min={0.1} step={1} style={{ width: "100%" }} placeholder="0" addonAfter={m?.materialUnit || "단위"} />
                  </Form.Item>
                  <span className="recipe-cost crud-num">{line?.materialNo && line?.quantity ? won(lineCost(line)) : "-"}</span>
                  <Button type="text" icon={<MinusCircleOutlined />} onClick={() => remove(name)} aria-label="줄 삭제" />
                </div>
              );
            })}
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})}>
              식자재 추가
            </Button>
          </>
        )}
      </Form.List>
      <div className="recipe-total">
        <div>
          <span>1개 원가</span>
          <strong>{won(total)}</strong>
        </div>
        <div>
          <span>원가율</span>
          <strong className={`is-${costRateTone(rate)}`}>{price > 0 ? `${rate.toFixed(1)}%` : "판매가 입력 필요"}</strong>
        </div>
        <div>
          <span>1개 이익</span>
          <strong>{price > 0 ? won(price - total) : "-"}</strong>
        </div>
      </div>
    </section>
  );
};

const RATE_TONE = { red: "neg", amber: "warn", green: "pos" };

const COLUMNS = [
  { title: "코드", dataIndex: "menuCode", sorter: byText((r) => r.menuCode), render: (v) => <span className="crud-code">{v}</span> },
  { title: "메뉴명", dataIndex: "menuName", sorter: byText((r) => r.menuName), render: (v) => <span className="crud-strong">{v}</span> },
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
  { title: "레시피", key: "recipe", align: "right", sorter: byNum((r) => r.recipe.length), render: (_, r) => <span className="crud-num">{r.recipe.length}종</span> },
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

  return (
    <CrudPage
      title="메뉴 · 레시피 관리"
      description="행을 누르면 레시피와 원가 구성이 보입니다. 원가는 식자재 단가로 자동 계산되고, 가격 · 판매 상태 · 레시피를 바꾸면 전 매장 POS 메뉴판에 바로 반영됩니다."
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
      quickFilters={[{ key: "highCost", label: "원가율 40% 이상", unit: "개", tone: "neg", test: (r) => Number(r.costRate) >= 40 }]}
      modalWidth={720}
      renderDetail={({ record, close, edit, DeleteButton }) => (
        <MenuDetail menu={record} onClose={close} edit={edit} DeleteButton={DeleteButton} />
      )}
      sections={[{ key: "basic", title: "메뉴 정보" }]}
      fields={[
        { name: "menuCode", label: "메뉴 코드", section: "basic", required: true, half: true, placeholder: "예: CF-AME", unique: true, maxLength: 30 },
        { name: "menuName", label: "메뉴명", section: "basic", required: true, half: true, placeholder: "예: 아메리카노", maxLength: 50 },
        { name: "category", label: "카테고리", section: "basic", type: "select", options: MENU_CATEGORIES, required: true, half: true },
        { name: "price", label: "판매가", section: "basic", type: "money", required: true, half: true },
        { name: "status", label: "판매 상태", section: "basic", type: "select", options: MENU_STATUS, required: true, half: true, initialValue: "판매중" },
        { name: "description", label: "설명", section: "basic", type: "textarea", maxLength: 200 },
      ]}
      extraFields={["recipe"]}
      renderFormExtra={({ form }) => <RecipeEditor form={form} materials={materials} />}
      toFormValues={(r) => ({
        ...r,
        recipe: r.recipe.map((l) => ({ materialNo: l.materialNo, quantity: l.quantity })),
      })}
      itemName={(r) => r.menuName}
      onCreate={async (values) => {
        await ERPapi.post(URL, { ...values, recipe: (values.recipe || []).filter((l) => l?.materialNo) });
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.menuNo}`, { ...values, recipe: (values.recipe || []).filter((l) => l?.materialNo) });
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
