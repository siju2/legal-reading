# 로컬 법률 AI

로그인 후 메뉴 **로컬 법률 AI** 또는 사건 노트의 **이 노트로 질문하기**를 엽니다. 노트를 선택하고 동의한 경우만 해당 사용자의 노트 일부가 이 PC의 모델에 제공됩니다. 질문과 답변을 대화 기록으로 저장하거나 추가 학습에 사용하지 않습니다.

- 기본 응답은 Qwen3-4B 원본 모델과 공식 법률·판례 자료를 사용합니다. 기존 LoRA 학습본은 반복 답변 증상 때문에 기본 추론에서 제외했고 파일은 보존합니다. 검증한 학습본을 명시적으로 사용할 때만 `serve.py --use-adapter`를 지정합니다. 모델·어댑터·학습 지표·전용 Python은 `Documents/Codex/local-legal-ai`에 있습니다.
- 학습은 공개 법령 요약과 작성한 예제만 사용합니다. 모델의 법률 정확도나 전문가 자격을 인증하는 과정은 아닙니다.
- 법령 요약은 `src/main/resources/legal-ai/sources.json`, 판례는 앱의 공개 실제 판결 자료에서 찾습니다. 공식 링크와 자료 확인일을 표시하며 해당 자료에 없는 질문은 답변을 보류합니다. 새로운 법령이나 판례는 출처와 적용 시점을 확인해 자료를 갱신하세요.
- 실행 중에는 외부 모델 호출을 하지 않습니다. 루프백 `127.0.0.1:11435`만 사용하고 인증 토큰을 요구합니다. OpenAI로 자동 전환하지 않습니다.

## PC를 다시 시작한 뒤

Spring Tools에서 `ProjectApplication`을 실행하면 로컬 AI도 자동 실행됩니다. Spring이 시작한 AI는 Spring 종료 시 함께 종료됩니다. 이미 실행 중이던 AI는 재사용합니다. 별도로 켤 때는 `./local-ai/start-local-ai.ps1`을 사용합니다. 시작 로그는 로컬 런타임 폴더에 저장됩니다. 실행 중인 모델을 중복으로 띄우지 않습니다.

## 학습 다시 실행

기존 어댑터를 보존하려면 새 출력 디렉터리를 지정하세요. 설치된 Python 3.11, CUDA PyTorch와 `requirements.txt`의 의존성을 사용합니다. 모델 가중치는 공식 Qwen/Qwen3-4B의 safetensors 파일입니다.

```powershell
$aiRuntime = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex/local-legal-ai'
& "$aiRuntime/python-portable/python.exe" ./local-ai/train.py --base "$aiRuntime/model" --output "$aiRuntime/training-new" --corpus ./src/main/resources/legal-ai/sources.json --steps 40
```

Spring 설정은 기존 암호화 런타임 설정에 `legal.ai.provider=local`, `legal.ai.enabled=true`, `legal.ai.local-token`을 지정합니다. 토큰과 모델 파일을 Git에 추가하지 않습니다. 전체 테스트 중 실모델 테스트는 `-Dlegal.ai.smoke=true`와 `-Dlegal.ai.smoke-token-file=<토큰 파일 경로>`가 지정된 경우에만 실행합니다.

## 답변 품질 수정 적용

Spring과 로컬 AI를 모두 다시 실행해야 자료 선택 및 모델 기본값 변경이 반영됩니다. 이미 실행 중인 AI는 Spring 재시작만으로 교체되지 않을 수 있습니다. `/health`의 `ready=true`, `trained_adapter=false`는 정상적인 원본 모델 실행 상태입니다. 이 변경은 재학습이나 법률 정확도 인증이 아닙니다.

## 중간 저장·이어 학습

`train_legal_incremental.py`는 별도 출력 폴더에 초기 모델과 40건마다 체크포인트를 저장합니다. 모델 가중치뿐 아니라 옵티마이저, 처리 건수, 난수 상태도 저장합니다. 파일 저장이 끝나고 무결성 검사를 위한 해시를 기록한 뒤에만 `latest.json`을 바꿉니다. 이전 저장본은 덮어쓰지 않습니다.

학습을 안전하게 멈추려면 출력 폴더에 `STOP` 파일을 만드세요. 현재 배치를 마친 뒤 저장하고 종료합니다. `STOP` 파일을 지우고 같은 명령을 실행하면 마지막 완료 체크포인트에서 이어집니다. 데이터나 학습 설정이 바뀌면 이어 학습을 거부합니다. 강제 종료 시에는 마지막 저장 이후 최대 39건을 다시 계산할 수 있습니다.

```powershell
$aiRuntime = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Codex/local-legal-ai'
& "$aiRuntime/python-portable/python.exe" ./local-ai/train_legal_incremental.py --base "$aiRuntime/model" --dataset "$aiRuntime/training-1000-20261117/dataset.json" --output "$aiRuntime/training-1000-20261117"
```

2026-11-17 작업의 학습 예제와 평가 결과는 `training-1000-20261117`에 구분해 기록합니다. 수집·예제 생성과 실제 가중치 학습은 다릅니다. `result.json`의 `completed=true`, `seen=1000`으로 완료 여부를 확인하고, 생성 답변 비교를 통과하기 전에는 서비스에 적용하지 않습니다.

2026-11-17 추가 학습 결과: 공식 법률 700건과 판례 발췌 300건의 새 예제로 별도 LoRA 학습을 완료했습니다. 40건마다 저장하고 재개 및 최종 파일 무결성을 검증했습니다. 평가에서 근거 없는 출처 표시가 발견되어 서비스에는 적용하지 않았습니다. 데이터·가중치·체크포인트·상세 평가 결과는 PC의 로컬 런타임에 보관하며 Git에 포함하지 않습니다.
