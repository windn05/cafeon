// 재고 관리에서 고른 품목으로 발주서 만들기: 공급업체별로 묶어 추천 수량을 미리 넣고, 확인하면 업체마다 발주서 한 장씩
import { useEffect, useState } from "react";
import { Alert, Empty, InputNumber, Modal, Segmented, Table, message } from "antd";
import dayjs from "dayjs";
import { Loading } from "../../../components/Dashboard/Dashboard";
import { errorText } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { num, useFetch, won } from "../../Ops/opsShared";
import "../../Weekly/weekly.css";

const day = (d) => dayjs(d).format("M/D (dd)");
const keyOf = (g, l) => `${g.clientNo}-${l.materialNo}`;

const OrderModal = ({ open, storeNo, materialNos, onClose, onOrdered }) => {
  const [cycle, setCycle] = useState(7);
  const [packs, setPacks] = useState({});
  const [saving, setSaving] = useState(false);
  const { data, loading } = useFetch(`${ERP_API}/purchase-orders/suggestion`, { storeNo, cycleDays: cycle }, open && !!storeNo);

  // 고른 품목만, 공급업체별로
  const picked = new Set(materialNos);
  const groups = (data || []).map((g) => ({ ...g, lines: g.lines.filter((l) => picked.has(l.materialNo)) })).filter((g) => g.lines.length);

  // 추천 수량으로 채운다 (추천이 0이어도 직접 골랐으면 1개부터)
  useEffect(() => {
    if (!data) return;
    const init = {};
    data.forEach((g) => g.lines.forEach((l) => (init[keyOf(g, l)] = Math.max(l.suggestedPacks, 1))));
    setPacks(init);
  }, [data]);

  const packsOf = (g, l) => packs[keyOf(g, l)] || 0;
  const orderable = groups.filter((g) => g.clientNo && g.lines.some((l) => packsOf(g, l) > 0));
  const total = orderable.reduce((s, g) => s + g.lines.reduce((t, l) => t + packsOf(g, l) * l.packPrice, 0), 0);

  const submit = async () => {
    setSaving(true);
    const made = [];
    try {
      for (const g of orderable) {
        const lines = g.lines.map((l) => ({ materialNo: l.materialNo, packs: packsOf(g, l) })).filter((l) => l.packs > 0);
        const res = await ERPapi.post(`${ERP_API}/purchase-orders`, { storeNo, clientNo: g.clientNo, lines, note: "재고 관리에서 발주" });
        made.push(`${res.data.poNumber} ${g.clientName}`);
      }
      message.success(`발주서 ${made.length}건을 만들었습니다. ${made.join(", ")}`);
      onOrdered();
    } catch (err) {
      message.error(errorText(err, "발주서를 만들지 못했습니다.") + (made.length ? ` (먼저 만든 발주서: ${made.join(", ")})` : ""));
      if (made.length) onOrdered();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      width={860}
      title="발주서 만들기"
      okText={orderable.length ? `발주서 ${orderable.length}건 만들기 · ${won(total)}` : "발주서 만들기"}
      okButtonProps={{ disabled: !orderable.length, loading: saving }}
      cancelText="닫기"
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div className="ops-toolbar" style={{ marginBottom: 12 }}>
        <span className="ops-note">다음 발주까지</span>
        <Segmented value={cycle} onChange={setCycle} options={[{ label: "3일", value: 3 }, { label: "4일", value: 4 }, { label: "1주", value: 7 }]} />
        <span className="ops-note">추천 수량 = 다음 발주까지 + 납품 소요일 + 안전 2일 동안 쓸 양 − 현재고 − 입고 대기</span>
      </div>
      {!data || loading ? (
        <Loading />
      ) : !groups.length ? (
        <Empty description="고른 품목 중 물류센터로 발주할 수 있는 품목이 없습니다." />
      ) : (
        <div className="po-groups">
          {groups.map((g) => {
            const sum = g.lines.reduce((s, l) => s + packsOf(g, l) * l.packPrice, 0);
            return (
              <section key={g.clientName} className="po-group">
                <header className="po-group-head">
                  <div>
                    <h3>{g.clientName}</h3>
                    <div className="meta">
                      납품 소요 {g.leadTimeDays}일 → {day(g.expectedDate)} 도착 예정
                    </div>
                  </div>
                  <strong className="crud-num">{won(sum)}</strong>
                </header>
                {!g.clientNo && <Alert type="warning" banner message="공급업체가 지정되지 않은 식자재입니다. 식자재 화면에서 공급업체를 지정하면 발주할 수 있습니다." />}
                <Table
                  size="small"
                  rowKey="materialNo"
                  pagination={false}
                  dataSource={g.lines}
                  columns={[
                    { title: "식자재", dataIndex: "materialName", render: (v) => <span className="crud-strong">{v}</span> },
                    { title: "구매 단위", dataIndex: "packLabel", render: (v) => <span className="crud-sub">{v}</span> },
                    { title: "현재고", dataIndex: "stock", align: "right", render: (v, l) => <span className="crud-num">{num(v, 0)} {l.unit}</span> },
                    { title: "남은 일수", dataIndex: "coverDays", align: "right", render: (v) => <span className="crud-num">{v === null || v === undefined ? "-" : `${num(v)}일`}</span> },
                    { title: "추천", dataIndex: "suggestedPacks", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
                    {
                      title: "발주 수량",
                      key: "packs",
                      width: 130,
                      render: (_, l) => (
                        <InputNumber
                          size="small"
                          min={0}
                          value={packsOf(g, l)}
                          onChange={(v) => setPacks((x) => ({ ...x, [keyOf(g, l)]: v ?? 0 }))}
                          addonAfter="개"
                          disabled={!g.clientNo}
                          style={{ width: "100%" }}
                        />
                      ),
                    },
                    { title: "단가", dataIndex: "packPrice", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
                    { title: "금액", key: "amount", align: "right", render: (_, l) => <span className="crud-num">{won(packsOf(g, l) * l.packPrice)}</span> },
                  ]}
                />
              </section>
            );
          })}
        </div>
      )}
    </Modal>
  );
};

export default OrderModal;
