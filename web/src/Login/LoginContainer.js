import LoginPresenter from "./LoginPresenter";
import { useState, useEffect } from "react";
import { LOGIN_API } from "../config";
import { useLocation, useNavigate } from "react-router-dom";
import { LOGINapi } from "../components/api/api";
import { clearSession, getSessionName, saveSession, wasExpired } from "../components/api/session";

const LoginContainer = () => {
  const [selectedTab, setSelectedTab] = useState("erp");
  const [username, setUsername] = useState(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [notice, setNotice] = useState("");
  const navigate = useNavigate();
  const location = useLocation();
  // 로그인이 필요한 화면에서 넘어왔으면, 로그인 뒤 그 화면으로 돌려보낸다
  const from = location.state?.from;

  useEffect(() => {
    // 토큰이 살아 있을 때만 안녕하세요 화면을 보여 준다 (만료됐으면 이름도 지우고 로그인 칸을 띄운다)
    const name = getSessionName();
    if (name) setUsername(name);
    if (wasExpired()) setNotice("로그인이 만료됐습니다.");
    else if (from) setNotice("로그인이 필요합니다.");
  }, [from]);

  // values: { userId, password } - antd Form이 넘겨준다 (Enter 키로도 제출됨)
  const HandleLogin = async (values) => {
    setLoading(true);
    setErrorMessage("");
    try {
      const response = await LOGINapi.post(`${LOGIN_API}`, values);
      saveSession(response.data.token, response.data.userName);
      navigate(from && from.startsWith(`/${selectedTab}`) ? from : `/${selectedTab}`, { replace: true });
    } catch (err) {
      setErrorMessage(
        err.response?.data?.message ?? "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
      );
    } finally {
      setLoading(false);
    }
  };

  const HandleLogout = () => {
    clearSession();
    setUsername(null);
    setNotice("");
  };

  const HandleTabSelect = (value) => {
    // 매장 POS는 본사 로그인 없이 바로 이동 (POS 화면에서 매장 연동 키로 기기 등록)
    if (value === "pos") {
      navigate("/pos");
      return;
    }
    setSelectedTab(value);
    setErrorMessage("");
  };

  const HandleMoveSystemHome = (value) => {
    // 로그인 화면을 오래 띄워 둔 사이 만료됐을 수 있으니 이동 직전에 한 번 더 확인
    if (!getSessionName()) {
      setUsername(null);
      setNotice("로그인이 만료됐습니다.");
      return;
    }
    navigate(`/${value}`);
  };

  return (
    <LoginPresenter
      username={username}
      selectedTab={selectedTab}
      loading={loading}
      errorMessage={errorMessage}
      notice={notice}
      HandleLogin={HandleLogin}
      HandleLogout={HandleLogout}
      HandleTabSelect={HandleTabSelect}
      HandleMoveSystemHome={HandleMoveSystemHome}
    />
  );
};

export default LoginContainer;
