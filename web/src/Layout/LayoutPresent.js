import "./layout.css";
import { useEffect, useState } from "react";
import {
  ApiOutlined,
  DatabaseOutlined,
  HomeOutlined,
  LineChartOutlined,
  NotificationOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
} from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import { Outlet, useLocation } from "react-router-dom";
import Chatbot from "../components/Chatbot/Chatbot";
import Brand from "../components/Brand/Brand";
import { erpTheme } from "../theme/erpTheme";

const { Header, Content, Sider } = Layout;

function getItem(label, key, icon, children, className) {
  return { key, icon, children, label, className };
}

// 본사 전용 ERP 메뉴 (업무 영역별). 매장 입력은 매장 POS에서 하고, 본사는 받은 데이터로 관리 · 분석하고 매장에 공지 · 지시를 보낸다.
const items = [
  getItem("ERP 홈", "/erp/home", <HomeOutlined />),
  getItem("기준 정보", "/erp/standard", <DatabaseOutlined />, [
    getItem("사업장", "/erp/standard/store"),
    getItem("메뉴", "/erp/standard/menu"),
    getItem("식자재", "/erp/standard/material"),
    getItem("공급업체", "/erp/standard/client"),
    getItem("자산", "/erp/standard/equipment"),
    getItem("직원 관리", "/erp/standard/staff"),
  ]),
  getItem("매장 공지 · 지시", "/erp/messages", <NotificationOutlined />),
  getItem("매장 모니터링", "/erp/monitor", <ShopOutlined />, [
    getItem("매출 현황", "/erp/monitor/sales"),
    getItem("마감 현황", "/erp/monitor/closing"),
    getItem("재고 · 로스 현황", "/erp/monitor/inventory"),
    getItem("근무 · 인건비 현황", "/erp/monitor/labor"),
  ]),
  getItem("구매 · 물류", "/erp/purchase", <ShoppingCartOutlined />, [
    getItem("재고 관리", "/erp/purchase/center"),
    getItem("매장 발주 요청", "/erp/purchase/requests"),
    getItem("발주 · 입고", "/erp/purchase/orders"),
  ]),
  getItem("경영 분석", "/erp/analysis", <LineChartOutlined />, [
    getItem("월간 손익 · 월 마감", "/erp/analysis/pnl"),
    getItem("메뉴 분석", "/erp/analysis/menus"),
  ]),
  getItem("데이터 수집", "/erp/integration", <ApiOutlined />, [
    getItem("수신 현황", "/erp/integration/status"),
    getItem("매장 영업 시뮬레이터", "/erp/integration/simulator"),
  ]),
];

// 지금 보는 화면이 속한 메뉴 묶음 (예: /erp/sales/closing → /erp/sales)
const groupOf = (path) => path.split("/").slice(0, 3).join("/");

// 상단 바 제목: 사이드바 메뉴명을 그대로 쓴다 (화면마다 제목을 따로 두지 않아 이름이 어긋나지 않게)
const titleOf = (path) => {
  for (const item of items) {
    if (item.key === path) return { label: item.label };
    const child = item.children?.find((c) => c.key === path);
    if (child) return { group: item.label, label: child.label };
  }
  return null;
};

const LayoutPresent = ({ collapsed, setCollapsed, loginUser, navigate, HandleLogout }) => {
  const { pathname } = useLocation();
  const selected = pathname === "/erp" ? "/erp/home" : pathname;
  const title = titleOf(selected);
  const [openKeys, setOpenKeys] = useState([groupOf(selected)]);
  useEffect(() => {
    setOpenKeys((keys) => (keys.includes(groupOf(selected)) ? keys : [...keys, groupOf(selected)]));
  }, [selected]);

  return (
    <ConfigProvider theme={erpTheme}>
    <Layout className="layout">
      <Sider className="erp-sider" theme="light" width={248} collapsedWidth={64} collapsible collapsed={collapsed} onCollapse={(value) => setCollapsed(value)}>
        <div className={`sider-brand ${collapsed ? "is-collapsed" : ""}`}>
          <Brand sub={collapsed ? undefined : "본사 ERP"} markOnly={collapsed} />
        </div>
        <Menu
          className="erp-menu"
          theme="light"
          selectedKeys={[selected]}
          openKeys={openKeys}
          onOpenChange={setOpenKeys}
          onClick={({ key }) => navigate(key)}
          mode="inline"
          items={items}
        />
      </Sider>
      <Layout>
        <Header className="main-header">
          <div className="header-title">
            {title?.group && (
              <>
                <span className="header-group">{title.group}</span>
                <span className="header-sep" aria-hidden>
                  /
                </span>
              </>
            )}
            {title && <h1>{title.label}</h1>}
          </div>
          <div className="header-user">
            <span className="user-name">{loginUser}</span>
            <button type="button" className="header-link" onClick={HandleLogout}>
              로그아웃
            </button>
          </div>
        </Header>
        <Content className="main-content">
          <div className="main-inner">
            <Outlet />
          </div>
        </Content>
      </Layout>
      <Chatbot />
    </Layout>
    </ConfigProvider>
  );
};
export default LayoutPresent;
