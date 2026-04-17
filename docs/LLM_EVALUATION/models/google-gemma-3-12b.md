# Model: `google/gemma-3-12b`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **4/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **5.0/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **5.0/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **5/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **4.8/5** | |

- 전체 성공: **4/4** (KO 2/2 · EN 2/2)
- 총 소요: 27.0s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 8.14 | ✓ | 1 |  |
| ko-group | ko | ✓ | 4.19 | ✓ | 6 |  |
| en-count | en | ✓ | 2.81 | ✓ | 1 |  |
| en-group | en | ✓ | 5.72 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 총 2444대의 차량이 있습니다.

### ko-group — SQL
```sql
SELECT factories.factory_name, COUNT(cars.car_id) AS car_count FROM cars JOIN factories ON cars.factory_id = factories.factory_id GROUP BY factories.factory_name LIMIT 100
```
**답변 (앞 280자):** 단풍공장: 371대, 백운공장: 435대, 은하공장: 410대, 청림공장: 402대, 태양공장: 404대, 한빛공장: 423대가 확인되었습니다.

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** There are a total of 2446 vehicles.

### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM cars c JOIN factories f ON c.factory_id = f.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** Here's the number of vehicles grouped by factory name:

*   **Danpung Plant:** 372
*   **Baegun Plant:** 435
*   **Eonha Plant:** 410
*   **Cheongrim Plant:** 402
*   **Taeyang Plant:** 404
*   **Hanbit Plant:** 424

## 리포트 결과

- ok: `True`  | latency: `6.17s`  | id: `25`
- title: **BMS 차량 수 현황 리포트**
