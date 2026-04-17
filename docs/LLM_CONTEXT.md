# LLM 컨텍스트 전달 방식 (ver02 · Spring Boot)

> **문서 버전:** 2026-04-17 (ver02 포팅 반영)
> **현재 아키텍처:** RAG-lite + Text-to-SQL (관리자 설정으로 모드 선택)
> **주요 구현 파일:** `backend/src/main/java/com/evernex/bms/controller/ChatController.java`, `service/ChatService.java`, `service/LlmClient.java`

> 원본 Node 버전의 대응 파일: `backend/src/routes/chat.js`

---

## 1. 개요

본 챗봇은 두 가지 응답 방식을 지원합니다.
LLM 이 DB 에 직접 접근하지 않고, **백엔드가 사용자 질문을 키워드/패턴 분석해 필요한 데이터를 미리 조회한 뒤 JSON 형태로 프롬프트에 주입** (RAG-lite) 하거나,
**LLM 이 스키마를 보고 SQL 을 생성 → 백엔드가 검증·실행 → 결과를 다시 LLM 에 전달** (Text-to-SQL) 합니다.

| 방식 | 설명 | 현재 채택 |
|---|---|:---:|
| RAG-lite | 키워드 기반 사전 조회 → 프롬프트에 데이터 주입 | ✅ (기본값) |
| Text-to-SQL | LLM 이 스키마 보고 SQL 생성 → 백엔드 실행 → 결과 다시 LLM 에 전달 | ✅ (관리자 설정) |
| Tool Use | LLM 이 OpenAI 호환 `tool_calls` 로 함수 호출 | ❌ (모델 호환성 미보장) |
| Pure RAG | 벡터 DB 로 유사도 검색 후 주입 | ❌ |

**모드 전환:** 관리자 설정(`Settings.vue` ⑥ LLM 모델 선택 섹션)의 라디오 버튼으로 선택.
설정값은 `admin_settings` 테이블의 `llm_mode` 키 (`rag_lite` | `text_to_sql`).

**근거:**
- `controller/ChatController.java` — `@PostMapping("")` 핸들러가 `chat.currentMode()` 값에 따라 `ChatService.handleRagLite()` 또는 `handleTextToSql()` 분기
- `db/DataSeeder.java` — `seedSettings()` 내 `llm_mode=rag_lite` 기본값 시드
- `service/SettingsService.java` → `get(key)` 로 런타임 조회

---

## 2. 전체 요청 흐름

```
[1] 프론트 (Chatbot.vue)
       │ POST /api/v1/chat { message, session_id, locale }
       ▼
[2] Spring MVC (ChatController.java)
       │ AuthContext.require() 로 사용자 확인
       │ ChatService 가 buildContext(message) 실행 — JdbcTemplate 로 SQLite 조회
       │ Jackson 으로 JSON 직렬화 → 프롬프트에 삽입
       ▼
[3] LM Studio (127.0.0.1:1234)
       │ /v1/chat/completions (OpenAI 호환)
       │ Gemma-4 등 로컬 모델 추론
       │ 호출 주체: LlmClient (java.net.http.HttpClient, HTTP/1.1 강제)
       ▼
[4] 백엔드 응답 처리
       │ content 또는 reasoning_content 추출
       │ llm_chat_logs 테이블에 INSERT
       ▼
[5] 프론트에 { answer, context } 반환
```

**근거:**
| 단계 | 코드 위치 |
|---|---|
| [1] 프론트 POST 호출 | `frontend/src/components/Chatbot.vue` — `api.post('/chat', { message: q, session_id: sessionId, locale: locale.value })` |
| [2] 컨트롤러 진입 | `ChatController.chat(...)` → `ChatService.handleRagLite(...)` |
| [2] buildContext 실행 | `ChatService.buildContext(message, ...)` |
| [2] JSON 직렬화 + 프롬프트 | `ChatService` 내 `objectMapper.writeValueAsString(context)` |
| [3] LM Studio 호출 | `LlmClient.call(baseUrl, model, systemPrompt, userContent, maxTokens)` |
| [4] 로그 저장 | `ChatService` 내 `jdbc.update("INSERT INTO llm_chat_logs ...", ...)` |
| [5] 응답 반환 | `ChatService` 가 `Map<String,Object>{answer,context}` 반환 → Spring 이 JSON 직렬화 |

