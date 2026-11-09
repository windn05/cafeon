export const ERP_API = "http://localhost:8080/api/erp/v1";
// 매장 POS 서버 (본사 ERP와 별개 시스템)
export const POS_API = "http://localhost:8081/api/pos/v1";
// 별도 인증 서버 없이 ERP가 로그인을 처리한다.
export const LOGIN_API = `${ERP_API}/auth/login`;
