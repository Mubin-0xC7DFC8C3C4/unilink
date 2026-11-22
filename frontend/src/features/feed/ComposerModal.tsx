import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client';
import { IconFile, IconImage, IconX } from '../../design/icons';

export interface PreviewPostDraft { title: string; body: string; hasMedia: boolean }

/**
 * The Community Home post composer (LinkedIn-style modal, kept clean):
 * the student's avatar and name, a title input, a borderless body
 * textarea, attachment buttons and the Post button. It posts at the
 * Department scope — the Community Home feed's own scope — through the
 * existing contracts only: POST /posts {title, body,
 * visibility: 'DEPARTMENT', sectorIds: []} (the backend validates title
 * and body, so both are required), followed by
 * POST /posts/{id}/attachments (multipart) for each attached file.
 * In Prototype Preview the modal never calls the API; it hands the
 * draft back to Feed, which prepends it locally.
 */
export default function ComposerModal({ open, authorName, avatar, preview, onClose, onPosted, onPreviewPost }: {
  open: boolean;
  authorName?: string;
  avatar: ReactNode;
  preview: boolean;
  onClose: () => void;
  onPosted: (note: string) => void;
  onPreviewPost: (draft: PreviewPostDraft) => void;
}) {
  const qc = useQueryClient();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState<string | null>(null);
  const photoRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => { if (open) setError(null); }, [open ]);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const createMut = useMutation({
    mutationFn: async () => {
      const created = await apiPost<any>('/posts', { title: title.trim(), body: body.trim(), visibility: 'DEPARTMENT', sectorIds: [] as number[] });
      let uploadFailed = false;
      const id = created?.id;
      if (id && files.length > 0) {
        for (const f of files) {
          const fd = new FormData();
          fd.append('file', f);
          try { await apiPost(`/posts/${id}/attachments`, fd); } catch { uploadFailed = true; }
        }
      }
      return { uploadFailed, id, title: title.trim(), body: body.trim() };
    },
    onSuccess: ({ uploadFailed }) => {
      qc.invalidateQueries({ queryKey: ['feed'] });
      onPosted(uploadFailed ? 'Your post was published, but an attachment could not be uploaded.' : 'Posted to your department.');
      reset();
      onClose();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const reset = () => { setTitle(''); setBody(''); setFiles([]); setError(null); };
  const addFiles = (list: FileList | null) => { if (list) setFiles((fs) => [...fs, ...Array.from(list)].slice(0, 5)); };
  const canPost = title.trim().length > 0 && body.trim().length > 0;

  if (!open) return null;
  return (
    <div className="cm-backdrop" onClick={onClose}>
      <div className="cm-modal" role="dialog" aria-modal="true" aria-label="Create a post" onClick={(e) => e.stopPropagation()}>
        <button type="button" className="cm-close" aria-label="Close composer" onClick={onClose}><IconX /></button>
        <div className="cm-head">
          {avatar}
          <p className="cm-name">{authorName || 'You'}</p>
        </div>
        <input aria-label="Post title" placeholder="Add a title" value={title} onChange={(e) => setTitle(e.target.value)} className="input-search cm-title" />
        <textarea aria-label="Post body" placeholder="Share your thoughts, notes, or project updates..." value={body} onChange={(e) => setBody(e.target.value)} rows={6} className="cm-body" autoFocus />
        {files.length > 0 && <div className="cm-files">
          {files.map((f, i) => f.type.startsWith('image/')
            ? <span key={i} className="cm-file"><img src={URL.createObjectURL(f)} alt="" className="cm-thumb" />{f.name}<button type="button" aria-label={`Remove ${f.name}`} onClick={() => setFiles((fs) => fs.filter((_, j) => j !== i))}>×</button></span>
            : <span key={i} className="cm-file">{f.name}<button type="button" aria-label={`Remove ${f.name}`} onClick={() => setFiles((fs) => fs.filter((_, j) => j !== i))}>×</button></span>)}
        </div>}
        {error && <p role="alert" className="field-error">{error}</p>}
        <div className="cm-foot">
          <div className="cm-attach">
            <button type="button" aria-label="Add photo" onClick={() => photoRef.current?.click()}><IconImage /></button>
            <button type="button" aria-label="Add code or file" onClick={() => fileRef.current?.click()}><IconFile /></button>
            <input ref={photoRef} type="file" accept="image/*" multiple hidden onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
            <input ref={fileRef} type="file" multiple hidden onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
          </div>
          {preview
            ? <button type="button" className="btn btn-primary" disabled={!canPost} onClick={() => { onPreviewPost({ title: title.trim(), body: body.trim(), hasMedia: files.some((f) => f.type.startsWith('image/')) }); reset(); onClose(); }}>Post</button>
            : <button type="button" className="btn btn-primary" disabled={!canPost || createMut.isPending} onClick={() => createMut.mutate()}>{createMut.isPending ? 'Posting…' : 'Post'}</button>}
        </div>
      </div>
    </div>
  );
}
