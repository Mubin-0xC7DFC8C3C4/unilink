import { useState } from 'react';

export interface SearchableOption { id: number; name: string }

/** Searchable single-select over an existing catalogue (Phase 2.2, owner
 *  instruction 2026-11-20). The options always come from the caller — in
 *  Sign Up they are the live /public catalogue responses, never a hardcoded
 *  list — and picking from the filtered list is the only way to set a value,
 *  so a selection is always a real catalogue id. */
export function SearchableSelect({ id, options, value, onChange, placeholder = 'Search and select…', disabled = false }: {
  id: string;
  options: SearchableOption[];
  value: number | undefined;
  onChange: (id: number | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const selected = options.find((o) => o.id === value);
  const shown = options.filter((o) => o.name.toLowerCase().includes(query.trim().toLowerCase()));
  const pick = (o: SearchableOption) => { onChange(o.id); setOpen(false); setQuery(''); };
  // Layering (owner polish, 2026-11-20): while open, the wrapper and its
  // list sit above the fields that follow; picking an option closes the
  // list immediately, so every field is fully visible after selection.
  return <div style={{ position: 'relative', zIndex: open ? 30 : undefined }}>
    <input id={id} role="combobox" aria-expanded={open} aria-controls={`${id}-listbox`} autoComplete="off"
      disabled={disabled} placeholder={placeholder}
      value={open ? query : (selected?.name ?? '')}
      onFocus={() => { setQuery(selected?.name ?? ''); setOpen(true); }}
      onBlur={() => { setOpen(false); setQuery(''); }}
      onChange={(e) => { setQuery(e.target.value); if (value !== undefined && e.target.value !== selected?.name) onChange(undefined); }}
      onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false); }} />
    {open && !disabled && <ul id={`${id}-listbox`} role="listbox" style={{ position: 'absolute', zIndex: 20, left: 0, right: 0, top: '100%', marginTop: 4, marginBottom: 0, maxHeight: 220, overflowY: 'auto', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: 4, listStyle: 'none' }}>
      {shown.length === 0 && <li className="body-sm secondary" style={{ padding: '6px 8px' }}>No matches — keep typing or choose from the full list.</li>}
      {shown.map((o) => <li key={o.id} role="option" aria-selected={o.id === value}
        onMouseDown={(e) => { e.preventDefault(); pick(o); }}
        style={{ padding: '6px 8px', cursor: 'pointer', borderRadius: 4, background: o.id === value ? 'var(--surface-muted)' : undefined }}>{o.name}</li>)}
    </ul>}
  </div>;
}
