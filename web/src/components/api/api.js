import axios from "axios";
import { ERP_API } from "../../config";
import { LOGIN_API } from "../../config";
import { clearSession } from "./session";

export const ERPapi = axios.create({
  baseURL: ERP_API,
});

export const LOGINapi = axios.create({
  baseURL: LOGIN_API,
});

const addInterceptor = (instance) => {
  instance.interceptors.request.use(
    (config) => {
      let token = null;
      try {
        token = localStorage.getItem("token");
      } catch {
        /* 저장소를 못 읽으면 토큰 없이 보낸다 */
      }
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  // 토큰이 없거나 만료되어 401이 오면 로그인 화면으로 보낸다 (다음 화면에 "다시 로그인" 안내).
  instance.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401 && instance !== LOGINapi) {
        clearSession({ expired: true });
        if (window.location.pathname !== "/") {
          window.location.href = "/";
        }
      }
      return Promise.reject(error);
    }
  );
};

addInterceptor(ERPapi);
addInterceptor(LOGINapi);
