import CrudPage, { useList } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";

const URL = `${ERP_API}/roles`;

// users를 넘기면 직급별 인원도 보여 준다 (직원 관리 [직급 설정] 창)
const RoleContainer = ({ users }) => {
  const { data, loading, reload } = useList(URL);

  return (
    <CrudPage
      embedded
      title="직급"
      description="직원의 직급을 관리합니다. 직원이 있는 직급은 삭제할 수 없습니다."
      rowKey="roleNo"
      data={data}
      loading={loading}
      searchFields={[{ key: "roleName", label: "직급명" }]}
      columns={[
        { title: "직급명", dataIndex: "roleName", render: (v) => <span className="crud-strong">{v}</span> },
        ...(users
          ? [{ title: "인원", key: "count", align: "right", render: (_, r) => <span className="crud-num">{users.filter((u) => u.role?.roleNo === r.roleNo).length}명</span> }]
          : []),
      ]}
      fields={[{ name: "roleName", label: "직급명", required: true, placeholder: "예: 점장" }]}
      itemName={(r) => r.roleName}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.roleNo}`, { ...values, roleNo: record.roleNo });
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.roleNo}`);
        await reload();
      }}
    />
  );
};

export default RoleContainer;
