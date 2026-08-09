# 코드 한입

## Front-end 소개

**코드 한입**은 개발자들이 오늘 개발하며 겪은 경험, 문제 해결 과정, 고민과 생각을 부담 없이 기록하고 나누는 가벼운 개발자 커뮤니티입니다.

초기 Vanilla JavaScript로 구현한 화면을 React 기반 SPA로 마이그레이션하고, 화면 구성부터 사용자 인증, 게시글·댓글·답글, 이미지, 실시간 알림, Backend API 연동과 배포 자동화까지 직접 구현했습니다.

### 핵심 구현

- SSE로 좋아요·댓글·답글 알림을 실시간 수신하고, 재연결·토큰 재발급·누락 알림 동기화 처리
- 커서 기반 페이지네이션과 `IntersectionObserver` 센티넬을 조합한 게시글·알림 무한 스크롤
- 다중 이미지 게시글 작성·수정과 기존 이미지 유지·삭제·신규 이미지 추가 UI
- 댓글·답글·좋아요와 작성자 전용 수정·삭제 UI, 알림을 통한 댓글 딥링크
- JWT 기반 Backend API와 연동되는 회원·인증 화면과 동시 인증 실패 시 중복 토큰 재발급 방지
- GitHub Actions를 통한 Lint·Build 검증, Docker 이미지 빌드·푸시, EC2 배포 자동화

## 개발 인원 및 기간

- 개발 기간: 2026-05-26 ~ 2026-08-09
- 개발 인원: 프론트엔드 / 백엔드 1명 (본인)
- 담당 범위: UI 구현, React 마이그레이션, Backend API 연동, 인증·실시간 알림, 배포 환경과 CI/CD 구성

## 사용 기술 및 Tools

| 구분 | 기술 및 도구 | 활용 |
| --- | --- | --- |
| Front-end | React 19, JavaScript | 컴포넌트 기반 UI와 상태 관리 |
| Routing | React Router 8 | SPA 라우팅과 인증 페이지 접근 제어 |
| API | Fetch API, Fetch Event Source | REST API 호출과 인증 헤더를 포함한 SSE 연결 |
| Build | Vite 8, npm | 개발 서버와 프로덕션 빌드 |
| Code Quality | ESLint | 정적 검사와 React Hooks 규칙 검증 |
| Web Server | Nginx | SPA 정적 파일 제공, API 프록시, SSE 버퍼링 해제 |
| Container | Docker, Docker Compose | Front-end 이미지 생성과 로컬 통합 실행 |
| CI/CD | GitHub Actions, Docker Hub | Lint·Build 검증, 이미지 푸시, EC2 자동 배포 |
| Collaboration | Git, GitHub | 버전 관리와 소스 코드 관리 |

## Back-end

<!-- Back-end 저장소 주소를 확정한 후 추가해 주세요. -->

- Back-end GitHub: 

## 주요 기능

### User

- Access Token은 메모리에서 관리하고 Refresh Token은 Cookie로 전송하는 JWT 인증 흐름 구현
- API 요청 전 Access Token이 없거나 `401 Unauthorized`가 발생하면 재발급 후 기존 요청을 자동 재시도
- 동시에 여러 인증 요청이 실패해도 하나의 재발급 Promise를 공유해 중복 호출 방지
- `ProtectedRoute`로 인증된 사용자만 서비스 페이지에 접근하도록 제어
- 회원가입, 로그인·로그아웃, 프로필·프로필 이미지 수정, 비밀번호 변경, 회원 탈퇴 UI 구현

### Post

- `lastPostId`를 사용한 커서 기반 페이지네이션과 `IntersectionObserver` 센티넬으로 무한 스크롤 구현
- JSON 요청 데이터와 다중 이미지를 `multipart/form-data`로 전송하는 게시글 작성·수정 UI 구현
- 수정 시 유지할 기존 이미지 ID와 새로 추가한 이미지를 분리해 서버에 전달
- 이미지 미리보기와 조회 실패 시 fallback 이미지 처리
- 좋아요 상태와 개수를 응답 결과로 즉시 갱신하고, 중복 요청을 방지하는 제출 상태 관리
- 작성자에게만 수정·삭제 조작을 노출하고 삭제 전 확인 모달 제공

### Comment & Reply

- 댓글과 답글의 작성·수정·삭제 기능과 작성자 전용 조작 UI 구현
- 댓글·답글 요청의 중복 제출을 방지하고, 완료 후 상세 데이터를 서버 상태로 다시 동기화
- 알림으로 접근한 경우 대상 댓글로 스크롤하고 일정 시간 강조 표시하는 딥링크 구현

### Notification

- SSE로 좋아요·댓글·답글 알림을 실시간 수신하고 헤더의 읽지 않은 알림 수를 갱신
- `lastNotificationId`를 사용한 커서 기반 알림 목록과 무한 스크롤 구현
- 개별 읽음·전체 읽음 처리와 관련 게시글·댓글 이동 구현
- Access Token 만료 시 토큰을 재발급하고 새 토큰으로 SSE 연결 복구
- 연결 종료 시 자동 재연결하고, 탭 복귀 시 알림 목록을 재조회해 연결 공백의 누락된 알림 동기화
- `sessionStorage`에 보관한 UUID로 브라우저 탭별 SSE 연결 식별

### Deployment

