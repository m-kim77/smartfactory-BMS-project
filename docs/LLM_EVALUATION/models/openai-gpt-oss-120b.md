# Model: `openai/gpt-oss-120b`

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
- 총 소요: 37.4s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 22.89 | ✓ | 1 |  |
| ko-group | ko | ✓ | 3.12 | ✓ | 6 |  |
| en-count | en | ✓ | 1.73 | ✓ | 1 |  |
| en-group | en | ✓ | 3.47 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) AS total_cars FROM cars LIMIT 100
```
**답변 (앞 280자):** 전체 차량 수는 **2,696대**입니다.

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f LEFT JOIN cars c ON c.factory_id = f.factory_id GROUP BY f.factory_name ORDER BY car_count DESC LIMIT 100
```
**답변 (앞 280자):** | factory_name | car_count |
|--------------|-----------|
| 백운공장     | 484       |
| 한빛공장     | 468       |
| 은하공장     | 458       |
| 태양공장     | 439       |
| 청림공장     | 436       |
| 단풍공장     | 411       |

### en-count — SQL
```sql
SELECT COUNT(*) AS total_vehicles FROM cars LIMIT 100
```
**답변 (앞 280자):** There are **2,697 vehicles** in total.

### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM cars c JOIN factories f ON c.factory_id = f.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** **Vehicle count by factory**

| Factory (English) | Car Count |
|-------------------|-----------|
| Danpung Plant     | 412 |
| Baekun Plant      | 484 |
| Eunha Plant       | 459 |
| Cheongrim Plant   | 436 |
| Taeyang Plant     | 439 |
| Hanbit Plant      | 468 |

These numbers

## 리포트 결과

- ok: `True`  | latency: `6.14s`  | id: `36`
- title: **공장별 차량 수 및 전체 차량 현황**