---

## 3. 프롬프트 구조

LM Studio 에 전달되는 메시지는 2개 role 로 구성됩니다.

### system 메시지 (고정)

```
You are a BMS quality inspection assistant for EverNex smart factory.
Rules:
- Answer ONLY based on provided data. Never invent data.
- Provide evidence (vehicle IDs, values, timestamps).
- When suggesting actions, specify vehicle and action.
- Use Korean.
- Be concise and actionable.
```

영문 응답이 필요한 경우를 위해 `SYSTEM_PROMPT_EN` 도 별도 정의됩니다 (공장·모델·국가명의 영문 치환 규칙 포함).

**근거:** `ChatService.java` 상수 — `SYSTEM_PROMPT_KO`, `SYSTEM_PROMPT_EN`. `LlmClient.call()` 호출 시 첫 메시지로 전달.

### user 메시지 (동적)

```
사용자 질문: {사용자 입력}

참고 데이터 (JSON):
{buildContext 결과를 JSON 직렬화한 문자열}
```

**근거:** `ChatService.handleRagLite()` 내 `String userContent = "사용자 질문: " + message + "\n\n참고 데이터 (JSON):\n" + contextText;`

---

## 4. buildContext() 데이터 명세

`ChatService.buildContext(message, uid, allowedFactoryIds, adminScope)` 가 생성하는 JSON 구조.

| 필드 | 조건 | 건수 | 내용 | 코드 위치 |
|------|------|------|------|-----------|
| `stats.total` | **항상** | 숫자 1개 | 전체 차량 수 | `SELECT COUNT(*) AS c FROM cars` + Factory scope |
| `stats.byStatus` | **항상** | 상태별 그룹 | `[{ status, count }, ...]` | `GROUP BY current_status` |
| `openAlerts` | **항상** | **최대 10건** | 미해결 경보 (`current_status != 'RESOLVED'`) | `LIMIT 10` |
| `filteredCars` | 키워드 매칭 시 | **최대 20건** | 상태 키워드에 해당하는 차량 목록 | 키워드 매칭 분기 |
| `selectedVehicle` | `VH-YYYYMMDD-NNNN` 패턴 매칭 시 | 1건 | 차량 + 최신 배터리 측정값 | `Pattern.compile("VH-\\d{8}-\\d{4}")` |
| `statusLabels` | **항상** | 고정 객체 | 상태코드 → 한글명 매핑 | `Constants.STATUS_LABELS_KR` |

**Factory scope:** `db/FactoryScope.java` 가 로그인 사용자의 `allowedFactoryIds` 를 SQL WHERE 절로 주입하여 공장별 데이터 격리를 강제합니다 (`clause(alias)` 가 `AND factory_id IN (?, ?)` 형태 문자열 + 파라미터 반환).

### JSON 예시

```json
{
  "stats": {
    "total": 1200,
    "byStatus": [
      { "status": "BATTERY_INSPECTION", "count": 42 },
      { "status": "SHIPMENT_WAITING", "count": 156 }
    ]
  },
  "openAlerts": [
    { "car_id": "VH-20260415-0023", "alert_type": "SOC_ABNORMAL",
      "alert_message": "...", "severity": "HIGH",
      "occurred_at": "2026-04-15T09:12:34", "current_status": "OPEN" }
  ],
  "filteredCars": [
    { "car_id": "VH-20260415-0152", "model_name": "노바 X5",
      "current_status": "BATTERY_INSPECTION", "destination_country": "미국" }
  ],
  "selectedVehicle": null,
  "statusLabels": { "ARRIVAL": "입고", "BATTERY_INSPECTION": "배터리 검사중", ... }
}
```

---

## 5. 키워드 매칭 테이블

사용자 메시지에 아래 키워드가 포함되면 해당 상태의 차량을 조회해 `filteredCars` 에 담습니다.