- Pull Request와 `main` 브랜치 Push 시 ESLint와 Production Build를 자동 검증
- 검증 통과 후 커밋 SHA와 `latest` 태그의 Docker 이미지를 Docker Hub에 푸시
- SSH로 EC2에 접속해 새 이미지를 배포하고 컨테이너 상태와 HTTP 응답을 확인
- Nginx에서 SPA fallback, Backend API 프록시, SSE 응답 버퍼링 해제를 구성

## 폴더 구조

<details>
<summary>폴더 구조 보기 / 숨기기</summary>

```text
├── README.md
├── .github
│   └── workflows
│       └── ci-cd.yml
├── compose.yaml
├── docs
│   └── migration
│       ├── common
│       └── pages
└── react-app
    ├── public
    ├── src
    │   ├── api
    │   │   ├── client.js
    │   │   ├── authApi.js
    │   │   ├── userApi.js
    │   │   ├── postApi.js
    │   │   └── notificationApi.js
    │   ├── app
    │   │   ├── App.jsx
    │   │   └── router.jsx
    │   ├── assets
    │   ├── components
    │   │   ├── feedback
    │   │   ├── form
    │   │   ├── layout
    │   │   ├── media
    │   │   └── routing
    │   ├── features
    │   │   ├── auth
    │   │   ├── posts
    │   │   │   ├── create
    │   │   │   ├── detail
    │   │   │   ├── edit
    │   │   │   ├── editor
    │   │   │   └── hooks
    │   │   ├── notifications
    │   │   ├── profile
    │   │   └── password
    │   ├── hooks
    │   ├── styles
    │   ├── utils
    │   └── main.jsx
    ├── Dockerfile
    ├── nginx.conf
    ├── package.json
    └── vite.config.js
```

</details>

### 디렉터리 설계

- `api`: 공통 HTTP 클라이언트와 도메인별 API 함수
- `app`: 앱 진입점과 라우팅
- `components`: 여러 도메인에서 재사용하는 UI와 라우팅 컴포넌트
- `features`: 인증, 게시글, 알림, 프로필 등 기능 단위 코드
- `hooks`: 무한 스크롤과 헤더 제어 등 공통 동작
- `styles`: 공통 및 페이지별 스타일
- `docs/migration`: Vanilla JavaScript에서 React로 마이그레이션하기 위한 페이지별 분석·설계·검증 문서

## 페이지 구조

| 페이지 | 경로 | 접근 | 주요 기능 |
| --- | --- | --- | --- |
| 로그인 | `/login` | 비인증 | 이메일·비밀번호 로그인, 인증 오류 처리 |
| 회원가입 | `/signup` | 비인증 | 이메일·비밀번호·닉네임 검증, 프로필 이미지 등록 |
| 게시글 목록 | `/posts` | 인증 | 커서 기반 목록 조회, 무한 스크롤 |
| 게시글 작성 | `/posts/new` | 인증 | 제목·본문 작성, 다중 이미지 첨부·미리보기 |
| 게시글 상세 | `/posts/:postId` | 인증 | 상세 조회, 좋아요, 댓글·답글, 작성자 전용 조작 |
| 게시글 수정 | `/posts/:postId/edit` | 인증 | 제목·본문 수정, 기존·신규 이미지 관리 |
| 알림 목록 | `/notifications` | 인증 | 실시간 알림, 무한 스크롤, 개별·전체 읽음, 대상 콘텐츠 이동 |
| 프로필 수정 | `/profile/edit` | 인증 | 닉네임·프로필 이미지 수정, 회원 탈퇴 |
| 비밀번호 수정 | `/password/edit` | 인증 | 비밀번호 유효성 검증과 변경 |

## 서비스 화면

<!-- docs/images 디렉터리에 스크린샷을 추가한 후 아래 경로를 맞춰 주세요. -->

### 로그인 / 회원가입

| 로그인 | 회원가입 |
| --- | --- |
| <!-- ![로그인](docs/images/login.png) --> 스크린샷 추가 예정 | <!-- ![회원가입](docs/images/signup.png) --> 스크린샷 추가 예정 |

### 게시글 목록 / 상세

| 게시글 목록 | 게시글 상세 |
| --- | --- |
| <!-- ![게시글 목록](docs/images/posts.png) --> 스크린샷 추가 예정 | <!-- ![게시글 상세](docs/images/post-detail.png) --> 스크린샷 추가 예정 |

### 게시글 작성 / 수정

| 게시글 작성 | 게시글 수정 |
| --- | --- |
| <!-- ![게시글 작성](docs/images/post-create.png) --> 스크린샷 추가 예정 | <!-- ![게시글 수정](docs/images/post-edit.png) --> 스크린샷 추가 예정 |

### 실시간 알림 / 댓글 이동

| 실시간 알림 | 알림을 통한 댓글 이동 |
| --- | --- |
| <!-- ![실시간 알림](docs/images/notification.gif) --> GIF 추가 예정 | <!-- ![댓글 딥링크](docs/images/comment-deep-link.gif) --> GIF 추가 예정 |

### 프로필 / 비밀번호 수정

| 프로필 수정 | 비밀번호 수정 |
| --- | --- |
| <!-- ![프로필 수정](docs/images/profile-edit.png) --> 스크린샷 추가 예정 | <!-- ![비밀번호 수정](docs/images/password-edit.png) --> 스크린샷 추가 예정 |

