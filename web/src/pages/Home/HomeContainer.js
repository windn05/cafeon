import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { message } from "antd";
import HomePresenter from "./HomePresenter";
import { ERPapi } from "../../components/api/api";
import { errorText } from "../../components/CrudPage/CrudPage";
import { ERP_API } from "../../config";

const cityCoordinates = {
  seoul: { name: "서울", lat: 37.5665, lon: 126.978 },
  busan: { name: "부산", lat: 35.1796, lon: 129.0756 },
  daejeon: { name: "대전", lat: 36.3504, lon: 127.3845 },
};

// 뉴스 제목에 섞여 오는 <b> 태그와 &quot; 같은 HTML 코드를 일반 글자로 바꾼다
const plainText = (html = "") =>
  new DOMParser().parseFromString(html, "text/html").documentElement.textContent || "";

const HomeContainer = () => {
  const navigate = useNavigate();
  const [dash, setDash] = useState(null);
  const [notices, setNotices] = useState([]);
  const [weather, setWeather] = useState(null);
  const [selectedCity, setSelectedCity] = useState("seoul");

  const fetchWeather = async (cityKey) => {
    const { lat, lon } = cityCoordinates[cityKey];
    setSelectedCity(cityKey);
    try {
      const res = await ERPapi.get("http://localhost:8080/api/weather", { params: { lat, lon } });
      setWeather(res.data);
    } catch (err) {
      console.error("날씨 정보를 가져오는 데 실패했습니다:", err);
    }
  };

  useEffect(() => {
    ERPapi.get(`${ERP_API}/dashboard/home`)
      .then((res) => setDash(res.data))
      .catch((err) => {
        message.error(errorText(err, "홈 화면 데이터를 불러오지 못했습니다."));
        setDash({ error: true });
      });
    ERPapi.get(`${ERP_API}/news`)
      .then((res) => setNotices(res.data.map((item, idx) => ({ id: idx, title: plainText(item.title), link: item.link }))))
      .catch(() => setNotices([]));
    fetchWeather("seoul");
  }, []);

  // 할 일 · 매장 행을 누르면 그 매장을 고른 상태로 해당 화면을 연다
  const go = (link, storeNo) => {
    if (storeNo) {
      try {
        sessionStorage.setItem("ops.storeNo", String(storeNo));
      } catch {
        /* 저장 못 해도 이동은 한다 */
      }
    }
    navigate(link);
  };

  return (
    <HomePresenter
      dash={dash}
      notices={notices}
      weather={weather}
      cityCoordinates={cityCoordinates}
      selectedCity={selectedCity}
      onSelectCity={fetchWeather}
      go={go}
    />
  );
};

export default HomeContainer;
