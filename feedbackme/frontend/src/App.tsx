import { ChangeEvent, FormEvent, useEffect, useRef, useState } from 'react';
import {
  CheckCircle2,
  FileText,
  Flame,
  LoaderCircle,
  Send,
  Sparkles,
  Trophy,
  Upload,
  XCircle
} from 'lucide-react';

type FeedbackStatus = 'IDLE' | 'SUBMITTING' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

type SubmitResponse = {
  historyId: number;
  message: string;
};

type StatusResponse = {
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  result: string;
  updatedAt?: string;
};

const statusCopy: Record<FeedbackStatus, string> = {
  IDLE: '채용공고와 자기소개서를 넣고 피드백 퀘스트를 시작해보세요.',
  SUBMITTING: '요청을 접수하는 중입니다.',
  PENDING: '대기열에서 순서를 기다리는 중입니다.',
  PROCESSING: 'AI가 피드백을 생성하는 중입니다.',
  COMPLETED: '피드백 생성이 완료되었습니다.',
  FAILED: '피드백 생성 중 오류가 발생했습니다.'
};

const statusProgress: Record<FeedbackStatus, number> = {
  IDLE: 8,
  SUBMITTING: 25,
  PENDING: 45,
  PROCESSING: 78,
  COMPLETED: 100,
  FAILED: 100
};

export function App() {
  const [url, setUrl] = useState('');
  const [coverLetter, setCoverLetter] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<FeedbackStatus>('IDLE');
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [historyId, setHistoryId] = useState<number | null>(null);
  const pollTimer = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
      }
    };
  }, []);

  async function submitFeedback(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!url.trim() || !coverLetter.trim()) {
      setError('채용공고 URL과 자기소개서를 모두 입력해 주세요.');
      return;
    }

    setError('');
    setResult('');
    setStatus('SUBMITTING');

    const formData = new FormData();
    formData.append('url', url.trim());
    formData.append('coverLetter', coverLetter.trim());
    if (file) {
      formData.append('file', file);
    }

    try {
      const response = await fetch('/api/feedback', {
        method: 'POST',
        body: formData
      });
      const data = (await response.json()) as SubmitResponse & { error?: string };

      if (!response.ok) {
        throw new Error(data.error || data.message || '요청 접수에 실패했습니다.');
      }

      setHistoryId(data.historyId);
      setStatus('PENDING');
      startPolling(data.historyId);
    } catch (submitError) {
      setStatus('FAILED');
      setError(submitError instanceof Error ? submitError.message : '알 수 없는 오류가 발생했습니다.');
    }
  }

  function startPolling(id: number) {
    if (pollTimer.current) {
      window.clearInterval(pollTimer.current);
    }

    pollTimer.current = window.setInterval(async () => {
      try {
        const response = await fetch(`/api/feedback/status/${id}`);
        if (!response.ok) {
          throw new Error('상태 확인에 실패했습니다.');
        }

        const data = (await response.json()) as StatusResponse;
        setStatus(data.status);

        if (data.status === 'COMPLETED') {
          setResult(data.result);
          stopPolling();
        }

        if (data.status === 'FAILED') {
          setError('피드백 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
          stopPolling();
        }
      } catch (pollError) {
        setError(pollError instanceof Error ? pollError.message : '상태 확인 중 오류가 발생했습니다.');
      }
    }, 1500);
  }

  function stopPolling() {
    if (pollTimer.current) {
      window.clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }

  function resetQuest() {
    stopPolling();
    setStatus('IDLE');
    setResult('');
    setError('');
    setHistoryId(null);
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] ?? null);
  }

  const isWorking = status === 'SUBMITTING' || status === 'PENDING' || status === 'PROCESSING';
  const isComplete = status === 'COMPLETED';
  const isFailed = status === 'FAILED';

  return (
    <main className="app-shell">
      <header className="topbar" aria-label="FeedbackMe 상태">
        <div className="brand-mark" aria-hidden="true">
          <Sparkles size={28} />
        </div>
        <div>
          <p className="eyebrow">FeedbackMe</p>
          <h1>자소서 피드백 퀘스트</h1>
        </div>
        <div className="streak-pill" title="오늘의 분석 스트릭">
          <Flame size={20} />
          <span>1 day</span>
        </div>
      </header>

      <section className="progress-card" aria-live="polite">
        <div className="progress-card__header">
          <div>
            <p className="eyebrow">AI Queue</p>
            <h2>{statusCopy[status]}</h2>
          </div>
          <div className="xp-badge">
            <Trophy size={18} />
            <span>{statusProgress[status]} XP</span>
          </div>
        </div>
        <div className="progress-track" aria-label="피드백 진행률">
          <div className="progress-fill" style={{ width: `${statusProgress[status]}%` }} />
        </div>
        {historyId && <p className="job-id">작업 ID #{historyId}</p>}
      </section>

      <section className="workspace">
        <form className="quest-card" onSubmit={submitFeedback}>
          <div className="card-title-row">
            <FileText size={24} />
            <div>
              <p className="eyebrow">Mission</p>
              <h2>분석할 내용을 입력하세요</h2>
            </div>
          </div>

          <label className="field">
            <span>채용공고 URL</span>
            <input
              type="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://www.saramin.co.kr/..."
              disabled={isWorking}
            />
          </label>

          <label className="field">
            <span>자기소개서</span>
            <textarea
              value={coverLetter}
              onChange={(event) => setCoverLetter(event.target.value)}
              placeholder="자기소개서 내용을 입력하세요."
              disabled={isWorking}
            />
          </label>

          <label className="upload-box">
            <Upload size={22} />
            <span>{file ? file.name : 'PDF 또는 DOCX 첨부하기'}</span>
            <input type="file" accept=".pdf,.docx" onChange={handleFileChange} disabled={isWorking} />
          </label>

          {error && (
            <div className="feedback-alert feedback-alert--error">
              <XCircle size={20} />
              <span>{error}</span>
            </div>
          )}

          <button className="primary-button" type="submit" disabled={isWorking}>
            {isWorking ? <LoaderCircle className="spin" size={22} /> : <Send size={22} />}
            <span>{isWorking ? '진행 중' : '피드백 받기'}</span>
          </button>
        </form>

        <aside className="result-card">
          <div className="card-title-row">
            {isComplete ? <CheckCircle2 size={24} /> : <Sparkles size={24} />}
            <div>
              <p className="eyebrow">Result</p>
              <h2>{isComplete ? '피드백 리포트' : '결과 대기 중'}</h2>
            </div>
          </div>

          {isWorking && (
            <div className="waiting-state">
              <LoaderCircle className="spin" size={36} />
              <p>{statusCopy[status]}</p>
            </div>
          )}

          {isFailed && (
            <div className="feedback-alert feedback-alert--error">
              <XCircle size={20} />
              <span>작업이 실패했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.</span>
            </div>
          )}

          {isComplete && <pre className="result-text">{result}</pre>}

          {!isWorking && !isComplete && !isFailed && (
            <div className="empty-state">
              <Sparkles size={42} />
              <p>분석을 시작하면 이곳에 피드백이 표시됩니다.</p>
            </div>
          )}

          {(isComplete || isFailed) && (
            <button className="secondary-button" type="button" onClick={resetQuest}>
              새 피드백 시작
            </button>
          )}
        </aside>
      </section>
    </main>
  );
}
