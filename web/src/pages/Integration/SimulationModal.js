// 가상 운영 데이터 다시 만들기 (매장 시스템들이 보낸 것처럼 판매 · 마감 · 발주 요청 · 입고 · 근무 기록을 만든다)
import { useState } from "react";
import { Alert, InputNumber, Modal, message } from "antd";
import { ERPapi } from "../../components/api/api";
import { errorText } from "../../components/CrudPage/CrudPage";
import { ERP_API } from "../../config";

const SimulationModal = ({ open, onClose, onDone }) => {
  const [days, setDays] = useState(70);
  const [running, setRunning] = useState(false);
  const run = async () => {
    setRunning(true);
    try {
      const res = await ERPapi.post(`${ERP_API}/simulation/regenerate`, null, { params: { days }, timeout: 300000 });
      const r = res.data;
      message.success(`${r.from} ~ ${r.to} 영수증 ${r.receipts.toLocaleString()}장 · 마감 ${r.closings}건 · 발주서 ${r.purchaseOrders}건을 만들었습니다. (${r.seconds}초)`);
      onDone?.();
      onClose();
    } catch (err) {
      message.error(errorText(err, "가상 데이터를 만들지 못했습니다."));
    } finally {
      setRunning(false);
    }
  };
  return (
    <Modal
      open={open}
      onCancel={() => !running && onClose()}
      title="가상 운영 데이터 만들기"
      okText="지우고 새로 만들기"
      okButtonProps={{ danger: true, loading: running }}
      cancelButtonProps={{ disabled: running }}
      onOk={run}
    >
      <Alert
        type="warning"
        showIcon
        message="지금까지 받은 판매 · 마감 · 재고 · 실사 · 발주 · 근무 · 수집 기록이 모두 지워집니다."
        description="분석·보고 기능을 시험하기 위해, 매장 시스템들이 매일 데이터를 보낸 것처럼 기록을 만듭니다. 요일·시간대·계절 패턴이 있는 판매, 어제까지의 마감·폐기·주간 실사, 월·목 발주 요청과 입고, 근무표가 생기고, 오늘은 지금 시각까지만 판매가 들어온 상태로 남습니다."
      />
      <div style={{ marginTop: 16 }}>
        <div className="ops-note">며칠 치를 만들까요?</div>
        <InputNumber min={7} max={120} value={days} onChange={(v) => setDays(v || 70)} addonAfter="일" style={{ width: 160 }} />
      </div>
    </Modal>
  );
};

export default SimulationModal;
