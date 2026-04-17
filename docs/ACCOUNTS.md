# 테스트 계정 (BMS Smart Factory)

| 역할 | 이메일 | 비밀번호 | 권한 |
|------|--------|----------|------|
| 관리자 | admin@evernex.com | admin1234 | 대시보드 열람, 경보 처리, **설정 변경**, 국가 관리 |
| 운영자 | operator@evernex.com | operator1234 | 대시보드 열람, 경보 처리 |

## 권한 매트릭스

| 기능 | 관리자 | 운영자 |
|------|--------|--------|
| 대시보드 보기 | ✅ | ✅ |
| 차량 상세 보기 | ✅ | ✅ |
| 경보 확인 | ✅ | ✅ |
| 알람 해결 (재검사 트리거) | ✅ | ✅ |
| 차량 생성 간격 변경 | ✅ | ❌ |
| 검사 시간 변경 | ✅ | ❌ |
| 정상 확률 변경 | ✅ | ❌ |
| 배송국가 관리 | ✅ | ❌ |

> 비밀번호는 bcrypt 로 해싱되어 SQLite 에 저장됩니다.
> ver02 에서는 Spring Security 의 `BCryptPasswordEncoder` 로 해싱하며,
> 최초 실행 시 `backend/src/main/java/com/evernex/bms/db/DataSeeder.java` 가 자동 시드합니다.
> DB 를 초기화하려면 `backend/database/bms.db` 를 삭제하고 백엔드를 재시작하세요.
