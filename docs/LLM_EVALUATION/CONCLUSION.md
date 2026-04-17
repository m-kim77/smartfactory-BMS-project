# 최종 결론 — 2026-04-17

> **범위:** LM Studio 로컬 모델 20종 × Text-to-SQL 모드 × 한국어+영어 4쿼리 + 1리포트
> **원본:** [`RESULTS_2026_04_17.md`](RESULTS_2026_04_17.md) · 모델별 상세는 `models/<모델>.md` 참조
> **주의:** RAG-lite 모드는 이번 라운드에 포함되지 않음. 현재 backend 기본 모드가 Text-to-SQL이기 때문. RAG-lite 측정은 별도 후속 과제.

## 1. 종합 순위표

### Text-to-SQL 모드

총 20종 중 **16종**이 4쿼리 파이프라인을 완주, **4종**은 모델 로드 실패(메모리/호환성).

| 순위 | 모델 | 총점 | 속도(20%) | SQL(30%) | 행(30%) | 리포트(20%) | 총시간 | 리포트 ID |
|---|---|---|---|---|---|---|---|---|
| 1 | `google/gemma-3-4b` | **5.00** | 5 | 5.0 | 5.0 | 5 | 9.0s | 21 |
| 2 | `openai/gpt-oss-20b` | **5.00** | 5 | 5.0 | 5.0 | 5 | 18.9s | 28 |
| 3 | `qwen/qwen3-coder-next` | **5.00** | 5 | 5.0 | 5.0 | 5 | 23.2s | 32 |
| 4 | `nvidia/nemotron-3-nano` | **4.80** | 4 | 5.0 | 5.0 | 5 | 46.6s | 22 |
| 4 | `nvidia/nemotron-3-nano-4b` | **4.80** | 4 | 5.0 | 5.0 | 5 | 30.2s | 23 |
| 4 | `google/gemma-3-12b` | **4.80** | 4 | 5.0 | 5.0 | 5 | 27.0s | 25 |
| 4 | `google/gemma-3-27b` | **4.80** | 4 | 5.0 | 5.0 | 5 | 48.3s | 29 |
| 4 | `google/gemma-4-26b-a4b` | **4.80** | 4 | 5.0 | 5.0 | 5 | 57.3s | 30 |
| 4 | `openai/gpt-oss-120b` | **4.80** | 4 | 5.0 | 5.0 | 5 | 37.4s | 36 |
| 10 | `nousresearch/hermes-4-70b` | **4.60** | 3 | 5.0 | 5.0 | 5 | 69.3s | 35 |
| 11 | `deepseek/deepseek-r1-0528-qwen3-8b` | **4.40** | 2 | 5.0 | 5.0 | 5 | 101.8s | 26 |
| 11 | `qwen/qwen3.5-9b` | **4.40** | 2 | 5.0 | 5.0 | 5 | 147.5s | 27 |
| 11 | `nvidia/nemotron-3-super` | **4.40** | 2 | 5.0 | 5.0 | 5 | 124.1s | 33 |
| 11 | `meta/llama-3.3-70b` | **4.40** | 2 | 5.0 | 5.0 | 5 | 109.9s | 34 |
| 15 | `nemotron3-nano-4b-uncensored` | **4.08** | 4 | 3.8 | 3.8 | 5 | 30.9s | 24 |
| 16 | `qwen/qwen3.5-35b-a3b` | **3.68** | 2 | 3.8 | 3.8 | 5 | 129.2s | 31 |
| — | `google/gemma-4-e2b` | 0.00 | — | — | — | — | load fail | — |
| — | `google/gemma-4-e4b` | 0.00 | — | — | — | — | load fail | — |
| — | `supergemma4-26b-uncensored-mlx-v2` | 0.00 | — | — | — | — | load fail | — |
| — | `google/gemma-4-31b` | 0.00 | — | — | — | — | load fail | — |

### RAG-lite 모드

미측정. 본 라운드는 Text-to-SQL 모드 벤치만 수행함.

### 결합 점수

측정 범위가 Text-to-SQL 단일 모드이므로 생략 (향후 RAG-lite 라운드 완료 후 갱신).

---

## 2. 카테고리별 1위

