// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RequestDetails from './RequestDetails';
import { requestsApi } from './api';
import type { HistoryEvent, ServiceRequest } from './api';

vi.mock('./api', () => ({ requestsApi: { get: vi.fn(), history: vi.fn() } }));
const request: ServiceRequest = { id: 1, title: 'Освещение улицы', description: 'Фонарь возле дома 12', status: 'IN_PROGRESS', createdAt: '2026-01-01T00:00:00Z' };
const events: HistoryEvent[] = [
  { id: 1, action: 'CREATED', fromStatus: null, toStatus: 'NEW', occurredAt: request.createdAt },
  { id: 2, action: 'STATUS_CHANGED', fromStatus: 'NEW', toStatus: 'IN_PROGRESS', occurredAt: '2026-01-02T00:00:00Z' },
];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(requestsApi.get).mockResolvedValue(request);
  vi.mocked(requestsApi.history).mockResolvedValue(events);
});
afterEach(cleanup);

it('shows full request details and the chronological transitions', async () => {
  render(<RequestDetails id={1} revision={0} onClose={() => {}} />);
  await screen.findByText(request.title);
  expect(screen.getByText(request.description)).not.toBeNull();
  expect(screen.getAllByRole('listitem')).toHaveLength(2);
  expect(screen.getByText('Новое → В работе')).not.toBeNull();
  expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Обращение #1' }));
});

it('explains that imported events do not reconstruct earlier transitions', async () => {
  vi.mocked(requestsApi.history).mockResolvedValue([{ id: 3, action: 'IMPORTED', fromStatus: null, toStatus: 'IN_PROGRESS', occurredAt: request.createdAt }]);
  render(<RequestDetails id={1} revision={0} onClose={() => {}} />);
  await screen.findByText('Статус при подключении истории: В работе');
  expect(screen.getByText(/Ранние изменения не записывались/)).not.toBeNull();
});

it('retries failed loads without displaying stale details', async () => {
  vi.mocked(requestsApi.history).mockRejectedValueOnce(new Error('Сервер недоступен'));
  const user = userEvent.setup();
  render(<RequestDetails id={1} revision={0} onClose={() => {}} />);
  await screen.findByRole('alert');
  expect(screen.queryByText(request.title)).toBeNull();
  await user.click(screen.getByRole('button', { name: 'Повторить загрузку карточки' }));
  await screen.findByText(request.title);
  expect(screen.queryByRole('alert')).toBeNull();
});

it('cancels requests on close and invokes the close callback', async () => {
  const close = vi.fn();
  const user = userEvent.setup();
  const view = render(<RequestDetails id={1} revision={0} onClose={close} />);
  await screen.findByText(request.title);
  await user.click(screen.getByRole('button', { name: 'Закрыть карточку обращения' }));
  expect(close).toHaveBeenCalledOnce();
  const signal = vi.mocked(requestsApi.get).mock.calls[0][1];
  view.unmount();
  expect(signal?.aborted).toBe(true);
});

it('ignores a late response after the selected request changes', async () => {
  let finish!: (value: ServiceRequest) => void;
  vi.mocked(requestsApi.get).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(<RequestDetails id={1} revision={0} onClose={() => {}} />);
  vi.mocked(requestsApi.get).mockResolvedValue({ ...request, id: 2, title: 'Второе обращение' });
  view.rerender(<RequestDetails id={2} revision={0} onClose={() => {}} />);
  await screen.findByText('Второе обращение');
  await act(async () => { finish(request); });
  expect(screen.queryByText(request.title)).toBeNull();
});

it('reloads the history when the dashboard revision changes', async () => {
  const view = render(<RequestDetails id={1} revision={0} onClose={() => {}} />);
  await screen.findByText(request.title);
  vi.mocked(requestsApi.history).mockResolvedValue([]);
  view.rerender(<RequestDetails id={1} revision={1} onClose={() => {}} />);
  await screen.findByText('История пока недоступна.');
  expect(requestsApi.history).toHaveBeenCalledTimes(2);
});
