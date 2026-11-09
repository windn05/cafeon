import { Navigate, useLocation } from "react-router-dom";
import { getValidToken } from "../api/session";

// 로그인이 필요한 화면. 토큰이 없거나 만료됐으면 안내 창 없이 바로 로그인 화면으로 보내고,
// 로그인한 뒤 원래 보려던 화면으로 돌아올 수 있게 주소를 넘긴다.
const ProtectedRoute = ({ children }) => {
  const location = useLocation();
  if (!getValidToken()) {
    return <Navigate to="/" replace state={{ from: location.pathname }} />;
  }
  return children;
};

export default ProtectedRoute;
