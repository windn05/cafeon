// 매장 운영 화면(판매 · 마감 · 재고)이 같이 쓰는 부품
import { useCallback, useEffect, useState } from "react";
import { DatePicker, Select, message } from "antd";
import dayjs from "dayjs";
import "dayjs/locale/ko";
import { ERPapi } from "../../components/api/api";
import { errorText } from "../../components/CrudPage/CrudPage";
import { ERP_API } from "../../config";
import "./ops.css";

dayjs.locale("ko");

export { ACCENT } from "../../theme/erpTheme";
export const SHOP_TYPES = ["직영점", "가맹점"];
export const won = (v) => `${Math.round(Number(v) || 0).toLocaleString()}원`;
export const num = (v, digits = 1) => Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: digits });
export const todayStr = () => dayjs().format("YYYY-MM-DD");
export const dayLabel = (d) => dayjs(d).format("M월 D일 (ddd)");

// 증감률 표시: +12.3% / -4.0%
export const changeRate = (now, before) => {
  if (!before) return null;
  return Math.round(((now - before) / before) * 1000) / 10;
};

const STORAGE_KEY = "ops.storeNo";
const readSaved = (key) => {
  try {
    return Number(sessionStorage.getItem(key)) || null;
  } catch {
    return null;
  }
};

// 매장 목록 + 선택한 매장 (화면을 옮겨도 같은 매장을 유지)
// shopsOnly: 판매 매장(직영·가맹)만
// types: 매장 유형으로 더 좁힘 (예: ["직영점"])
// storageKey: 다른 화면과 따로 기억할 때
export const useStoreChoice = ({ shopsOnly = false, types = null, storageKey = STORAGE_KEY } = {}) => {
  const typeKey = types ? types.join(",") : "";
  const [stores, setStores] = useState([]);
  const [storeNo, setStoreNoState] = useState(() => readSaved(storageKey));
  // 매장 목록을 받기 전에는 저장해 둔 번호(지워졌을 수 있음)로 조회하지 않는다
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    ERPapi.get(`${ERP_API}/stores`)
      .then((res) => {
        // 판매 매장을 먼저, 그다음 물류센터 (각각 코드 순)
        const isShop = (s) => SHOP_TYPES.includes(s.storeType);
        const list = res.data
          .filter((s) => !shopsOnly || isShop(s))
          .filter((s) => !typeKey || typeKey.split(",").includes(s.storeType))
          .filter((s) => s.status !== "폐점")
          .sort((a, b) => Number(isShop(b)) - Number(isShop(a)) || a.storeCode.localeCompare(b.storeCode));
        setStores(list);
        setLoaded(true);
        setStoreNoState((cur) => {
          const next = list.some((s) => s.storeNo === cur) ? cur : list[0]?.storeNo ?? null;
          try {
            if (next) sessionStorage.setItem(storageKey, String(next));
          } catch {
            /* 저장 못 해도 동작에는 문제없음 */
          }
          return next;
        });
      })
      .catch((err) => {
        setLoaded(true);
        message.error(errorText(err, "매장 목록을 불러오지 못했습니다."));
      });
  }, [shopsOnly, typeKey, storageKey]);

  const setStoreNo = (no) => {
    setStoreNoState(no);
    try {
      sessionStorage.setItem(storageKey, String(no));
    } catch {
      /* 저장 못 해도 동작에는 문제없음 */
    }
  };

  const store = stores.find((s) => s.storeNo === storeNo) || null;
  return { stores, storeNo: loaded ? storeNo : null, store, setStoreNo, loaded };
};

export const StoreSelect = ({ stores, value, onChange }) => (
  <Select
    className="ops-store-select"
    value={value}
    onChange={onChange}
    showSearch
    optionFilterProp="label"
    options={stores.map((s) => ({
      value: s.storeNo,
      label: `${s.storeName}${s.status !== "영업중" ? ` (${s.status})` : ""}`,
    }))}
    placeholder="매장 선택"
  />
);

// 날짜 고르기 (오늘 이후는 못 고름) + 이전/다음 날 버튼
export const DayPicker = ({ value, onChange }) => {
  const d = dayjs(value);
  const isToday = d.isSame(dayjs(), "day");
  return (
    <div className="ops-day">
      <button type="button" onClick={() => onChange(d.subtract(1, "day").format("YYYY-MM-DD"))} aria-label="전날">
        ‹
      </button>
      <DatePicker
        value={d}
        allowClear={false}
        disabledDate={(x) => x.isAfter(dayjs(), "day")}
        onChange={(x) => x && onChange(x.format("YYYY-MM-DD"))}
        format={(v) => v.locale("ko").format("YYYY-MM-DD (ddd)")}
      />
      <button type="button" disabled={isToday} onClick={() => onChange(d.add(1, "day").format("YYYY-MM-DD"))} aria-label="다음날">
        ›
      </button>
      {!isToday && (
        <button type="button" className="ops-day-today" onClick={() => onChange(todayStr())}>
          오늘
        </button>
      )}
    </div>
  );
};

// GET 요청 하나를 불러오고 다시 불러오기 (params가 바뀌면 자동 재요청)
export const useFetch = (url, params, enabled = true) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const key = JSON.stringify(params);
  const load = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    try {
      const res = await ERPapi.get(url, { params: JSON.parse(key) });
      setData(res.data);
    } catch (err) {
      message.error(errorText(err, "데이터를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [url, key, enabled]);
  useEffect(() => {
    load();
  }, [load]);
  return { data, loading, reload: load };
};
