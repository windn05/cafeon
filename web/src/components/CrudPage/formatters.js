// 입력·표시용 형식 도우미 (사업자등록번호, 전화번호)
import { Input } from "antd";

export const onlyDigits = (v) => String(v ?? "").replace(/\D/g, "");

// 사업자등록번호: 숫자만 저장하고, 보여 줄 때 3-2-5 자리로 하이픈
export const formatBrn = (v) => {
  const d = onlyDigits(v).slice(0, 10);
  if (d.length <= 3) return d;
  if (d.length <= 5) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
};

// 국세청 사업자등록번호 검증 공식 (마지막 자리가 검증 숫자)
export const isValidBrn = (v) => {
  const d = onlyDigits(v);
  if (d.length !== 10) return false;
  const w = [1, 3, 7, 1, 3, 7, 1, 3, 5];
  let sum = 0;
  for (let i = 0; i < 9; i += 1) sum += Number(d[i]) * w[i];
  sum += Math.floor((Number(d[8]) * 5) / 10);
  return (10 - (sum % 10)) % 10 === Number(d[9]);
};

// 전화번호: 입력하는 대로 하이픈 (02 서울번호, 휴대폰·지역번호, 1588 같은 대표번호)
export const formatPhone = (v) => {
  const d = onlyDigits(v).slice(0, 11);
  if (d.startsWith("02")) {
    if (d.length <= 2) return d;
    if (d.length <= 5) return `${d.slice(0, 2)}-${d.slice(2)}`;
    if (d.length <= 9) return `${d.slice(0, 2)}-${d.slice(2, 5)}-${d.slice(5)}`;
    return `${d.slice(0, 2)}-${d.slice(2, 6)}-${d.slice(6, 10)}`;
  }
  if (/^1[5-9]/.test(d) && d.length <= 8) {
    return d.length <= 4 ? d : `${d.slice(0, 4)}-${d.slice(4)}`;
  }
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}-${d.slice(3)}`;
  if (d.length <= 10) return `${d.slice(0, 3)}-${d.slice(3, 6)}-${d.slice(6)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`;
};

// 사업자등록번호 입력칸: 화면에는 000-00-00000, 폼 값은 숫자 10자리
export const BrnInput = ({ value, onChange, ...rest }) => (
  <Input
    {...rest}
    inputMode="numeric"
    value={formatBrn(value)}
    onChange={(e) => onChange?.(onlyDigits(e.target.value).slice(0, 10))}
    maxLength={12}
  />
);

// 전화번호 입력칸: 입력하는 대로 하이픈을 넣어 저장
export const PhoneInput = ({ value, onChange, ...rest }) => (
  <Input
    {...rest}
    inputMode="tel"
    value={formatPhone(value)}
    onChange={(e) => onChange?.(formatPhone(e.target.value))}
    maxLength={13}
  />
);
