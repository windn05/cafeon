// 카페 기준정보 화면들이 같이 쓰는 선택지

export const MATERIAL_TYPES = ["원두·커피", "유제품", "시럽·소스", "파우더·티", "베이커리·디저트", "과일·채소", "포장재·소모품", "기타"];
export const STORAGE_TYPES = ["상온", "냉장", "냉동"];
export const USAGE_UNITS = ["g", "ml", "EA"];

export const MENU_CATEGORIES = ["커피", "논커피", "티", "에이드·주스", "베이커리", "디저트"];
export const MENU_STATUS = ["판매중", "일시품절", "단종"];
// 제공 방식: 매장 제조(레시피대로 만듦) · 완제품(납품받은 그대로 판매)
export const PRODUCT_TYPES = ["매장 제조", "완제품"];
export const isFinished = (menu) => menu?.productType === "완제품";

export const STORE_TYPES = ["직영점", "가맹점", "물류센터"];
// 공급 방식: 물류센터(본사 재고에서 출고) · 직송(공급업체가 매장으로 바로 배송)
export const SUPPLY_TYPES = ["물류센터", "직송"];
export const STORE_STATUS = ["영업중", "휴점", "폐점"];

export const CLIENT_TYPES = ["원두·커피", "유제품", "베이커리·디저트", "농산물·과일", "시럽·소스", "포장재·소모품", "설비·유지보수", "물류·배송"];

export const EMPLOYMENT_TYPES = ["정규직", "계약직", "파트타임"];

// 원가율 구간 색 (카페 업계에서 식자재 원가율 30% 안팎을 적정선으로 본다)
export const costRateTone = (rate) => (rate >= 40 ? "red" : rate >= 30 ? "amber" : "green");

// 사용 단위 원가 표시: 1g당 18.5원 → "18.5원/g"
export const formatUnitCost = (cost, unit) =>
  cost === null || cost === undefined ? "-" : `${Number(cost).toLocaleString(undefined, { maximumFractionDigits: 2 })}원/${unit}`;
