// 본사 ERP 화면들이 같이 쓰는 대시보드 부품 (제목 · 지표 카드 · 패널 · 날씨)
import { Empty, Spin } from "antd";
import { WarningFilled } from "@ant-design/icons";
import dayjs from "dayjs";
import "dayjs/locale/ko";
import "./dashboard.css";

// 날씨에 따른 매장 준비 안내 (카페 매출은 기온·비의 영향을 많이 받는다)
export const demandHint = (temp, main = "") => {
  if (/Rain|Drizzle|Thunderstorm/.test(main)) return { tone: "warn", text: "비 예보: 배달·포장 주문이 늘 수 있습니다. 포장재 재고를 확인하세요." };
  if (/Snow/.test(main)) return { tone: "warn", text: "눈 예보: 매장 방문이 줄 수 있습니다. 당일 생산량을 줄여 폐기를 막으세요." };
  if (temp >= 27) return { tone: "warn", text: "더운 날: 아이스 음료 수요가 늘어납니다. 얼음·우유·컵 재고를 넉넉히 두세요." };
  if (temp <= 5) return { tone: "ok", text: "추운 날: 따뜻한 음료와 베이커리 판매가 늘어납니다." };
  return { tone: "ok", text: "평소 수준의 수요가 예상됩니다." };
};

export const todayLabel = () => {
  const d = new Date();
  return d.toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "long",
  });
};

// 화면 맨 위 설명 줄 (메뉴명은 상단 바에 있다)
export const DashHeader = ({ subtitle, extra }) => (
  <div className="dash-header">
    <div>
      <p className="dash-subtitle">{subtitle}</p>
    </div>
    {extra && <div className="dash-header-extra">{extra}</div>}
  </div>
);

// 숫자 하나를 크게 보여 주는 카드
// 카드는 숫자만: 라벨 · 큰 숫자 · 비교 기준이 있는 변화 (아이콘은 넣지 않는다)
export const StatCard = ({ label, value, unit, hint, tone, keyed }) => (
  <div className={`stat-card ${tone ? `tone-${tone}` : ""} ${keyed ? "is-key" : ""}`}>
    <div className="stat-top">
      <span className="stat-label">{label}</span>
    </div>
    <div className="stat-value">
      {value}
      {unit && <span className="stat-unit">{unit}</span>}
    </div>
    {hint && <div className="stat-hint">{hint}</div>}
  </div>
);

// 제목이 있는 흰색 영역
export const Panel = ({ title, extra, children, className = "", bodyClassName = "" }) => (
  <section className={`dash-panel ${className}`}>
    {(title || extra) && (
      <header className="dash-panel-head">
        <h2 className="dash-panel-title">{title}</h2>
        {extra && <div className="dash-panel-extra">{extra}</div>}
      </header>
    )}
    <div className={`dash-panel-body ${bodyClassName}`}>{children}</div>
  </section>
);

export const EmptyState = ({ text, action }) => (
  <div className="dash-empty">
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={text} />
    {action}
  </div>
);

export const Loading = () => (
  <div className="dash-loading">
    <Spin />
  </div>
);

const weatherIcon = (icon) =>
  `https://openweathermap.org/img/wn/${icon.replace("n", "d")}@2x.png`;

// 날씨 카드: 현재 날씨 + 5일 예보 + 매장 준비 안내
export const WeatherPanel = ({ weather, cityCoordinates, selectedCity, onSelectCity }) => {
  const cities = (
    <div className="city-switch">
      {Object.entries(cityCoordinates).map(([key, { name }]) => (
        <button
          key={key}
          type="button"
          className={selectedCity === key ? "is-active" : ""}
          onClick={() => onSelectCity(key)}
        >
          {name}
        </button>
      ))}
    </div>
  );

  if (!weather || !weather.current) {
    return (
      <Panel title="날씨" extra={cities}>
        <Loading />
      </Panel>
    );
  }

  const { current, forecast } = weather;
  const wind = current.wind?.speed ?? 0;
  const hint = demandHint(current.main.temp, current.weather[0]?.main);

  // 하루에 한 개씩 (가능하면 정오 예보)
  const list = forecast?.list || [];
  const noon = list.filter((item) => item.dt_txt?.includes("12:00:00"));
  const daily = (noon.length >= 3 ? noon : list.filter((_, i) => i % 8 === 0)).slice(0, 5);

  return (
    <Panel title={`${cityCoordinates[selectedCity].name} 날씨`} extra={cities}>
      <div className="weather-now">
        <img src={weatherIcon(current.weather[0].icon)} alt="" className="weather-now-icon" />
        <div>
          <div className="weather-now-temp">{Math.round(current.main.temp)}°</div>
          <div className="weather-now-desc">{current.weather[0].description}</div>
        </div>
        <dl className="weather-now-meta">
          <div>
            <dt>풍속</dt>
            <dd>{wind} m/s</dd>
          </div>
          <div>
            <dt>습도</dt>
            <dd>{current.main.humidity ?? "-"}%</dd>
          </div>
        </dl>
      </div>

      <div className={`work-alert ${hint.tone === "warn" ? "is-danger" : "is-ok"}`}>
        {hint.tone === "warn" && <WarningFilled />} {hint.text}
      </div>

      <ul className="weather-days">
        {daily.map((item) => (
          <li key={item.dt}>
            <span className="weather-day-name">{dayjs(item.dt_txt).locale("ko").format("ddd")}</span>
            <img src={weatherIcon(item.weather[0].icon)} alt="" />
            <span className="weather-day-temp">{Math.round(item.main.temp)}°</span>
          </li>
        ))}
      </ul>
    </Panel>
  );
};
