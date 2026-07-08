import { ChangeEvent, FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import {
  CheckCircle2,
  Download,
  FileText,
  LoaderCircle,
  Send,
  Sparkles,
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
  queuePosition?: number;
  updatedAt?: string;
};

const statusCopy: Record<FeedbackStatus, string> = {
  IDLE: '채용공고와 이력서/포트폴리오를 넣고 직무 적합도 분석을 시작해보세요.',
  SUBMITTING: '분석 요청을 접수하는 중입니다.',
  PENDING: '대기열에서 순서를 기다리는 중입니다.',
  PROCESSING: 'AI가 직무 적합도와 지원 전략을 분석하는 중입니다.',
  COMPLETED: '분석이 완료되었습니다.',
  FAILED: '분석 중 오류가 발생했습니다.'
};

const statusProgress: Record<FeedbackStatus, number> = {
  IDLE: 0,
  SUBMITTING: 8,
  PENDING: 45,
  PROCESSING: 78,
  COMPLETED: 100,
  FAILED: 100
};

function renderInline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }

    return part;
  });
}

function MarkdownReport({ content }: { content: string }) {
  const elements: ReactNode[] = [];

  content.split(/\r?\n/).forEach((rawLine, index) => {
    const line = rawLine.trim();

    if (!line) {
      return;
    }

    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      const level = heading[1].length;
      const text = heading[2].replace(/^#\s+/, '');

      if (level === 1) {
        elements.push(
          <h2 className="report-heading report-heading--main" key={index}>
            {renderInline(text)}
          </h2>
        );
      } else {
        elements.push(
          <h3 className="report-heading" key={index}>
            {renderInline(text)}
          </h3>
        );
      }
      return;
    }

    const bullet = rawLine.match(/^(\s*)[-*]\s+(.+)$/);
    if (bullet) {
      const level = Math.min(Math.floor(bullet[1].replace(/\t/g, '  ').length / 2), 4);
      elements.push(
        <div className="report-bullet" data-level={level} key={index}>
          <span className="report-bullet__marker" aria-hidden="true" />
          <span>{renderInline(bullet[2])}</span>
        </div>
      );
      return;
    }

    const numbered = rawLine.match(/^(\s*)\d+[.)]\s+(.+)$/);
    if (numbered) {
      const level = Math.min(Math.floor(numbered[1].replace(/\t/g, '  ').length / 2), 4);
      elements.push(
        <div className="report-bullet report-bullet--numbered" data-level={level} key={index}>
          <span className="report-bullet__marker" aria-hidden="true" />
          <span>{renderInline(numbered[2])}</span>
        </div>
      );
      return;
    }

    elements.push(
      <p className="report-paragraph" key={index}>
        {renderInline(line)}
      </p>
    );
  });

  return <div className="report-markdown">{elements}</div>;
}

