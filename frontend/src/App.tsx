import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { requestsApi } from './api';
import type { ServiceRequest, Status } from './api';

const labels: Record<Status, string> = { NEW: 'Новое', IN_PROGRESS: 'В работе', RESOLVED: 'Решено' };
const message = (error: unknown) => error instanceof Error ? error.message : 'Не удалось выполнить действие.';

export default function App() {
  const [items, setItems] = useState<ServiceRequest[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Status | 'ALL'>('ALL');
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
    setError('');
    try {
      const result = await requestsApi.list(controller.signal);
      if (!controller.signal.aborted) { setItems(result); setLoaded(true); }
    } catch (error) {
      if (!controller.signal.aborted) setError(message(error));
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    return () => pendingLoad.current?.abort();
  }, [refresh]);

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
      const created = await requestsApi.create(title, description);
      setItems(previous => [created, ...previous]);
      setTitle('');
      setDescription('');
      setSearch('');
      setFilter('ALL');
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
      const updated = await requestsApi.changeStatus(id, status);
      setItems(previous => previous.map(item => item.id === id ? updated : item));
      setNotice('Статус обращения обновлён.');
    } catch (error) {
      setError(message(error));
    } finally {
      mutationLock.current = false;
      setSaving(false);
    }
  }

  const visible = items.filter(item =>
    (filter === 'ALL' || item.status === filter) &&
    (item.title + ' ' + item.description).toLowerCase().includes(search.trim().toLowerCase()));
  const count = (status: Status) => items.filter(item => item.status === status).length;
  const total = (value: number) => loaded ? value : '—';

  return <div className="layout">
    <aside><h2>◆ GovFlow</h2><p>РАБОЧАЯ ОБЛАСТЬ</p>
      <div className="selected">▦ Обращения</div><small>Управление обращениями</small>
    </aside>
    <main>
      <header><span>Рабочая область / Обращения</span><b aria-label="Актилек Корганбек">AK</b></header>
      <div className="content">
        <h1>Обзор обращений</h1><p>Управляйте заявками и отслеживайте их выполнение.</p>
        <div className="cards">
          <article><span>Всего обращений</span><strong>{total(items.length)}</strong></article>
          <article><span>В работе</span><strong>{total(count('IN_PROGRESS'))}</strong></article>
          <article><span>Решено</span><strong>{total(count('RESOLVED'))}</strong></article>
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
            <button disabled={loading || saving} onClick={() => void refresh()}>Обновить</button>
          </div>
          <div className="filters">
            <input aria-label="Поиск обращений" placeholder="Поиск по названию и описанию…" value={search}
              onChange={event => setSearch(event.target.value)} />
            <select aria-label="Статус обращения" value={filter} onChange={event => setFilter(event.target.value as Status | 'ALL')}>
              <option value="ALL">Все статусы</option>
              {Object.entries(labels).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
            </select>
          </div>
          {loading ? <p role="status">Загрузка обращений…</p> : !loaded ?
            <p>Не удалось загрузить обращения. Нажмите «Обновить», чтобы повторить.</p> :
            visible.length === 0 ? <p>{items.length === 0 ? 'Пока нет обращений. Создайте первое выше.' : 'По вашему запросу ничего не найдено.'}</p> :
            <div className="scroll"><table>
              <caption>Обращения: {visible.length} из {items.length}</caption>
              <thead><tr><th scope="col">Обращение</th><th scope="col">Дата</th><th scope="col">Статус</th><th scope="col">Действие</th></tr></thead>
              <tbody>{visible.map(item => <tr key={item.id}>
                <td><b>#{item.id} · {item.title}</b><p className="description">{item.description}</p></td>
                <td><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString('ru-RU')}</time></td>
                <td><span className={'badge ' + item.status}>{labels[item.status]}</span></td>
                <td>{item.status === 'RESOLVED' ? 'Завершено' :
                  <button disabled={saving} aria-label={(item.status === 'NEW' ? 'Взять в работу: ' : 'Завершить: ') + item.title}
                    onClick={() => void update(item.id, item.status === 'NEW' ? 'IN_PROGRESS' : 'RESOLVED')}>
                    {item.status === 'NEW' ? 'В работу' : 'Завершить'}
                  </button>}</td>
              </tr>)}</tbody>
            </table></div>}
        </section>
      </div>
    </main>
  </div>;
}
