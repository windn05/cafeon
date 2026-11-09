import { useState } from "react";
import LayoutPresent from "./LayoutPresent";
import { useNavigate } from "react-router-dom";
import { clearSession, getSessionName } from "../components/api/session";

const LayoutContainer = () => {
  const [collapsed, setCollapsed] = useState(false);
  const loginUser = getSessionName();
  const navigate = useNavigate();
  const HandleLogout = () => {
    clearSession();
    navigate("/");
  };

  return (
    <div>
      <LayoutPresent
        collapsed={collapsed}
        setCollapsed={setCollapsed}
        navigate={navigate}
        loginUser={loginUser}
        HandleLogout={HandleLogout}
      />
    </div>
  );
};

export default LayoutContainer;
