// 직원 관리: 왼쪽 조직도(본사 부서 · 매장)에서 소속을 고르면 오른쪽에 그 소속 직원 카드.
// 부서는 조직도에서 바로 고치고, 직급은 자주 바꾸지 않으니 [직급 설정] 창에서 관리한다.
import { useMemo, useState } from "react";
import { Modal } from "antd";
import { useList } from "../../../components/CrudPage/CrudPage";
import { ERPapi } from "../../../components/api/api";
import { ERP_API } from "../../../config";
import User from "../User";
import Role from "../Role";
import OrgPanel from "./OrgPanel";

const DEPT_URL = `${ERP_API}/departments`;

const TabPresenter = () => {
  const users = useList(`${ERP_API}/users`);
  const departments = useList(DEPT_URL);
  const roles = useList(`${ERP_API}/roles`);
  const stores = useList(`${ERP_API}/stores`);
  const [node, setNode] = useState({ kind: "all" });
  const [rolesOpen, setRolesOpen] = useState(false);

  // 고른 소속으로 거른 직원 + 오른쪽 제목 + 새 직원에 미리 채울 소속
  const view = useMemo(() => {
    const all = users.data;
    if (node.kind === "hq") {
      return { rows: all.filter((u) => !u.storeNo), title: "본사", description: "본사 소속 직원입니다. 소속 매장을 비워 두면 본사 직원이 됩니다.", initial: {} };
    }
    if (node.kind === "dept") {
      const d = departments.data.find((x) => x.departmentNo === node.no);
      return {
        rows: all.filter((u) => !u.storeNo && u.department?.departmentNo === node.no),
        title: `본사 · ${d?.departmentName || "부서"}`,
        description: "이 부서의 본사 직원입니다.",
        initial: { departmentNo: node.no },
      };
    }
    if (node.kind === "kitchen") {
      const nos = stores.data.filter((s) => s.storeType === "센트럴키친").map((s) => s.storeNo);
      return { rows: all.filter((u) => nos.includes(u.storeNo)), title: "센트럴키친", description: "센트럴키친 소속 직원입니다.", initial: {} };
    }
    if (node.kind === "store") {
      const s = stores.data.find((x) => x.storeNo === node.no);
      return {
        rows: all.filter((u) => u.storeNo === node.no),
        title: s?.storeName || "매장",
        description: s ? `${s.storeType} · 점장 ${s.managerName || "미지정"}` : "",
        initial: { storeNo: node.no },
      };
    }
    return { rows: all, title: "전체 직원", description: "본사와 모든 매장의 직원입니다. 왼쪽 조직도에서 소속을 고르면 그 직원만 보입니다.", initial: {} };
  }, [node, users.data, departments.data, stores.data]);

  return (
    <div className="crud-page">
      <div className="crud-head">
        <div>
          <h1 className="crud-title">직원 관리</h1>
          <p className="crud-desc">왼쪽 조직도에서 본사 부서나 매장을 고르면 그 소속 직원이 보입니다. 부서는 조직도에서 바로 추가하거나 이름을 바꿀 수 있습니다.</p>
        </div>
      </div>

      <div className="org-layout">
        <OrgPanel
          users={users.data}
          departments={departments.data}
          stores={stores.data}
          node={node}
          setNode={setNode}
          onCreateDept={async (name) => {
            await ERPapi.post(DEPT_URL, { departmentName: name });
            await departments.reload();
          }}
          onRenameDept={async (d, name) => {
            await ERPapi.put(`${DEPT_URL}/${d.departmentNo}`, { departmentNo: d.departmentNo, departmentName: name });
            await Promise.all([departments.reload(), users.reload()]);
          }}
          onDeleteDept={async (d) => {
            await ERPapi.delete(`${DEPT_URL}/${d.departmentNo}`);
            await departments.reload();
          }}
          onOpenRoles={() => setRolesOpen(true)}
        />
        <div className="crud-card org-main">
          <User
            users={{ data: view.rows, loading: users.loading, reload: users.reload }}
            departments={departments.data}
            roles={roles.data}
            stores={stores.data}
            title={view.title}
            description={view.description}
            initial={view.initial}
          />
        </div>
      </div>

      <Modal
        open={rolesOpen}
        title="직급 설정"
        footer={null}
        width={560}
        destroyOnHidden
        onCancel={() => {
          setRolesOpen(false);
          // 직급 이름을 바꿨을 수 있으니 직원 카드와 등록 팝업을 새로 읽는다
          roles.reload();
          users.reload();
        }}
      >
        <Role users={users.data} />
      </Modal>
    </div>
  );
};

export default TabPresenter;
