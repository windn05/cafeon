// 로그인 상태 한 곳에서 관리: 토큰이 없거나 만료됐으면 저장된 이름까지 지운다.
// (이름만 남아 있으면 로그인 화면이 '안녕하세요'를 보여 주는데 막상 이동하면 로그인하라고 뜨는 문제가 생긴다)
import { jwtDecode } from "jwt-decode";

const TOKEN = "token";
const NAME = "username";
// 다음 로그인 화면에 "시간이 지나 로그아웃됐다"고 알려 주기 위한 표시
const EXPIRED_FLAG = "session.expired";

const safe = (fn, fallback = null) => {
  try {
    return fn();
  } catch {
    return fallback;
  }
};

export const saveSession = (token, userName) => {
  safe(() => {
    localStorage.setItem(TOKEN, token);
    localStorage.setItem(NAME, userName);
    sessionStorage.removeItem(EXPIRED_FLAG);
  });
};

export const clearSession = ({ expired = false } = {}) => {
  safe(() => {
    localStorage.removeItem(TOKEN);
    localStorage.removeItem(NAME);
    if (expired) sessionStorage.setItem(EXPIRED_FLAG, "1");
    else sessionStorage.removeItem(EXPIRED_FLAG); // 직접 로그아웃하면 '시간이 지나' 안내는 띄우지 않는다
  });
};

// 쓸 수 있는 토큰만 돌려준다. 만료됐거나 깨진 토큰이면 정리하고 null
export const getValidToken = () => {
  const token = safe(() => localStorage.getItem(TOKEN));
  if (!token) return null;
  const exp = safe(() => jwtDecode(token).exp);
  if (!exp || exp * 1000 <= Date.now()) {
    clearSession({ expired: true });
    return null;
  }
  return token;
};

// 로그인된 사람 이름 (토큰이 유효할 때만)
export const getSessionName = () => (getValidToken() ? safe(() => localStorage.getItem(NAME)) : null);

// 시간이 지나 로그아웃된 상태인지 (다시 로그인하면 saveSession이 지운다)
export const wasExpired = () => safe(() => sessionStorage.getItem(EXPIRED_FLAG) === "1", false);
