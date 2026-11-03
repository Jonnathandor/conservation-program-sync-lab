import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from './App';
const result = {
  received: 2,
  created: 1,
  updated: 0,
  unchanged: 0,
  rejected: 1,
};
const receipt = {
  id: 'fixture-receipt',
  completedAt: '2026-10-30T04:00:00Z',
  result,
};
function json(body: unknown, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });
}
afterEach(() => vi.unstubAllGlobals());
test('anonymous user sees a real server login link', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)));
  render(<App />);
  expect(await screen.findByRole('link', { name: 'Sign in' })).toHaveAttribute(
    'href',
    '/login',
  );
  expect(
    screen.queryByRole('button', { name: 'Upload CSV' }),
  ).not.toBeInTheDocument();
});
test('uploads with CSRF, shows quarantine counters, and reloads history', async () => {
  const fetchMock = vi.fn().mockImplementation(async (url: string) => {
    if (url === '/api/session') return json({ username: 'demo' });
    if (url === '/api/csrf')
      return json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
    if (url === '/api/imports')
      return json(result, 201, { Location: '/api/imports/fixture-receipt' });
    if (url === '/api/imports/fixture-receipt') return json(receipt);
    return json({ receipts: [receipt], hasMore: false });
  });
  vi.stubGlobal('fetch', fetchMock);
  render(<App />);
  await screen.findByText('Signed in as demo');
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: 'Refresh history' }),
    ).toBeEnabled(),
  );
  const user = userEvent.setup();
  await user.upload(
    screen.getByLabelText('CSV file'),
    new File(['synthetic'], 'demo.csv', { type: 'text/csv' }),
  );
  await user.click(screen.getByRole('button', { name: 'Upload CSV' }));
  expect(
    await screen.findByRole('heading', { name: 'Completed import' }),
  ).toBeInTheDocument();
  expect(screen.getByText(/Some rows were rejected/)).toBeInTheDocument();
  const post = fetchMock.mock.calls.find(([url]) => url === '/api/imports');
  expect(post?.[1].headers).toEqual({ 'X-CSRF-TOKEN': 'test-token' });
  expect(post?.[1].body).toBeInstanceOf(FormData);
});
test('header error is shown without claiming upload success and retry stays available', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (url: string) => {
      if (url === '/api/session') return json({ username: 'demo' });
      if (url === '/api/csrf')
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      if (url === '/api/imports')
        return json(
          { code: 'INVALID_CSV_HEADERS', message: 'Headers must match.' },
          400,
        );
      return json({ receipts: [], hasMore: false });
    }),
  );
  render(<App />);
  await screen.findByText('Signed in as demo');
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: 'Refresh history' }),
    ).toBeEnabled(),
  );
  const user = userEvent.setup();
  await user.upload(
    screen.getByLabelText('CSV file'),
    new File(['bad'], 'bad.csv', { type: 'text/csv' }),
  );
  await user.click(screen.getByRole('button', { name: 'Upload CSV' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Headers must match.',
  );
  expect(
    screen.queryByRole('heading', { name: 'Completed import' }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Upload CSV' })).toBeEnabled();
});
test('history failure is visible and a refresh retries', async () => {
  let reads = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (url: string) => {
      if (url === '/api/session') return json({ username: 'demo' });
      if (reads++ === 0) throw new Error('Connection failed.');
      return json({ receipts: [], hasMore: false });
    }),
  );
  render(<App />);
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Connection failed.',
  );
  await userEvent.click(
    screen.getByRole('button', { name: 'Refresh history' }),
  );
  expect(
    await screen.findByText('No completed imports on this page.'),
  ).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('shows a loading state until session check finishes', () => {
  vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})));
  render(<App />);
  expect(screen.getByRole('status')).toHaveTextContent('Checking session');
});
test('expired session during refresh clears private history and offers sign in', async () => {
  let reads = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (url: string) => {
      if (url === '/api/session') return json({ username: 'demo' });
      return reads++ === 0
        ? json({ receipts: [receipt], hasMore: false })
        : json({}, 401);
    }),
  );
  render(<App />);
  await screen.findByText('fixture-receipt');
  await userEvent.click(
    screen.getByRole('button', { name: 'Refresh history' }),
  );
  expect(
    await screen.findByRole('link', { name: 'Sign in' }),
  ).toBeInTheDocument();
  expect(screen.queryByText('fixture-receipt')).not.toBeInTheDocument();
});
