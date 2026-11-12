import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ConfigProvider } from "antd";
import koKR from "antd/locale/ko_KR";
import dayjs from "dayjs";
import "dayjs/locale/ko";
import Login from "./Login";
import Layout from "./Layout";
import Home from "./pages/Home";
import Tab from "./pages/Standard/Tab";
import Store from "./pages/Standard/Store";
import Menu from "./pages/Standard/Menu";
import Material from "./pages/Standard/Material";
import Client from "./pages/Standard/Client";
import Equipment from "./pages/Standard/Equipment";
import Sales from "./pages/Ops/Sales";
import Closing from "./pages/Ops/Closing";
import Inventory from "./pages/Ops/Inventory";
import Orders from "./pages/Weekly/Orders";
import Schedule from "./pages/Weekly/Schedule";
import Pnl from "./pages/Monthly/Pnl";
import MenuAnalysis from "./pages/Monthly/Menus";
import Requests from "./pages/Purchase/Requests";
import Center from "./pages/Purchase/Center";
import Integration from "./pages/Integration";
import Simulator from "./pages/Integration/Simulator";
import PosApp from "./Pos/PosApp";
import Messages from "./pages/Messages";
import ProtectedRoute from "./components/ProtectedRoute/ProtectedRoute";
import NotFound from "./components/NotFound";
import { ChatbotProvider } from "./context/ChatbotContext";

// 버튼·달력 등 antd 기본 문구를 한국어로
dayjs.locale("ko");

const App = () => {
  return (
    <ConfigProvider locale={koKR}>
    <ChatbotProvider>
      <BrowserRouter>
        <Routes>
          <Route path="*" element={<NotFound />} />
          <Route path="/" element={<Login />} />
          {/* 본사 ERP */}
          <Route
            path="/erp"
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Home />} />
            <Route path="home" element={<Home />} />
            <Route path="standard/store" element={<Store />} />
            <Route path="standard/menu" element={<Menu />} />
            <Route path="standard/material" element={<Material />} />
            <Route path="standard/client" element={<Client />} />
            <Route path="standard/equipment" element={<Equipment />} />
            <Route path="standard/staff" element={<Tab />} />
            {/* 매장 모니터링 (매장 시스템에서 받은 데이터) */}
            <Route path="monitor/sales" element={<Sales />} />
            <Route path="monitor/closing" element={<Closing />} />
            <Route path="monitor/inventory" element={<Inventory />} />
            <Route path="monitor/labor" element={<Schedule />} />
            {/* 구매 · 물류 */}
            <Route path="purchase/requests" element={<Requests />} />
            <Route path="purchase/center" element={<Center />} />
            <Route path="purchase/orders" element={<Orders />} />
            {/* 경영 분석 */}
            <Route path="analysis/pnl" element={<Pnl />} />
            <Route path="analysis/menus" element={<MenuAnalysis />} />
            {/* 매장 공지 · 지시 (본사 → 매장 POS) */}
            <Route path="messages" element={<Messages />} />
            {/* 데이터 수집 */}
            <Route path="integration" element={<Navigate to="/erp/integration/status" replace />} />
            <Route path="integration/status" element={<Integration />} />
            <Route path="integration/simulator" element={<Simulator />} />
          </Route>
          {/* 매장 POS: 본사 로그인과 별개 시스템 (매장 연동 키로 기기 등록) */}
          <Route path="/pos" element={<PosApp />} />
          <Route path="/store-app" element={<Navigate to="/pos" replace />} />
          <Route path="/mes/*" element={<Navigate to="/pos" replace />} />
        </Routes>
      </BrowserRouter>
    </ChatbotProvider>
    </ConfigProvider>
  );
};
export default App;
