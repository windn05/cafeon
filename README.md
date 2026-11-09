# CafeOn · 카페 프랜차이즈 운영 시스템

카페 프랜차이즈 본사가 쓰는 **ERP**와 매장에서 쓰는 **POS**를 함께 만든 프로젝트입니다.

- **본사 ERP:** 매장 · 메뉴/레시피 · 식자재 · 공급업체 · 자산 · 직원 같은 기준 정보를 관리합니다. 매장 공지·지시, 매장 모니터링(마감 · 근무), 구매·물류, 경영 분석(손익)도 여기서 합니다.
- **매장 POS:** 판매 · 일 마감 · 발주 요청을 처리합니다. 데이터는 POS DB에 먼저 저장하고, 본사로 보낼 것은 전송 대기열에 쌓아 두었다가 본사 ERP로 보냅니다.

## 구성

| 폴더 | 내용 | 포트 |
|---|---|---|
| `erp-server` | 본사 ERP 서버 (Spring Boot 3.5, Java 17, JPA) | 8080 |
| `pos-server` | 매장 POS 서버 (Spring Boot 3.5, Java 17, JPA) | 8081 |
| `web` | ERP · POS 화면 (React 19, antd 5, recharts) | 3000 |
| `server-control.ps1`, `서버관리.bat` | 세 서버를 한 번에 켜고 끄는 Windows용 작은 프로그램 | |

## 실행 준비

1. **Java 17**, **Node.js 18 이상**, **MySQL 8.0 이상**을 설치합니다. 기본 설정은 MySQL 포트 `3307`입니다.
2. DB와 계정을 만듭니다. 테이블은 서버가 처음 뜰 때 자동으로 만들어집니다.

   ```sql
   CREATE DATABASE dock_system_erp CHARACTER SET utf8mb4;
   CREATE DATABASE dock_system_mes CHARACTER SET utf8mb4;
   CREATE USER 'dock'@'localhost' IDENTIFIED BY 'dock';
   GRANT ALL ON dock_system_erp.* TO 'dock'@'localhost';
   GRANT ALL ON dock_system_mes.* TO 'dock'@'localhost';
   ```

   다른 주소 · 계정을 쓰려면 환경변수 `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`를 지정합니다.
3. 비밀값 파일을 만듭니다. 이 파일은 깃에 올라가지 않습니다.
   - `erp-server/local.properties.example` → `erp-server/local.properties`로 복사하고 `jwt.secret`(64자 이상 임의 문자열)을 채웁니다. 네이버 뉴스 · 날씨 API 키는 비워 두면 홈 화면의 그 칸만 빕니다.
   - `pos-server/local.properties.example` → `pos-server/local.properties`로 복사하고 `pos.jwt.secret`을 채웁니다.
4. 화면 패키지를 설치합니다.

   ```bash
   npm --prefix web install
   ```

## 실행

Windows에서는 `서버관리.bat`을 더블클릭한 뒤 **모두 켜기**를 누릅니다. 처음 켤 때는 1~2분 걸리고, 로그는 `%LOCALAPPDATA%\CafeOn\logs`에 쌓입니다.

직접 켤 때는 터미널 세 개에서 각각 실행합니다.

```bash
cd erp-server && ./gradlew bootRun
```

```bash
cd pos-server && ./gradlew bootRun
```

```bash
npm --prefix web start
```

브라우저에서 http://localhost:3000 을 엽니다.

- **본사 ERP:** 처음 실행할 때 사용자가 없으면 초기 관리자 계정이 만들어집니다. 아이디와 비밀번호 기본값은 `erp-server/src/main/resources/application.properties`의 `app.init-admin.*`에 있고, 실제 운영에서는 환경변수 `INIT_ADMIN_ID`, `INIT_ADMIN_PASSWORD`로 바꿉니다.
- **매장 POS:** 본사 계정 없이 씁니다. 본사 ERP의 [데이터 수집]에서 매장별 연동 키를 발급하고, POS 화면에서 그 키로 기기를 등록합니다.