| 키워드 | 매칭 상태 |
|--------|-----------|
| `검사중` | BATTERY_INSPECTION, CELL_INSPECTION, RE_INSPECTION |
| `출고대기` | SHIPMENT_WAITING |
| `출고완료` | SHIPMENT_COMPLETE |
| `이상` | ANOMALY_DETECTED, QA_MAINTENANCE |
| `경고` | ANOMALY_DETECTED, QA_MAINTENANCE |
| `입고` | ARRIVAL |

**테이블 정의 근거:** `ChatService.java` 내 `KEYWORD_TO_STATUSES` 상수 / 분기.

SQL 템플릿:
```sql
SELECT car_id, model_name, current_status, destination_country
FROM cars
WHERE current_status IN (?)
  {factoryScope}
ORDER BY updated_at DESC
LIMIT 20
```

**첫 번째로 매칭된 키워드만 사용**됩니다. 여러 키워드가 포함돼도 우선순위는 위 테이블 순서 (iteration + early return).

---

## 6. 차량 ID 직접 참조

사용자 메시지에 `VH-YYYYMMDD-NNNN` 형식의 차량 ID가 포함되면, 해당 차량의 상세 데이터를 `selectedVehicle` 에 담습니다.

| 항목 | 내용 |
|---|---|
| 정규식 | `Pattern.compile("VH-\\d{8}-\\d{4}")` |
| 차량 조회 | `SELECT * FROM cars WHERE car_id=?` + factory scope |
| 배터리 조회 | `SELECT * FROM batteries WHERE car_id=?` |
| 최신 측정값 조회 | `SELECT * FROM battery_measurements WHERE battery_id=? ORDER BY inspected_at DESC LIMIT 1` |
| 응답 구조 | `{ car, measurement }` |

---

## 7. LM Studio 호출 파라미터

OpenAI 호환 `/v1/chat/completions` 엔드포인트 호출 (`LlmClient.call(...)`).

| 파라미터 | 값 | 비고 |
|---|---|---|
| `baseURL` | `${LLM_BASE_URL:http://127.0.0.1:1234}` | `application.yml` / 환경변수, `ChatService.baseUrl()` 로 노출 |
| `model` | `SettingsService.get("llm_model", env.LLM_MODEL or "local-model")` | 관리자 설정에서 변경 가능 |
| `temperature` | `0.2` | 일관된 답변 유도 |
| `max_tokens` | `3000` | thinking 모델 reasoning 공간 확보 |
| HTTP version | **HTTP/1.1 강제** | Java 17+ `HttpClient` 기본은 HTTP/2 + ALPN — LM Studio 와 호환 안됨. `HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)` 필수 |

**근거:** `LlmClient.java` — `HttpClient` 빌더 + request body 조립 (`ObjectMapper` 로 JSON 직렬화).

---

## 8. 응답 처리

LM Studio 응답에서 답변을 추출하는 우선순위.

| 순서 | 조건 | 동작 |
|---|---|---|
| 1 | `choices[0].message.content` 값 존재 | 해당 문자열을 `answer` 로 사용 |
| 2 | content 가 빈 문자열 + `reasoning_content` 존재 | `reasoning_content` 를 fallback 사용 |
| 3 | 둘 다 비고 `finish_reason == "length"` | 502 "토큰 한도 도달" 에러 |
| 4 | 그 외 | 502 "빈 응답" 에러 |

**Gemma-4, DeepSeek-R1** 같은 thinking 모델은 `<think>` 추론 토큰에 max_tokens 를 다 써버려 `content` 가 비는 경우가 있어 fallback 처리가 필요합니다.

**근거:** `LlmClient.call()` 반환 타입 `Result(answer, reasoningContent, finishReason, model)` + 분기 처리.

---

## 9. 로깅

모든 대화는 `llm_chat_logs` 테이블에 저장됩니다.

