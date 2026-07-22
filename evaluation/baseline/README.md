# Baseline Evaluation

고도화 전 FeedbackMe의 결과를 보관하는 기준선입니다.

각 테스트 케이스에는 다음 자료를 보관합니다.

- `job-posting.txt`: 분석에 사용한 공고 원문
- `candidate.docx`: 테스트 조건에 맞춘 허구의 지원자 이력서·포트폴리오
- `candidate-personal.pdf`: 동일 공고에 함께 실행할 개인 이력서(로컬 전용, Git 제외)
- `expected.md`: 반드시 충족하거나 피해야 할 조건
- `actual-run-01.md` 등: 실행별 원본 결과
- `score.json`: 수동 평가 점수와 오류 개수
- `run-metadata.json`: 커밋, 모델, 실행 시간, 캐시 여부

점수는 항목별로 `0`(실패), `1`(부분 충족), `2`(충족)를 사용합니다.
