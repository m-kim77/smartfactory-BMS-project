# LLM 응답 방식 비교 — RAG-lite vs Text-to-SQL

> **문서 버전:** 2026-04-17 (ver02 · Spring Boot 포팅 반영)
> **관련 문서:** `LLM_CONTEXT.md` (기술 구현 상세), `RUN_GUIDE.md` (실행 방법)

챗봇은 두 가지 응답 방식을 지원하며, 관리자 설정(⑥ LLM 모델 선택 → 응답 방식)에서 선택할 수 있습니다.

---

## 1. 한 줄 요약

| 모드 | 비유 |
|------|------|
| **RAG-lite** | 미리 준비한 답변 카드 |
| **Text-to-SQL** | LLM이 직접 DB에 쿼리 날리는 방식 |

---

## 2. 동작 방식

### RAG-lite (키워드 기반)

```
[1] 사용자 질문
       │
       ▼
[2] 백엔드가 키워드 감지 → 미리 정해진 SQL 실행
    (인식 키워드: 검사중 / 출고대기 / 출고완료 / 이상 / 경고 / 입고)
       │
       ▼
[3] 결과를 프롬프트에 JSON으로 주입
       │
       ▼
[4] LLM 호출 1회 → 답변
```

**호출 횟수:** LLM 1회

### Text-to-SQL (자유 질의)

```
[1] 사용자 질문
       │
       ▼
[2] 1차 LLM 호출 — 스키마 보고 SQL 작성
       │
       ▼
[3] 백엔드 검증 (SELECT only, 테이블 화이트리스트) + 실행
       │
       ▼
[4] 2차 LLM 호출 — 쿼리 결과 보고 자연어 답변
       │
       ▼
[5] 최종 답변
```

**호출 횟수:** LLM 2회

---

## 3. 답할 수 있는 질문 범위

| 질문 예시 | RAG-lite | Text-to-SQL |
|-----------|:---:|:---:|
| "검사중인 차량 보여줘" | ✅ | ✅ |
| "출고대기 차량 알려줘" | ✅ | ✅ |
| "VH-20260415-0152 차량 상태" | ✅ | ✅ |
| "청림공장 벡터 E6만 보여줘" | ❌ (공장·모델 필터 없음) | ✅ |
| "어제 발생한 경보" | ❌ (날짜 필터 없음) | ✅ |
| "청림공장 평균 SOC" | ❌ (집계 불가) | ✅ |
| "해결된 경보 알려줘" | ❌ (미해결만 조회) | ✅ |
| "4월 15일 이후 CRITICAL 경보" | ❌ | ✅ |
| "모델별 불량률 알려줘" | ❌ | ✅ |
| "노바 브랜드 차량 중 출고 대기" | ❌ | ✅ |

---

## 4. 장단점 비교

| 항목 | RAG-lite | Text-to-SQL |
|------|----------|-------------|
| **속도** | 빠름 (5~20초, 1회 호출) | 느림 (10~40초, 2회 호출) |
| **정확도** | 정해진 쿼리만 쓰므로 안정적 | LLM이 틀린 SQL 쓸 가능성 있음 |
| **유연성** | 6개 키워드에 한정 | 자유 질의 가능 |
| **LLM 모델 요구 수준** | 낮음 (데이터 요약만 필요) | 높음 (SQL 작성 능력 필요) |
| **토큰 사용량** | 적음 (context ~500~2,000 토큰) | 많음 (스키마 + 결과 ~3,000~20,000 토큰) |
| **실패 가능성** | 낮음 | 있음 (JSON 파싱·SQL 구문·비허용 테이블) |
| **실패 시 동작** | 거의 안 실패 | 에러 메시지 표시 (폴백 없음) |
| **민감 데이터 노출 위험** | 없음 (정적 쿼리) | 없음 (users/admin_settings 차단) |

---

## 5. 언제 어떤 걸 써야 하나

### RAG-lite 선택 상황
- 데모·시연처럼 **응답 속도가 중요**할 때
- 정해진 질문만 반복적으로 받는 상황 (예: 현장 공정 작업자)
- LLM이 작은 모델이거나 SQL 작성을 잘 못할 때
- 안정성이 최우선일 때

### Text-to-SQL 선택 상황
- 관리자가 **탐색적 분석**을 하고 싶을 때
- 날짜·공장·모델별 **집계·필터링** 질문이 많을 때
- 응답 시간이 좀 걸려도 **자유로운 질문**이 필요할 때
- LLM이 SQL 생성에 충분한 성능을 가질 때 (예: Gemma-4 26B 이상, GPT-4급)

---

## 6. 모드 전환 방법

1. 관리자 계정으로 로그인
2. 좌측 메뉴 → **관리자 설정**
3. **⑥ LLM 모델 선택** 섹션
4. **응답 방식** 라디오 버튼에서 원하는 모드 선택
5. 선택 즉시 저장됨 (다음 챗봇 질문부터 적용)

---

## 7. 실패 시 동작 (Text-to-SQL)

Text-to-SQL 모드는 실패할 수 있으며, 실패 시 폴백 없이 **에러 메시지가 챗봇에 표시**됩니다.

| 실패 유형 | 사용자에게 보이는 문구 |
|-----------|-----------------------|
| LLM이 SQL 대신 엉뚱한 응답 | "LLM이 생성한 SQL JSON을 파싱할 수 없습니다" |
| SELECT가 아닌 쿼리 생성 | "SQL 검증 실패: SELECT 쿼리만 허용됩니다" |
| 비허용 테이블 접근 시도 | "SQL 검증 실패: 허용되지 않는 테이블: users" |
| SQL 구문 오류 | "SQL 실행 실패: near \"...\": syntax error" |