**스키마 정의:** `backend/src/main/java/com/evernex/bms/db/SchemaInitializer.java`
```sql
CREATE TABLE IF NOT EXISTS llm_chat_logs (
  log_id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER,
  session_id TEXT,
  user_message TEXT,
  assistant_message TEXT,
  context_data TEXT,
  created_at TEXT NOT NULL
);
```

**INSERT (ChatService):**
```java
jdbc.update(
  "INSERT INTO llm_chat_logs (user_id,session_id,user_message,assistant_message,context_data,created_at) VALUES (?,?,?,?,?,?)",
  uid, sessionId, message, answer,
  contextJson.length() > 4000 ? contextJson.substring(0, 4000) : contextJson,
  TimeUtil.nowISO()
);
```

| 필드 | 세부사항 |
|---|---|
| `session_id` | 프론트에서 난수 생성 (`Math.random().toString(36).slice(2)`) |
| `context_data` | buildContext 결과 JSON 을 **최대 4000자 truncate** |
| `user_id` | JWT 인증된 사용자 UID (`AuthContext.require().uid()`) |

---

## 10. 에러 메시지 분류

Spring 의 `@ControllerAdvice` (`security/GlobalExceptionHandler.java`) 가 `ApiException` 을 통일된 JSON 포맷으로 반환합니다.

| 상황 | HTTP | 에러 문구 | 코드 위치 |
|---|---|---|---|
| LM Studio 응답이 200 아님 + "No models loaded" | 502 | "로드된 모델이 없습니다..." | `LlmClient.call()` |
| LM Studio 응답이 200 아님 + "model not found" | 502 | "요청한 모델({model})을 찾을 수 없습니다..." | `LlmClient.call()` |
| content + reasoning_content 모두 비고 `finish_reason="length"` | 502 | "응답이 토큰 한도에 도달했습니다..." | `LlmClient.call()` |
| content 가 빈 응답 | 502 | "LLM 이 빈 응답을 반환했습니다" | `LlmClient.call()` |
| ECONNREFUSED / fetch failed / ENOTFOUND | 502 | "LM Studio({baseURL})에 연결 실패..." | `ChatController.chat()` catch |
| timeout / aborted | 502 | "LLM 응답 대기 시간 초과 (cold start)..." | `ChatController.chat()` catch |
| 기타 catch | 502 | "LLM 서버에 연결할 수 없습니다..." | `ChatController.chat()` catch |

**프론트 측 detail 보존:**
- `frontend/src/composables/api.js` — `err.detail = data.detail; err.status = r.status; throw err;`
- `frontend/src/components/Chatbot.vue` — `catch (e) { history.value.push({ ..., detail: e.detail, error: true }); }`
- 에러 메시지 하단에 `m.detail` 원문을 monospace 로 표시.

응답 JSON: `{ "error": "<message>", "detail": "<optional>" }` — 원본 Express 포맷과 동일.

---

## 11. RAG-lite 방식의 한계 (Text-to-SQL 모드로 해소)

아래 한계는 **RAG-lite 모드에서만** 발생합니다. Text-to-SQL 모드로 전환하면 대부분 해결됩니다 (섹션 13 참조).

| # | 한계 | 예시 질문 | 근거 | Text-to-SQL 로 해결? |
|---|------|-----------|-----------|:---:|
| 1 | 경보는 항상 미해결만 | "해결된 경보 보여줘" → 미해결 10건만 감 | `WHERE a.current_status!='RESOLVED'` 하드코딩 | ✅ |
| 2 | 날짜 범위 조회 불가 | "어제 이상난 차량" → 데이터 없음 | `buildContext()` 내 어떤 쿼리도 `created_at`/`occurred_at` 으로 필터하지 않음 | ✅ |
| 3 | 공장/모델/국가 필터 없음 | "청림공장 벡터 E6만" → 키워드 매칭 안됨 | 키워드 테이블이 상태만 다룸 | ✅ |
| 4 | 집계 질문 불가 | "청림공장 평균 SOC" → 원시 데이터만 있고 계산 불가 | `buildContext` 가 반환하는 집계는 `stats.byStatus` 뿐 | ✅ |
| 5 | 키워드 매칭 시에만 차량 목록 | 상태 키워드 없는 질문은 통계만 전달 | 조건부 분기 | ✅ |
| 6 | 프롬프트 토큰 고정 | 질문과 무관하게 경보 10건 + 상태 라벨 항상 포함 | LIMIT 10 고정, `statusLabels` 항상 포함 | ✅ |

