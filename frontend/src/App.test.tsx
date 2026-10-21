// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { requestsApi } from './api';
import type { RequestPage, RequestQuery, ServiceRequest } from './api';

vi.mock('./api', () => ({ requestsApi: { page: vi.fn(), stats: vi.fn(), create: vi.fn(), changeStatus: vi.fn(), get: vi.fn(), history: vi.fn() } }));

const item: ServiceRequest = { id: 1, title: 'Освещение', description: 'Не работает фонарь', status: 'NEW', createdAt: '2026-01-01T00:00:00Z' };
const result = (query: RequestQuery): RequestPage => ({
  items: query.q ? [] : [{ ...item, title: query.page ? 'Вторая страница' : item.title }],
  page: query.page, size: query.size, totalElements: query.q ? 0 : 25, totalPages: query.q ? 0 : 2,
});

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(requestsApi.page).mockImplementation(async query => result(query));
  vi.mocked(requestsApi.stats).mockResolvedValue({ total: 25, newCount: 25, inProgress: 0, resolved: 0 });
  vi.mocked(requestsApi.create).mockResolvedValue(item);
  vi.mocked(requestsApi.changeStatus).mockResolvedValue({ ...item, status: 'IN_PROGRESS' });
  vi.mocked(requestsApi.get).mockResolvedValue(item);
  vi.mocked(requestsApi.history).mockResolvedValue([]);
});
afterEach(cleanup);

it('opens request details and returns keyboard focus to the row after closing', async () => {
  const user = userEvent.setup();
  render(<App />);
  const open = await screen.findByRole('button', { name: 'Подробнее: Освещение' });
  await user.click(open);
  await screen.findByRole('heading', { name: 'История обращения' });
  expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Обращение #1' }));
  expect(open.getAttribute('aria-expanded')).toBe('true');
  await user.click(screen.getByRole('button', { name: 'Закрыть карточку обращения' }));
  expect(screen.queryByRole('heading', { name: 'Обращение #1' })).toBeNull();
  expect(document.activeElement).toBe(open);
});

it('navigates pages and resets to the first page after a debounced search', async () => {
  const user = userEvent.setup();
  render(<App />);
  await screen.findByText('Страница 1 из 2');
  expect(screen.getByRole('button', { name: 'Назад' })).toHaveProperty('disabled', true);
  await user.click(screen.getByRole('button', { name: 'Далее' }));
  await screen.findByText('Страница 2 из 2');
  expect(screen.getByRole('button', { name: 'Далее' })).toHaveProperty('disabled', true);
  await user.type(screen.getByRole('textbox', { name: 'Поиск обращений' }), 'свет');
  await waitFor(() => expect(requestsApi.page).toHaveBeenLastCalledWith(
    { page: 0, size: 20, status: 'ALL', q: 'свет' }, expect.any(AbortSignal)));
  await screen.findByText('По вашему запросу ничего не найдено.');
  expect(screen.getByText('25')).not.toBeNull();
});

it('creates a request and resets search, filters and form fields', async () => {
  const user = userEvent.setup();
  render(<App />);
  await screen.findByText('Страница 1 из 2');
  await user.selectOptions(screen.getByRole('combobox', { name: 'Статус обращения' }), 'NEW');
  await waitFor(() => expect(requestsApi.page).toHaveBeenLastCalledWith(
    expect.objectContaining({ status: 'NEW' }), expect.any(AbortSignal)));
  await user.type(screen.getByLabelText('Название обращения'), ' Новый фонарь ');
  await user.type(screen.getByLabelText('Описание'), ' Подробности ');
  await user.click(screen.getByRole('button', { name: '+ Создать обращение' }));
  await screen.findByText('Обращение создано.');
  expect(requestsApi.create).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText('Название обращения')).toHaveProperty('value', '');
  expect(screen.getByRole('combobox', { name: 'Статус обращения' })).toHaveProperty('value', 'ALL');
});

it('offers retry after a failed initial load', async () => {
  vi.mocked(requestsApi.page).mockRejectedValueOnce(new Error('Сервер недоступен'));
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole('alert');
  expect(screen.getByRole('button', { name: '+ Создать обращение' })).toHaveProperty('disabled', true);
  await user.click(screen.getByRole('button', { name: 'Обновить' }));
  await screen.findByText('Страница 1 из 2');
  expect(screen.queryByRole('alert')).toBeNull();
});

it('ignores a late response from an aborted filter request', async () => {
  let finish!: (value: RequestPage) => void;
  vi.mocked(requestsApi.page).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const user = userEvent.setup();
  render(<App />);
  await user.selectOptions(screen.getByRole('combobox', { name: 'Статус обращения' }), 'IN_PROGRESS');
  await screen.findByText('Страница 1 из 2');
  const firstSignal = vi.mocked(requestsApi.page).mock.calls[0][1];
  expect(firstSignal?.aborted).toBe(true);
  await act(async () => { finish({ items: [{ ...item, title: 'Устаревшее обращение' }], page: 0, size: 20, totalElements: 1, totalPages: 1 }); });
  await waitFor(() => expect(screen.queryByText(/Устаревшее обращение/)).toBeNull());
  expect(screen.getByText(/#1 · Освещение/)).not.toBeNull();
});

it('reloads the filtered page and global counters after a status change', async () => {
  let changed = false;
  vi.mocked(requestsApi.page).mockImplementation(async query => ({ ...result(query), items: changed ? [] : [item], totalElements: changed ? 0 : 1, totalPages: changed ? 0 : 1 }));
  vi.mocked(requestsApi.stats).mockImplementation(async () => ({ total: 1, newCount: changed ? 0 : 1, inProgress: changed ? 1 : 0, resolved: 0 }));
  vi.mocked(requestsApi.changeStatus).mockImplementation(async () => { changed = true; return { ...item, status: 'IN_PROGRESS' }; });
  const user = userEvent.setup();
  render(<App />);
  await screen.findByText('Страница 1 из 1');
  await user.selectOptions(screen.getByRole('combobox', { name: 'Статус обращения' }), 'NEW');
  await user.click(await screen.findByRole('button', { name: 'Взять в работу: Освещение' }));
  await screen.findByText('По вашему запросу ничего не найдено.');
  expect(requestsApi.changeStatus).toHaveBeenCalledWith(1, 'IN_PROGRESS');
  expect(requestsApi.stats).toHaveBeenCalledTimes(3);
});
