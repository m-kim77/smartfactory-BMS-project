# 경보 심각도 분류 기준

배터리 검사 실패 시 측정값이 **정상 범위에서 얼마나 벗어났는지(편차)**에 따라  
LOW / MEDIUM / HIGH / CRITICAL 4단계로 심각도를 자동 분류합니다.

---

## 정상 범위

| 지표 | 정상 하한 | 정상 상한 | 단위 |
|---|---|---|---|
| SOC (배터리 잔량) | 90 | 100 | % |
| SOH (배터리 건강도) | 95 | 100 | % |
| SOP (출력 가능율) | 90 | 100 | % |
| 팩 전압 | 350 | 400 | V |
| 셀 온도 | 5 | 32 | ℃ |
| 셀 전압 | 3.6 | 4.2 | V |

---

## 심각도 분류 기준 (편차 = 정상 범위 경계로부터의 절댓값 차이)

### SOC — 배터리 잔량 (단위: %)

| 심각도 | 편차 범위 | 예시 |
|---|---|---|
| LOW | 0 < 편차 ≤ 2% | SOC 88~90% 미만 또는 100% 초과~102% |
| MEDIUM | 2 < 편차 ≤ 5% | SOC 85~88% |
| HIGH | 5 < 편차 ≤ 10% | SOC 80~85% |
| CRITICAL | 편차 > 10% | SOC 80% 미만 |

### SOH — 배터리 건강도 (단위: %)

| 심각도 | 편차 범위 | 예시 |
|---|---|---|
| LOW | 0 < 편차 ≤ 2% | SOH 93~95% 미만 |
| MEDIUM | 2 < 편차 ≤ 5% | SOH 90~93% |
| HIGH | 5 < 편차 ≤ 10% | SOH 85~90% |
| CRITICAL | 편차 > 10% | SOH 85% 미만 |

### SOP — 출력 가능율 (단위: %)

| 심각도 | 편차 범위 | 예시 |
|---|---|---|
| LOW | 0 < 편차 ≤ 3% | SOP 87~90% 미만 |
| MEDIUM | 3 < 편차 ≤ 8% | SOP 82~87% |
| HIGH | 8 < 편차 ≤ 15% | SOP 75~82% |
| CRITICAL | 편차 > 15% | SOP 75% 미만 |

### 팩 전압 (단위: V)

| 심각도 | 편차 범위 | 예시 |
|---|---|---|
| LOW | 0 < 편차 ≤ 5V | 345~350V 또는 400~405V |
| MEDIUM | 5 < 편차 ≤ 15V | 335~345V 또는 400~415V |
| HIGH | 15 < 편차 ≤ 30V | 320~335V 또는 415~430V |
| CRITICAL | 편차 > 30V | 320V 미만 또는 430V 초과 |

### 셀 온도 (단위: ℃)

| 심각도 | 편차 범위 | 예시 (하한 이탈) | 예시 (상한 이탈 — 승격 적용) |
|---|---|---|---|
| LOW | 0 < 편차 ≤ 2℃ | 3~5℃ | — |
| MEDIUM | 2 < 편차 ≤ 5℃ | 0~3℃ | 32~34℃ (하한 LOW → 승격) |
| HIGH | 5 < 편차 ≤ 10℃ | −5~0℃ | 34~37℃ (하한 MEDIUM → 승격) |
| CRITICAL | 편차 > 10℃ | −5℃ 미만 | 37℃ 초과 (하한 HIGH → 승격), 45℃ 이상은 위험 구간 |

> **⚠️ 상한 초과(과열) 가중**: 셀 온도가 정상 상한(32℃) 초과 시 한 단계 상향 조정.  
> 과열은 배터리 열폭주(Thermal Runaway) 및 화재로 이어질 수 있습니다.  
> UI 는 경고(32~45℃) 구간은 주황색, 위험(≥45℃) 구간은 빨간색으로 구분하며 해당 셀은 재검사 시 정상 범위로 복구됩니다.

### 셀 전압 (단위: V)

| 심각도 | 편차 범위 | 예시 (하한 이탈) | 예시 (상한 이탈 — 승격 적용) |
|---|---|---|---|
| LOW | 0 < 편차 ≤ 0.05V | 3.55~3.60V | — |
| MEDIUM | 0.05 < 편차 ≤ 0.15V | 3.45~3.55V | 4.20~4.25V (하한 LOW → 승격) |
| HIGH | 0.15 < 편차 ≤ 0.30V | 3.30~3.45V | 4.25~4.40V (하한 MEDIUM → 승격) |
| CRITICAL | 편차 > 0.30V | 3.30V 미만 | 4.40V 초과 (하한 HIGH → 승격) |

> **⚠️ 상한 초과(과충전) 가중**: 셀 전압이 정상 상한(4.2V) 초과 시 한 단계 상향 조정.  
> 과충전은 전해질 분해, 셀 팽창, 폭발 위험을 수반합니다.

---

## 심각도 색상 표시 (UI)

| 심각도 | 색상 | 의미 |
|---|---|---|
| CRITICAL | 빨간색 (진함) | 즉각 조치 필요 / 안전 위험 |
| HIGH | 빨간색 | 우선 처리 필요 |
| MEDIUM | 노란색 | 모니터링 및 점검 필요 |
| LOW | 파란색 | 경미한 이탈, 추적 관찰 |

---

## 안전 가중 규칙

다음 두 지표는 **정상 범위 상한 초과 시** 분류된 심각도를 한 단계 상향합니다:

- **셀 온도 > 32℃**: 과열 → 열폭주(Thermal Runaway) 위험 (≥45℃ 는 즉시 CRITICAL)
- **셀 전압 > 4.2V**: 과충전 → 전해질 분해·폭발 위험

이 규칙은 `service/RandomMetricService.java` 의 `classifySeverity()` 메서드에 구현되어 있습니다.

---

## 관련 코드 위치 (ver02 · Spring Boot)

| 항목 | 파일 |
|---|---|
| 심각도 분류 함수 | `backend/src/main/java/com/evernex/bms/service/RandomMetricService.java` → `classifySeverity()` |
| 정상 범위 정의 | `backend/src/main/java/com/evernex/bms/domain/Constants.java` → `RANGES` |
| 알람 생성 | `backend/src/main/java/com/evernex/bms/service/SimulationService.java` → `finishStep()` |
| 경보 목록 API | `backend/src/main/java/com/evernex/bms/controller/AlertsController.java` |
| 경보 UI | `frontend/src/views/Alerts.vue` (원본과 동일) |

> 원본 Node 버전의 대응 파일: `backend/src/services/random.js`, `constants.js`, `simulation.js`, `routes/alerts.js`.