**Text-to-SQL 의 단점:** LLM 호출 횟수 2배 (SQL 생성 + 답변 생성), 응답 지연 증가, 모델이 틀린 SQL 생성 가능성.

---

## 12. 관련 코드 위치 (ver02)

| 항목 | 파일 |
|------|------|
| 컨트롤러 진입점 (모드 분기) | `controller/ChatController.java` → `@PostMapping("")` |
| RAG-lite 핸들러 | `service/ChatService.java` → `handleRagLite()`, `buildContext()` |
| Text-to-SQL 핸들러 | `service/ChatService.java` → `handleTextToSql()` |
| SQL 검증 | `service/ChatService.java` → `validateSql()`, `injectLimit()`, `extractJson()`, `ALLOWED_TABLES` |
| LLM HTTP 호출 | `service/LlmClient.java` (HTTP/1.1 강제, Jackson) |
| 시스템 프롬프트 | `service/ChatService.java` → `SYSTEM_PROMPT_KO/EN`, `SQL_SYSTEM_PROMPT`, `SCHEMA_PROMPT` |
| 상태 정의 | `domain/Constants.java` → `STATUSES`, `STATUS_LABELS_KR` |
| 대화 로그 스키마 | `db/SchemaInitializer.java` → `llm_chat_logs` |
| 관리자 설정 시드 | `db/DataSeeder.java` → `seedSettings()` (`llm_model`, `llm_mode`) |
| 설정 읽기 | `service/SettingsService.java` → `get(key, default)` |
| 공장 scope 적용 | `db/FactoryScope.java` → `clause(alias)` |
| 예외 → JSON 변환 | `security/GlobalExceptionHandler.java` |
| 관리자 UI | `frontend/src/views/Settings.vue` → ⑥ LLM 모델 선택 섹션 |
| 프론트 챗봇 UI | `frontend/src/components/Chatbot.vue` |
| 에러 detail 전달 | `frontend/src/composables/api.js` → `req()` |

---

## 13. Text-to-SQL 모드 동작

관리자 설정에서 `llm_mode = 'text_to_sql'` 로 변경하면 활성화됩니다.

### 13.1 2단계 호출 흐름

```
[1] 사용자 질문 (예: "청림공장 벡터 E6 평균 SOC")
       │
       ▼
[2] 1차 LLM 호출 — SQL 생성
    system: SQL_SYSTEM_PROMPT (JSON 만 반환하도록 강제)
    user:   SCHEMA_PROMPT + 사용자 질문
    응답:   {"sql": "SELECT AVG(bm.soc) ...", "reasoning": "..."}
       │
       ▼
[3] 백엔드 검증·실행 (ChatService)
    - extractJson() — markdown fence·외곽 텍스트 제거 후 JSON 파싱
    - validateSql() — SELECT 시작, 화이트리스트 테이블, 세미콜론 다중 구문 차단
    - injectLimit() — 기존 LIMIT 없으면 LIMIT 100 자동 주입
    - jdbc.queryForList(sql) 실행
       │
       ▼
[4] 2차 LLM 호출 — 자연어 답변 생성
    system: SYSTEM_PROMPT (일반 답변용)
    user:   질문 + 실행한 SQL + 결과(JSON) → 지정 언어로 답변하게 지시
       │
       ▼
[5] { answer, context: {mode, sql, reasoning, rows_count, rows}, mode: "text_to_sql" }
```

**근거:**
- `ChatService.handleTextToSql()` 함수가 위 5단계를 순차 실행
- `LlmClient.call()` 헬퍼가 1차·2차 호출 모두에서 재사용

### 13.2 SQL 검증 규칙

