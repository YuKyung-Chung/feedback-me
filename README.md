# Feedback Me

채용 공고 URL과 이력서/포트폴리오 파일을 기반으로 지원자의 직무 적합도를 분석하는 AI 커리어 전략 서비스입니다.

사용자는 채용 공고 URL과 PDF/DOCX 형식의 이력서 또는 포트폴리오를 제출합니다. 백엔드는 공고 페이지를 크롤링하고 첨부 파일을 추출한 뒤 Gemini API를 통해 직무 적합도, SWOT 기반 지원 전략, 보완 역량, 차별화 강점, 이력서/포트폴리오 개선 제안을 생성합니다.

현재 서비스는 자기소개서 입력을 받지 않습니다. 자기소개서 분석은 추후 별도 탭 또는 프리미엄 기능으로 확장할 예정입니다.

## 서비스 기획

### 핵심 사용자 흐름

1. 사용자가 채용 공고 URL을 입력합니다.
2. 이력서 또는 포트폴리오 PDF/DOCX 파일을 첨부합니다.
3. 분석 버튼 클릭 시 로그인 여부를 확인합니다.
4. 로그인하지 않은 사용자는 로그인/회원가입 화면으로 이동합니다.
5. 로그인 사용자는 잔여 분석권을 확인한 뒤 분석 요청을 접수합니다.
6. 서버가 채용 공고를 크롤링하고 지원자 첨부 자료를 분석합니다.
7. Redis 큐 기반 워커가 Gemini API를 호출해 분석 리포트를 생성합니다.
8. 사용자는 분석 결과를 화면에서 확인하고 Markdown 파일로 내보낼 수 있습니다.
9. 완료된 분석은 마이페이지의 분석 히스토리에서 다시 열람할 수 있습니다.

### 분석 리포트 구성

- 채용공고 핵심 요약
- 지원자 자료 요약
- 직무 적합도 점수
- 점수 근거
- 강하게 매칭되는 요구사항
- 부족하거나 근거가 약한 요구사항
- SWOT 기반 지원 전략
- 보완해야 할 역량
- 차별화된 강점
- 이력서/포트폴리오 개선 제안
- 최종 지원 전략

### 회원/마이페이지

- 이메일 기반 회원가입/로그인
- HttpOnly 쿠키 기반 서버 세션
- 비밀번호 BCrypt 해시 저장
- 로그인 상태별 우측 상단 메뉴
  - 비로그인: 로그인, 회원가입
  - 로그인: 마이페이지, 로그아웃
- 마이페이지 탭 구성
  - 분석 히스토리
  - 결제
  - 회원 관리
  - 분석권 관리

### 분석권/결제 정책

- 회원가입 시 무료 분석권 1회 지급
- 분석 요청 접수 시 분석권 1회 차감
- 분석 실패 시 차감된 분석권 자동 환급
- 잔여 분석권이 없으면 분석 요청 대신 마이페이지 결제 화면으로 안내
- 마이페이지 분석권 관리 탭에서 잔여/무료 지급/구매/사용 횟수 확인
- 결제 상품
  - 분석권 1회: 1,900원
  - 분석권 5회: 7,900원
  - 분석권 10회: 12,900원

현재 로컬 개발 환경에서는 실제 PG 결제 대신 개발 결제 승인 버튼을 제공합니다. 실제 결제 연동은 토스페이먼츠 승인 API 구조를 기준으로 준비되어 있으며, 운영 환경에서는 `TOSS_SECRET_KEY`와 토스 결제창 연동이 필요합니다.

## 주요 기능

- 채용 공고 URL 크롤링
- 공고 제목/회사명 추출 및 히스토리 저장
- PDF, DOCX 첨부 파일 분석
- PDF 첨부 파일 이미지 변환 후 Gemini Vision 요청
- Gemini API 기반 직무 적합도 분석 리포트 생성
- Gemini 일시 장애/과부하 응답 재시도
- Redis 큐 기반 비동기 분석 처리
- 큐 대기 순번 표시
- 동일 입력에 대한 Redis 캐시 재사용
- MySQL 기반 분석 이력 저장
- Markdown 분석 리포트 렌더링
- 분석 리포트 Markdown 내보내기
- 회원가입/로그인/로그아웃
- 사용자별 분석 히스토리
- 분석권 잔액/사용/환급/구매 관리
- 결제 주문 생성 및 토스페이먼츠 승인 API 연동 구조
- 로컬 개발용 결제 승인 흐름
- Prometheus, Grafana 모니터링 구성
- Jenkins와 Docker Compose를 활용한 EC2 배포 구성

