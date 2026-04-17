# 실행 가이드 (ver02 · Spring Boot)

> **문서 버전:** 2026-04-17
> **대상:** `04_BMS_SF_ver02` (Node.js → Java Spring Boot 포팅 버전)
> 원본 Node.js 버전의 실행 방법은 `04_BMS_SF/docs/RUN_GUIDE.md` 참고.

## 사전 준비

- **JDK 17+** (권장: `brew install openjdk@17`)
- **Maven 3.9+** (`brew install maven` — 맥/리눅스)
- **Node.js 18+** (프론트엔드용; ESM·fetch 지원 필요)
- macOS / Linux / Windows 모두 OK
- (선택) **LM Studio** — 챗봇 LLM 응답용. 없어도 대시보드·차량검사는 정상 동작합니다.

자바 설치 확인:
```bash
java -version    # 17.x 이상이어야 함
mvn -v           # 3.9.x 이상 권장
```

## 1. 백엔드 실행 (Spring Boot)

```bash
cd 04_BMS_SF_ver02/backend
mvn spring-boot:run
```

- 최초 실행 시 `backend/database/bms.db` SQLite 파일이 자동 생성됩니다.
- 사용자, 공장, 국가, 설정, 샘플 차량이 자동 시드됩니다 (`db/DataSeeder.java`).
- `SimulationService` 의 `@Scheduled(fixedDelay=500)` 루프가 시작되어 검사 파이프라인을 진행합니다.
- API: `http://localhost:3000/api/v1`
- 헬스 체크: `http://localhost:3000/health`

> 포트는 기본 3000이며, 원본 Node 버전과 동일한 API 계약을 유지합니다 (`/api/v1/*`).
> 프론트엔드는 `.env` 변경 없이 그대로 붙습니다.

### 실행 가능한 JAR 로 빌드

```bash
cd 04_BMS_SF_ver02/backend
mvn clean package -DskipTests
java -jar target/bms-sf-backend-1.0.0.jar
```

## 2. 프론트엔드 실행

```bash
cd 04_BMS_SF_ver02/frontend
cp .env.example .env       # 필요 시 편집 (API URL 등)
npm install
npm run dev
```

- 개발 서버: `http://localhost:5173`
- 브라우저 접속 → 로그인 화면 → 테스트 계정 중 하나로 로그인
- 테스트 계정: `admin@evernex.com / admin1234`, `operator@evernex.com / operator1234`

## 3. LM Studio (선택)

1. LM Studio 설치 후 임의의 chat-tuned 모델 로드 (예: Gemma-4, Qwen2.5-Coder, Llama-3.x)
2. 좌측 **Local Server → Start Server**
3. 기본 URL `http://127.0.0.1:1234` (변경 시 백엔드 환경변수 `LLM_BASE_URL` 수정)
4. 프론트 오른쪽 하단 캐릭터 버튼을 눌러 대화창 열기
5. 관리자 설정 → `⑥ LLM 모델 선택` 에서 로드된 실제 모델 ID 선택

LM Studio가 꺼져 있으면 챗봇은 다음과 같이 안내합니다:
> "LLM 서버에 연결할 수 없습니다. LM Studio가 실행 중인지 확인해주세요."

> **주의 (Java 17+ HttpClient):** Spring Boot 측 `LlmClient` / `SettingsController` 는 **HTTP/1.1 로 강제** 구성되어 있어야 합니다. Java HttpClient 기본값(HTTP/2 + ALPN) 은 LM Studio 와 호환되지 않아 `/llm/models` 30초 타임아웃이 발생합니다.
> 코드 위치: `LlmClient.java`, `SettingsController.java` 의 `HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)`.

## 4. 환경 변수

ver02는 `.env` 파일 대신 **환경 변수 + Spring `application.yml`** 로 설정을 주입합니다.

### 백엔드 (환경 변수)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `PORT` | 3000 | 서버 리슨 포트 |
| `JWT_SECRET` | `bms-sf-secret-change-in-production-must-be-long-enough-for-hs256` | HS256 서명 키 (32바이트 이상) |
| `DB_PATH` | `./database/bms.db` | SQLite 파일 경로 |
| `LLM_BASE_URL` | `http://127.0.0.1:1234` | LM Studio URL |
| `LLM_MODEL` | `local-model` | 기본 모델 ID (관리자 설정에서 재정의 가능) |

