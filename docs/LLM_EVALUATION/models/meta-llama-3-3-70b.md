# Model: `meta/llama-3.3-70b`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **2/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **5.0/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **5.0/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **5/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **4.4/5** | |

- 전체 성공: **4/4** (KO 2/2 · EN 2/2)
- 총 소요: 109.9s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 32.52 | ✓ | 1 |  |
| ko-group | ko | ✓ | 18.57 | ✓ | 6 |  |
| en-count | en | ✓ | 7.64 | ✓ | 1 |  |
| en-group | en | ✓ | 22.48 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(car_id) FROM cars LIMIT 100
```
**답변 (앞 280자):** 전체 차량은 총 2,646대 있습니다.

### ko-group — SQL
```sql
SELECT T2.factory_name, COUNT(T1.car_id) AS car_count FROM cars T1 INNER JOIN factories T2 ON T1.factory_id = T2.factory_id GROUP BY T2.factory_name LIMIT 100
```
**답변 (앞 280자):** 단풍공장: 401대, 백운공장: 476대, 은하공장: 453대, 청림공장: 427대, 태양공장: 432대, 한빛공장: 461대로 총 6개의 공장이 있습니다.

### en-count — SQL
```sql
SELECT COUNT(car_id) FROM cars LIMIT 100
```
**답변 (앞 280자):** There are 2653 vehicles in total.

### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** Here are the vehicle counts grouped by factory name:

- Maple Plant: 402
- White Cloud Plant: 477
- Galaxy Plant: 453
- Cheongrim Plant: 429
- Sunshine Plant: 433
- Hanbit Plant: 463 

Translated factory names:
1. 단풍공장 → Maple Plant
2. 백운공장 → White Cloud Plant
3. 은하공장 → Galaxy Pl

## 리포트 결과

- ok: `True`  | latency: `28.68s`  | id: `34`
- title: **BMS 차량 현황 리포트**
