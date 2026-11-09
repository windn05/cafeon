// 매장 상세: 표에서 행을 누르면 오른쪽에 열린다 (고정비 · 직원 · 본사 자산까지 한 번에)
import { useMemo } from "react";
import { Button, Drawer, Spin } from "antd";
import { EditOutlined, EnvironmentOutlined, PhoneOutlined, UserOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useList } from "../../../components/CrudPage/CrudPage";
import { Dot, won } from "../../../components/CrudPage/cells";
import { formatPhone } from "../../../components/CrudPage/formatters";
import { ERP_API } from "../../../config";
import "./store.css";

export const SHOPS = ["직영점", "가맹점"];
export const STORE_TONE = { 영업중: "ok", 휴점: "warn", 폐점: "off" };
export const fixedCost = (s) => (s.monthlyRent || 0) + (s.monthlyFixedCost || 0);

const StoreDetail = ({ store, onClose, edit, DeleteButton }) => {
  const users = useList(`${ERP_API}/users`);
  const equips = useList(`${ERP_API}/equipments`);
  const staff = useMemo(() => users.data.filter((u) => u.storeNo === store?.storeNo), [users.data, store]);
  const machines = useMemo(() => equips.data.filter((e) => e.storeNo === store?.storeNo), [equips.data, store]);
  if (!store) return null;

  return (
    <Drawer
      open
      onClose={onClose}
      width={480}
      title={
        <span className="st-drawer-title">
          {store.storeName}
          <span className="crud-code">{store.storeCode}</span>
        </span>
      }
      extra={
        <span className="st-drawer-actions">
          <Button icon={<EditOutlined />} onClick={() => edit(store)}>
            수정
          </Button>
          <DeleteButton record={store} />
        </span>
      }
    >
      <div className="st-detail">
        <dl className="st-dl">
          <div>
            <dt>유형</dt>
            <dd>{store.storeType}</dd>
          </div>
          <div>
            <dt>상태</dt>
            <dd>
              <Dot tone={STORE_TONE[store.status]}>{store.status}</Dot>
            </dd>
          </div>
          <div>
            <dt>오픈</dt>
            <dd>{store.openDate ? `${dayjs(store.openDate).format("YYYY.MM")} · ${dayjs().diff(dayjs(store.openDate), "year") + 1}년차` : "-"}</dd>
          </div>
        </dl>

        <section>
          <h4>위치 · 연락처</h4>
          <p className="st-line">
            <EnvironmentOutlined /> {store.address}
          </p>
          <p className="st-line">
            <UserOutlined /> {store.managerName || "점장 미지정"}
          </p>
          <p className="st-line">
            <PhoneOutlined /> {store.phone ? formatPhone(store.phone) : "연락처 미입력"}
          </p>
        </section>

        {SHOPS.includes(store.storeType) && (
          <section>
            <h4>월 고정비</h4>
            <div className="st-cost">
              <div>
                <span>임차료</span>
                <b>{won(store.monthlyRent)}</b>
              </div>
              <div>
                <span>기타 고정비</span>
                <b>{won(store.monthlyFixedCost)}</b>
              </div>
              <div className="is-total">
                <span>합계</span>
                <b>{won(fixedCost(store))}</b>
              </div>
            </div>
            {store.storeType === "가맹점" && <p className="st-note">가맹점 비용은 점주가 관리하므로 본사 손익에는 들어가지 않습니다.</p>}
          </section>
        )}

        <section>
          <h4>
            직원 <span className="st-count">{staff.length}명</span>
          </h4>
          {users.loading ? (
            <Spin size="small" />
          ) : staff.length === 0 ? (
            <p className="st-note">배정된 직원이 없습니다. [직원 · 부서 · 직급]에서 소속 매장을 지정하세요.</p>
          ) : (
            <ul className="st-list">
              {staff.map((u) => (
                <li key={u.userNo}>
                  <span>
                    <b>{u.userName}</b>
                    <small>{u.role?.roleName || "-"}</small>
                  </span>
                  <span className="st-list-meta">{u.employmentType || "-"}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <h4>
            본사 자산 <span className="st-count">{machines.length}개</span>
          </h4>
          {store.storeType === "가맹점" ? (
            <p className="st-note">가맹점 설비는 점주 소유라 본사 자산에 들어가지 않습니다.</p>
          ) : equips.loading ? (
            <Spin size="small" />
          ) : machines.length === 0 ? (
            <p className="st-note">등록된 자산이 없습니다.</p>
          ) : (
            <ul className="st-list">
              {machines.map((e) => (
                <li key={e.equipNo}>
                  <span>
                    <b>{e.equipName}</b>
                    <small>{e.equipCategory || "-"}</small>
                  </span>
                  <span className="st-list-meta">
                    <span className={`st-equip is-${e.typeLabel}`}>{e.typeLabel}</span>
                    <small>장부가 {won(e.bookValue)}</small>
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

export default StoreDetail;
