# Model: `google/gemma-3-27b`

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
- 총 소요: 48.3s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 10.7 | ✓ | 1 |  |
| ko-group | ko | ✓ | 11.19 | ✓ | 6 |  |
| en-count | en | ✓ | 5.18 | ✓ | 1 |  |
| en-group | en | ✓ | 10.7 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 총 차량 대수는 2528대입니다.


### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories AS f JOIN cars AS c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 각 공장별 차량 수는 다음과 같습니다:

*   단풍공장: 384대
*   백운공장: 447대
*   은하공장: 428대
*   청림공장: 415대
*   태양공장: 416대
*   한빛공장: 440대

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** There are a total of 2532 vehicles.


### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM cars c JOIN factories f ON c.factory_id = f.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** Here's the vehicle count by factory:

*   Danpung Plant: 385
*   Baekun Plant: 447
*   Eunha Plant: 428
*   Cheongrim Plant: 415
*   Taeyang Plant: 416
*   Hanbit Plant: 443

## 리포트 결과

- ok: `True`  | latency: `10.51s`  | id: `29`
- title: **BMS 차량 수 현황 (공장별)**
