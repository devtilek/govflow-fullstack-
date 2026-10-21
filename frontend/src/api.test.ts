import { afterEach, describe, expect, it, vi } from 'vitest';
import { pagePath, requestsApi } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('paged API client', () => {
  it('loads details and history through their request-specific paths', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response('{}')).mockResolvedValueOnce(new Response('[]'));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    await requestsApi.get(42, controller.signal);
    await requestsApi.history(42, controller.signal);
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/requests/42', { signal: controller.signal });
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/requests/42/history', { signal: controller.signal });
  });
  it('encodes search text and omits the ALL status', () => {
    const path = pagePath({ page: 2, size: 20, q: '  Свет & 100%_  ', status: 'ALL' });
    const params = new URLSearchParams(path.split('?')[1]);
    expect(params.get('q')).toBe('Свет & 100%_');
    expect(params.get('page')).toBe('2');
    expect(params.has('status')).toBe(false);
  });

  it('passes status, paging and cancellation to fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })));
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    await requestsApi.page({ page: 0, size: 10, q: '', status: 'NEW' }, controller.signal);
    expect(fetchMock).toHaveBeenCalledWith('/api/requests/page?page=0&size=10&status=NEW', { signal: controller.signal });
  });

  it('keeps abort errors distinguishable from network failures', async () => {
    const abort = new DOMException('Cancelled', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abort));
    await expect(requestsApi.stats()).rejects.toBe(abort);
  });

  it('explains network failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(requestsApi.stats()).rejects.toThrow('Сервер недоступен');
  });

  it('explains status conflicts', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 409 })));
    await expect(requestsApi.changeStatus(1, 'IN_PROGRESS')).rejects.toThrow('Статус уже изменён');
  });
});
