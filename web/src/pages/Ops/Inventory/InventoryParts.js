// 재고 화면 공용 부품: 현재고 표 · 재고 변동(입출고) 내역. 매장 재고 · 로스 현황과 물류센터 재고가 같이 쓴다
import { useState } from "react";
import { Button, DatePicker, Select, Table } from "antd";
import dayjs from "dayjs";
import { Badge } from "../../../components/CrudPage/CrudPage";
import { ERP_API } from "../../../config";
import { num, useFetch, won } from "../opsShared";

export const STATUS_TONE = { 부족: "red", 주의: "amber", 정상: "green", 미사용: "gray", 없음: "gray" };
const TYPE_TONE = { 입고: "blue", 기초재고: "blue", 출고: "gray", 판매소진: "gray", 폐기: "red", 실사조정: "amber" };
const { RangePicker } = DatePicker;

export const Qty = ({ v, unit }) => (
  <span className="crud-num">
    {v > 0 ? "+" : ""}
    {num(v)} {unit}
  </span>
);

// 열 값으로 거르기 목록 (표에 있는 값만)
const filtersOf = (rows, key) => [...new Set(rows.map((r) => r[key]).filter(Boolean))].map((v) => ({ text: v, value: v }));

// 현재고 표: 현재고 · 일평균 · (물류센터는 적정 재고 · 과부족) · 남은 일수. 바닥나기 가까운 식자재부터
// usage: "사용"(매장) · "출고"(물류센터). 물류센터 행에는 서버가 targetQty · gapQty · gapPacks를 붙인다
const DAYS_TONE = { 부족: "is-neg", 주의: "is-warn" };
export const StockTable = ({ rows, loading, usage = "사용", onAdjust, rowSelection, toolbar }) => {
  const withTarget = rows.some((r) => r.targetQty !== undefined);
  return (
    <>
      <div className="ops-toolbar" style={{ marginBottom: 10 }}>
        {toolbar}
        <span className="ops-note">
          일평균 = 최근 14일 하루 평균 {usage}량 · 남은 일수 = 현재고 ÷ 일평균 (2일 미만 빨강, 4일 미만 노랑)
          {withTarget ? " · 적정 재고 = 일평균 × (정기 보충 4일 + 안전 2일) · 과부족 = 현재고 + 입고 대기 − 적정 재고" : ""}
        </span>
      </div>
      <Table
        size="small"
        rowKey="materialNo"
        rowSelection={rowSelection}
        loading={loading}
        dataSource={rows}
        scroll={{ x: "max-content" }}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        columns={[
          {
            title: "식자재",
            dataIndex: "materialName",
            render: (v, r) => (
              <span className="stock-name">
                <b>{v}</b>
                <small>{r.packLabel}</small>
              </span>
            ),
          },
          { title: "분류", dataIndex: "materialType", filters: filtersOf(rows, "materialType"), onFilter: (v, r) => r.materialType === v },
          {
            title: "현재고",
            dataIndex: "quantity",
            align: "right",
            sorter: (a, b) => a.quantity - b.quantity,
            render: (v, r) => (
              <span className="crud-num">
                {num(v)} {r.unit}
              </span>
            ),
          },
          {
            title: `일평균 ${usage}`,
            dataIndex: "avgDaily",
            align: "right",
            render: (v, r) => <span className="crud-num">{v ? `${num(v)} ${r.unit}` : <span className="crud-sub">-</span>}</span>,
          },
          ...(withTarget
            ? [
                {
                  title: "적정 재고",
                  dataIndex: "targetQty",
                  align: "right",
                  render: (v, r) => <span className="crud-num">{v === undefined ? <span className="crud-sub">-</span> : `${num(v, 0)} ${r.unit}`}</span>,
                },
                {
                  title: "과부족",
                  dataIndex: "gapQty",
                  align: "right",
                  sorter: (a, b) => (a.gapQty ?? 0) - (b.gapQty ?? 0),
                  render: (v, r) =>
                    v === undefined ? (
                      <span className="crud-sub">-</span>
                    ) : v < 0 ? (
                      <span className="stock-gap is-neg">
                        <span className="crud-num">
                          −{num(-v, 0)} {r.unit}
                        </span>
                        <small>{r.gapPacks}개 모자람</small>
                      </span>
                    ) : (
                      <span className="crud-num stock-gap">
                        +{num(v, 0)} {r.unit}
                      </span>
                    ),
                },
              ]
            : []),
          {
            title: "남은 일수",
            dataIndex: "coverDays",
            align: "right",
            sorter: (a, b) => (a.coverDays ?? 999) - (b.coverDays ?? 999),
            defaultSortOrder: "ascend", // 바닥나기 가까운 식자재부터
            filters: Object.keys(STATUS_TONE).map((s) => ({ text: s, value: s })),
            onFilter: (v, r) => r.status === v,
            render: (v, r) =>
              v === null || v === undefined ? (
                <span className="crud-sub">{r.status}</span>
              ) : (
                <b className={`crud-num stock-days ${DAYS_TONE[r.status] || ""}`}>{num(v)}일</b>
              ),
          },
          {
            title: "입고 대기",
            dataIndex: "onOrder",
            align: "right",
            render: (v, r) => <span className="crud-num">{v ? `+${num(v, 0)} ${r.unit}` : <span className="crud-sub">-</span>}</span>,
          },
          { title: "재고 금액", dataIndex: "value", align: "right", sorter: (a, b) => a.value - b.value, render: (v) => <span className="crud-num">{won(v)}</span> },
          ...(onAdjust
            ? [
                {
                  title: "",
                  key: "adjust",
                  align: "right",
                  render: (_, r) => (
                    <Button size="small" onClick={() => onAdjust(r)}>
                      수량 조정
                    </Button>
                  ),
                },
              ]
            : []),
        ]}
      />
    </>
  );
};