| 카테고리 | 추천 모델 | 근거 |
|---|---|---|
| 정확도 최고 | `google/gemma-3-4b`, `openai/gpt-oss-20b`, `qwen/qwen3-coder-next` (3파전) | KO/EN 4쿼리 모두에서 올바른 SQL + 정확한 `rows_count` 반환 |
| 속도 최고 (cold+warm 평균) | **`google/gemma-3-4b`** | 4쿼리 평균 2.25s, 총 9.0초로 압도적 |
| Warm 속도 최고 | `qwen/qwen3-coder-next` / `openai/gpt-oss-120b` | warm 쿼리에서 1.2–3.5초 (첫 쿼리만 모델 로드 오버헤드) |
| 한국어 품질 최고 | `google/gemma-3-4b`, `openai/gpt-oss-20b` | ko-count·ko-group 모두 SQL + 정확한 요약, 리포트 제목 자연스러움 |
| 영어 품질 최고 | `openai/gpt-oss-20b`, `qwen/qwen3-coder-next` | en-group에서 컬럼 alias(`car_count`) 정확히 반영 |
| SQL 생성 최고 | 4.4점 이상 14종 모두 (SQL 5.0/5) | JSON 포맷 손상 없이 `SELECT` 생성 — 단, 4B uncensored, 35B-a3b는 일부 쿼리에서 실패 |
| 리포트 작성 최고 | `openai/gpt-oss-20b` (4.95s) / `google/gemma-3-27b` (10.5s) / `openai/gpt-oss-120b` (6.14s) | 제목이 "대화 리포트"가 아닌 실제 주제형 제목 ("BMS 차량 수 및 공장별 분포 리포트" 등) |
| 가성비 최고 (리소스/성능) | **`google/gemma-3-4b`** | 4B 모델로 5.0/5 — 대형 모델 대비 VRAM/추론비 대폭 절감 |
| 작은 모델 베스트 (< 10B) | `google/gemma-3-4b` | 만점. 경쟁자: nvidia/nemotron-3-nano(4.8), qwen3.5-9b(4.4, 단 속도 느림) |

---

## 3. 권장 운영 조합

### 추천 안 — A: "범용 프로덕션 기본" ⭐

- **Text-to-SQL:** **`google/gemma-3-4b`**
- **근거:**
  - 종합 5.0/5, 총 9.0초 (4쿼리) — 실사용 UX 즉시성 우수
  - 4B 소형 모델 → 일반 데스크탑/서버에서 무난히 구동
  - 한·영 양쪽 모두 정확한 SQL·rows·리포트 생성
  - 대시보드 챗봇 UX에서 "질문 → 답변" 체감 지연 최소
- **예상 응답 시간:** cold ~3s, warm ~1–2s
- **필요 VRAM:** ~4 GB 내외 (Q4_K_M 기준 예상치)

### 추천 안 — B: "저리소스·단일 모델"

- **단일 모델:** **`nvidia/nemotron-3-nano-4b`** (또는 `google/gemma-3-4b`)
- **근거:** 4B급에서 SQL·rows·리포트 모두 완벽. 모델 스위칭 없이 한 벌만 유지해도 충분
- **타협:** cold-start가 약간 더 길지만 장시간 세션에서는 warm 상태가 유지됨

### 추천 안 — C: "최고 성능 (리소스 제약 없음)"

- **Text-to-SQL:** **`openai/gpt-oss-20b`** 또는 **`qwen/qwen3-coder-next`**
- **근거:**
  - 두 모델 모두 종합 5.0, gpt-oss-20b는 warm에서 1.2초 수준
  - 20B MoE / coder 계열 모델이라 복잡한 스키마 추론에도 대응 여지 큼 (본 벤치는 단순 2테이블 JOIN만 측정 — 실제 복잡 쿼리 대응력은 후속 테스트 필요)
- **필요 VRAM:** 20B Q4 기준 ~12 GB

### 추천 안 — D: "최대 추론 성능" (서버 자원 충분 시)

- **Text-to-SQL:** `openai/gpt-oss-120b`
- **근거:** 종합 4.8, warm 쿼리 1.7–3.5초로 120B급 치고 빠름. 장기적으로 복잡 질의 확장 대응용 후보
- **주의:** 총 시간 37.4s는 cold-start 포함 — 실서비스에서는 warm 상태 유지 필수

---

## 4. 기각된 모델과 이유

