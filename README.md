# 판결읽기 - 생활법률 판결 아카이브

기존 Spring Boot `Project`를 공개 판결을 생활 언어로 이해하는 서비스로 전환했습니다. 프로젝트 이름·기존 회원은 유지하고 새 도메인은 `com.example.project.legal`, 새 테이블은 `legal_*`를 사용합니다.

## 실행

- Java 17+, Maven, MySQL / 기존 `project` DB 연결을 사용합니다.
- `mvn test` : H2 기반 테스트. `mvn clean package` : 실행 JAR 생성.
- STS에서 기존 `ProjectApplication`을 Spring Boot App으로 실행하거나 `mvn spring-boot:run`.
- 브라우저: http://localhost:8080/
- 실제 DB 비밀번호·로컬 설정은 저장소에 커밋하지 않습니다. 환경변수 및 `application-local.properties.example`을 참고하세요.

## 구현 기능

1. 분야·키워드·생활용어 확장·유형 필터와 페이지별 목록.
2. 실제 판결과 가상 학습 자료의 구분, 주장/사실/쟁점/판단/결과/비교 조건/자료 체크리스트.
3. 공식 원문 발췌·법령 링크·출처 확인일. 현행 법령과 판결 당시 법령 구분 안내.
4. 두 자료 비교, 법률 용어 검색, 공식 정보·상담 기관 링크.
5. 기존 회원가입·BCrypt 로그인·세션·CSRF 유지.
6. 회원별 북마크·비공개 메모·보관 해제. 다른 회원의 메모 수정 불가.
7. 관리자 등록·편집·공개/비공개·스냅샷 이력·낙관적 잠금.
8. 실제 자료는 공식 HTTPS 법령/법원 출처·사건번호·법원·선고일·근거 발췌를 확인해야 공개 가능.

공개 해설은 전문가 검수 완료로 표시하지 않습니다. 가상 자료는 실제 사건번호와 선고일을 저장하지 않습니다. 비공개 초안은 일반 목록·상세·비교·북마크에서 제외됩니다.

## 초기 자료

새 자료 테이블이 비어 있을 때만 실제 판결 3건·가상 학습 사례 3건을 넣습니다. 전국 모든 판결이나 전체 소송기록을 수록한 서비스는 아닙니다.

| 사건 | 선고일 | 출처 |
| --- | --- | --- |
| 2020다247190 임금 | 2024-12-19 | https://www.law.go.kr/LSW/precInfoP.do?precSeq=600537 |
| 2023다302838 임금 | 2024-12-19 | https://www.law.go.kr/LSW/precInfoP.do?precSeq=600539 |
| 2019다16000 대여금 | 2019-12-27 | https://www.law.go.kr/LSW/precInfoP.do?precSeq=225923 |

출처 확인일: 2026-10-28. 편집 해설은 원문 전체를 대체하지 않으며 판결 변경·후속 재판·구체적 사실관계를 별도로 확인해야 합니다. 가상 학습 사례는 주거·중고거래·온라인 표현의 사실 정리 연습입니다.

## 관리자

`LEGAL_BOOTSTRAP_ADMIN_PASSWORD`에 16자 이상의 초기 비밀번호를 지정한 최초 실행에서 `legal_admin` 계정을 생성합니다. 사용자명은 예약되어 일반 회원가입에 사용할 수 없습니다. 이미 있는 계정의 비밀번호나 권한은 부팅 시 자동 변경하지 않습니다. 최초 생성 후 환경변수를 지워도 저장된 관리자 계정은 유지됩니다.

로컬 전환 과정에서 생성한 계정 정보는 `data/legal-admin-initial.txt`에 보관합니다. 이 파일과 `data/`는 Git에서 제외됩니다. `/admin`은 ADMIN만 접근하고 일반 회원에게 등록 권한을 주지 않습니다.

## 주요 경로

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| GET | `/` | 대문 |
| GET | `/cases` | 검색·분류·유형 필터 |
| GET | `/cases/{id}` | 해설·공식 출처 |
| GET | `/compare?left=&right=` | 두 자료 비교 |
| GET | `/glossary?q=` | 용어 사전 |
| GET | `/guide` | 자료·상담 경로 안내 |
| GET | `/api/cases?q=&category=` | 공개 실제 판결 최대 100건 JSON |
| GET | `/my` | 개인 보관함 |
| POST | `/cases/{id}/bookmark` | 저장 |
| POST | `/cases/{id}/unbookmark` | 저장 해제 |
| POST | `/cases/{id}/note` | 본인 메모 |
| GET | `/admin` | 관리자 목록 |
| GET/POST | `/admin/cases/new` | 등록 |
| GET/POST | `/admin/cases/{id}/edit` | 편집·이력 |

## 데이터와 범위

