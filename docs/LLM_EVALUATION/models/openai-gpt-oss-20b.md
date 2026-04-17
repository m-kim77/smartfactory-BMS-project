# Model: `openai/gpt-oss-20b`

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
- 총 소요: 18.9s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 7.45 | ✓ | 1 |  |
| ko-group | ko | ✓ | 2.08 | ✓ | 6 |  |
| en-count | en | ✓ | 1.25 | ✓ | 1 |  |
| en-group | en | ✓ | 3.12 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) AS total_cars FROM cars LIMIT 100
```
**답변 (앞 280자):** 총 차량 수는 **2,521대**입니다.  
(쿼리 결과: `total_cars : 2521`)

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f LEFT JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 공장별 차량 수는 다음과 같습니다.

| 공장명 | 차량수 |
|--------|--------|
| 단풍공장 | 381 |
| 백운공장 | 447 |
| 은하공장 | 426 |
| 청림공장 | 415 |
| 태양공장 | 415 |
| 한빛공장 | 438 |

### en-count — SQL
```sql
SELECT COUNT(*) AS total_vehicles FROM cars LIMIT 100
```
**답변 (앞 280자):** There are **2,522** vehicles in total.

### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f LEFT JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** **Vehicle count by factory**

| Factory (English) | Car Count |
|-------------------|-----------|
| Danpung Plant    | 382       |
| Baegun Plant     | 447       |
| Eunha Plant      | 426       |
| Cheongrim Plant  | 415       |
| Taeyang Plant    | 415       |
| Hanbit Plant   

## 리포트 결과

- ok: `True`  | latency: `4.95s`  | id: `28`
- title: **BMS 차량 수 및 공장별 분포 리포트**
