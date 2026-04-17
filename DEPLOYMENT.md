# 배포 가이드

이 문서를 위에서 아래로 순서대로 따라가세요. 약 **20~30분**이면 프론트+백엔드 모두 온라인에 뜹니다.

## 준비물

- GitHub 계정 (이미 있음: `m-kim77`)
- Vercel 계정 (없으면 https://vercel.com/signup → "Continue with GitHub")
- Render 계정 (없으면 https://dashboard.render.com/register → "GitHub")
- (선택) OpenAI API 키 — 챗봇 기능 쓰려면 필요. 없어도 다른 기능은 다 돌아감.

---

## Part 1. Render 백엔드 배포 (먼저 배포)

백엔드 URL을 알아야 프론트엔드를 배포할 수 있어서 순서 중요.

### 1-1. Render에 Blueprint 등록

1. https://dashboard.render.com 로그인
2. 좌측 상단 **New +** → **Blueprint** 클릭
3. **Connect GitHub** → `m-kim77/smartfactory-BMS-project` 리포지토리 선택
4. Render가 자동으로 `render.yaml`을 감지 → **Apply** 클릭
5. 서비스 이름 `bms-backend` 확인 후 **Create New Resources**

### 1-2. 빌드 대기

- 첫 빌드는 **5~10분** 걸림 (Maven 의존성 다운로드 + Docker 빌드)
- Logs 탭에서 진행상황 확인 가능
- `Started BmsApplication in X seconds` 메시지 나오면 성공

### 1-3. 백엔드 URL 확인 + 저장

배포 완료 후 상단에 URL이 뜸, 예:
```
https://bms-backend-xxxx.onrender.com
```

이 URL을 복사해두세요. **Part 2에서 쓸 거예요.**

### 1-4. 헬스 체크

브라우저에서 `https://bms-backend-xxxx.onrender.com/api/v1/auth/login` 접속
→ `405 Method Not Allowed` 또는 비슷한 JSON 응답이 나오면 **정상** (POST 엔드포인트에 GET으로 접근했으니까).

### (옵션) OpenAI API 키 등록 — 챗봇 쓰려면

Render 대시보드 → `bms-backend` 서비스 → **Environment** 탭:
- `LLM_API_KEY` = `sk-...` (OpenAI에서 받은 키)
- `LLM_BASE_URL` = `https://api.openai.com` (이미 render.yaml에 있음)
- `LLM_MODEL` = `gpt-4o-mini` (이미 render.yaml에 있음)

Save → 자동 재배포.

---

## Part 2. Vercel 프론트엔드 배포

### 2-1. Vercel에 프로젝트 Import

1. https://vercel.com/new 접속
2. GitHub → `m-kim77/smartfactory-BMS-project` 옆 **Import** 클릭
3. **Configure Project** 화면에서:
   - **Root Directory**: `frontend` ← **중요! 꼭 지정**
   - Framework Preset: Vite (자동 감지됨)
   - Build Command, Output Directory: 그대로 둠

### 2-2. 환경변수 추가

같은 화면의 **Environment Variables** 섹션:
- Key: `VITE_API_BASE_URL`
- Value: `https://bms-backend-xxxx.onrender.com/api/v1` ← Part 1-3에서 복사한 URL + `/api/v1`

### 2-3. Deploy 클릭

- 빌드 약 1~2분
- 끝나면 URL이 나옴, 예: `https://smartfactory-bms-project.vercel.app`

### 2-4. 동작 확인

Vercel URL 접속 → 로그인 페이지 나오면 일단 프론트 성공.
- Render 백엔드가 **슬립 상태**면 첫 로그인이 30초 걸릴 수 있음 (cold start)
- 이후부터는 정상 속도

---

## Part 3. GitHub Actions 확인

별도 설정 불필요. `main` 브랜치에 push하면 자동으로:
- `frontend/**` 변경 → Frontend CI 실행 (빌드 검증)
- `backend/**` 변경 → Backend CI 실행 (Maven 빌드 + Docker 빌드 검증)

GitHub repo → **Actions** 탭에서 결과 확인.

**자동 배포는 Render/Vercel이 직접 함:**
- Render: `backend/**` push → 자동 재배포 (`render.yaml`의 `autoDeploy: true`)
- Vercel: 모든 push → 자동 재배포

---

## 비용/제약 요약

| 서비스 | 무료 한도 | 주의 |
|--------|----------|------|
| Vercel Hobby | 100GB 대역폭/월 | 상업 용도 불가 (개인/포폴은 OK) |
| Render Free Web | 750시간/월 (1개 서비스 풀타임 OK) | **15분 미사용 시 슬립** → 첫 호출 30초 지연 |
| GitHub Actions | public repo 무제한 | — |

---

## 자주 나는 문제

**Render 빌드 실패 — "Maven build timeout"**
- 무료 플랜은 메모리 512MB라 드물게 OOM. Logs 보내주면 원인 찾아드림.

**프론트에서 API 호출 시 CORS 에러**
- Render 백엔드가 아직 슬립 상태. 30초 기다리고 새로고침.

**로그인 안 됨 — 404**
- `VITE_API_BASE_URL` 끝에 `/api/v1`이 빠졌는지 확인 (Vercel 환경변수).

**Render 재배포할 때마다 DB 초기화됨**
- SQLite는 컨테이너와 함께 사라짐. 데이터 영속성 필요하면 PostgreSQL로 전환(별도 작업).

---

## 요약: 오늘 만진 파일들

- `backend/Dockerfile` — 백엔드 Docker 이미지
- `backend/.dockerignore` — Docker 빌드 제외 파일
- `render.yaml` — Render 블루프린트
- `frontend/vercel.json` — SPA 라우팅
- `.github/workflows/frontend-ci.yml` — 프론트 CI
- `.github/workflows/backend-ci.yml` — 백엔드 CI
- `DEPLOYMENT.md` — 이 문서
