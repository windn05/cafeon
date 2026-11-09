import { Button, Result } from "antd";
import "./notfound.css";

const NotFoundPresenter = ({ HandleGoBack }) => {
  return (
    <div>
      <Result
        status="404"
        title="404"
        subTitle="존재하지 않는 페이지 입니다."
        extra={
          <Button type="primary" onClick={HandleGoBack}>
            이전 페이지로 이동
          </Button>
        }
      />
    </div>
  );
};

export default NotFoundPresenter;