// 실사 · 조정 기록: 펼치면 식자재별 장부 · 실사 · 차이
export const StocktakeTable = ({ data, loading, emptyText = "실사 기록이 없습니다." }) => (
  <Table
    size="small"
    rowKey="stocktakeNo"
    loading={loading}
    dataSource={data || []}
    pagination={{ pageSize: 10, hideOnSinglePage: true }}
    locale={{ emptyText }}
    expandable={{
      expandedRowRender: (st) => (
        <Table
          size="small"
          rowKey="materialNo"
          pagination={false}
          dataSource={[...st.lines].sort((a, b) => a.diffAmount - b.diffAmount)}
          columns={[
            { title: "식자재", dataIndex: "materialName" },
            { title: "장부", dataIndex: "expectedQty", align: "right", render: (v, r) => <span className="crud-num">{num(v)} {r.unit}</span> },
            { title: "실사", dataIndex: "countedQty", align: "right", render: (v, r) => <span className="crud-num">{num(v)} {r.unit}</span> },
            {
              title: "차이",
              dataIndex: "diffQty",
              align: "right",
              render: (v, r) => (
                <span className={`crud-num count-row-diff ${v < 0 ? "is-minus" : v > 0 ? "is-plus" : ""}`}>
                  {v ? <Qty v={v} unit={r.unit} /> : "일치"}
                </span>
              ),
            },
            { title: "금액", dataIndex: "diffAmount", align: "right", render: (v) => <span className="crud-num">{v ? won(v) : "-"}</span> },
          ]}
        />
      ),
    }}
    columns={[
      { title: "실사일", dataIndex: "countDate", render: (v) => <span className="crud-num">{v}</span> },
      { title: "품목", dataIndex: "lineCount", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
      { title: "차이 품목", dataIndex: "diffCount", align: "right", render: (v) => <span className="crud-num">{v}개</span> },
      {
        title: "차이 금액",
        dataIndex: "diffAmount",
        align: "right",
        render: (v) => <span className={`crud-num count-row-diff ${v < 0 ? "is-minus" : v > 0 ? "is-plus" : ""}`}>{won(v)}</span>,
      },
      { title: "메모", dataIndex: "note", render: (v) => <span className="crud-sub">{v || ""}</span> },
      { title: "담당", dataIndex: "createdBy" },
    ]}
  />
);

// 재고 변동 내역: 기간 · 종류 · 식자재로 걸러 본다
export const TxnPanel = ({ storeNo, materials, types }) => {
  const [range, setRange] = useState([dayjs().subtract(13, "day"), dayjs()]);
  const [txnType, setTxnType] = useState(null);
  const [txnMaterial, setTxnMaterial] = useState(null);
  const txns = useFetch(
    `${ERP_API}/inventory/txns`,
    { storeNo, from: range[0].format("YYYY-MM-DD"), to: range[1].format("YYYY-MM-DD"), type: txnType || undefined, materialNo: txnMaterial || undefined },
    !!storeNo
  );
  return (
    <>
      <div className="ops-toolbar" style={{ marginBottom: 10 }}>
        <RangePicker value={range} allowClear={false} onChange={(v) => v && setRange(v)} disabledDate={(d) => d.isAfter(dayjs(), "day")} />
        <Select allowClear placeholder="종류 전체" style={{ width: 130 }} value={txnType} onChange={setTxnType} options={types.map((t) => ({ value: t, label: t }))} />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="식자재 전체"
          style={{ width: 200 }}
          value={txnMaterial}
          onChange={setTxnMaterial}
          options={materials.map((s) => ({ value: s.materialNo, label: s.materialName }))}
        />
      </div>
      <Table
        size="small"
        rowKey="txnNo"
        loading={txns.loading}
        dataSource={txns.data || []}
        pagination={{ pageSize: 15, showSizeChanger: false }}
        columns={[
          { title: "날짜", dataIndex: "txnDate", render: (v) => <span className="crud-num">{v}</span> },
          { title: "종류", dataIndex: "txnType", render: (v) => <Badge value={v} tones={TYPE_TONE} /> },
          { title: "식자재", dataIndex: "materialName" },
          { title: "수량", dataIndex: "quantity", align: "right", render: (v, r) => <Qty v={v} unit={r.unit} /> },
          { title: "금액", dataIndex: "amount", align: "right", render: (v) => <span className="crud-num">{won(v)}</span> },
          { title: "사유 · 메모", key: "why", render: (_, r) => <span className="crud-sub">{[r.reason, r.note].filter(Boolean).join(" · ")}</span> },
        ]}
      />
    </>
  );
};
