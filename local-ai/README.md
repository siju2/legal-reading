# 로컬 법률 AI

로그인 후 메뉴 **로컬 법률 AI** 또는 사건 노트의 **이 노트로 질문하기**를 엽니다. 노트를 선택하고 동의한 경우만 해당 사용자의 노트 일부가 이 PC의 모델에 제공됩니다. 질문과 답변을 대화 기록으로 저장하거나 추가 학습에 사용하지 않습니다.

- 기본 응답 모델은 이미 내려받은 Qwen3-4B-Instruct-2507입니다([공식 GitHub](https://github.com/QwenLM/Qwen3), [모델 카드·Apache-2.0 라이선스](https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507)). 일반 대화형 모델이지 한국 법률 특화 모델은 아닙니다. 법률 답변은 기존 공식 법령·판례 검색과 근거 검사를 함께 사용하며, 정확도나 전문가 자격을 보증하지 않습니다. 기존 모델과 학습 어댑터 파일은 보존합니다.
- 학습은 공개 법령 요약과 작성한 예제만 사용합니다. 모델의 법률 정확도나 전문가 자격을 인증하는 과정은 아닙니다.
- 법령 요약은 `src/main/resources/legal-ai/sources.json`, 분야별 보강 자료와 전체 공식 코퍼스는 `src/main/resources/legal-ai/sources-research-100.json`, `sources-bulk.json`에서 찾습니다. 보강 자료는 공식 법령 50개 조문과 대법원 판례 50건으로 구성했고, 각 항목의 원문 발췌·공식 링크·자료 확인일을 보존합니다. 답변에는 직접 관련된 근거만 인용하며, 자료에 없는 질문은 확인을 요청합니다. 새로운 법령이나 판례는 출처와 적용 시점을 확인해 자료를 갱신하세요.
- 외부 모델로 자동 전환하지 않습니다. 질문에 인터넷·검색·링크·최신 정보 요청이 있을 때만 GitHub `deedy5/ddgs`의 Bing 검색 백엔드를 실행하고, 식별정보를 뺀 검색어만 Bing에 보냅니다. 사건 노트와 이전 대화는 검색에 보내지 않습니다. 검색 결과는 법원·법령 사이트(`law.go.kr`, `scourt.go.kr`)만 AI 문맥에 넣고, 미리보기 발췌와 공식 원문 링크를 보여줍니다. 검색 결과가 제한되거나 실패하면 로컬 자료 또는 공식 검색 링크로 대체합니다.
- 로컬 AI API는 인증 토큰을 요구하고 `127.0.0.1:11435`에만 바인딩됩니다. 인터넷 검색은 GPU를 사용하지 않습니다.

## PC를 다시 시작한 뒤

Spring Tools에서 `ProjectApplication`을 실행하면 로컬 AI도 자동 실행됩니다. Spring이 시작한 AI는 Spring 종료 시 함께 종료됩니다. 이미 실행 중이던 AI는 재사용합니다. 별도로 켤 때는 `./local-ai/start-local-ai.ps1`을 사용합니다. 시작 로그는 로컬 런타임 폴더에 저장됩니다. 실행 중인 모델을 중복으로 띄우지 않습니다.

## 학습 다시 실행

추가 Qwen 학습은 기존 어댑터를 보존하도록 새 출력 디렉터리를 지정하세요. 설치된 Python 3.11, CUDA PyTorch와 requirements.txt의 의존성을 사용합니다. 학습 스크립트의 기본 베이스는 `Documents/Codex/local-legal-ai/model`에 둔 Qwen3-4B 모델이므로, 이번 서비스 기본 모델 경로와 다를 수 있습니다.

```powershell
$aiRuntime = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex/local-legal-ai'
& "$aiRuntime/python-portable/python.exe" ./local-ai/train.py --base "$aiRuntime/model" --output "$aiRuntime/training-new" --corpus ./src/main/resources/legal-ai/sources.json --steps 40
```

Spring은 로컬 모델을 기본 제공자로 사용하고 시작 시 로컬 AI를 자동 실행합니다. API 토큰은 `Documents/Codex/local-legal-ai/api-token.txt`에서 읽으며 별도 API 키나 외부 AI 제공자는 필요하지 않습니다. 토큰과 모델 파일을 Git에 추가하지 않습니다. 전체 테스트 중 실모델 테스트는 `-Dlegal.ai.smoke=true`와 `-Dlegal.ai.smoke-token-file=<토큰 파일 경로>`가 지정된 경우에만 실행합니다.

## 답변 품질 수정 적용

로컬 AI 서버를 다시 실행해야 새 모델이 반영됩니다. 실행 중인 AI는 Spring 재시작만으로 교체되지 않을 수 있습니다. `/health`의 `model` 항목으로 실제 실행 모델을 확인하세요. 이 변경은 재학습이나 법률 정확도 인증이 아닙니다.

## 중간 저장·이어 학습

`train_legal_incremental.py`는 별도 출력 폴더에 초기 모델과 40건마다 체크포인트를 저장합니다. 모델 가중치뿐 아니라 옵티마이저, 처리 건수, 난수 상태도 저장합니다. 파일 저장이 끝나고 무결성 검사를 위한 해시를 기록한 뒤에만 `latest.json`을 바꿉니다. 이전 저장본은 덮어쓰지 않습니다.

학습을 안전하게 멈추려면 출력 폴더에 `STOP` 파일을 만드세요. 현재 배치를 마친 뒤 저장하고 종료합니다. `STOP` 파일을 지우고 같은 명령을 실행하면 마지막 완료 체크포인트에서 이어집니다. 데이터나 학습 설정이 바뀌면 이어 학습을 거부합니다. 강제 종료 시에는 마지막 저장 이후 최대 39건을 다시 계산할 수 있습니다.

```powershell
$aiRuntime = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex/local-legal-ai'
& "$aiRuntime/python-portable/python.exe" ./local-ai/train_legal_incremental.py --base "$aiRuntime/model" --dataset "$aiRuntime/training-1000-20261117/dataset.json" --output "$aiRuntime/training-1000-20261117"
```

2026-11-17 작업의 학습 예제와 평가 결과는 `training-1000-20261117`에 구분해 기록합니다. 수집·예제 생성과 실제 가중치 학습은 다릅니다. `result.json`의 `completed=true`, `seen=1000`으로 완료 여부를 확인하고, 생성 답변 비교를 통과하기 전에는 서비스에 적용하지 않습니다.

## 자연스러운 대화 예시 데이터

`data/natural-conversation-v1.json`에는 국가법령정보센터의 근로기준법 제36조·제43조와 주택임대차보호법 제3조의3을 근거로 직접 작성한 대화 예시 24개(학습 18, 보류 평가 6)가 있습니다.

2026-11-19 대규모 데이터 v3는 `data/natural-conversation-1000-v3.json`에 공식 법령·판례 자료 100개에서 만든 1,000개(학습 900, 평가 100)를 담습니다. 이전 직접 작성 예시 120개를 더한 학습 데이터는 `data/natural-conversation-1120-v3.json`에 있습니다. 990개를 2회 학습한 후보는 평가 손실이 1.3811에서 1.0927로 낮아졌지만, 앱 프롬프트로 비교한 50개 답변에서 장황한 조문 복사와 법률 오류가 남아 적용하지 않았습니다. 체크포인트와 답변 비교는 `Documents/Codex/local-legal-ai/training-natural-conversation-1120-v3-2epoch-20261119`에 보관합니다.

2026-11-19 예시 데이터 v2는 15개 법령 조항에 대한 총 120개 대화(학습 90, 평가 30)입니다. 별도 `training-natural-conversation-v2-3epoch-20261119` 후보를 3회 학습했으며, 평가 손실은 2.0697에서 1.9344로 내려갔습니다. 하지만 평가 질문 30개에서 법률상 중요한 오류가 남아 어댑터는 적용하지 않았습니다. 비교 결과와 검토 기록은 해당 로컬 런타임 폴더에 보관합니다. 기존 모델은 계속 사용합니다.

2026-11-19 후보 학습은 별도 `training-natural-conversation-20261119` 폴더에 저장했습니다. 보류 손실은 2.3999에서 2.3973으로 소폭 감소했지만, 생성 답변 비교에서 기존 답변의 오류가 유지되어 `generation_gate=failed_manual_review`로 기록했습니다. 이 어댑터는 적용하지 않았고 기존 로컬 모델은 그대로 사용합니다.

2026-11-17 추가 학습 결과: 공식 법률 700건과 판례 발췌 300건의 새 예제로 별도 LoRA 학습을 완료했습니다. 40건마다 저장하고 재개 및 최종 파일 무결성을 검증했습니다. 평가에서 근거 없는 출처 표시가 발견되어 서비스에는 적용하지 않았습니다. 데이터·가중치·체크포인트·상세 평가 결과는 PC의 로컬 런타임에 보관하며 Git에 포함하지 않습니다.
