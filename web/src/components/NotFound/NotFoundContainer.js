import { useNavigate } from "react-router-dom";
import NotFoundPresenter from "./NotFoundPresenter";

const NotFoundContainer = () => {
  const navigate = useNavigate();
  const HandleGoBack = () => {
    navigate(-1);
  };

  return <NotFoundPresenter HandleGoBack={HandleGoBack} />;
};

export default NotFoundContainer;
