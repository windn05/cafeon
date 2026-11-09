import { ShopOutlined } from "@ant-design/icons";
import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { companyFields, companySearchFields, companySections } from "../../../components/CrudPage/companyFields";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import { CLIENT_TYPES } from "../cafeOptions";
import { useMemo } from "react";
import { byNum, byText } from "../../../components/CrudPage/cells";
import { formatBrn, formatPhone } from "../../../components/CrudPage/formatters";

const URL = `${ERP_API}/clients`;

const none = <span className="crud-sub">-</span>;
const lead = (d) => (d === null || d === undefined ? none : d === 0 ? "당일" : `${d}일`);
const SHOW_ITEMS = 3;

const ClientContainer = () => {
  const { data, loading, reload } = useList(URL);
  const { data: materials } = useList(`${ERP_API}/materials`);

  const columns = useMemo(
    () => [
      { title: "업체명", dataIndex: "clientName", sorter: byText((r) => r.clientName), render: (v) => <span className="crud-strong">{v}</span> },
      { title: "업체 유형", dataIndex: "typeLabel", filter: true, filterOptions: CLIENT_TYPES },
      {
        title: "납품 소요일",
        dataIndex: "leadTimeDays",
        align: "right",
        sorter: byNum((r) => r.leadTimeDays ?? 99),
        defaultSortOrder: "ascend",
        render: (v) => <span className="crud-num">{lead(v)}</span>,
      },
      { title: "담당자", dataIndex: "clientManager", render: (v) => v || none },
      { title: "연락처", dataIndex: "clientPhone", render: (v) => (v ? <span className="crud-num">{formatPhone(v)}</span> : none) },
      {
        title: "공급 식자재",
        key: "items",
        className: "is-wrap",
        sorter: byNum((r) => materials.filter((m) => m.clientNo === r.clientNo).length),
        render: (_, r) => {
          const items = materials.filter((m) => m.clientNo === r.clientNo);
          if (!items.length) return <span className="crud-sub">없음</span>;
          const names = items.slice(0, SHOW_ITEMS).map((m) => m.materialName).join(", ");
          return (
            <span>
              <span className="crud-num">{items.length}종</span> · {names}
              {items.length > SHOW_ITEMS ? ` 외 ${items.length - SHOW_ITEMS}` : ""}
            </span>
          );
        },
      },
      { title: "대표", dataIndex: "clientCeo", render: (v) => v || none },
      { title: "사업자등록번호", dataIndex: "clientBrn", render: (v) => (v ? <span className="crud-num">{formatBrn(v)}</span> : none) },
    ],
    [materials]
  );

  return (
    <CrudPage
      title="공급업체 관리"
      description="식자재를 납품하는 업체입니다. 납품이 빠른 업체부터 보여 주며, 납품 소요일은 주간 발주 계획에 쓰입니다."
      entity="공급업체"
      icon={<ShopOutlined />}
      rowKey="clientNo"
      data={data}
      loading={loading}
      searchFields={companySearchFields("client", "업체명")}
      columns={columns}
      sections={companySections}
      fields={[
        {
          name: "clientName",
          label: "업체명",
          section: "basic",
          required: true,
          half: true,
          maxLength: 50,
          placeholder: "예: 한빛로스터리(주)",
        },
        {
          name: "type",
          label: "업체 유형",
          section: "basic",
          type: "select",
          options: CLIENT_TYPES,
          required: true,
          half: true,
          tooltip: "주로 납품하는 품목 기준으로 고르세요.",
        },
        {
          name: "leadTimeDays",
          label: "납품 소요일",
          section: "basic",
          type: "number",
          unit: "일",
          half: true,
          help: "발주한 날부터 매장에 도착하기까지 걸리는 날 수 (당일 배송은 0)",
        },
        ...companyFields("client"),
      ]}
      toFormValues={(r) => ({ ...r, type: r.typeLabel })}
      itemName={(r) => r.clientName}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.clientNo}`, { ...values, clientNo: record.clientNo });
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.clientNo}`);
        await reload();
      }}
    />
  );
};

export default ClientContainer;
