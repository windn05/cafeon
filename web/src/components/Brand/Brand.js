// 서비스 로고 (이미지 대신 글자 로고). 이름을 바꿀 때는 이 파일만 고치면 된다.
import { CoffeeOutlined } from "@ant-design/icons";
import "./brand.css";

export const BRAND_NAME = "CafeOn";
export const BRAND_TAGLINE = "카페 프랜차이즈 운영 시스템";

const Brand = ({ sub, tone = "dark", size = "md", markOnly = false }) => (
  <span className={`brand brand-${tone} brand-${size}`}>
    <span className="brand-mark" aria-hidden>
      <CoffeeOutlined />
    </span>
    {!markOnly && (
    <span className="brand-text">
      <span className="brand-name">{BRAND_NAME}</span>
      {sub && <span className="brand-sub">{sub}</span>}
    </span>
    )}
  </span>
);

export default Brand;
