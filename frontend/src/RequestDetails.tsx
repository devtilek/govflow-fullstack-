import { useEffect, useRef, useState } from 'react';
import { requestsApi } from './api';
import type { HistoryEvent, ServiceRequest, Status } from './api';

const labels: Record<Status, string> = { NEW: 'Новое', IN_PROGRESS: 'В работе', RESOLVED: 'Решено' };
const formatDate = (value: string) => new Date(value).toLocaleString('ru-RU');

function eventLabel(event: HistoryEvent) {
  if (event.action === 'CREATED') return 'Обращение создано';
  if (event.action === 'IMPORTED') return 'Статус при подключении истории: ' + labels[event.toStatus];
  return (event.fromStatus ? labels[event.fromStatus] + ' → ' : '') + labels[event.toStatus];
}

export default function RequestDetails({ id, revision, onClose }: { id: number; revision: number; onClose: () => void }) {
  const [details, setDetails] = useState<ServiceRequest | null>(null);
  const [history, setHistory] = useState<HistoryEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => { heading.current?.focus(); }, [id]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setDetails(null);
    setHistory([]);
    setError('');
    void Promise.all([requestsApi.get(id, controller.signal), requestsApi.history(id, controller.signal)])
      .then(([request, events]) => {
        if (!controller.signal.aborted) { setDetails(request); setHistory(events); }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setError(error instanceof Error ? error.message : 'Не удалось загрузить историю.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [id, revision, retry]);

  return <section id="request-details" className="request-details" aria-labelledby="request-details-heading" aria-busy={loading}>
    <div className="heading">
      <h2 ref={heading} tabIndex={-1} id="request-details-heading">Обращение #{id}</h2>
      <button type="button" onClick={onClose} aria-label="Закрыть карточку обращения">Закрыть</button>
    </div>
    {loading ? <p role="status">Загрузка карточки…</p> : error ? <div>
      <p role="alert" className="error">{error}</p>
      <button type="button" onClick={() => setRetry(previous => previous + 1)}>Повторить загрузку карточки</button>
    </div> : details && <>
      <h3>{details.title}</h3>
      <p className="detail-description">{details.description}</p>
      <dl className="detail-meta">
        <div><dt>Текущий статус</dt><dd><span className={'badge ' + details.status}>{labels[details.status]}</span></dd></div>
        <div><dt>Создано</dt><dd><time dateTime={details.createdAt}>{formatDate(details.createdAt)}</time></dd></div>
      </dl>
      <h3>История обращения</h3>
      {history.length === 0 ? <p>История пока недоступна.</p> : <ol className="history-list">
        {history.map(event => <li key={event.id}>
          <strong>{eventLabel(event)}</strong>
          <time dateTime={event.occurredAt}>{formatDate(event.occurredAt)}</time>
          {event.action === 'IMPORTED' && <p>Ранние изменения не записывались. Указано время подключения истории.</p>}
        </li>)}
      </ol>}
    </>}
  </section>;
}