- `members`: 기존 회원 + 관리자 여부.
- `legal_cases`: 판결·해설·출처·공개 상태·버전.
- `legal_bookmarks`: 사용자/자료 유일키, 개인 메모.
- `legal_revisions`: 관리자 수정 스냅샷.
- 기존 `archive_*` 테이블과 업로드 파일은 보존하며 새 서비스는 조회하지 않습니다.
- 기존 소스·문서는 전환 전 복구 ZIP으로 보관했습니다. 예전 로스트미디어 SRS·일정표는 새 서비스 명세로 사용하지 않습니다.
- 현재는 등록 자료 검색과 공식 원문 링크를 제공합니다. AI 상담·승소 예측·전국 판결 자동 수집·개인 사건 파일 업로드·실제 결제는 구현하지 않았습니다. 공식 API 검색 코드는 아래 추가 기능 항목을 확인하세요.
- 국가법령정보 공동활용 API를 나중에 연결할 경우 신청·인증·제공 범위·이용 조건에 맞춰 별도 구현해야 합니다: https://open.law.go.kr/LSO/openApi/guideList.do

GitHub: https://github.com/siju2/legal-reading (비공개 저장소). 이전 로스트미디어 이력은 로컬 lost-media-main 브랜치와 lost-media 원격에 보존합니다.


## 추가 기능 (2026-10-28)

- 본문 용어 풀이: 표시된 용어를 누르면 정의가 펼쳐집니다. 텍스트를 분절해 안전하게 출력하며 원격 HTML은 실행하지 않습니다.
- `/situation`: 상황·문서 유무·기록 유무의 세 질문으로 확인할 사항과 등록 자료를 연결합니다. 규칙 기반 안내이며 법률상 결론을 판정하지 않습니다.
- `/notebooks`: 회원별 사건 노트, 날짜순 기록, 유형·메모, 분야별 또는 참고 사례 기반 체크리스트와 진행률. 다른 회원은 관리자라도 읽거나 수정할 수 없습니다.
- `/law-search`: 법제처 판례·법령 검색과 판례 본문 조회. 검색어만 외부로 보내고 개인 노트는 보내지 않습니다.
- `/api/law/status`: 인증값 설정 여부만 제공하며 인증값 자체는 출력하지 않습니다.

### 법제처 API 설정

아직 사용자 OC 인증값이 없어 실시간 서비스 연동은 대기 상태입니다. 실제 API 성공 검증과 샘플 응답 테스트를 구분합니다.

1. 국가법령정보 공동활용에서 API 이용 신청 후 필요한 판례·법령 목록/본문의 승인 조건을 확인합니다.
2. Project 폴더의 `data/law-api.properties`에 `legal.law-api.oc=발급받은인증값`을 저장합니다. UTF-8 텍스트를 사용하세요. 예시 값 `YOUR_OC`, `CHANGE_ME`, `test`는 활성화하지 않습니다.
3. 파일은 다음 조회 때 읽으므로 재시작하지 않아도 됩니다. 환경 설정 `legal.law-api.oc`가 지정된 경우 파일보다 우선합니다.
4. `/law-search`에서 검색합니다. 설정됨은 인증값 존재만 의미하며 이용 승인·유효성을 보장하지 않습니다. 실제 성공 여부는 검색 응답으로 확인하세요.
5. 인증값·data 폴더는 Git에서 제외됩니다. 인증값을 채팅·공개 저장소·스크린샷에 넣지 마세요.

고정 HTTPS `www.law.go.kr/DRF/lawSearch.do`와 `lawService.do`만 호출합니다. 연결 4초·읽기 8초, 동시 2요청, 응답 2MB 제한, 최대 32개 응답 2분 캐시를 적용합니다. 장애·인증 문제와 정상 0건 결과를 구분합니다. 공식 문서가 HTTP 예시를 제시해도 구현은 TLS 검증을 유지한 HTTPS만 사용합니다.

공식 명세:
- 판례 목록: https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=precListGuide
- 판례 본문: https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=precInfoGuide
- 법령 목록: https://open.law.go.kr/LSO/openApi/guideResult.do

개인 기록 테이블은 `legal_notebooks`, `legal_timeline`, `legal_checklist`입니다. 신규 POST 요청도 로그인·CSRF·소유권을 검사합니다. 기존 회원·자료 테이블은 유지합니다.


## 저장소 받기

```sh
git clone https://github.com/siju2/legal-reading.git
```

기본 브랜치는 `main`입니다. STS에서는 File → Import → Maven → Existing Maven Projects로 가져옵니다. MySQL `project` 데이터베이스와 로컬 접속 설정을 준비한 다음 `ProjectApplication`을 실행하세요. `application-local.properties.example`을 복사해 비밀번호를 설정하고 실제 비밀번호 파일은 커밋하지 마세요.

검증: 인증·권한·검색·노트 소유권·체크리스트·API 모의 응답 등 테스트 25개 통과. MySQL에서 노트·타임라인·체크리스트 저장 및 삭제를 확인했습니다. 실제 법제처 API 조회는 사용자 OC 인증값·승인 후 별도 확인이 필요합니다.
