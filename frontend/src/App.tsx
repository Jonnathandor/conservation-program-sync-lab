import { useEffect, useState, type FormEvent } from 'react';

type Result = {
  received: number;
  created: number;
  updated: number;
  unchanged: number;
  rejected: number;
};
type Receipt = { id: string; completedAt: string; result: Result };
type History = { receipts: Receipt[]; hasMore: boolean };
class SignedOut extends Error {}

async function api<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, { credentials: 'same-origin', ...options });
  if (response.status === 401)
    throw new SignedOut('Your session ended. Please sign in again.');
  if (!response.ok) {
    if (response.status === 403)
      throw new Error(
        'Request denied. Refresh the page and sign in again if needed.',
      );
    const body = response.headers
      .get('content-type')
      ?.includes('application/json')
      ? await response.json()
      : null;
    throw new Error(body?.message || 'The request failed. Please try again.');
  }
  return response.json();
}

function Counts({ result }: { result: Result }) {
  return (
    <dl className="counts">
      {Object.entries(result).map(([name, value]) => (
        <div key={name}>
          <dt>{name}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function App() {
  const [user, setUser] = useState<string | null>(null);
  const [starting, setStarting] = useState(true);
  const [file, setFile] = useState<File | null>(null);
  const [history, setHistory] = useState<History>({
    receipts: [],
    hasMore: false,
  });
  const [offset, setOffset] = useState(0);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [busy, setBusy] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [error, setError] = useState('');

  function failure(error: unknown) {
    if (error instanceof SignedOut) {
      setUser(null);
      setReceipt(null);
      setHistory({ receipts: [], hasMore: false });
    }
    setError(
      error instanceof Error
        ? error.message
        : 'Connection failed. Please try again.',
    );
  }
  async function loadHistory(nextOffset: number) {
    setLoadingHistory(true);
    try {
      const page = await api<History>(`/api/imports?offset=${nextOffset}`);
      setHistory(page);
      setOffset(nextOffset);
    } catch (error) {
      failure(error);
    } finally {
      setLoadingHistory(false);
    }
  }
  useEffect(() => {
    api<{ username: string }>('/api/session')
      .then(async (session) => {
        setUser(session.username);
        await loadHistory(0);
      })
      .catch((error) => {
        if (!(error instanceof SignedOut)) failure(error);
      })
      .finally(() => setStarting(false));
  }, []);

  async function upload(event: FormEvent) {
    event.preventDefault();
    if (!file || busy) return;
    setBusy(true);
    setError('');
    setReceipt(null);
    try {
      const csrf = await api<{ headerName: string; token: string }>(
        '/api/csrf',
      );
      const body = new FormData();
      body.append('file', file);
      // Do not set Content-Type: the browser supplies the multipart boundary.
      const response = await fetch('/api/imports', {
        method: 'POST',
        credentials: 'same-origin',
        body,
        headers: { [csrf.headerName]: csrf.token },
      });
      if (response.status === 401)
        throw new SignedOut('Your session ended. Please sign in again.');
      if (!response.ok) {
        const data = response.headers
          .get('content-type')
          ?.includes('application/json')
          ? await response.json()
          : null;
        throw new Error(
          data?.message || 'Upload failed. Refresh the page and try again.',
        );
      }
      const location = response.headers.get('Location');
      if (!location?.startsWith('/api/imports/'))
        throw new Error(
          'Upload completed but its receipt link is missing. Refresh history.',
        );
      setReceipt(await api<Receipt>(location));
      await loadHistory(0);
    } catch (error) {
      failure(error);
    } finally {
      setBusy(false);
    }
  }
  async function signOut() {
    setBusy(true);
    setError('');
    try {
      const csrf = await api<{ headerName: string; token: string }>(
        '/api/csrf',
      );
      const response = await fetch('/logout', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { [csrf.headerName]: csrf.token },
      });
      if (!response.ok) throw new Error('Sign out failed. Please try again.');
      setUser(null);
      setReceipt(null);
      setFile(null);
      setHistory({ receipts: [], hasMore: false });
    } catch (error) {
      failure(error);
    } finally {
      setBusy(false);
    }
  }
  async function viewReceipt(id: string) {
    setError('');
    setBusy(true);
    try {
      setReceipt(await api<Receipt>(`/api/imports/${id}`));
    } catch (error) {
      failure(error);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <header>
        <p className="eyebrow">MEADOWLINE / LOCAL DEMO</p>
        <h1>Program imports</h1>
        <p>
          Upload synthetic conservation records and revisit completed import
          receipts.
        </p>
      </header>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {starting ? (
        <p role="status">Checking session…</p>
      ) : !user ? (
        <section>
          <h2>Sign in to continue</h2>
          <p>
            This local demo uses one shared demo account. It contains synthetic
            data only.
          </p>
          <a className="button" href="/login">
            Sign in
          </a>
        </section>
      ) : (
        <>
          <div className="session">
            <span>Signed in as {user}</span>
            <button disabled={busy} onClick={signOut}>
              Sign out
            </button>
          </div>
          <section>
            <h2>Upload a CSV</h2>
            <p>
              All five required columns, in any order: source_id, region_code,
              status, area_hectares, source_updated_at.
            </p>
            <form onSubmit={upload}>
              <label htmlFor="csv">CSV file</label>
              <input
                id="csv"
                type="file"
                accept=".csv,text/csv"
                disabled={busy}
                onChange={(event) => setFile(event.target.files?.[0] || null)}
              />
              <button disabled={!file || busy || loadingHistory} type="submit">
                {busy ? 'Working…' : 'Upload CSV'}
              </button>
            </form>
            {busy && <p role="status">Working on your request…</p>}
          </section>
          {receipt && (
            <section aria-label="Receipt detail">
              <h2>Completed import</h2>
              <p>{new Date(receipt.completedAt).toLocaleString()}</p>
              <p className="id">{receipt.id}</p>
              <Counts result={receipt.result} />
              <p>
                {receipt.result.rejected > 0
                  ? 'Some rows were rejected and quarantined; the other rows were processed.'
                  : 'No rows were rejected.'}
              </p>
            </section>
          )}
          <section>
            <div className="section-title">
              <h2>Completed history</h2>
              <button
                disabled={loadingHistory || busy}
                onClick={() => {
                  setError('');
                  void loadHistory(offset);
                }}
              >
                Refresh history
              </button>
            </div>
            <p>
              20 receipts per page, newest first. Failed attempts are not
              recorded yet.
            </p>
            {loadingHistory ? (
              <p role="status">Loading history…</p>
            ) : history.receipts.length === 0 ? (
              <p>No completed imports on this page.</p>
            ) : (
              <ul>
                {history.receipts.map((item) => (
                  <li key={item.id}>
                    <div>
                      <time>{new Date(item.completedAt).toLocaleString()}</time>
                      <p>
                        {item.result.received} received · {item.result.rejected}
                        {' rejected'}
                      </p>
                      <span className="id">{item.id}</span>
                    </div>
                    <button
                      disabled={busy}
                      onClick={() => void viewReceipt(item.id)}
                    >
                      View receipt
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <nav aria-label="History pages">
              <button
                disabled={offset === 0 || loadingHistory || busy}
                onClick={() => void loadHistory(offset - 20)}
              >
                Previous
              </button>
              <span>Page {offset / 20 + 1}</span>
              <button
                disabled={!history.hasMore || loadingHistory || busy}
                onClick={() => void loadHistory(offset + 20)}
              >
                Next
              </button>
            </nav>
          </section>
        </>
      )}
      <footer>
        Local synthetic demonstration. No real program or personal data.
      </footer>
    </main>
  );
}
