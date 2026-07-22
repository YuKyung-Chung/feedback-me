# FeedbackMe Evaluation

## Automated Single-step vs Harness comparison

From the backend directory, run:

```powershell
./gradlew harnessComparisonEvaluation
```

The runner executes TC-01 to TC-05 for both modes (sample and personal candidates), three repetitions by default, and stores raw results plus `summary.json` under `evaluation/runs/comparison-*`.

Use `EVALUATION_REPETITIONS=5` to change repetition count. The summary automatically includes average duration, average usage-based cost, and success rate. Human quality scores remain in each test case's `score.json` so correctness and hallucination judgments are not replaced by a superficial automated score.

FeedbackMe 고도화 전후의 분석 품질을 동일한 입력과 기준으로 비교하기 위한 평가 자료입니다.

## 디렉터리

- `baseline/`: 고도화 전 결과
- 추후 고도화 결과는 `harness/` 아래에 같은 테스트 케이스 ID로 저장합니다.

## 실행 원칙

1. 각 테스트 케이스의 `job-posting.txt`를 실제 공고 원문으로 교체합니다.
2. 각 테스트 케이스에 포함된 허구의 평가용 `candidate.docx`를 사용합니다.
3. 동일한 입력을 사용해 최소 3회 실행합니다.
4. Redis 캐시 사용 여부와 Git 커밋을 `run-metadata.json`에 기록합니다.
5. 결과 전문은 `actual-run-01.md`부터 순서대로 저장합니다.
6. `score.json`에 공통 평가 기준에 따른 점수와 관찰 내용을 기록합니다.

개인정보와 실제 지원 문서는 공개 저장소에 커밋하지 마세요.

## 자동 기준선 실행

백엔드 프로젝트 폴더에서 실행합니다.

```powershell
cd C:\Users\jyk45\feedbackme\feedbackme\feedbackme
$env:EVALUATION_CASES = "TC-01"
$env:EVALUATION_CANDIDATES = "sample"
.\gradlew.bat baselineEvaluation
```

위 명령은 TC-01의 샘플 문서만 실행하는 스모크 테스트입니다. 성공 후 전체 10건을 실행하려면 선택 변수를 제거합니다.

```powershell
Remove-Item Env:EVALUATION_CASES -ErrorAction SilentlyContinue
Remove-Item Env:EVALUATION_CANDIDATES -ErrorAction SilentlyContinue
.\gradlew.bat baselineEvaluation
```

결과는 `evaluation/runs/baseline-<실행시각>/`에 저장됩니다. 전체 실행은 Gemini API를 10회 호출하며, PDF 입력 5건은 Vision 요청으로 처리됩니다.
# Evaluation baseline policy

The `baseline/` cases represent the OpenAI single-step baseline. The comparison target is `harness/`, which will use the same inputs with the multi-step OpenAI workflow. Historical Gemini results are not used as a quality comparison baseline.
