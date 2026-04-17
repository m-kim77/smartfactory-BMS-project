# Model: `deepseek/deepseek-r1-0528-qwen3-8b`

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
- 총 소요: 101.8s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 12.01 | ✓ | 1 |  |
| ko-group | ko | ✓ | 25.29 | ✓ | 6 |  |
| en-count | en | ✓ | 13.86 | ✓ | 1 |  |
| en-group | en | ✓ | 41.21 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) AS total_cars FROM cars LIMIT 100
```
**답변 (앞 280자):** 
전체 차량 수는 **2452 대**입니다.  
증거: 쿼리 결과에서 `total_cars` 값이 **2452**으로 확인되었습니다.

### ko-group — SQL
```sql
SELECT f.factory_name AS factory_name, COUNT(c.car_id) AS car_count FROM factories AS f LEFT JOIN cars AS c ON c.factory_id = f.factory_id GROUP BY f.factory_id LIMIT 100
```
**답변 (앞 280자):** 
청림공장: 405 대  
은하공장: 412 대  
백운공장: 437 대  
단풍공장: 373 대  
태양공장: 405 대  
한빛공장: 427 대  

해당 공장들의 차량 수는 모두 다릅니다.

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 
Based on the data, there are a total of 2462 vehicles.

### en-group — SQL
```sql
SELECT factories.factory_name AS factory_name, COUNT(cars.car_id) AS car_count FROM cars JOIN factories ON cars.factory_id = factories.factory_id GROUP BY factory_name LIMIT 100
```
**답변 (앞 280자):** 
Based on the provided data:

- Maple Plant: 373 vehicles  
- White Cloud Plant: 440 vehicles  
- Galaxy Plant: 414 vehicles  
- Cheongrim Plant: 407 vehicles  
- Sun Plant: 406 vehicles  
- Hanbit Plant: 429 vehicles

## 리포트 결과

- ok: `True`  | latency: `9.44s`  | id: `26`
- title: **BMS 공장별 차량 수 분석**
