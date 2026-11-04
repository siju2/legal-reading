# 암호화 및 보호 배포

## 적용 범위

- 판례 본문·해설·출처, 개인 노트 제목·상담 내용, 타임라인 제목·메모, 체크리스트, 상담 후 기록, 북마크 메모, 수정 스냅샷: AES-256-GCM 암호문으로 저장합니다.
- 저장할 때마다 무작위 96비트 nonce와 128비트 인증 태그를 사용합니다. 평문 DB 값, 변조된 암호문, 다른 키는 자동으로 허용하지 않습니다.
- ID, username, 분야·유형, 날짜, 공개/완료 상태, 결제 상태·금액·상점/결제 ID 등 동작에 필요한 메타데이터는 평문입니다. 비밀번호는 기존 BCrypt 해시를 유지합니다. 디스크 전체 암호화 또는 모든 DB 열 암호화를 의미하지 않습니다.
- PDF·JSON 첨부파일은 `.pdf.enc`·`.json.enc` AES 암호화 파일로 다운로드됩니다. 복원은 암호화 백업을 받습니다. 서버 메모리에서는 처리에 필요한 평문이 존재합니다.
- 브라우저에 개인 기록 평문을 남기지 않도록 탭 임시 저장·복원을 비활성화했습니다. 기존 앱의 sessionStorage 임시본은 다음 페이지 로드 시 지웁니다. 저장 전 이동 경고는 유지합니다.

## 키와 런타임 설정

기본 키 위치는 사용자 홈의 `.legal-reading/security/data-key.bin`입니다. `LEGAL_CRYPTO_KEY_FILE` 또는 JVM 시스템 속성 `legal.crypto.key-file`로 지정할 수 있습니다. 파일은 정확히 32바이트여야 하며 애플리케이션은 키를 자동 생성하지 않습니다. 기존 DB에 새 키를 생성하면 복호화할 수 없습니다.

키 디렉터리의 `runtime.properties.enc`를 부팅 초기에 복호화합니다. 실제 DB 접속값과 HTTPS 키스토어 비밀번호를 여기에 보관합니다. 로컬 비밀 설정은 Maven 배포 리소스에서 제외했습니다. 키·런타임 파일·키스토어·SQL 백업은 저장소 밖에서 사용자 전용 ACL로 관리합니다. 같은 사용자 계정을 장악한 공격자로부터 키를 격리하는 KMS/HSM을 대신하지 않습니다.

현재 로컬 서버는 `https://localhost:8443`으로 실행합니다. 개발용 자체 서명 인증서이므로 브라우저에서 인증서 경고가 표시될 수 있습니다. OS 신뢰 저장소를 자동 변경하지 않습니다. 운영에는 정식 도메인 인증서를 사용하세요. 로컬 MySQL 연결에는 `sslMode=REQUIRED`를 적용했습니다. 이는 전송 암호화를 요구하지만 서버 호스트 신원 검증까지 제공하는 `VERIFY_IDENTITY` 설정은 아닙니다.

**키는 DB 백업과 별도로 안전한 매체에 백업하세요. 키를 잃으면 데이터·암호화 파일을 복구할 수 없습니다.** 키 교체에는 기존 키로 복호화 후 새 키로 다시 암호화하는 별도 작업이 필요합니다.

## 파일 복호화

프로젝트 폴더 PowerShell에서 실행합니다. 결과 파일은 기존 파일을 덮어쓰지 않습니다.

```powershell
./scripts/secure-file.ps1 decrypt 'C:/Downloads/notebook-1.pdf.enc' 'C:/Downloads/notebook-1.pdf'
./scripts/secure-file.ps1 decrypt 'C:/Downloads/legal-notebooks.json.enc' 'C:/Downloads/legal-notebooks.json'
```

복호화된 파일은 평문입니다. 필요한 작업 후 안전하게 관리하세요. 과거에 다운로드한 파일이나 이전 Git 커밋이 소급해 암호화되지는 않습니다. 기존 공개 ERD·보고서는 그대로 읽을 수 있는 문서입니다.

## 소스와 배포본

```powershell
mvn -Pprotected-release clean verify
```

ProGuard 7.8.2로 내부 비공개 멤버 이름을 난독화하고 디버그 정보를 제거합니다. Spring·JPA·리플렉션과 공개 API 호환을 위해 클래스명·공개 멤버·설정 메서드 등은 보존합니다. 완전한 소스 은닉 또는 역공학 방지가 아닙니다. 브라우저에 제공하는 HTML/JS는 이용자가 볼 수 있습니다.

배포할 파일은 `target/Project-0.0.1-SNAPSHOT.jar`입니다. `target/*_proguard_base.jar`, 매핑 파일, 원본 클래스, `.git`, 소스 폴더 및 비밀 설정은 배포하지 마세요. 개발·유지보수용 소스는 편집 가능한 상태로 유지하며 별도의 AES 암호화 소스 보관본을 생성합니다.

## 마이그레이션과 검증

실행 중인 앱을 중지한 뒤 `scripts/MigrateEncryptedData.java`를 오프라인 실행합니다. 먼저 전체 로컬 DB의 암호화 SQL 스냅샷을 만들고 복호화 일치를 확인합니다. 열을 LONGTEXT로 확장한 후 본문 데이터는 단일 트랜잭션으로 암호화합니다. MySQL DDL 변경 자체는 트랜잭션 롤백 대상이 아닙니다. 운영 적용 전 별도 복구 연습이 필요합니다.

기존 평문 SQL·더미 삽입기를 그대로 사용하면 새 암호화 코드가 읽을 수 없습니다. 저장소의 생성 스크립트는 암호화 필드 변환을 사용하도록 갱신했습니다. 운영 데이터에 직접 SQL로 콘텐츠를 넣지 말고 앱 서비스 또는 검증된 도구를 사용하세요.

테스트 시 실제 운영 키를 공유하지 말고 별도 임시 키 파일을 준비한 뒤 다음과 같이 실행합니다.

```powershell
mvn '-Dlegal.crypto.key-file=C:/temporary/test-key.bin' test
node --test src/test/js/notebook-drafts.test.cjs
```

기존 기능 회귀 테스트, 무작위 암호문·변조 거부·한글/이모지·빈 값·파일 복호화, 실제 SQL 저장값 검사를 포함합니다. 난독화 JAR의 기동·로그인·검색·출력은 별도로 확인해야 합니다.

공식 근거: [Spring Boot SSL](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html), [ProGuard 설정](https://www.guardsquare.com/manual/configuration/usage).
