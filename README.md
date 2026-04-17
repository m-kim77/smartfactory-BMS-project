# EverNex BMS Smart Factory — Spring Boot Edition (ver02)

`04_BMS_SF`의 Node.js 백엔드를 **Java 17 + Spring Boot 3.3** 로 포팅한 버전입니다.
프론트엔드(Vue 3 + Vite)와 DB 스키마(SQLite), API 계약, 비즈니스 로직은 동일합니다.

## 변경 사항

| 영역 | 원본 (`04_BMS_SF`) | 포팅 (`04_BMS_SF_ver02`) |
|---|---|---|
| 백엔드 런타임 | Node.js (ESM) | **Java 17 + Spring Boot 3.3.4** |
| 웹 프레임워크 | Express.js | **Spring MVC** |
| DB 접근 | `better-sqlite3` | **Spring JdbcTemplate** + `sqlite-jdbc` |
| JWT | `jsonwebtoken` | **jjwt 0.12.x** |
| 비밀번호 해시 | `bcryptjs` | **spring-security-crypto `BCryptPasswordEncoder`** |
| 시뮬레이션 루프 | `setInterval(500)` | **`@Scheduled(fixedDelay=500)`** |
| LLM HTTP | `fetch` | **`java.net.http.HttpClient`** |
| 프론트엔드 | Vue 3 (변경 없음) | Vue 3 (그대로 복사) |

## 요구사항

- **JDK 17+** (`openjdk@17` 권장)
- **Maven 3.9+** (`brew install maven`)
- **Node.js 18+** (프론트엔드용)
- **LM Studio** (AI 챗봇 사용 시, 127.0.0.1:1234 에서 OpenAI 호환 서버 실행)

## 실행 방법

### 1) 백엔드 (포트 3000)

```bash
cd backend
mvn spring-boot:run
```

초기 실행 시 `backend/database/bms.db` 가 자동 생성되고 샘플 데이터가 시드됩니다.
- 관리자: `admin@evernex.com` / `admin1234`
- 운영자: `operator@evernex.com` / `operator1234`

환경 변수(선택):
```bash
export PORT=3000
export JWT_SECRET="change-me-in-production-at-least-32-chars"
export LLM_BASE_URL="http://127.0.0.1:1234"
export LLM_MODEL="local-model"
export DB_PATH="./database/bms.db"
```

### 2) 프론트엔드 (포트 5173)

```bash
cd frontend
npm install
npm run dev
```

기본적으로 `http://localhost:3000/api/v1` 로 API를 호출합니다. 원본 프로젝트와 동일.

### 3) LM Studio (선택)

AI 챗봇/리포트 기능을 쓰려면 LM Studio 를 실행하고 아무 모델이나 로드하세요.
관리자 설정에서 `llm_mode` 를 `rag_lite` / `text_to_sql` 로 변경 가능합니다.

## 빌드

```bash
cd backend
mvn clean package -DskipTests
# → target/bms-sf-backend-1.0.0.jar
java -jar target/bms-sf-backend-1.0.0.jar
```

## 디렉토리 구조

```
04_BMS_SF_ver02/
├── backend/                    # Spring Boot
│   ├── pom.xml
│   ├── database/               # SQLite 파일 위치
│   └── src/main/
│       ├── java/com/evernex/bms/
│       │   ├── BmsApplication.java
│       │   ├── config/         # CORS 등
│       │   ├── controller/     # 8개 REST 컨트롤러
│       │   ├── db/             # 스키마 초기화, 시드, FactoryScope
│       │   ├── domain/         # Constants
│       │   ├── security/       # JwtAuthFilter, AuthContext
│       │   └── service/        # Simulation, Vehicle, Chat, Llm 등
│       └── resources/application.yml
├── frontend/                   # Vue 3 (원본과 동일)
├── docs/                       # 원본 문서
└── sql/                        # MySQL DDL 등
```

## API 호환성

원본 Express 경로와 **완전히 동일**한 응답 형태를 유지합니다:
- `GET /health`
- `POST /api/v1/auth/{login,signup}` / `GET /api/v1/auth/me`
- `GET/POST/PUT/DELETE /api/v1/vehicles...`
- `GET/POST /api/v1/alerts...`
- `GET /api/v1/dashboard/stats`
- `POST /api/v1/chat`, `POST /api/v1/chat/report`
- `GET/PUT /api/v1/settings...`
- `GET/PUT/DELETE /api/v1/users/{id}...`
- `GET/DELETE /api/v1/reports/{id}`

에러 형식도 동일: `{ "error": "<message>", "detail": "<optional>" }`

## Git 관점 참고

원본 저장소와 **폴더가 다르기 때문에** 기존 파일과의 diff 가 발생하지 않습니다.
새 폴더 전체가 "added" 로 표시되므로 병렬 실험/비교에 적합합니다.
