# Model: `qwen/qwen3.5-9b`

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
- 총 소요: 147.5s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 26.32 | ✓ | 1 |  |
| ko-group | ko | ✓ | 33.73 | ✓ | 6 |  |
| en-count | en | ✓ | 27.11 | ✓ | 1 |  |
| en-group | en | ✓ | 26.87 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 

전체 차량 총수는 **2479 대**입니다.
(증거: SQL 쿼리 결과 JSON 에서 `"COUNT(*)" : 2479` 값 확인)

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f INNER JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_id ORDER BY car_count DESC LIMIT 100
```
**답변 (앞 280자):** 

공장별 차량 수 조회 결과를 확인했습니다. (총 6 건)

- **백운공장**: 441 대
- **한빛공장**: 433 대
- **은하공장**: 416 대
- **청림공장**: 410 대
- **태양공장**: 409 대
- **단풍공장**: 377 대

*데이터 출처: factories, cars 테이블 JOIN 집계 결과 (factory_name, car_count)*

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 

Total vehicles: 2496.
Evidence: SQL query result shows COUNT(*) = 2496.

### en-group — SQL
```sql
SELECT COUNT(c.car_id) AS car_count, f.factory_name FROM cars c JOIN factories f ON c.factory_id = f.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 

Vehicle counts grouped by factory:

- **Danpung Plant**: 379 vehicles
- **Baekun Plant**: 445 vehicles
- **Eonha Plant**: 419 vehicles
- **Cheongrim Plant**: 411 vehicles
- **Taeyang Plant**: 414 vehicles
- **Hanbit Plant**: 435 vehicles

## 리포트 결과

- ok: `True`  | latency: `33.5s`  | id: `27`
- title: **대화 리포트**
