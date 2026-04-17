# Model: `nvidia/nemotron-3-nano`

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
- 총 소요: 46.6s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 9.28 | ✓ | 1 |  |
| ko-group | ko | ✓ | 5.71 | ✓ | 6 |  |
| en-count | en | ✓ | 3.11 | ✓ | 1 |  |
| en-group | en | ✓ | 9.27 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 
전체 차량은 **2413대**입니다. (COUNT(*) = 2413)

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 
공장별 차량 수:

- 단풍공장: 364대  
- 백운공장: 430대  
- 은하공장: 405대  
- 청림공장: 398대  
- 태양공장: 399대  
- 한빛공장: 418대  

위와 같이 각 공장의 차량 수를 확인할 수 있습니다.

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 
There are **2,415** vehicles in total. (Result: `COUNT(*) = 2415`)

### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f JOIN cars c ON c.factory_id = f.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** 
Factory counts (car_count):  
- Maple Plant – 365  
- White Cloud Plant – 430  
- Milky Way Plant – 405  
- Cheongrim Plant – 400  
- Sun Plant – 399  
- Brightness Plant – 418

## 리포트 결과

- ok: `True`  | latency: `19.19s`  | id: `22`
- title: **대화 리포트**