## 기술 스택

### Backend

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- Spring Data Redis
- MySQL 8
- H2 Database
- Gradle
- BCrypt password hashing
- Jsoup
- Apache PDFBox
- Apache POI
- Google Gemini API

### Frontend

- React
- TypeScript
- Vite
- lucide-react

### Infra

- Docker, Docker Compose
- Redis
- Prometheus
- Grafana
- Jenkins

## 프로젝트 구조

```text
.
├── docker/                     # Prometheus, Grafana 등 인프라 설정
├── docs/                       # 배포 문서
├── feedbackme/                 # Spring Boot 애플리케이션
│   ├── frontend/               # React/Vite 프론트엔드
│   ├── src/main/java/          # 백엔드 소스 코드
│   └── src/main/resources/     # 애플리케이션 설정
├── docker-compose.yml          # 로컬 인프라 구성
├── docker-compose.prod.yml     # 운영 배포 구성
├── Jenkinsfile                 # Jenkins 배포 파이프라인
└── README.md
```

## 도메인 모델

### 인증

- `app_user`: 사용자 계정
- `auth_session`: HttpOnly 쿠키 기반 로그인 세션

### 분석

- `feedback_history`: 분석 요청/결과 이력
  - 사용자
  - 공고 URL
  - 회사명
  - 공고 제목
  - 첨부 파일명
  - 첨부 텍스트 또는 PDF 이미지
  - 분석 상태
  - 분석 결과

### 분석권/결제

- `user_credit_balance`: 사용자별 분석권 잔액
- `credit_ledger`: 무료 지급, 구매, 사용, 환급 이력
- `payment_order`: 결제 주문 및 승인 상태

## 실행 흐름

1. 사용자가 채용 공고 URL과 첨부 파일을 입력합니다.
2. 프론트엔드는 로그인 여부를 확인합니다.
3. 백엔드는 사용자의 잔여 분석권을 확인합니다.
4. 잔여 분석권이 있으면 공고 URL을 크롤링하고 첨부 파일을 추출합니다.
5. 분석 요청을 MySQL에 `PENDING` 상태로 저장합니다.
6. 분석권 1회를 차감하고 Redis 큐에 요청 ID를 넣습니다.
7. 워커가 큐를 폴링하며 요청을 `PROCESSING` 상태로 변경합니다.
8. Gemini API를 호출해 분석 리포트를 생성합니다.
9. 결과를 MySQL에 저장하고 Redis에 캐싱합니다.
10. 분석이 실패하면 요청을 `FAILED`로 변경하고 분석권을 환급합니다.
11. 프론트엔드는 상태 조회 API를 주기적으로 호출해 결과를 표시합니다.

## 시작하기

### 사전 준비

- Java 21
- Node.js 22 이상 권장
- Docker, Docker Compose
- Gemini API Key
- 실제 결제 연동 시 Toss Payments Secret Key

### 환경 변수

루트의 `.env.example`을 참고해 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

주요 환경 변수:

```env
MYSQL_ROOT_PASSWORD=change-me-root-password
MYSQL_PASSWORD=change-me-user-password
GEMINI_API_KEY=change-me-gemini-api-key
TOSS_SECRET_KEY=change-me-toss-secret-key
PAYMENT_DEV_MODE=true
GRAFANA_ADMIN_PASSWORD=change-me-grafana-password
APP_VERSION=latest
```

로컬 개발 시 기본 DB 계정은 `docker-compose.yml` 기준으로 다음과 같습니다.

- database: `feedbackme`
- username: `user`
- password: `password`
- port: `3306`

## 로컬 개발 실행

### 1. 인프라 실행

```bash
docker compose up -d db redis prometheus grafana
```

### 2. 백엔드 실행

```bash
cd feedbackme
./gradlew bootRun
```

Windows PowerShell:

```powershell
cd feedbackme
.\gradlew.bat bootRun
```

