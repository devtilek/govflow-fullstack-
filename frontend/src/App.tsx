import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { requestsApi } from './api';
import type { RequestQuery, RequestStats, ServiceRequest, Status } from './api';

const labels: Record<Status, string> = { NEW: 'Новое', IN_PROGRESS: 'В работе', RESOLVED: 'Решено' };
const message = (error: unknown) => error instanceof Error ? error.message : 'Не удалось выполнить действие.';

export default function App() {
  const [items, setItems] = useState<ServiceRequest[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState<RequestQuery>({ page: 0, size: 20, q: '', status: 'ALL' });
  const [stats, setStats] = useState<RequestStats | null>(null);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [revision, setRevision] = useState(0);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const pendingLoad = useRef<AbortController | null>(null);
  const mutationLock = useRef(false);

  const refresh = useCallback(async () => {
    pendingLoad.current?.abort();
    const controller = new AbortController();
    pendingLoad.current = controller;
    setLoading(true);
    setLoaded(false);
    setError('');
    try {
      const [result, counters] = await Promise.all([
        requestsApi.page(query, controller.signal), requestsApi.stats(controller.signal),
      ]);
      if (!controller.signal.aborted) {
        const lastPage = Math.max(0, result.totalPages - 1);
        if (query.page > lastPage) {
          setQuery(previous => ({ ...previous, page: lastPage }));
          return;
        }
        setItems(result.items);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setStats(counters);
        setLoaded(true);
      }
    } catch (error) {
      if (!controller.signal.aborted) setError(message(error));
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [query, revision]);

  useEffect(() => {
    void refresh();
    return () => pendingLoad.current?.abort();
  }, [refresh]);

  useEffect(() => {
    const timer = setTimeout(() => {
      const q = search.trim();
      setQuery(previous => previous.q === q ? previous : { ...previous, q, page: 0 });
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  function beginMutation() {
    if (mutationLock.current) return false;
    mutationLock.current = true;
    pendingLoad.current?.abort();
    setLoading(false);
    setSaving(true);
    setError('');
    setNotice('');
    return true;
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim() || !description.trim()) {
      setError('Название и описание не могут состоять только из пробелов.');
      return;
    }
    if (!beginMutation()) return;
    try {
      await requestsApi.create(title, description);
      setTitle('');
      setDescription('');
      setSearch('');
      setQuery(previous => ({ ...previous, page: 0, q: '', status: 'ALL' }));
      setRevision(previous => previous + 1);
      setNotice('Обращение создано.');
    } catch (error) {
      setError(message(error));
    } finally {
      mutationLock.current = false;
      setSaving(false);
    }
  }

  async function update(id: number, status: Status) {
    if (!beginMutation()) return;
    try {
      await requestsApi.changeStatus(id, status);
      setRevision(previous => previous + 1);
      setNotice('Статус обращения обновлён.');
    } catch (error) {
      setError(message(error));
    } finally {
      mutationLock.current = false;
      setSaving(false);
    }
  }

  const total = (value: number | undefined) => stats && value !== undefined ? value : '—';

  return <div className="layout">
    <aside><h2>◆ GovFlow</h2><p>РАБОЧАЯ ОБЛАСТЬ</p>
      <div className="selected">▦ Обращения</div><small>Управление обращениями</small>
    </aside>
    <main>
      <header><span>Рабочая область / Обращения</span><b aria-label="Актилек Корганбек">AK</b></header>
      <div className="content">
        <h1>Обзор обращений</h1><p>Управляйте заявками и отслеживайте их выполнение.</p>
        <div className="cards">
          <article><span>Всего обращений</span><strong>{total(stats?.total)}</strong></article>
          <article><span>В работе</span><strong>{total(stats?.inProgress)}</strong></article>
          <article><span>Решено</span><strong>{total(stats?.resolved)}</strong></article>
        </div>
        {error && <p role="alert" className="error">{error}</p>}
        <p role="status" className="notice">{notice}</p>
        <section>
          <h2>Новое обращение</h2>
          <form onSubmit={create} aria-busy={saving}>
            <label htmlFor="title">Название обращения</label>
            <input id="title" required maxLength={120} disabled={saving} placeholder="Например: не работает освещение"
              value={title} onChange={event => setTitle(event.target.value)} />
            <label htmlFor="description">Описание</label>
            <textarea id="description" required maxLength={2000} disabled={saving} placeholder="Опишите ситуацию и адрес"
              value={description} onChange={event => setDescription(event.target.value)} />
            <small>{description.length} / 2000</small>
            <button disabled={saving || loading || !loaded || !title.trim() || !description.trim()}>
              {saving ? 'Сохранение…' : '+ Создать обращение'}
            </button>
          </form>
        </section>
        <section aria-busy={loading}>
          <div className="heading"><h2>Список обращений</h2>
            <button disabled={loading || saving} onClick={() => setRevision(previous => previous + 1)}>Обновить</button>
          </div>
          <div className="filters">
            <input disabled={saving} maxLength={200} aria-label="Поиск обращений" placeholder="Поиск по названию и описанию…" value={search}
              onChange={event => setSearch(event.target.value)} />
            <select disabled={saving} aria-label="Статус обращения" value={query.status} onChange={event => setQuery(previous => ({ ...previous, page: 0, status: event.target.value as Status | 'ALL' }))}>
              <option value="ALL">Все статусы</option>
              {Object.entries(labels).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
            </select>
          </div>
          {loading ? <p role="status">Загрузка обращений…</p> : !loaded ?
            <p>Не удалось загрузить обращения. Нажмите «Обновить», чтобы повторить.</p> :
            items.length === 0 ? <p>{stats?.total === 0 ? 'Пока нет обращений. Создайте первое выше.' : 'По вашему запросу ничего не найдено.'}</p> :
            <div className="scroll"><table>
              <caption>Найдено: {totalElements} · На странице: {items.length}</caption>
              <thead><tr><th scope="col">Обращение</th><th scope="col">Дата</th><th scope="col">Статус</th><th scope="col">Действие</th></tr></thead>
              <tbody>{items.map(item => <tr key={item.id}>
                <td><b>#{item.id} · {item.title}</b><p className="description">{item.description}</p></td>
                <td><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString('ru-RU')}</time></td>
                <td><span className={'badge ' + item.status}>{labels[item.status]}</span></td>
                <td>{item.status === 'RESOLVED' ? 'Завершено' :
                  <button disabled={saving || loading} aria-label={(item.status === 'NEW' ? 'Взять в работу: ' : 'Завершить: ') + item.title}
                    onClick={() => void update(item.id, item.status === 'NEW' ? 'IN_PROGRESS' : 'RESOLVED')}>
                    {item.status === 'NEW' ? 'В работу' : 'Завершить'}
                  </button>}</td>
              </tr>)}</tbody>
            </table></div>}
          <nav className="pagination" aria-label="Страницы обращений">
            <label>На странице <select aria-label="Количество на странице" value={query.size} disabled={loading || saving}
              onChange={event => setQuery(previous => ({ ...previous, page: 0, size: Number(event.target.value) }))}>
              {[10, 20, 50].map(size => <option key={size} value={size}>{size}</option>)}
            </select></label>
            <span aria-live="polite">{loaded ? `Страница ${query.page + 1} из ${Math.max(1, totalPages)}` : loading ? 'Загрузка страницы…' : 'Страница недоступна'}</span>
            <div className="page-actions">
              <button disabled={loading || saving || !loaded || query.page === 0}
                onClick={() => setQuery(previous => ({ ...previous, page: previous.page - 1 }))}>Назад</button>
              <button disabled={loading || saving || !loaded || query.page + 1 >= totalPages}
                onClick={() => setQuery(previous => ({ ...previous, page: previous.page + 1 }))}>Далее</button>
            </div>
          </nav>
        </section>
      </div>
    </main>
  </div>;
}
