export type Status = 'NEW' | 'IN_PROGRESS' | 'RESOLVED';
export type ServiceRequest = {
  id: number;
  title: string;
  description: string;
  status: Status;
  createdAt: string;
};

export type RequestQuery = { page: number; size: number; q: string; status: Status | 'ALL' };
export type RequestPage = { items: ServiceRequest[]; page: number; size: number; totalElements: number; totalPages: number };
export type RequestStats = { total: number; newCount: number; inProgress: number; resolved: number };

export function pagePath(query: RequestQuery): string {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
  if (query.q.trim()) params.set('q', query.q.trim());
  if (query.status !== 'ALL') params.set('status', query.status);
  return '/page?' + params.toString();
}

async function request<T>(path = '', options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch('/api/requests' + path, options);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new Error('Сервер недоступен. Запустите backend и повторите попытку.');
  }
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Проверьте поля: название до 120 символов, описание до 2000.',
      404: 'Обращение не найдено. Обновите список.',
      409: 'Статус уже изменён или переход недопустим. Обновите список.',
    };
    throw new Error(messages[response.status] ?? 'Ошибка сервера. Попробуйте позже.');
  }
  return response.json() as Promise<T>;
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const requestsApi = {
  page: (query: RequestQuery, signal?: AbortSignal) => request<RequestPage>(pagePath(query), { signal }),
  stats: (signal?: AbortSignal) => request<RequestStats>('/stats', { signal }),
  list: (signal?: AbortSignal) => request<ServiceRequest[]>('', { signal }),
  create: (title: string, description: string) =>
    request<ServiceRequest>('', json('POST', { title: title.trim(), description: description.trim() })),
  changeStatus: (id: number, status: Status) =>
    request<ServiceRequest>('/' + id + '/status', json('PATCH', { status })),
};