export function App() {
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<FeedbackStatus>('IDLE');
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [historyId, setHistoryId] = useState<number | null>(null);
  const [queuePosition, setQueuePosition] = useState<number | null>(null);
  const pollTimer = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    return () => {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
      }
    };
  }, []);

  async function submitFeedback(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!url.trim()) {
      setError('채용공고 URL을 입력해 주세요.');
      return;
    }

    if (!file) {
      setError('이력서 또는 포트폴리오 파일을 첨부해 주세요.');
      return;
    }

    setError('');
    setResult('');
    setQueuePosition(null);
    setStatus('SUBMITTING');

    const formData = new FormData();
    formData.append('url', url.trim());
    formData.append('file', file);

    try {
      const response = await fetch('/api/feedback', {
        method: 'POST',
        body: formData
      });
      const data = (await response.json()) as SubmitResponse & { error?: string };

      if (!response.ok) {
        throw new Error(data.error || data.message || '분석 요청 접수에 실패했습니다.');
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

    void fetchFeedbackStatus(id);
    pollTimer.current = window.setInterval(() => {
      void fetchFeedbackStatus(id);
    }, 1500);
  }

  async function fetchFeedbackStatus(id: number) {
    try {
      const response = await fetch(`/api/feedback/status/${id}`);
      if (!response.ok) {
        throw new Error('분석 상태 확인에 실패했습니다.');
      }

      const data = (await response.json()) as StatusResponse;
      setStatus(data.status);
      setQueuePosition(data.queuePosition ?? null);

      if (data.status === 'COMPLETED') {
        setResult(data.result);
        stopPolling();
      }

      if (data.status === 'FAILED') {
        setError('분석 중 오류가 발생했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.');
        stopPolling();
      }
    } catch (pollError) {
      setError(pollError instanceof Error ? pollError.message : '상태 확인 중 오류가 발생했습니다.');
    }
  }

  function stopPolling() {
    if (pollTimer.current) {
      window.clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }

  function resetQuest() {
    stopPolling();
    setUrl('');
    setFile(null);
    setStatus('IDLE');
    setResult('');
    setError('');
    setHistoryId(null);
    setQueuePosition(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] ?? null);
  }

  function downloadReport() {
    if (!result.trim()) {
      return;
    }

    const blob = new Blob([result], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const timestamp = new Date().toISOString().slice(0, 10);
    link.href = url;
    link.download = `feedbackme-report-${historyId ?? timestamp}.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  const isWorking = status === 'SUBMITTING' || status === 'PENDING' || status === 'PROCESSING';
  const isComplete = status === 'COMPLETED';
  const isFailed = status === 'FAILED';
  const hasStarted = status !== 'IDLE' || historyId !== null;
  const queueMessage =
    status === 'PENDING'
      ? queuePosition
        ? `현재 대기열 ${queuePosition}번째입니다.`
        : '대기열 순번을 확인하는 중입니다.'
      : status === 'PROCESSING'
        ? '대기열을 통과해 분석을 시작했습니다.'
        : '';

  return (
    <main className="app-shell">
      <header className="topbar" aria-label="FeedbackMe 상태">
        <div className="brand-mark" aria-hidden="true">
          <Sparkles size={28} />
        </div>
        <div>
          <p className="eyebrow">FeedbackMe</p>
          <h1>직무 적합도 분석 Agent</h1>
        </div>
      </header>

      {hasStarted && (
        <section className="progress-card" aria-live="polite">
          <div className="progress-card__header">
            <div>
              <p className="eyebrow">Analysis Queue</p>
              <h2>{statusCopy[status]}</h2>
            </div>
          </div>
          <div className="progress-track" aria-label="분석 진행률">
            <div className="progress-fill" style={{ width: `${statusProgress[status]}%` }} />
          </div>
          {queueMessage && <p className="queue-meta">{queueMessage}</p>}
        </section>
      )}

      <section className="workspace workspace--single">
        {!hasStarted ? (
          <form className="quest-card panel-card" onSubmit={submitFeedback}>
            <div className="card-title-row">
              <FileText size={24} />
              <div>
                <p className="eyebrow">Input</p>
                <h2>분석할 공고와 지원자 자료를 입력하세요</h2>
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

            <label className="upload-box">
              <Upload size={22} />
              <span>{file ? file.name : '이력서 또는 포트폴리오 PDF/DOCX 첨부하기'}</span>
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.docx"
                onChange={handleFileChange}
                disabled={isWorking}
              />
            </label>

            {error && (
              <div className="feedback-alert feedback-alert--error">
                <XCircle size={20} />
                <span>{error}</span>
              </div>
            )}

            <button className="primary-button" type="submit" disabled={isWorking}>
              <Send size={22} />
              <span>직무 적합도 분석하기</span>
            </button>
          </form>
        ) : (
          <aside className="result-card panel-card result-card--expanded">
            <div className="card-title-row">
              {isComplete ? <CheckCircle2 size={24} /> : <Sparkles size={24} />}
              <div>
                <p className="eyebrow">Result</p>
                <h2>{isComplete ? '분석 리포트' : '결과 대기 중'}</h2>
              </div>
            </div>

            {isWorking && (
              <div className="waiting-state">
                <LoaderCircle className="spin" size={36} />
                <p>{statusCopy[status]}</p>
                {queueMessage && <p className="queue-meta queue-meta--center">{queueMessage}</p>}
              </div>
            )}

            {isFailed && (
              <div className="feedback-alert feedback-alert--error result-alert">
                <XCircle size={20} />
                <span>작업이 실패했습니다. 입력 내용을 확인하고 다시 시도해 주세요.</span>
              </div>
            )}

            {isComplete && <MarkdownReport content={result} />}

            {(isComplete || isFailed) && (
              <div className="result-actions">
                {isComplete && (
                  <button className="secondary-button" type="button" onClick={downloadReport}>
                    <Download size={20} />
                    <span>리포트 내보내기</span>
                  </button>
                )}
                <button className="secondary-button" type="button" onClick={resetQuest}>
                  새 분석 시작
                </button>
              </div>
            )}
          </aside>
        )}
      </section>
    </main>
  );
}