예시 (맥/리눅스):
```bash
export JWT_SECRET="change-me-in-production-at-least-32-chars"
export LLM_BASE_URL="http://127.0.0.1:1234"
mvn spring-boot:run
```

실제 정의 위치: `backend/src/main/resources/application.yml`

### 프론트엔드 `frontend/.env`
```
VITE_API_BASE_URL=http://localhost:3000/api/v1
```

## 5. 주요 페이지

| URL | 설명 |
|-----|------|
| `/login` | 로그인 / 회원가입 |
| `/` | 대시보드 (5초 폴링) |
| `/vehicles` | 차량 목록 / 필터 / 검색 / 수동 등록 |
| `/vehicles/:carId` | 차량 상세 — 게이지, 셀 히트맵, 타임라인, 경보 |
| `/alerts` | 경보 로그 / 일괄 확인·해결 |
| `/reports` | 챗봇 대화 기반 리포트 목록/상세 |
| `/settings` | 관리자 설정 (admin 전용) |
| `/users` | 사용자 관리 (admin 전용) |

## 6. 데이터 초기화

```bash
rm 04_BMS_SF_ver02/backend/database/bms.db
# 백엔드를 재시작하면 스키마/기본 데이터가 재시드됩니다.
```

`SchemaInitializer` 가 최초 실행 시 필요한 테이블을 생성하고, `DataSeeder` 가 사용자·공장·국가·설정·샘플 차량을 투입합니다.

## 7. MySQL 마이그레이션

`04_BMS_SF_ver02/sql/schema.sql` + `seed.sql` 을 프로덕션 MySQL 에 적용하세요.
SQLite 와 컬럼/제약은 호환 설계입니다 (REAL → DECIMAL 등 자동 매핑).
DataSource 전환 시 `application.yml` 의 `spring.datasource.url` / `driver-class-name` 을 변경하고 `sqlite-jdbc` 대신 `mysql-connector-j` 를 추가하면 됩니다.

## 8. 자주 겪는 문제

- **`mvn: command not found`**: Maven 미설치. `brew install maven` 또는 <https://maven.apache.org/download.cgi>
- **`Unsupported class file major version`**: JDK 버전 불일치. `java -version` 이 17 이상인지 확인. `brew install openjdk@17` 후 `JAVA_HOME` 재설정:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  ```
- **포트 3000 이미 사용 중**: `lsof -i :3000` 로 기존 프로세스 찾아 종료, 또는 `PORT=3001 mvn spring-boot:run`
- **`/llm/models` 30초 지연 / 빈 결과**: 위 §3의 HTTP/1.1 강제 설정 확인. 백엔드 **재시작** 필요 (핫리로드 안됨).
- **관리자 설정의 LLM 모델 드롭다운이 `local-model` 만 보임**: LM Studio 가 꺼져있거나 Start Server 누르지 않음. 로드하면 드롭다운이 실제 모델 ID 로 채워짐.
- **CORS 오류**: 프론트 `.env` 의 API URL 이 백엔드 주소와 일치하는지 확인 (`CorsConfig.java`).
- **로그인 실패 (401)**: DB 파일을 삭제하고 재시작하면 기본 계정 (`admin1234` / `operator1234`) 이 재시드됩니다.
- **`Could not find or load main class`**: `mvn clean package` 로 JAR 재빌드.

## 9. Node.js 원본과의 주요 차이

| 영역 | 원본 (`04_BMS_SF`) | 포팅 (`04_BMS_SF_ver02`) |
|---|---|---|
| 실행 명령 | `npm install && npm run dev` | `mvn spring-boot:run` |
| 의존성 설치 | `package.json` → `node_modules/` | `pom.xml` → Maven local repo `~/.m2/` |
| 설정 파일 | `backend/.env` | `application.yml` + 환경변수 |
| 재시작 방식 | nodemon 핫리로드 | `mvn spring-boot:run` 재실행 (또는 Spring DevTools) |
| 빌드 산출물 | (없음, 소스 직접 실행) | `target/bms-sf-backend-1.0.0.jar` |
| 코드 위치 | `backend/src/routes/*.js` | `backend/src/main/java/com/evernex/bms/controller/*.java` |
