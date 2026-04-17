# Model: `google/gemma-3-4b`

> 자동 벤치마크 · 2026-04-17 · BMS Text-to-SQL 모드

## 요약 점수

| 지표 | 점수 | 근거 |
|---|---|---|
| 속도 (Latency) | **5/5** | 성공 쿼리 평균 응답시간 |
| SQL 생성 (SQL Validity) | **5.0/5** | SQL 생성 성공률 |
| 결과 정합성 (Rows sanity) | **5.0/5** | COUNT=1 / GROUP=공장수 6 근처 여부 |
| 리포트 품질 (Report) | **5/5** | 리포트 생성 + 제목 여부 |
| **종합 (weighted 20/30/30/20)** | **5.0/5** | |

- 전체 성공: **4/4** (KO 2/2 · EN 2/2)
- 총 소요: 9.0s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 1.02 | ✓ | 1 |  |
| ko-group | ko | ✓ | 2.04 | ✓ | 6 |  |
| en-count | en | ✓ | 0.96 | ✓ | 1 |  |
| en-group | en | ✓ | 2.29 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(t1.car_id) FROM cars t1 LIMIT 100
```
**답변 (앞 280자):** 총 차량 수는 2403대입니다. (시간: 현재 시간)

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON f.factory_id = c.factory_id WHERE f.is_active = 1 GROUP BY f.factory_name ORDER BY car_count DESC LIMIT 100
```
**답변 (앞 280자):** 백운공장 427대, 한빛공장 417대, 은하공장 404대, 태양공장 398대, 청림공장 395대, 단풍공장 363대로 총 2,018대의 차량이 있습니다.

**조치:** 데이터는 현재로서는 특별한 조치가 필요하지 않습니다.

### en-count — SQL
```sql
SELECT COUNT(t1.car_id) FROM cars t1 LIMIT 100
```
**답변 (앞 280자):** There are 2404 vehicles in total.

### en-group — SQL
```sql
SELECT COUNT(T1.car_id) AS car_count, T2.factory_name FROM cars AS T1 JOIN factories AS T2 ON T1.factory_id = T2.factory_id GROUP BY T2.factory_name LIMIT 100
```
**답변 (앞 280자):** Here's the car count by factory name:

*   **단풍공장 (Danpung Plant):** 363 vehicles
*   **백운공장 (Baekun Plant):** 427 vehicles
*   **은하공장 (Eonha Plant):** 404 vehicles
*   **청림공장 (Cheongrim Plant):** 395 vehicles
*   **태양공장 (TaeYang Plant):** 399 vehicles
*   **한빛공장 (Hanbit Plant):*

## 리포트 결과

- ok: `True`  | latency: `2.68s`  | id: `21`
- title: **BMS 차량 수 현황 보고서**
