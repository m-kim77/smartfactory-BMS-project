# Model: `google/gemma-4-31b`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **0/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **0.0/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **0.0/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **0/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **0.0/5** | |

- 전체 성공: **0/4** (KO 0/2 · EN 0/2)
- 총 소요: 8.0s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✗ | 2.03 | ✗ | None | {     "error": {         "message": "Failed to load model \"google/gemma-4-31b\" |
| ko-group | ko | ✗ | 1.99 | ✗ | None | {     "error": {         "message": "Failed to load model \"google/gemma-4-31b\" |
| en-count | en | ✗ | 2.03 | ✗ | None | {     "error": {         "message": "Failed to load model \"google/gemma-4-31b\" |
| en-group | en | ✗ | 1.91 | ✗ | None | {     "error": {         "message": "Failed to load model \"google/gemma-4-31b\" |
