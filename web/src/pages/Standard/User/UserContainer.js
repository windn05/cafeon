import CrudPage from "../../../components/CrudPage/CrudPage";
import { EMPLOYMENT_TYPES } from "../cafeOptions";
import { formatPhone, onlyDigits } from "../../../components/CrudPage/formatters";
import { byText } from "../../../components/CrudPage/cells";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";

const URL = `${ERP_API}/users`;
const EMP_TONE = { 정규직: "is-info", 계약직: "is-gray", 파트타임: "is-warn" };
const where = (u) => (u.storeNo ? u.storeName : `본사 · ${u.department?.departmentName || "부서 미지정"}`);

const COLUMNS = [
  { title: "이름", dataIndex: "userName", sorter: byText((r) => r.userName), render: (v) => <span className="crud-strong">{v}</span> },
  { title: "아이디", dataIndex: "userId", render: (v) => <span className="crud-code">{v}</span> },
  { title: "소속", key: "where", filter: true, filterValue: where, sorter: byText(where), render: (_, r) => where(r) },
  { title: "직급", key: "role", filter: true, filterValue: (r) => r.role?.roleName || "직급 없음", render: (_, r) => r.role?.roleName || <span className="crud-sub">-</span> },
  { title: "업무", dataIndex: "userWork" },
  {
    title: "고용 형태",
    dataIndex: "employmentType",
    filter: true,
    filterOptions: EMPLOYMENT_TYPES,
    render: (v) => (v ? <span className={`crud-tag ${EMP_TONE[v] || "is-gray"}`}>{v}</span> : <span className="crud-sub">-</span>),
  },
  {
    title: "연락처",
    dataIndex: "userPhone",
    render: (v) => (v && v !== "-" ? <span className="crud-num">{formatPhone(v)}</span> : <span className="crud-sub">-</span>),
  },
];

const PHONE_RULE ={ pattern: /^[0-9-]{9,14}$/, message: "숫자와 - 만 입력하세요. 예: 010-1234-5678" };

// 직원 관리 화면이 조직도에서 고른 소속의 직원만 넘겨준다.
// users: { data, loading, reload } (고른 소속으로 거른 목록), initial: 새 직원 등록 때 미리 채울 소속
const UserContainer = ({ users, departments, roles, stores, title = "직원", description, initial = {}, onChanged }) => {
  const { data, loading } = users;
  const reload = async () => {
    await users.reload();
    if (onChanged) onChanged();
  };

  return (
    <CrudPage
      embedded
      title={title}
      entity="직원"
      description={description}
      rowKey="userNo"
      data={data}
      loading={loading}
      searchFields={[
        { key: "userName", label: "이름" },
        { key: "userId", label: "아이디" },
        { key: "userWork", label: "업무" },
        { key: "userPhone", label: "연락처", normalize: onlyDigits },
      ]}
      columns={COLUMNS}
      fields={[
        {
          name: "userId",
          label: "아이디",
          required: true,
          half: true,
          disabledOnEdit: true,
          unique: true,
          uniqueMessage: "이미 사용 중인 아이디입니다.",
          maxLength: 30,
          editHelp: "아이디는 바꿀 수 없습니다.",
        },
        {
          name: "userPw",
          label: "비밀번호",
          type: "password",
          required: true,
          optionalOnEdit: true,
          half: true,
          rules: [{ min: 4, message: "4자 이상 입력하세요." }],
          editHelp: "바꿀 때만 입력하세요. 비워 두면 기존 비밀번호가 유지됩니다.",
        },
        { name: "userName", label: "이름", required: true, half: true },
        { name: "userPhone", label: "연락처", type: "phone", required: true, half: true, placeholder: "숫자만 입력 (하이픈 자동)", rules: [PHONE_RULE] },
        {
          name: "storeNo",
          label: "소속",
          type: "select",
          half: true,
          placeholder: "본사 (비워 두기)",
          help: "매장 · 센트럴키친 직원은 소속만 고르면 됩니다. 비워 두면 본사 직원이 되어 부서를 고릅니다.",
          options: stores.map((s) => ({ value: s.storeNo, label: `${s.storeName} (${s.storeType})` })),
          initialValue: initial.storeNo,
        },
        {
          name: "departmentNo",
          label: "본사 부서",
          type: "select",
          required: true,
          showWhen: (v) => !v.storeNo,
          half: true,
          options: departments.map((d) => ({ value: d.departmentNo, label: d.departmentName })),
          initialValue: initial.departmentNo,
        },
        {
          name: "roleNo",
          label: "직급",
          type: "select",
          required: true,
          half: true,
          options: roles.map((r) => ({ value: r.roleNo, label: r.roleName })),
        },
        { name: "userWork", label: "업무", required: true, half: true, placeholder: "예: 바리스타, 매장 운영" },
        {
          name: "employmentType",
          label: "고용 형태",
          type: "select",
          options: EMPLOYMENT_TYPES,
          required: true,
          half: true,
          initialValue: "정규직",
        },
        {
          name: "userSalary",
          label: "급여",
          type: "money",
          required: true,
          half: true,
          help: "정규직·계약직은 월급, 파트타임은 시급을 입력합니다.",
        },
      ]}
      toFormValues={(r) => ({
        ...r,
        userPw: "",
        departmentNo: r.department?.departmentNo,
        roleNo: r.role?.roleNo,
      })}
      itemName={(r) => `${r.userName}(${r.userId})`}
      modalWidth={620}
      onCreate={async (values) => {
        await ERPapi.post(URL, values);
        await reload();
      }}
      onUpdate={async (record, values) => {
        await ERPapi.put(`${URL}/${record.userNo}`, {
          ...values,
          userNo: record.userNo,
          userId: record.userId,
        });
        await reload();
      }}
      onDelete={async (record) => {
        await ERPapi.delete(`${URL}/${record.userNo}`);
        await reload();
      }}
    />
  );
};

export default UserContainer;