백엔드는 기본적으로 `http://localhost:8080`에서 실행됩니다.

### 3. 프론트엔드 실행

```bash
cd feedbackme/frontend
npm install
npm run dev
```

Vite 개발 서버는 기본적으로 `http://localhost:5173`에서 실행됩니다.

## Docker로 실행

애플리케이션 이미지를 빌드합니다.

```bash
docker build -t feedbackme-app:local ./feedbackme
```

로컬 인프라를 실행합니다.

```bash
docker compose up -d db redis prometheus grafana
```

애플리케이션 컨테이너를 실행합니다.

```bash
docker run -d \
  --name feedbackme-app \
  --network feedbackme_default \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e MYSQL_HOST=db \
  -e MYSQL_PORT=3306 \
  -e MYSQL_DATABASE=feedbackme \
  -e MYSQL_USER=user \
  -e MYSQL_PASSWORD=password \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e GEMINI_API_KEY=your-gemini-api-key \
  -e PAYMENT_DEV_MODE=true \
  feedbackme-app:local
```

접속 주소:

- App: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## API 요약

### Auth

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

### Feedback

```http
POST /api/feedback
GET  /api/feedback/status/{id}
GET  /api/feedback/history
```

`POST /api/feedback` 요청 필드:

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `url` | string | required | 채용 공고 URL |
| `file` | file | required | PDF 또는 DOCX 이력서/포트폴리오 |

상태 값:

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

### Payments

```http
GET  /api/payments/summary
POST /api/payments/orders
POST /api/payments/confirm
POST /api/payments/orders/{orderId}/dev-confirm
```

`/api/payments/confirm`은 실제 토스페이먼츠 결제 승인용 엔드포인트입니다. 프론트에서 결제창 성공 후 받은 `paymentKey`, `orderId`, `amount`를 서버로 전달하면 서버가 주문 정보와 금액을 검증한 뒤 토스 승인 API를 호출합니다.

## DB 확인

DBeaver 또는 MySQL CLI로 로컬 DB에 접속할 수 있습니다.

```text
Host: localhost
Port: 3306
Database: feedbackme
Username: user
Password: password
```

자주 확인하는 테이블:

- `app_user`
- `auth_session`
- `feedback_history`
- `user_credit_balance`
- `credit_ledger`
- `payment_order`

예시 쿼리:

```sql
SELECT id, status, job_url, company_name, job_title, attachment_name, created_at, updated_at
FROM feedback_history
ORDER BY id DESC
LIMIT 20;

SELECT *
FROM user_credit_balance;

SELECT *
FROM credit_ledger
ORDER BY id DESC
LIMIT 20;
```

## 테스트

```bash
cd feedbackme
./gradlew test
```

Windows PowerShell:

```powershell
cd feedbackme
.\gradlew.bat test
```

테스트 프로필은 H2 인메모리 데이터베이스를 사용합니다.

## 모니터링

Spring Actuator와 Micrometer Prometheus Registry가 설정되어 있습니다.

- 로컬 Prometheus: `http://localhost:9090`
- 로컬 Grafana: `http://localhost:3000`
- Prometheus 메트릭 엔드포인트: `http://localhost:8080/actuator/prometheus`

운영 프로필에서는 `health`, `prometheus` 엔드포인트가 노출됩니다.

## 배포

Jenkins 기반 EC2 배포 흐름은 [docs/jenkins-ec2-deploy.md](docs/jenkins-ec2-deploy.md)를 참고하세요.

Jenkins에서 필요한 Secret text credentials:

- `feedbackme-mysql-root-password`
- `feedbackme-mysql-password`
- `feedbackme-gemini-api-key`
- `feedbackme-grafana-admin-password`
- `feedbackme-toss-secret-key`

## 향후 개선 계획

- 실제 토스페이먼츠 결제창 프론트 연동
- 자기소개서 분석 탭 또는 프리미엄 기능
- 분석 결과 점수/SWOT/역량 항목 구조화 저장
- 리포트 PDF 내보내기
- 관리자 페이지
- 회원 정보 수정, 비밀번호 변경, 회원 탈퇴
- 분석 실패 사유 사용자 친화 메시지 개선
- 결제 취소/환불 정책 및 API 연동
