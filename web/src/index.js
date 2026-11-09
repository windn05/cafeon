// antd v5는 React 19를 정식 지원하지 않아 message·Modal.confirm 같은 팝업이 뜨지 않는다 → 공식 호환 패치 (가장 먼저 불러와야 함)
import '@ant-design/v5-patch-for-react-19';
import React from 'react';
import ReactDOM from 'react-dom/client';
import 'antd/dist/reset.css';
import './index.css';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