해결책: **RAG-lite로 임시 전환** 후 동일 질문을 더 단순한 키워드로 다시 물어봅니다.

---

## 8. 관련 코드 위치 (ver02 · Spring Boot)

| 항목 | 파일 |
|---|---|
| 모드 분기 진입점 | `backend/src/main/java/com/evernex/bms/controller/ChatController.java` → `@PostMapping("")` |
| RAG-lite 핸들러 | `service/ChatService.java` → `handleRagLite()` |
| Text-to-SQL 핸들러 | `service/ChatService.java` → `handleTextToSql()` |
| 스키마 프롬프트 | `service/ChatService.java` → `SCHEMA_PROMPT` 상수 |
| SQL 검증 | `service/ChatService.java` → `validateSql()`, `ALLOWED_TABLES` |
| LLM HTTP 클라이언트 | `service/LlmClient.java` (HTTP/1.1 강제) |
| 설정 시드 | `db/DataSeeder.java` → `seedIfEmpty()` (키: `llm_mode`) |
| 설정 읽기 | `service/SettingsService.java` → `get(String key)` |
| UI 라디오 버튼 | `frontend/src/views/Settings.vue` → ⑥ LLM 섹션 (원본과 동일) |

> 원본 Node 버전의 대응 파일: `backend/src/routes/chat.js`, `backend/src/db/init.js`.

---

## 9. LLM 제공자 선택 (ver02 추가)

로컬 LM Studio 외에 **OpenAI / Google Gemini 유료 API**도 사용할 수 있습니다.

| 제공자 | 설정 키 | 엔드포인트 |
|--------|---------|-----------|
| LM Studio (로컬) | `llm_provider=lm_studio` | `llm_base_url` (기본 `http://127.0.0.1:1234`) |
| OpenAI | `llm_provider=openai` | `https://api.openai.com/v1/chat/completions` (+ `llm_openai_api_key`) |
| Google Gemini | `llm_provider=gemini` | `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` (+ `llm_gemini_api_key`) |

- 관리자 설정 → ⑥ LLM 섹션에서 제공자 선택 시 입력란이 자동 전환됩니다 (URL vs API 키).
- API 키는 UI에서 마스킹되며, 서버 측 `admin_settings` 테이블에 저장됩니다.
- 제공자별 모델 목록:
  - OpenAI: `gpt-4o`, `gpt-4o-mini`, `gpt-4-turbo`, `gpt-4.1`, `gpt-4.1-mini`, `gpt-3.5-turbo`, `o1-mini`, `o3-mini`
  - Gemini: `gemini-2.0-flash`, `gemini-2.0-flash-lite`, `gemini-1.5-flash`, `gemini-1.5-flash-8b`, `gemini-1.5-pro`
  - LM Studio: `GET /v1/models` 호출로 동적 조회
- 공통 OpenAI 호환 `/chat/completions` 스키마를 사용하므로 호출 코드는 `LlmClient.call(Config, system, user, maxTokens)` 1곳으로 통합.

---

## 10. 리포트에 SQL 포함 (Text-to-SQL 모드)

Text-to-SQL 모드로 답변을 받은 뒤 **리포트 생성** 버튼을 누르면, 챗봇이 실행한 SQL·근거·조회 건수가 리포트에 함께 저장됩니다.

**데이터 흐름:**

```
[챗봇] Text-to-SQL 응답 수신
   └→ history.push({ role: 'assistant', content, context })
           (context = { mode, sql, reasoning, rows_count, rows })

[리포트 생성] POST /api/v1/chat/report
   └→ assistant 메시지에서 sql/reasoning/rows_count 추출 → messages[].sql 필드로 전송

[백엔드 저장] reports.content.messages[]
   └→ 각 assistant 메시지에 { role, content, sql, reasoning, rows_count }

[리포트 상세] Reports.vue
   └→ "🗄️ 실행된 SQL 쿼리" 섹션 표시 (근거 + 쿼리 결과 건수 포함)
   └→ 원본 대화 섹션에도 SQL 인라인 표시
   └→ Markdown 다운로드 시 SQL 블록 포함
```

**UI 위치:**
- 리포트 상세 화면: `reports.sectionSql` 섹션 (SQL 개별 카드 · 근거 인용 · rows 수)
- Markdown 다운로드: `## 실행된 SQL 쿼리` 섹션 + 각 assistant 답변 하단에 `sql` 코드블록

**관련 코드:**

| 항목 | 파일:라인 |
|---|---|
| 백엔드 context 생성 | `service/ChatService.java:387-392` (`handleTextToSql`) |
| 챗봇 history 저장 | `components/Chatbot.vue:42` |
| 리포트 전송 payload | `components/Chatbot.vue:86-90` |
| 리포트 저장 시 SQL 보존 | `controller/ChatController.java:112-126` |
| SQL 추출 (계산 속성) | `views/Reports.vue:52-61` (`sqlQueries`) |
| SQL 섹션 렌더링 | `views/Reports.vue:275-293` |
| 대화 내 SQL 인라인 | `views/Reports.vue:311-312` |
| Markdown SQL 포함 | `views/Reports.vue:94-112` (`downloadMd`) |
