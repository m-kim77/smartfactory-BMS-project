# Model: `nousresearch/hermes-4-70b`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **3/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **5.0/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **5.0/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **5/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **4.6/5** | |

- 전체 성공: **4/4** (KO 2/2 · EN 2/2)
- 총 소요: 69.3s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 16.78 | ✓ | 1 |  |
| ko-group | ko | ✓ | 10.72 | ✓ | 6 |  |
| en-count | en | ✓ | 8.41 | ✓ | 1 |  |
| en-group | en | ✓ | 16.05 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) AS total_cars FROM cars LIMIT 100
```
**답변 (앞 280자):** 전체 차량은 총 2674대 있습니다.

해당하는 데이터가 없습니다.

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f LEFT JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 단풍공장: 405대, 백운공장: 480대, 은하공장: 454대, 청림공장: 434대, 태양공장: 438대, 한빛공장: 465대의 차량이 있습니다.

### en-count — SQL
```sql
SELECT COUNT(car_id) AS total_vehicles FROM cars LIMIT 100
```
**답변 (앞 280자):** There are a total of 2678 vehicles in the dataset.

Evidence:
- Executed SQL: SELECT COUNT(car_id) AS total_vehicles FROM cars LIMIT 100
- Query result: {"total_vehicles": 2678}

### en-group — SQL
```sql
SELECT factory_name, COUNT(cars.car_id) AS car_count FROM factories JOIN cars ON factories.factory_id = cars.factory_id GROUP BY factory_name LIMIT 100
```
**답변 (앞 280자):** The query results show the number of vehicles grouped by factory name:

- 단풍공장 (Danpung Plant): 405 cars
- 백운공장 (Baegun Plant): 482 cars  
- 은하공장 (Eunha Plant): 456 cars
- 청림공장 (Cheongrim Plant): 435 cars
- 태양공장 (Taeyang Plant): 438 cars
- 한빛공장 (Hanbit Plant): 465 cars

No furthe

## 리포트 결과

- ok: `True`  | latency: `17.36s`  | id: `35`
- title: **전체 차량 수 및 공장별 분포 리포트**
