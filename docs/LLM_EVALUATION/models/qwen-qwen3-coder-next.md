# Model: `qwen/qwen3-coder-next`

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
- 총 소요: 23.2s

## 쿼리별 결과

| ID | Locale | 성공 | 응답시간(s) | SQL 생성 | rows_count | 에러 요약 |
|---|---|---|---|---|---|---|
| ko-count | ko | ✓ | 10.68 | ✓ | 1 |  |
| ko-group | ko | ✓ | 2.83 | ✓ | 6 |  |
| en-count | en | ✓ | 1.44 | ✓ | 1 |  |
| en-group | en | ✓ | 2.63 | ✓ | 6 |  |

### ko-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** 전체 차량은 2,599대입니다.

### ko-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM factories f LEFT JOIN cars c ON f.factory_id = c.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** - **단풍공장**: 392대  
- **백운공장**: 462대  
- **은하공장**: 444대  
- **청림공장**: 422대  
- **태양공장**: 425대  
- **한빛공장**: 455대

### en-count — SQL
```sql
SELECT COUNT(*) FROM cars LIMIT 100
```
**답변 (앞 280자):** There are 2,600 vehicles in total.

### en-group — SQL
```sql
SELECT f.factory_name, COUNT(c.car_id) AS car_count FROM cars c JOIN factories f ON c.factory_id = f.factory_id GROUP BY f.factory_name LIMIT 100
```
**답변 (앞 280자):** - **Dankun Plant**: 392 vehicles  
- **Baekwoon Plant**: 462 vehicles  
- **Eunha Plant**: 444 vehicles  
- **Cheongrim Plant**: 423 vehicles  
- **Taeyang Plant**: 425 vehicles  
- **Hanbit Plant**: 455 vehicles

## 리포트 결과

- ok: `True`  | latency: `5.62s`  | id: `32`
- title: **전체 차량 수 및 공장별 분포 리포트**
