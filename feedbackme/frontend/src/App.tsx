import { ChangeEvent, FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import {
  CheckCircle2,
  CreditCard,
  Download,
  FileText,
  History,
  LogIn,
  LogOut,
  LoaderCircle,
  Settings,
  Send,
  Sparkles,
  Upload,
  UserPlus,
  Wallet,
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

type HistoryItem = {
  id: number;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  jobUrl: string;
  companyName: string;
  jobTitle: string;
  attachmentName: string;
  hasResult: boolean;
  createdAt: string;
  updatedAt: string;
};

type HistoryResponse = {
  histories: HistoryItem[];
};

type AuthUser = {
  id: number;
  email: string;
  name: string;
};

type AuthResponse = {
  user: AuthUser;
  message?: string;
};

type CreditSummary = {
  balance: number;
  totalGranted: number;
  totalPurchased: number;
  totalUsed: number;
};

type PaymentProduct = {
  code: string;
  name: string;
  credits: number;
  amount: number;
};

type PaymentOrder = {
  orderId: string;
  productCode: string;
  orderName: string;
  amount: number;
  creditAmount: number;
  status: 'READY' | 'PAID' | 'FAILED';
  requestedAt: string;
  approvedAt: string;
};

type PaymentSummaryResponse = {
  credit: CreditSummary;
  products: PaymentProduct[];
  devMode: boolean;
};

type ViewMode = 'input' | 'auth' | 'mypage' | 'result';
type MyPageTab = 'history' | 'payment' | 'account' | 'credits';

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

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function buildHistoryTitle(item: HistoryItem): string {
  const company = item.companyName || '회사명 미확인';
  const attachment = item.attachmentName || '첨부 파일명 없음';
  return `${company} · ${attachment}`;
}

function buildHistoryMeta(item: HistoryItem): string {
  const title = item.jobTitle || item.jobUrl || '저장된 분석';
  return `${title} · ${formatDate(item.createdAt)}`;
}

export function App() {
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<FeedbackStatus>('IDLE');
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [historyId, setHistoryId] = useState<number | null>(null);
  const [queuePosition, setQueuePosition] = useState<number | null>(null);
  const [histories, setHistories] = useState<HistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [authName, setAuthName] = useState('');
  const [authEmail, setAuthEmail] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(true);
  const [viewMode, setViewMode] = useState<ViewMode>('input');
  const [creditSummary, setCreditSummary] = useState<CreditSummary | null>(null);
  const [paymentProducts, setPaymentProducts] = useState<PaymentProduct[]>([]);
  const [paymentDevMode, setPaymentDevMode] = useState(false);
  const [paymentError, setPaymentError] = useState('');
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [myPageTab, setMyPageTab] = useState<MyPageTab>('history');
  const pollTimer = useRef<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    void loadCurrentUser();

    return () => {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
      }
    };
  }, []);

  async function submitFeedback(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!user) {
      setAuthMode('login');
      setAuthError('분석을 시작하려면 로그인이 필요합니다.');
      setViewMode('auth');
      return;
    }

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
        body: formData,
        credentials: 'same-origin'
      });
      const data = (await response.json()) as SubmitResponse & { error?: string };

      if (!response.ok) {
        if (response.status === 402) {
          setPaymentError(data.error || data.message || '분석권이 부족합니다. 분석권을 충전해 주세요.');
          setViewMode('mypage');
          void loadPaymentSummary();
        }
        throw new Error(data.error || data.message || '분석 요청 접수에 실패했습니다.');
      }

      setHistoryId(data.historyId);
      setStatus('PENDING');
      setViewMode('result');
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
        const response = await fetch(`/api/feedback/status/${id}`, {
          credentials: 'same-origin'
        });
      if (!response.ok) {
        throw new Error('분석 상태 확인에 실패했습니다.');
      }

      const data = (await response.json()) as StatusResponse;
      setStatus(data.status);
      setQueuePosition(data.queuePosition ?? null);

      if (data.status === 'COMPLETED') {
      setResult(data.result);
      void loadHistories();
      void loadPaymentSummary();
      stopPolling();
      }

      if (data.status === 'FAILED') {
        setError('분석 중 오류가 발생했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.');
        void loadHistories();
        void loadPaymentSummary();
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
    setViewMode('input');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] ?? null);
  }

  async function loadHistories() {
    if (!user) {
      setHistories([]);
      return;
    }

    setHistoryLoading(true);
    try {
      const response = await fetch('/api/feedback/history', {
        credentials: 'same-origin'
      });
      if (!response.ok) {
        throw new Error('히스토리 조회에 실패했습니다.');
      }

      const data = (await response.json()) as HistoryResponse;
      setHistories(data.histories ?? []);
    } catch {
      setHistories([]);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function openHistory(item: HistoryItem) {
    stopPolling();
    setError('');
    setResult('');
    setQueuePosition(null);
    setHistoryId(item.id);
    setViewMode('result');

    try {
      const response = await fetch(`/api/feedback/status/${item.id}`, {
        credentials: 'same-origin'
      });
      if (!response.ok) {
        throw new Error('저장된 분석을 불러오지 못했습니다.');
      }

      const data = (await response.json()) as StatusResponse;
      setStatus(data.status);
      setQueuePosition(data.queuePosition ?? null);
      setResult(data.result ?? '');

      if (data.status === 'PENDING' || data.status === 'PROCESSING') {
        startPolling(item.id);
      }
    } catch (historyError) {
      setStatus('FAILED');
      setError(historyError instanceof Error ? historyError.message : '저장된 분석을 불러오지 못했습니다.');
    }
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

  async function loadCurrentUser() {
    setAuthLoading(true);
    try {
      const response = await fetch('/api/auth/me', {
        credentials: 'same-origin'
      });

      if (!response.ok) {
        setUser(null);
        setHistories([]);
        return;
      }

      const data = (await response.json()) as AuthResponse;
      setUser(data.user);
      await loadHistoriesForCurrentUser();
      await loadPaymentSummary();
    } finally {
      setAuthLoading(false);
    }
  }

  async function loadHistoriesForCurrentUser() {
    setHistoryLoading(true);
    try {
      const response = await fetch('/api/feedback/history', {
        credentials: 'same-origin'
      });
      if (!response.ok) {
        throw new Error('히스토리 조회에 실패했습니다.');
      }

      const data = (await response.json()) as HistoryResponse;
      setHistories(data.histories ?? []);
    } catch {
      setHistories([]);
    } finally {
      setHistoryLoading(false);
    }
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthError('');

    try {
      const response = await fetch(`/api/auth/${authMode === 'login' ? 'login' : 'register'}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'same-origin',
        body: JSON.stringify({
          email: authEmail,
          password: authPassword,
          name: authName
        })
      });
      const data = (await response.json()) as AuthResponse;

      if (!response.ok) {
        throw new Error(data.message || '인증 처리에 실패했습니다.');
      }

      setUser(data.user);
      setAuthName('');
      setAuthEmail('');
      setAuthPassword('');
      setViewMode('input');
      await loadHistoriesForCurrentUser();
      await loadPaymentSummary();
    } catch (authSubmitError) {
      setAuthError(authSubmitError instanceof Error ? authSubmitError.message : '인증 처리에 실패했습니다.');
    }
  }

  async function logout() {
    await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'same-origin'
    });
    resetQuest();
    setUser(null);
    setHistories([]);
    setCreditSummary(null);
    setPaymentProducts([]);
    setViewMode('input');
  }

  async function loadPaymentSummary() {
    setPaymentLoading(true);
    setPaymentError('');
    try {
      const response = await fetch('/api/payments/summary', {
        credentials: 'same-origin'
      });

      if (!response.ok) {
        throw new Error('결제 정보를 불러오지 못했습니다.');
      }

      const data = (await response.json()) as PaymentSummaryResponse;
      setCreditSummary(data.credit);
      setPaymentProducts(data.products ?? []);
      setPaymentDevMode(Boolean(data.devMode));
    } catch (paymentSummaryError) {
      setPaymentError(paymentSummaryError instanceof Error ? paymentSummaryError.message : '결제 정보를 불러오지 못했습니다.');
    } finally {
      setPaymentLoading(false);
    }
  }

  async function purchaseDev(productCode: string) {
    setPaymentLoading(true);
    setPaymentError('');
    try {
      const orderResponse = await fetch('/api/payments/orders', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'same-origin',
        body: JSON.stringify({ productCode })
      });
      const orderData = (await orderResponse.json()) as { order?: PaymentOrder; message?: string };
      if (!orderResponse.ok || !orderData.order) {
        throw new Error(orderData.message || '주문 생성에 실패했습니다.');
      }

      const confirmResponse = await fetch(`/api/payments/orders/${orderData.order.orderId}/dev-confirm`, {
        method: 'POST',
        credentials: 'same-origin'
      });
      const confirmData = (await confirmResponse.json()) as { credit?: CreditSummary; message?: string };
      if (!confirmResponse.ok) {
        throw new Error(confirmData.message || '개발 결제 승인에 실패했습니다.');
      }

      await loadPaymentSummary();
    } catch (purchaseError) {
      setPaymentError(purchaseError instanceof Error ? purchaseError.message : '결제 처리에 실패했습니다.');
    } finally {
      setPaymentLoading(false);
    }
  }

  function goHome() {
    resetQuest();
    setAuthError('');
    setViewMode('input');
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
        <button className="brand-home" type="button" onClick={goHome} aria-label="홈으로 이동">
          <span className="brand-mark" aria-hidden="true">
            <Sparkles size={28} />
          </span>
          <span>
            <strong>Feedback Me</strong>
            <small>AI 직무 적합도 분석</small>
          </span>
        </button>
        <nav className="account-nav" aria-label="계정 메뉴">
          {user ? (
            <div className="auth-summary">
              <div>
                <strong>{user.name}</strong>
                <span>{user.email}</span>
              </div>
              <button
                className="icon-text-button"
                type="button"
                onClick={() => {
                  setViewMode('mypage');
                  setMyPageTab('history');
                  void loadHistories();
                  void loadPaymentSummary();
                }}
              >
                <History size={18} />
                <span>마이페이지</span>
              </button>
              <button className="icon-text-button" type="button" onClick={() => void logout()}>
                <LogOut size={18} />
                <span>로그아웃</span>
              </button>
            </div>
          ) : (
            <div className="account-actions">
              <button
                className="icon-text-button"
                type="button"
                onClick={() => {
                  setAuthMode('login');
                  setAuthError('');
                  setViewMode('auth');
                }}
              >
                <LogIn size={18} />
                <span>로그인</span>
              </button>
              <button
                className="icon-text-button"
                type="button"
                onClick={() => {
                  setAuthMode('register');
                  setAuthError('');
                  setViewMode('auth');
                }}
              >
                <UserPlus size={18} />
                <span>회원가입</span>
              </button>
            </div>
          )}
        </nav>
      </header>

      {viewMode === 'result' && hasStarted && (
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
        {authLoading ? (
          <section className="auth-card panel-card">
            <LoaderCircle className="spin" size={32} />
            <p>로그인 상태를 확인하는 중입니다.</p>
          </section>
        ) : viewMode === 'auth' ? (
          <form className="auth-card panel-card" onSubmit={submitAuth}>
            <div className="card-title-row">
              {authMode === 'login' ? <LogIn size={24} /> : <UserPlus size={24} />}
              <div>
                <p className="eyebrow">Account</p>
                <h2>{authMode === 'login' ? '로그인' : '회원가입'}</h2>
              </div>
            </div>

            {authMode === 'register' && (
              <label className="field">
                <span>이름</span>
                <input
                  type="text"
                  value={authName}
                  onChange={(event) => setAuthName(event.target.value)}
                  placeholder="정유경"
                />
              </label>
            )}

            <label className="field">
              <span>이메일</span>
              <input
                type="email"
                value={authEmail}
                onChange={(event) => setAuthEmail(event.target.value)}
                placeholder="you@example.com"
                required
              />
            </label>

            <label className="field">
              <span>비밀번호</span>
              <input
                type="password"
                value={authPassword}
                onChange={(event) => setAuthPassword(event.target.value)}
                placeholder="8자 이상"
                minLength={8}
                required
              />
            </label>

            {authError && (
              <div className="feedback-alert feedback-alert--error">
                <XCircle size={20} />
                <span>{authError}</span>
              </div>
            )}

            <button className="primary-button" type="submit">
              {authMode === 'login' ? <LogIn size={22} /> : <UserPlus size={22} />}
              <span>{authMode === 'login' ? '로그인하기' : '가입하고 시작하기'}</span>
            </button>

            <button
              className="text-button"
              type="button"
              onClick={() => {
                setAuthError('');
                setAuthMode(authMode === 'login' ? 'register' : 'login');
              }}
            >
              {authMode === 'login' ? '계정이 없나요? 회원가입' : '이미 계정이 있나요? 로그인'}
            </button>
          </form>
        ) : viewMode === 'mypage' ? (
          <section className="mypage-shell panel-card">
            <aside className="mypage-nav" aria-label="마이페이지 메뉴">
              <button
                className={myPageTab === 'history' ? 'mypage-nav__item is-active' : 'mypage-nav__item'}
                type="button"
                onClick={() => setMyPageTab('history')}
              >
                <History size={18} />
                <span>분석 히스토리</span>
              </button>
              <button
                className={myPageTab === 'payment' ? 'mypage-nav__item is-active' : 'mypage-nav__item'}
                type="button"
                onClick={() => setMyPageTab('payment')}
              >
                <CreditCard size={18} />
                <span>결제</span>
              </button>
              <button
                className={myPageTab === 'account' ? 'mypage-nav__item is-active' : 'mypage-nav__item'}
                type="button"
                onClick={() => setMyPageTab('account')}
              >
                <Settings size={18} />
                <span>회원 관리</span>
              </button>
              <button
                className={myPageTab === 'credits' ? 'mypage-nav__item is-active' : 'mypage-nav__item'}
                type="button"
                onClick={() => setMyPageTab('credits')}
              >
                <Wallet size={18} />
                <span>분석권 관리</span>
              </button>
            </aside>

            <div className="mypage-content">
              {myPageTab === 'history' && (
                <section>
                  <div className="page-title-row">
                    <div className="card-title-row">
                      <History size={24} />
                      <div>
                        <p className="eyebrow">My Page</p>
                        <h2>분석 히스토리</h2>
                      </div>
                    </div>
                    <button className="history-item__button" type="button" onClick={() => setViewMode('input')}>
                      새 분석
                    </button>
                  </div>

                  {historyLoading && <p className="history-empty">히스토리를 불러오는 중입니다.</p>}

                  {!historyLoading && histories.length === 0 && (
                    <p className="history-empty">아직 저장된 분석이 없습니다.</p>
                  )}

                  {!historyLoading && histories.length > 0 && (
                    <div className="history-list">
                      {histories.map((item) => (
                        <article className="history-item" key={item.id}>
                          <div>
                            <p className="history-item__title">{buildHistoryTitle(item)}</p>
                            <p className="history-item__meta">{buildHistoryMeta(item)}</p>
                          </div>
                          <button
                            className="history-item__button"
                            type="button"
                            onClick={() => void openHistory(item)}
                            disabled={!item.hasResult && item.status === 'FAILED'}
                          >
                            {item.status === 'COMPLETED' ? '리포트 보기' : statusCopy[item.status]}
                          </button>
                        </article>
                      ))}
                    </div>
                  )}
                </section>
              )}

              {myPageTab === 'payment' && (
                <section className="billing-panel billing-panel--flat">
                  <div className="card-title-row">
                    <CreditCard size={24} />
                    <div>
                      <p className="eyebrow">Payment</p>
                      <h2>분석권 결제</h2>
                    </div>
                  </div>

                  {paymentError && (
                    <div className="feedback-alert feedback-alert--error">
                      <XCircle size={20} />
                      <span>{paymentError}</span>
                    </div>
                  )}

                  <div className="product-grid">
                    {paymentProducts.map((product) => (
                      <article className="product-card" key={product.code}>
                        <div>
                          <strong>{product.name}</strong>
                          <span>{product.credits}회 충전</span>
                        </div>
                        <p>{product.amount.toLocaleString('ko-KR')}원</p>
                        <button
                          className="history-item__button"
                          type="button"
                          disabled={paymentLoading || !paymentDevMode}
                          onClick={() => void purchaseDev(product.code)}
                        >
                          <CreditCard size={16} />
                          <span>{paymentDevMode ? '개발 결제 완료' : '결제 준비 중'}</span>
                        </button>
                      </article>
                    ))}
                  </div>
                </section>
              )}

              {myPageTab === 'account' && (
                <section className="account-panel">
                  <div className="card-title-row">
                    <Settings size={24} />
                    <div>
                      <p className="eyebrow">Account</p>
                      <h2>회원 관리</h2>
                    </div>
                  </div>
                  <div className="account-detail">
                    <div>
                      <span>이름</span>
                      <strong>{user?.name}</strong>
                    </div>
                    <div>
                      <span>이메일</span>
                      <strong>{user?.email}</strong>
                    </div>
                  </div>
                </section>
              )}

              {myPageTab === 'credits' && (
                <section className="credits-panel">
                  <div className="card-title-row">
                    <Wallet size={24} />
                    <div>
                      <p className="eyebrow">Credits</p>
                      <h2>분석권 관리</h2>
                    </div>
                  </div>

                  <div className="credit-hero">
                    <span>잔여 분석권</span>
                    <strong>{creditSummary?.balance ?? 0}회</strong>
                  </div>

                  <div className="credit-metrics">
                    <div>
                      <span>무료 지급</span>
                      <strong>{creditSummary?.totalGranted ?? 0}회</strong>
                    </div>
                    <div>
                      <span>구매</span>
                      <strong>{creditSummary?.totalPurchased ?? 0}회</strong>
                    </div>
                    <div>
                      <span>사용</span>
                      <strong>{creditSummary?.totalUsed ?? 0}회</strong>
                    </div>
                  </div>

                  <button className="primary-button" type="button" onClick={() => setMyPageTab('payment')}>
                    <CreditCard size={22} />
                    <span>분석권 충전하기</span>
                  </button>
                </section>
              )}
            </div>
          </section>
        ) : viewMode === 'input' ? (
          <div className="idle-stack">
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
          </div>
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
