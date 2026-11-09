// 메뉴 상세: 표에서 행을 누르면 오른쪽에 열린다 (가격 · 원가 · 레시피 원가 구성)
import { Button, Drawer } from "antd";
import { EditOutlined } from "@ant-design/icons";
import { Dot, won } from "../../../components/CrudPage/cells";
import { costRateTone } from "../cafeOptions";

export const MENU_TONE = { 판매중: "ok", 일시품절: "warn", 단종: "off" };

const MenuDetail = ({ menu, onClose, edit, DeleteButton }) => {
  if (!menu) return null;
  const lines = [...menu.recipe].sort((a, b) => b.cost - a.cost);
  const top = lines[0]?.cost || 0;
  const rate = Number(menu.costRate) || 0;

  return (
    <Drawer
      open
      onClose={onClose}
      width={560}
      title={menu.menuName}
      extra={
        <span className="mb-detail-actions">
          <Button icon={<EditOutlined />} onClick={() => edit(menu)}>
            수정
          </Button>
          <DeleteButton record={menu} />
        </span>
      }
    >
      <div className="mb-detail">
        <p className="mb-detail-meta">
          {menu.category} · <span className="crud-code">{menu.menuCode}</span> · <Dot tone={MENU_TONE[menu.status]}>{menu.status}</Dot>
        </p>
        {menu.description && <p className="mb-desc">{menu.description}</p>}

        <div className="mb-figures">
          <div>
            <span>판매가</span>
            <b>{won(menu.price)}</b>
          </div>
          <div>
            <span>1개 원가</span>
            <b>{won(menu.cost)}</b>
          </div>
          <div className={`is-${costRateTone(rate)}`}>
            <span>원가율</span>
            <b>{rate.toFixed(1)}%</b>
            <small>적정선 30% 안팎</small>
          </div>
          <div>
            <span>1개 이익</span>
            <b>{won(menu.price - menu.cost)}</b>
          </div>
        </div>

        <section className="mb-recipe">
          <h4>
            레시피 · 원가 구성 <span>{lines.length}종</span>
          </h4>
          {lines.length === 0 ? (
            <p className="mb-note">레시피가 없어 원가를 계산할 수 없습니다. [수정]에서 식자재를 추가하세요.</p>
          ) : (
            <ul>
              {lines.map((l, i) => (
                <li key={l.recipeNo || l.materialCode}>
                  <span className="mb-ing">
                    <b>{l.materialName}</b>
                    <small>
                      {Number(l.quantity).toLocaleString()} {l.materialUnit}
                    </small>
                  </span>
                  <span className="mb-share">
                    <i className={i === 0 ? "is-top" : ""} style={{ width: `${top ? (l.cost / top) * 100 : 0}%` }} />
                  </span>
                  <span className="mb-cost">
                    {won(l.cost)}
                    <small>{menu.cost ? `${((l.cost / menu.cost) * 100).toFixed(0)}%` : "-"}</small>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </Drawer>
  );
};

export default MenuDetail;