| 규칙 | 실패 시 에러 | 코드 |
|---|---|---|
| SELECT 로 시작 (주석 제거 후) | "SELECT 쿼리만 허용됩니다" | `validateSql()` 정규식 `^\s*SELECT\b` |
| 세미콜론 다중 구문 금지 | "다중 구문은 허용되지 않습니다" | `validateSql()` — 세미콜론 위치 검사 |
| FROM/JOIN 뒤 테이블명이 화이트리스트 | "허용되지 않는 테이블: {name}" | `ALLOWED_TABLES` Set |
| SELECT 인데 실행 실패 | "SQL 실행 실패: {msg}" | `handleTextToSql()` try/catch |

### 13.3 화이트리스트 테이블

민감 테이블 (`users`, `admin_settings`) 은 **제외**.

```
cars, batteries, battery_measurements, alerts,
factories, countries, battery_cells, battery_cell_measurements,
car_status_histories, alert_status_histories,
process_step_histories, inspection_results
```

**근거:** `ChatService.java` → `private static final Set<String> ALLOWED_TABLES = Set.of(...)`

### 13.4 프롬프트 크기

- **SCHEMA_PROMPT**: 12개 테이블의 핵심 컬럼 + 타입 + 한글 설명 + 상태 라벨 매핑 (약 1,500 토큰)
- **1차 호출 입력**: SCHEMA_PROMPT + 질문 = 약 1,500~1,600 토큰
- **2차 호출 입력**: 질문 + SQL + 결과 JSON (최대 100건 × 평균 200 토큰 = ~20,000 토큰 가능)

최대 행이 많은 쿼리는 2차 호출 입력이 커서 응답이 느려지거나 토큰 한도를 넘을 수 있음.

### 13.5 로깅

`llm_chat_logs.context_data` 에 다음 형태로 저장 (4000자 truncate):
```json
{ "mode": "text_to_sql",
  "sql": "SELECT ... LIMIT 100",
  "reasoning": "...",
  "rows_count": 42,
  "rows": [ ...최대 50건... ] }
```

### 13.6 Text-to-SQL 모드 에러 패턴

| 상황 | 에러 문구 |
|---|---|
| LLM 응답에서 JSON 추출 실패 | "LLM 이 생성한 SQL JSON 을 파싱할 수 없습니다: ..." |
| JSON 에 `sql` 필드 없음 | "LLM 응답에 sql 필드가 없습니다" |
| 검증 실패 (SELECT 아님) | "SQL 검증 실패: SELECT 쿼리만 허용됩니다" |
| 검증 실패 (비허용 테이블) | "SQL 검증 실패: 허용되지 않는 테이블: {name}" |
| SQL 실행 예외 | "SQL 실행 실패: {sqlite 에러 메시지}" |

### 13.7 공장 권한 강제

Text-to-SQL 도 **FactoryScope 가 WHERE 절에 강제 주입**되어, 로그인 사용자가 접근 불가능한 공장의 데이터는 조회되지 않습니다.
LLM 이 생성한 SQL 이 공장 필터를 누락해도 백엔드가 `cars.factory_id IN (...)` 조건을 append 합니다 (관리자 전체 scope 가 아닐 때).

**근거:** `ChatService.handleTextToSql()` 내 `FactoryScope.appendToSql(sql, allowedFactoryIds)`.

### 13.8 리포트 생성 연계

챗봇 대화를 기반으로 `/api/v1/chat/report` 에 POST 하면:
- Text-to-SQL 모드 assistant 메시지의 `sql` / `reasoning` / `rows_count` 가 리포트 `content.messages[]` 에 보존됨 (프론트 `Chatbot.vue createReport()`)
- LLM 이 대화 요약 JSON 생성 → `reports` 테이블에 INSERT
- 리포트 상세 화면(`Reports.vue`) 에서 "🗄️ 실행된 SQL 쿼리" 섹션으로 시각화 + Markdown 다운로드

**근거:**
- 프론트 → `frontend/src/components/Chatbot.vue createReport()`
- 백엔드 → `controller/ChatController.java` `@PostMapping("/report")`
- 저장 → `reports` 테이블 (`content` 컬럼에 JSON 전체 저장)
