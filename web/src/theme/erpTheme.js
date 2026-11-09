// 본사 ERP 공통 테마. 색 · 글꼴을 바꿀 때는 이 파일과 erp-tokens.css만 고치면 된다.
import "./erp-tokens.css";

// CafeOn 로스팅 브라운. 주요 버튼 · 선택 · 핵심 숫자 하나에만 쓴다.
export const ACCENT = "#8a5a3b";
// 사이드바 · 상단 바 틀 색 (따뜻한 연회색). erp-tokens.css의 --chrome과 같게 둔다
export const CHROME = "#efece7";

// 차트용 무채색 단계 (강조할 하나만 ACCENT)
export const CHART = {
  grid: "#ededed",
  axis: "#666666",
  bar: "#d6d3d1",
  line: "#57534e",
};

export const FONT =
  '"Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif';

export const erpTheme = {
  token: {
    colorPrimary: ACCENT,
    colorLink: ACCENT,
    colorText: "#171717",
    colorTextSecondary: "#525252",
    colorTextTertiary: "#666666",
    colorBorder: "#e5e5e5",
    colorBorderSecondary: "#ededed",
    colorBgLayout: "#fafafa",
    colorSuccess: "#16a34a",
    colorWarning: "#b45309",
    colorError: "#dc2626",
    fontFamily: FONT,
    fontSize: 14,
    borderRadius: 8,
    controlHeight: 36,
  },
  components: {
    // 사이드바 · 상단 바는 한 덩어리 틀(CHROME), 본문은 그 안의 밝은 판
    Layout: { siderBg: CHROME, headerBg: CHROME, bodyBg: CHROME, triggerBg: CHROME, triggerColor: "#666666", lightSiderBg: CHROME, lightTriggerBg: CHROME, lightTriggerColor: "#666666", headerHeight: 56, headerPadding: "0 24px" },
    Menu: {
      itemBg: "transparent",
      subMenuItemBg: "transparent",
      itemColor: "#3a3a3a",
      itemHoverColor: "#171717",
      itemHoverBg: "rgba(23, 23, 23, 0.05)",
      itemSelectedBg: "#ffffff",
      itemSelectedColor: "#5b3a26",
      itemActiveBg: "rgba(23, 23, 23, 0.08)",
      itemHeight: 36,
      itemMarginBlock: 2,
      itemMarginInline: 8,
      itemBorderRadius: 8,
      iconSize: 16,
      iconMarginInlineEnd: 10,
      activeBarBorderWidth: 0,
      groupTitleColor: "#666666",
    },
    Table: { headerBg: "#fafafa", headerColor: "#525252", headerSplitColor: "transparent", rowHoverBg: "#fafafa", borderColor: "#efefef" },
    Button: { fontWeight: 500, primaryShadow: "none", defaultShadow: "none" },
    Tag: { borderRadiusSM: 6 },
    Segmented: { itemSelectedColor: "#171717" },
  },
};