| 모델 | 기각 사유 |
|---|---|
| `google/gemma-4-e2b` | LM Studio 로드 실패 (2초 내 즉시 에러) — 현 하드웨어/런타임 비호환 |
| `google/gemma-4-e4b` | 동상 — 로드 실패 |
| `google/gemma-4-31b` | 동상 — 로드 실패. **기존 기본 모델이었으나 현 환경에서 사용 불가** |
| `supergemma4-26b-uncensored-mlx-v2` | 동상 — 로드 실패 |
| `nemotron3-nano-4b-uncensored` | en-group 쿼리에서 존재하지 않는 컬럼(`c.fact_id`)을 참조 → SQL 실행 실패. 스키마 환각 경향 존재 |
| `qwen/qwen3.5-35b-a3b` | en-group에서 JSON 래퍼 파싱 실패 (plain SQL을 JSON 없이 반환). 3/4 성공이지만 안정성 부족 |
| `deepseek/deepseek-r1-0528-qwen3-8b`, `qwen/qwen3.5-9b`, `nvidia/nemotron-3-super`, `meta/llama-3.3-70b` | 기각은 아니지만, 평균 100초 이상 → 실시간 챗봇 UX에 부적합. 배치/오프라인 용도로는 가능 |

> 참고: 이전 세션에서 기본 모델로 지정되어 있던 **`google/gemma-4-31b`가 현 하드웨어에서 로드 불가**라는 점이 본 평가로 확인됨. 기본 모델을 `google/gemma-3-4b` 또는 `openai/gpt-oss-20b`로 교체 권장.

---

## 5. 발견된 시스템 개선점

- [ ] **기본 LLM 모델 설정 재검토**: `llm_model` 기본값 `google/gemma-4-31b`가 현 테스트 환경에서는 로드 실패. `google/gemma-3-4b`를 기본값 후보로 제안.
- [ ] **JSON 래퍼 강인화**: 일부 모델(qwen3.5-35b-a3b)이 JSON 없이 바로 `SELECT ...`를 반환하는 케이스가 있음. `ChatService.extractJson` 또는 프롬프트 측에서 fenced/unwrapped SQL도 복구 시도하도록 보강 여지.
- [ ] **컬럼명 환각 방지**: uncensored 계열 모델은 `c.fact_id` 같이 존재하지 않는 컬럼명을 만드는 경향. 시스템 프롬프트에 스키마 DDL을 더 명시적으로 넣는 실험 필요.
- [ ] **Cold-start 마스킹 UI**: 첫 쿼리에 모델 로드 비용이 섞임. 챗봇 UI에서 "모델 로드 중…" 같은 상태를 노출하면 UX가 개선될 것.
- [ ] **리포트 제목 품질**: deepseek-r1, qwen3.5-9b, llama-3.3-70b 등은 제목이 단순히 "대화 리포트"로 나옴. 리포트 생성 프롬프트에 "제목은 대화의 핵심 주제를 반영해야 한다"는 지시 강화 검토.
- [ ] **벤치 자동화 재사용**: `/tmp/bms_bench.py` + `bms_bench_analyze.py`를 프로젝트 내 `scripts/` 하위로 이관하여 새 모델 추가 시 재실행 가능하게 할 것.

---

## 6. 후속 테스트 제안

- **RAG-lite 모드 벤치** — 동일 방법론을 RAG-lite에 적용하여 두 모드의 결합 점수 산출
- **복잡 쿼리 세트** — 3개 이상 테이블 JOIN, 서브쿼리, 윈도우 함수가 섞인 프롬프트로 재평가 (현 세트는 2테이블 COUNT/GROUP이라 단순함)
- **대규모 컨텍스트 부하** — 차량 1,000대 이상 상태에서 응답 품질·지연 재측정
- **스트리밍 TTFT 측정** — 서버측 스트리밍 도입 시 첫 토큰 지연(Time to First Token) 별도 측정
- **다국어 혼합 질의** — 한 세션에서 한·영 섞어 사용 시 컨텍스트 전환 품질 측정
- **보안 필터 평가** — 일반 사용자 세션(WHERE factory_id IN (...) 강제 적용)에서 SQL 생성 동작 확인
- **환각 대조** — 본 벤치는 SQL 구조만 채점. 자연어 답변의 숫자 일치성(rows_count vs 본문 총합) 자동 검증 스크립트 추가 필요.
