// 메뉴 상세 패널: 표에서 행을 누르면 오른쪽에 열린다.
// 원가 · 원가율 · 이익을 보면서 레시피(매장 제조) 또는 납품 품목(완제품)을 바로 고친다
import { useEffect, useMemo, useState } from "react";
import { Button, Drawer, Empty, InputNumber, Modal, Select, Table, message } from "antd";
import { EditOutlined, MinusCircleOutlined, PlusOutlined } from "@ant-design/icons";
import { Dot, won } from "../../../components/CrudPage/cells";
import { errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { MATERIAL_TYPES, costRateTone, formatUnitCost, isFinished } from "../cafeOptions";

export const MENU_TONE = { 판매중: "ok", 일시품절: "warn", 단종: "off" };
// 카페 업계에서 식자재 원가율 30% 안팎을 적정선으로 본다
const TARGET_RATE = 0.3;
const RATE_CLASS = { red: "is-red", amber: "is-amber", green: "is-green" };

const toLines = (menu) => (menu && !isFinished(menu) ? menu.recipe.map((l) => ({ key: `m${l.materialNo}`, materialNo: l.materialNo, quantity: l.quantity })) : []);
const sameLines = (a, b) =>
  a.length === b.length && a.every((l) => b.some((x) => x.materialNo === l.materialNo && Number(x.quantity) === Number(l.quantity)));
// 원가율 30%가 되는 판매가 (100원 단위 올림)
const targetPrice = (cost) => Math.ceil(cost / TARGET_RATE / 100) * 100;

// 원가 · 원가율 · 이익: 고치는 중인 값으로 바로 다시 계산
const Figures = ({ menu, cost, dirty }) => {
  const rate = menu.price > 0 ? (cost / menu.price) * 100 : 0;
  return (
    <div className="mb-figures">
      <div>
        <span>판매가</span>
        <b>{won(menu.price)}</b>
      </div>
      <div>
        <span>1개 원가</span>
        <b>{won(cost)}</b>
        {dirty && <small>저장된 값 {won(menu.cost)}</small>}
      </div>
      <div className={cost ? RATE_CLASS[costRateTone(rate)] : ""}>
        <span>원가율</span>
        <b>{cost && menu.price > 0 ? `${rate.toFixed(1)}%` : "-"}</b>
        <small>{cost ? `30%가 되는 판매가 ${won(targetPrice(cost))}` : "적정선 30% 안팎"}</small>
      </div>
      <div>
        <span>1개 이익</span>
        <b>{won(menu.price - cost)}</b>
      </div>
    </div>
  );
};

// 매장 제조: 레시피 편집
const RecipeLines = ({ lines, setLines, byNo, materials }) => {
  const used = lines.map((l) => l.materialNo).filter(Boolean);
  const costOf = (l) => (byNo[l.materialNo] && l.quantity ? byNo[l.materialNo].unitCost * l.quantity : 0);
  const total = lines.reduce((s, l) => s + costOf(l), 0);
  const top = Math.max(0, ...lines.map(costOf));
  const set = (key, patch) => setLines(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  // 식자재 고르기: 분류별로 묶고, 이미 넣은 것은 막는다 (완제품 품목은 레시피 재료가 아니므로 뺀다)
  const options = MATERIAL_TYPES.filter((t) => t !== "베이커리·디저트")
    .map((t) => ({
      label: t,
      options: materials
        .filter((m) => m.materialType === t)
        .map((m) => ({ value: m.materialNo, label: m.materialName, desc: formatUnitCost(m.unitCost, m.materialUnit), disabled: used.includes(m.materialNo) })),
    }))
    .filter((g) => g.options.length);

  return (
    <>
      <Table
        size="small"
        bordered
        rowKey="key"
        pagination={false}
        dataSource={lines}
        className="mb-recipe-table"
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="아직 레시피가 없습니다. 아래에서 식자재를 추가하세요." /> }}
        columns={[
          {
            title: "식자재",
            key: "material",
            render: (_, l) => (
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="식자재 선택"
                value={l.materialNo}
                onChange={(v) => set(l.key, { materialNo: v })}
                options={options}
                optionRender={(o) => (
                  <span className="mb-opt">
                    {o.label} <small>{o.data.desc}</small>
                  </span>
                )}
                style={{ width: "100%" }}
                status={l.materialNo ? undefined : "warning"}
              />
            ),
          },
          {
            title: "1개 사용량",
            key: "quantity",
            width: 140,
            render: (_, l) => (
              <InputNumber
                min={0.1}
                step={1}
                value={l.quantity}
                onChange={(v) => set(l.key, { quantity: v })}
                addonAfter={byNo[l.materialNo]?.materialUnit || "단위"}
                status={l.quantity ? undefined : "warning"}
                style={{ width: "100%" }}
              />
            ),
          },
          {
            title: "원가",
            key: "cost",
            width: 112,
            align: "right",
            render: (_, l) => {
              const c = costOf(l);
              return (
                <span className="mb-line-cost">
                  <span className="crud-num">{l.materialNo && l.quantity ? won(c) : "-"}</span>
                  <span className="mb-share">
                    <i style={{ width: `${top ? (c / top) * 100 : 0}%` }} />
                    <small>{total ? `${Math.round((c / total) * 100)}%` : ""}</small>
                  </span>
                </span>
              );
            },
          },
          {
            title: "",
            key: "remove",
            width: 40,
            align: "center",
            render: (_, l) => <Button type="text" size="small" icon={<MinusCircleOutlined />} aria-label="줄 삭제" onClick={() => setLines(lines.filter((x) => x.key !== l.key))} />,
          },
        ]}
      />
      <Button type="dashed" block icon={<PlusOutlined />} className="mb-add" onClick={() => setLines([...lines, { key: `n${Date.now()}`, materialNo: undefined, quantity: null }])}>
        식자재 추가
      </Button>
    </>
  );
};

