# Model: `google/gemma-4-26b-a4b`

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
- 총 소요: 57.3s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 10.76 | ✓ | 1 |  |
| ko-group | ko | ✓ | 9.48 | ✓ | 6 |  |
| en-count | en | ✓ | 3.51 | ✓ | 1 |  |
| en-group | en | ✓ | 10.8 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 전체 차량은 총 2,541대입니다.

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 공장별 차량 수 현황입니다.

- **단풍공장**: 386대
- **백운공장**: 451대
- **은하공장**: 430대
- **청림공장**: 415대
- **태양공장**: 417대
- **한빛공장**: 444대

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** There are a total of 2,545 vehicles.

### en-group — SQL
```sql
SELECT T2.factory_name, COUNT(T1.car_id) AS car_count FROM cars AS T1 JOIN factories AS T2 ON T1.factory_id = T2.factory_id GROUP BY T2.factory_name LIMIT 100
```
**답변 (앞 280자):** The number of vehicles grouped by factory name is as follows:

| Factory Name | car_count |
| :--- | :--- |
| Baegun Plant | 453 |
| Hanbit Plant | 445 |
| Eunha Plant | 430 |
| Taeyang Plant | 417 |
| Cheongrim Plant | 415 |
| Danpung Plant | 387 |

## 리포트 결과

- ok: `True`  | latency: `22.77s`  | id: `30`
- title: **대화 리포트**
