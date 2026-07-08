# FeedbackMe

채용 공고와 자기소개서를 기반으로 AI 피드백을 생성하는 웹 애플리케이션입니다. 사용자가 채용 공고 URL, 자기소개서, 선택 첨부 파일을 제출하면 백엔드가 공고 내용을 수집하고 Gemini API를 호출해 직무 적합도, 문장 표현, 논리 구조, 차별화 포인트 중심의 피드백을 제공합니다.

## 주요 기능

- 채용 공고 URL 크롤링
- 자기소개서 기반 AI 피드백 생성
- PDF, DOCX 첨부 파일 분석
- PDF 첨부 파일 이미지 변환 후 Gemini Vision 요청
- Redis 큐 기반 비동기 피드백 처리
- 동일 입력에 대한 Redis 캐시 재사용
- MySQL 기반 피드백 이력 저장
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
- Google Gemini API

## 프로젝트 구조

```text
.
├── docker/                     # Prometheus 등 인프라 설정
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

## 실행 흐름

1. 사용자가 프론트엔드에서 채용 공고 URL, 자기소개서, 첨부 파일을 입력합니다.
2. 백엔드는 채용 공고 URL을 크롤링하고 첨부 파일 내용을 추출합니다.
3. 피드백 요청 이력을 MySQL에 `PENDING` 상태로 저장합니다.
4. 요청 ID를 Redis 큐에 넣고 즉시 `202 Accepted` 응답을 반환합니다.
5. 워커가 큐를 폴링하며 요청을 `PROCESSING` 상태로 변경합니다.
6. Gemini API를 호출해 피드백을 생성합니다.
7. 결과를 MySQL에 저장하고 Redis에 캐싱합니다.
8. 프론트엔드는 상태 조회 API를 주기적으로 호출해 결과를 표시합니다.

## 시작하기

### 사전 준비

- Java 21
- Node.js 22 이상 권장
- Docker, Docker Compose
- Gemini API Key

### 환경 변수

루트의 `.env.example`을 참고해 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

필요한 값은 다음과 같습니다.

```env
MYSQL_ROOT_PASSWORD=change-me-root-password
MYSQL_PASSWORD=change-me-user-password
GEMINI_API_KEY=change-me-gemini-api-key
GRAFANA_ADMIN_PASSWORD=change-me-grafana-password
APP_VERSION=latest
```

로컬 개발 시 기본 DB 계정은 `docker-compose.yml` 기준으로 다음과 같습니다.

- database: `feedbackme`
- username: `user`
- password: `password`
- port: `3306`

`application.yaml`은 `MYSQL_PASSWORD`, `GEMINI_API_KEY` 환경 변수를 사용합니다. 로컬에서 직접 애플리케이션을 실행하는 경우 해당 값을 셸에 설정하거나 `application-secret.yaml`을 별도로 구성하세요.

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

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

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

Vite 개발 서버는 기본적으로 `http://localhost:5173`에서 실행됩니다. 프론트엔드는 `/api` 경로로 백엔드에 요청합니다.

## Docker로 실행

애플리케이션 이미지를 빌드합니다.

```bash
docker build -t feedbackme-app:latest ./feedbackme
```

운영 Compose 구성을 실행합니다.

```bash
docker compose -f docker-compose.prod.yml up -d
```

접속 주소는 다음과 같습니다.

- App: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## API

### 피드백 요청

```http
POST /api/feedback
Content-Type: multipart/form-data
```

요청 필드:

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `url` | string | required | 채용 공고 URL |
| `coverLetter` | string | required | 자기소개서 내용 |
| `file` | file | optional | PDF 또는 DOCX 첨부 파일 |

응답 예시:

```json
{
  "message": "Feedback request accepted.",
  "historyId": 1
}
```

### 피드백 상태 조회

```http
GET /api/feedback/status/{id}
```

응답 예시:

```json
{
  "status": "COMPLETED",
  "result": "## 1. Job fit\n...",
  "updatedAt": "2026-07-08T15:30:00"
}
```

상태 값:

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

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

## 참고 사항

- 업로드 가능한 파일 크기는 기본 설정 기준 최대 10MB입니다.
- 첨부 파일은 PDF, DOCX를 지원합니다.
- Gemini API 호출 결과는 입력값 해시를 기준으로 Redis에 캐싱됩니다.
- 프로덕션에서는 MySQL, Redis 포트를 외부에 공개하지 않는 것을 권장합니다.