const MenuDetail = ({ menu, materials, onClose, edit, DeleteButton, onSaved }) => {
  const byNo = useMemo(() => Object.fromEntries(materials.map((m) => [m.materialNo, m])), [materials]);
  const finished = isFinished(menu);
  const saved = useMemo(() => toLines(menu), [menu]);
  const [lines, setLines] = useState(saved);
  const [product, setProduct] = useState(menu?.productMaterialNo);
  const [saving, setSaving] = useState(false);
  useEffect(() => setLines(saved), [saved]);
  useEffect(() => setProduct(menu?.productMaterialNo), [menu?.productMaterialNo]);

  if (!menu) return null;
  const dirty = finished ? product !== menu.productMaterialNo : !sameLines(lines, saved);
  const cost = finished
    ? byNo[product]?.unitCost || 0
    : lines.reduce((s, l) => s + (byNo[l.materialNo] && l.quantity ? byNo[l.materialNo].unitCost * l.quantity : 0), 0);
  const products = materials
    .filter((m) => m.materialUnit === "EA")
    .sort((a, b) => (b.materialType === "베이커리·디저트") - (a.materialType === "베이커리·디저트") || a.materialName.localeCompare(b.materialName))
    .map((m) => ({ value: m.materialNo, label: `${m.materialName} (${formatUnitCost(m.unitCost, m.materialUnit)})` }));

  const reset = () => (finished ? setProduct(menu.productMaterialNo) : setLines(saved));
  const close = () => {
    if (!dirty) return onClose();
    Modal.confirm({ title: "저장하지 않은 변경이 있습니다", content: "닫으면 지금 바꾼 내용은 사라집니다.", okText: "버리고 닫기", cancelText: "계속 편집", onOk: onClose });
  };
  const save = async () => {
    if (!finished && lines.some((l) => !l.materialNo || !l.quantity)) {
      message.warning("식자재와 사용량을 모두 채우거나 빈 줄을 지워 주세요.");
      return;
    }
    setSaving(true);
    try {
      if (finished) {
        const { menuCode, menuName, category, price, status, description, productType } = menu;
        await ERPapi.put(`${ERP_API}/menus/${menu.menuNo}`, { menuCode, menuName, category, price, status, description, productType, productMaterialNo: product });
        message.success(`${menu.menuName} 납품 품목을 바꿨습니다.`);
      } else {
        await ERPapi.put(
          `${ERP_API}/menus/${menu.menuNo}/recipe`,
          lines.map((l) => ({ materialNo: l.materialNo, quantity: l.quantity }))
        );
        message.success(`${menu.menuName} 레시피를 저장했습니다. 매장 POS에도 반영됩니다.`);
      }
      await onSaved();
    } catch (err) {
      message.error(errorText(err, "저장하지 못했습니다."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open
      onClose={close}
      width={640}
      title={menu.menuName}
      extra={
        <span className="mb-detail-actions">
          <Button icon={<EditOutlined />} onClick={() => (dirty ? message.warning("레시피를 먼저 저장하거나 되돌려 주세요.") : edit(menu))}>
            메뉴 정보
          </Button>
          <DeleteButton record={menu} />
        </span>
      }
    >
      <div className="mb-detail">
        <div>
          <p className="mb-detail-meta">
            {menu.category} · <span className="crud-code">{menu.menuCode}</span> · <Dot tone={MENU_TONE[menu.status]}>{menu.status}</Dot> ·{" "}
            {finished ? <span className="crud-tag is-gray">완제품</span> : "매장 제조"}
          </p>
          {menu.description && <p className="mb-desc">{menu.description}</p>}
        </div>

        <Figures menu={menu} cost={cost} dirty={dirty} />

        <section className="mb-recipe">
          <header className="mb-recipe-head">
            <h4>{finished ? "납품 품목" : "레시피 · 1개 기준"}</h4>
            {dirty && <span className="crud-tag is-warn">저장 안 됨</span>}
            <span className="mb-recipe-actions">
              <Button size="small" disabled={!dirty || saving} onClick={reset}>
                되돌리기
              </Button>
              <Button size="small" type="primary" disabled={!dirty} loading={saving} onClick={save}>
                {finished ? "저장" : "레시피 저장"}
              </Button>
            </span>
          </header>
          {finished ? (
            <>
              <Select showSearch optionFilterProp="label" value={product} onChange={setProduct} options={products} placeholder="납품 품목 선택" style={{ width: "100%" }} />
              <p className="mb-note">매장 제조 없이 납품받은 그대로 판매하므로 레시피가 없습니다. 1개 팔면 이 품목 재고 1개가 빠지고, 납품 단가가 원가가 됩니다.</p>
            </>
          ) : (
            <>
              <RecipeLines lines={lines} setLines={setLines} byNo={byNo} materials={materials} />
              <p className="mb-note">원가는 식자재 구매 단가로 자동 계산됩니다. 저장하면 매장 POS 레시피에 바로 반영되고, 다음 일 마감부터 바뀐 사용량으로 재고가 차감됩니다.</p>
            </>
          )}
        </section>
      </div>
    </Drawer>
  );
};

export default MenuDetail;
