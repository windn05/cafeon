import { Alert, Button, ConfigProvider, Form, Input } from "antd";
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  CheckCircleFilled,
  LockOutlined,
  LogoutOutlined,
  ShopOutlined,
  UserOutlined,
} from "@ant-design/icons";
import Brand, { BRAND_TAGLINE } from "../components/Brand/Brand";
import "./login.css";

// 시스템별 강조 색 (본사 ERP 파랑, 매장 POS 갈색 - 각 시스템 화면 색과 맞춤)
// 매장 POS는 본사 계정이 아니라 매장 연동 키로 기기를 등록해서 쓴다.
const SYSTEMS = {
  erp: {
    label: "ERP",
    color: "#1962a5",
    tint: "#eef4fb",
    desc: "본사 · 매장 · 메뉴 · 손익",
    icon: <AppstoreOutlined />,
  },
  pos: {
    label: "매장 POS",
    color: "#5b3a26",
    tint: "#f7f1ec",
    desc: "판매 · 마감 · 발주 요청 (매장용)",
    icon: <ShopOutlined />,
  },
};

// 배경 장식: 커피잔 · 원두 선화
const CafeIllustration = () => (
  <svg className="bp-ship" viewBox="0 0 1200 300" aria-hidden="true">
    <g fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
      {/* 테이크아웃 컵 */}
      <path d="M150 90 L250 90 L238 250 L162 250 Z" />
      <rect x="142" y="74" width="116" height="16" rx="4" />
      <path d="M156 140 L244 140 M159 190 L241 190" />
      {/* 머그컵 + 김 */}
      <path d="M520 150 L680 150 L668 250 Q600 262 532 250 Z" />
      <path d="M680 170 Q730 172 726 205 Q722 236 672 232" />
      <path d="M560 128 Q548 108 562 90 Q576 72 562 52" />
      <path d="M600 128 Q588 108 602 90 Q616 72 602 52" />
      <path d="M640 128 Q628 108 642 90 Q656 72 642 52" />
      <ellipse cx="600" cy="262" rx="120" ry="10" />
      {/* 원두 */}
      {[
        [900, 200, -25],
        [960, 236, 20],
        [1030, 196, -10],
        [1090, 240, 35],
        [380, 230, 15],
        [330, 200, -30],
      ].map(([x, y, r]) => (
        <g key={x} transform={`rotate(${r} ${x} ${y})`}>
          <ellipse cx={x} cy={y} rx="22" ry="15" />
          <path d={`M${x - 18} ${y} Q${x} ${y - 8} ${x + 18} ${y}`} />
        </g>
      ))}
    </g>
    <line className="bp-waterline" x1="30" y1="272" x2="1180" y2="272" />
  </svg>
);

const SystemTile = ({ systemKey, active, onClick, showArrow }) => {
  const s = SYSTEMS[systemKey];
  return (
    <button
      type="button"
      className={`sys-tile ${active ? "is-active" : ""}`}
      style={{ "--accent": s.color, "--tint": s.tint }}
      onClick={onClick}
      aria-pressed={active}
    >
      <span className="sys-tile-icon">{s.icon}</span>
      <span className="sys-tile-name">{s.label}</span>
      <span className="sys-tile-desc">{s.desc}</span>
      {showArrow ? (
        <ArrowRightOutlined className="sys-tile-mark" />
      ) : (
        active && <CheckCircleFilled className="sys-tile-mark" />
      )}
    </button>
  );
};

const LoginPresenter = ({
  username,
  selectedTab,
  loading,
  errorMessage,
  notice,
  HandleLogin,
  HandleLogout,
  HandleTabSelect,
  HandleMoveSystemHome,
}) => {
  const accent = SYSTEMS[selectedTab].color;

  return (
    <ConfigProvider theme={{ token: { colorPrimary: accent, borderRadius: 8 } }}>
      <div className="bp-page">
        <header className="bp-header">
          <Brand tone="light" sub={BRAND_TAGLINE} />
        </header>

        <CafeIllustration />

        <main className="bp-main">
          <section className="bp-card">
            <div className="bp-logo">
              <Brand size="lg" />
            </div>

            {username ? (
              // 이미 로그인한 상태: 시스템으로 이동
              <>
                <h1 className="bp-title">안녕하세요, {username}님</h1>
                <p className="bp-subtitle">이동할 시스템을 선택하세요.</p>
                <div className="sys-tiles">
                  {Object.keys(SYSTEMS).map((key) => (
                    <SystemTile
                      key={key}
                      systemKey={key}
                      showArrow
                      onClick={() => HandleMoveSystemHome(key)}
                    />
                  ))}
                </div>
                <Button
                  type="text"
                  icon={<LogoutOutlined />}
                  className="bp-logout"
                  onClick={HandleLogout}
                >
                  다른 계정으로 로그인
                </Button>
              </>
            ) : (
              // 로그인 폼
              <>
                <h1 className="bp-title">로그인</h1>
                <p className="bp-subtitle">본사 직원 계정으로 로그인하세요. 매장은 [매장 POS]를 누르세요.</p>

                <div className="sys-tiles">
                  {Object.keys(SYSTEMS).map((key) => (
                    <SystemTile
                      key={key}
                      systemKey={key}
                      active={selectedTab === key}
                      onClick={() => HandleTabSelect(key)}
                    />
                  ))}
                </div>

                {notice && !errorMessage && <Alert className="bp-error" type="warning" showIcon message={notice} />}
                {errorMessage && (
                  <Alert className="bp-error" type="error" showIcon message={errorMessage} />
                )}

                <Form requiredMark={false} onFinish={HandleLogin} className="bp-form">
                  <Form.Item
                    name="userId"
                    rules={[{ required: true, message: "아이디를 입력하세요." }]}
                  >
                    <Input
                      size="large"
                      prefix={<UserOutlined className="bp-input-icon" />}
                      placeholder="아이디"
                      autoComplete="username"
                      autoFocus
                    />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    rules={[{ required: true, message: "비밀번호를 입력하세요." }]}
                  >
                    <Input.Password
                      size="large"
                      prefix={<LockOutlined className="bp-input-icon" />}
                      placeholder="비밀번호"
                      autoComplete="current-password"
                    />
                  </Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    size="large"
                    block
                    loading={loading}
                    className="bp-submit"
                  >
                    로그인
                  </Button>
                </Form>
              </>
            )}
          </section>
        </main>
      </div>
    </ConfigProvider>
  );
};

export default LoginPresenter;
