# Model: `nemotron3-nano-4b-uncensored`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **4/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **3.8/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **3.8/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **5/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **4.08/5** | |

- 전체 성공: **3/4** (KO 2/2 · EN 1/2)
- 총 소요: 30.9s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 4.79 | ✓ | 1 |  |
| ko-group | ko | ✓ | 9.62 | ✓ | 6 |  |
| en-count | en | ✓ | 3.41 | ✓ | 1 |  |
| en-group | en | ✗ | 3.71 | ✗ | None | SQL: SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM cars c JOIN factor |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 
총 2,433대입니다.

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 
단풍공장: 368  
백운공장: 434  
은하공장: 409  
청림공장: 401  
태양공장: 402  
한빛공장: 420

### en-count — SQL
```sql
SELECT COUNT(*) AS total_vehicles FROM cars LIMIT 100
```
**답변 (앞 280자):** 
There are **2,437** vehicles in total (based on the SQL result showing `total_vehicles` = 2437).

## 리포트 결과

- ok: `True`  | latency: `9.38s`  | id: `24`
- title: **Vehicle Count Discrepancy Investigation**
