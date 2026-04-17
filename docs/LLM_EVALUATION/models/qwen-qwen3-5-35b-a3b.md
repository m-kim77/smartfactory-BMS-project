# Model: `qwen/qwen3.5-35b-a3b`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **2/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **3.8/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **3.8/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **5/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **3.68/5** | |

- 전체 성공: **3/4** (KO 2/2 · EN 1/2)
- 총 소요: 129.2s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 23.44 | ✓ | 1 |  |
| ko-group | ko | ✓ | 38.75 | ✓ | 6 |  |
| en-count | en | ✓ | 11.95 | ✓ | 1 |  |
| en-group | en | ✗ | 33.06 | ✗ | None |   {   "sql": "SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 

전체 차량은 총 **2,564 대**입니다.

- **증거**: SQL 쿼리 결과 (COUNT(*) = 2564)

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_id, f.factory_name LIMIT 100
```
**답변 (앞 280자):** 

제공된 쿼리 결과에 따른 공장별 차량 현황입니다.

*   **청림공장**: 419 대
*   **은하공장**: 435 대
*   **백운공장**: 456 대
*   **단풍공장**: 389 대
*   **태양공장**: 422 대
*   **한빛공장**: 453 대

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 

There are **2,578** vehicles in total.

**Evidence:**
- Query Result: `COUNT(*)` = 2578

## 리포트 결과

- ok: `True`  | latency: `22.01s`  | id: `31`
- title: **대화 리포트**
