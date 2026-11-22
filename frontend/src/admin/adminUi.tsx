import { useState, type ReactNode } from 'react';
import { Button, Modal } from '../design/primitives';

/** Human-readable admin timestamp: "Dec 5, 2026 · 9:00 AM".
 *  Raw ISO strings never reach the page. */
export function fmtDateTime(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const date = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  const time = d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  return `${date} · ${time}`;
}

export function fmtDate(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

const STATUS_TONES: Record<string, string> = {
  VERIFIED: 'is-good', ACTIVE: 'is-good', APPROVED: 'is-good', PUBLISHED: 'is-good',
  PENDING_REVIEW: 'is-warn', PENDING: 'is-warn', REGISTERED: 'is-warn', HIDDEN_ADMIN: 'is-warn', EDIT_REQUESTED: 'is-warn', DRAFT: 'is-warn',
  SUSPENDED: 'is-bad', BANNED: 'is-bad', REJECTED: 'is-bad', REMOVED: 'is-bad', CANCELLED: 'is-bad',
};
const STATUS_LABELS: Record<string, string> = {
  VERIFIED: 'Verified', REGISTERED: 'Pending verification', SUSPENDED: 'Suspended', BANNED: 'Banned',
  ACTIVE: 'Active', HIDDEN_ADMIN: 'Hidden', REMOVED: 'Removed', EDIT_REQUESTED: 'Edit requested',
  PENDING_REVIEW: 'Pending review', APPROVED: 'Approved', REJECTED: 'Rejected', PENDING: 'Pending', PUBLISHED: 'Published', DRAFT: 'Draft', CANCELLED: 'Cancelled', COMPLETED: 'Completed',
};

export function StatusBadge({ status }: { status?: string | null }) {
  if (!status) return <span className="adm-badge">—</span>;
  return <span className={`adm-badge ${STATUS_TONES[status] ?? ''}`}>{STATUS_LABELS[status] ?? status}</span>;
}

export function humanStatus(status?: string | null): string {
  if (!status) return '—';
  return STATUS_LABELS[status] ?? status;
}

/** Confirmation dialog for sensitive admin actions. When `reasonLabel`
 *  is set, a reason field is shown and Confirm stays disabled until it
 *  is filled — matching the backend's mandatory-reason rules. */
export function ConfirmDialog({ title, message, confirmLabel = 'Confirm', reasonLabel, onCancel, onConfirm, busy }: {
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  reasonLabel?: string;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
  busy?: boolean;
}) {
  const [reason, setReason] = useState('');
  return (
    <Modal title={title} onClose={onCancel}>
      <div>{message}</div>
      {reasonLabel && (
        <div style={{ marginTop: 12 }}>
          <label htmlFor="adm-confirm-reason">{reasonLabel}</label>
          <input id="adm-confirm-reason" className="input-search" style={{ width: '100%' }} autoFocus value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
      )}
      <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
        <Button variant="primary" disabled={!!busy || (!!reasonLabel && !reason.trim())} onClick={() => onConfirm(reason.trim())}>{confirmLabel}</Button>
        <Button onClick={onCancel}>Cancel</Button>
      </div>
    </Modal>
  );
}

/** Simple top tab bar used by the table pages (status filtering lives
 *  here, not in duplicate sidebar routes). */
export function Tabs({ tabs, active, onChange }: { tabs: { key: string; label: string; count?: number }[]; active: string; onChange: (k: string) => void }) {
  return (
    <div className="adm-tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.key} type="button" role="tab" aria-selected={active === t.key} className={active === t.key ? 'is-active' : ''} onClick={() => onChange(t.key)}>
          {t.label}{t.count != null ? ` (${t.count})` : ''}
        </button>
      ))}
    </div>
  );
}

export const ADM_SHARED_CSS = `
.adm-badge{display:inline-block;border:1px solid var(--border);border-radius:999px;padding:1px 9px;font-size:.76rem;font-weight:600;white-space:nowrap}
.adm-badge.is-good{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.adm-badge.is-warn{background:var(--surface-muted)}
.adm-badge.is-bad{background:#7a2e22;color:#fdf6f2;border-color:#7a2e22}
.adm-tabs{display:flex;gap:4px;border-bottom:1px solid var(--border);margin:14px 0 16px;flex-wrap:wrap}
.adm-tabs button{background:none;border:none;border-bottom:2px solid transparent;padding:8px 12px;cursor:pointer;color:inherit;font:inherit}
.adm-tabs button.is-active{border-bottom-color:var(--text-primary);font-weight:700}
.adm-cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin-bottom:20px}
.adm-stat{border:1px solid var(--border);border-radius:var(--radius-md);padding:14px 16px;background:var(--surface)}
.adm-stat .adm-stat-value{font-size:1.55rem;font-weight:700;line-height:1.15}
.adm-stat .adm-stat-label{color:var(--text-secondary);font-size:.85rem;margin-top:2px}
.adm-toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:14px}
.adm-toolbar .grow{flex:1;min-width:160px}
.adm-table-wrap{overflow-x:auto;border:1px solid var(--border);border-radius:var(--radius-md)}
.adm-table-wrap .table{margin:0;border:none}
.adm-pager{display:flex;gap:10px;align-items:center;margin-top:12px}
`;
