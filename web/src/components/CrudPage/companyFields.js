// 거래처·고객사 화면이 같이 쓰는 회사 정보 입력칸·표 열·검색 조건
import { isValidBrn, onlyDigits } from "./formatters";

// 등록 팝업 섹션 구성
export const companySections = [
  { key: "basic", title: "기본 정보" },
  { key: "biz", title: "사업자 정보", description: "사업자등록증에 적힌 내용과 같게 입력하세요." },
  { key: "contact", title: "담당자 정보", description: "발주·견적 연락을 받을 담당자입니다." },
];

export const companyFields = (prefix) => [
  {
    name: `${prefix}Brn`,
    label: "사업자등록번호",
    type: "brn",
    section: "biz",
    required: true,
    half: true,
    placeholder: "숫자만 입력 (하이픈 자동)",
    unique: true,
    normalize: onlyDigits,
    uniqueMessage: "이미 등록된 사업자등록번호입니다.",
    rules: [
      { len: 10, message: "숫자 10자리를 입력하세요." },
      // 국세청 검증 공식에 안 맞으면 경고만 (저장은 가능)
      {
        warningOnly: true,
        validator: async (_, v) => {
          if (v && onlyDigits(v).length === 10 && !isValidBrn(v)) {
            throw new Error("유효하지 않은 번호일 수 있습니다. 사업자등록증을 다시 확인하세요.");
          }
        },
      },
    ],
  },
  { name: `${prefix}Ceo`, label: "대표자", section: "biz", required: true, half: true, maxLength: 30 },
  { name: `${prefix}Manager`, label: "담당자", section: "contact", required: true, half: true, maxLength: 30 },
  {
    name: `${prefix}Phone`,
    label: "연락처",
    type: "phone",
    section: "contact",
    required: true,
    half: true,
    placeholder: "숫자만 입력 (하이픈 자동)",
    rules: [
      {
        validator: async (_, v) => {
          const n = onlyDigits(v).length;
          if (v && (n < 8 || n > 11)) throw new Error("전화번호를 끝까지 입력하세요.");
        },
      },
    ],
  },
];

// 검색 조건: 사업자번호·연락처는 하이픈 유무와 상관없이 찾기
export const companySearchFields = (prefix, nameLabel) => [
  { key: `${prefix}Name`, label: nameLabel },
  { key: `${prefix}Brn`, label: "사업자등록번호", normalize: onlyDigits },
  { key: `${prefix}Ceo`, label: "대표자" },
  { key: `${prefix}Manager`, label: "담당자" },
  { key: `${prefix}Phone`, label: "연락처", normalize: onlyDigits },
];
