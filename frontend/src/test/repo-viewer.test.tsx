import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { renderMarkdown } from '../features/workspace/RepoViewer';

/** D-203: the README renderer is a small safe Markdown implementation
 *  (no dependency, no dangerouslySetInnerHTML). */
describe('renderMarkdown', () => {
  const renderMd = (md: string) => render(<div>{renderMarkdown(md)}</div>);

  it('renders headings, bold, italic, inline code and links', () => {
    const { container } = renderMd('# Title\n\nSome **bold** and *italic* text with `code` and a [link](https://example.com/docs).');
    expect(screen.getByRole('heading', { level: 1, name: 'Title' })).toBeInTheDocument();
    expect(screen.getByText('bold')).toBeInTheDocument();
    expect(container.querySelector('strong')).not.toBeNull();
    expect(container.querySelector('em')).not.toBeNull();
    expect(container.querySelector('code')).not.toBeNull();
    const a = screen.getByRole('link', { name: 'link' });
    expect(a.getAttribute('href')).toBe('https://example.com/docs');
    expect(a.getAttribute('target')).toBe('_blank');
    expect(a.getAttribute('rel')).toContain('noreferrer');
  });

  it('renders lists, code fences, blockquotes and rules', () => {
    const { container } = renderMd('## Setup\n\n- first\n- second\n\n1. one\n2. two\n\n```bash\nnpm install\n```\n\n> a note\n\n---\n\ndone');
    expect(screen.getByRole('heading', { level: 2, name: 'Setup' })).toBeInTheDocument();
    expect(screen.getByText('first')).toBeInTheDocument();
    expect(screen.getByText('one')).toBeInTheDocument();
    expect(container.querySelector('pre code')?.textContent).toBe('npm install');
    expect(screen.getByText('a note')).toBeInTheDocument();
    expect(container.querySelector('hr')).not.toBeNull();
    expect(screen.getByText('done')).toBeInTheDocument();
  });

  it('does not interpret HTML — markup in the README stays literal text', () => {
    const { container } = renderMd('# Hi\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(2)>');
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText(/alert\(1\)/)).toBeInTheDocument();
  });

  it('renders only http(s) links as anchors', () => {
    const { container } = renderMd('[bad](javascript:alert(1)) and [ok](https://example.com)');
    expect(screen.getByRole('link', { name: 'ok' })).toBeInTheDocument();
    expect(container.querySelectorAll('a')).toHaveLength(1);
    expect(screen.getByText(/\[bad\]\(javascript:alert\(1\)\)/)).toBeInTheDocument();
  });
});
