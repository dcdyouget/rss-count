import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StructuredContentRenderer } from '../StructuredContentRenderer';
import type { StructuredContentNode } from '@/types';

describe('StructuredContentRenderer', () => {
  // T4-1: heading 类型 → 渲染 h2
  it('renders heading node as title', () => {
    const content: StructuredContentNode[] = [
      { type: 'heading', level: 2, text: '章节标题' },
    ];
    render(<StructuredContentRenderer content={content} />);
    const heading = screen.getByText('章节标题');
    expect(heading).toBeInTheDocument();
  });

  // T4-2: paragraph 类型 → 渲染 p
  it('renders paragraph node', () => {
    const content: StructuredContentNode[] = [
      { type: 'paragraph', text: '段落正文内容...' },
    ];
    render(<StructuredContentRenderer content={content} />);
    const paragraph = screen.getByText('段落正文内容...');
    expect(paragraph).toBeInTheDocument();
  });

  // T4-3: image 类型 → 渲染 img
  it('renders image node', () => {
    const content: StructuredContentNode[] = [
      { type: 'image', src: '/static/images/test.jpg', alt: '测试图片' },
    ];
    render(<StructuredContentRenderer content={content} />);
    const img = screen.getByAltText('测试图片');
    expect(img).toBeInTheDocument();
  });

  // T4-4: 混合多种类型 → 正确渲染顺序
  it('renders mixed content types in correct order', () => {
    const content: StructuredContentNode[] = [
      { type: 'heading', level: 2, text: '标题' },
      { type: 'paragraph', text: '第一段' },
      { type: 'paragraph', text: '第二段' },
    ];
    const { container } = render(<StructuredContentRenderer content={content} />);
    expect(screen.getByText('标题')).toBeInTheDocument();
    expect(screen.getByText('第一段')).toBeInTheDocument();
    expect(screen.getByText('第二段')).toBeInTheDocument();

    // Verify order: heading comes before paragraphs
    const children = Array.from(container.firstChild?.childNodes || []);
    const headingIdx = children.findIndex(
      (c) => c.textContent === '标题'
    );
    const para1Idx = children.findIndex(
      (c) => c.textContent === '第一段'
    );
    const para2Idx = children.findIndex(
      (c) => c.textContent === '第二段'
    );
    expect(headingIdx).toBeLessThan(para1Idx);
    expect(para1Idx).toBeLessThan(para2Idx);
  });

  // T4-5: 空数组 → 渲染 "暂无正文内容"
  it('renders empty state for empty content array', () => {
    render(<StructuredContentRenderer content={[]} />);
    const emptyText = screen.getByText('暂无正文内容');
    expect(emptyText).toBeInTheDocument();
  });

  it('renders blockquote node with styling', () => {
    const content: StructuredContentNode[] = [
      { type: 'blockquote', text: '引用内容' },
    ];
    const { container } = render(<StructuredContentRenderer content={content} />);
    expect(screen.getByText('引用内容')).toBeInTheDocument();
    const blockquote = container.querySelector('blockquote');
    expect(blockquote).toBeInTheDocument();
  });

  it('renders unordered list node', () => {
    const content: StructuredContentNode[] = [
      { type: 'list', ordered: false, items: ['项目1', '项目2'] },
    ];
    const { container } = render(<StructuredContentRenderer content={content} />);
    expect(screen.getByText('项目1')).toBeInTheDocument();
    expect(screen.getByText('项目2')).toBeInTheDocument();
    const ul = container.querySelector('ul');
    expect(ul).toBeInTheDocument();
  });

  it('renders ordered list node', () => {
    const content: StructuredContentNode[] = [
      { type: 'list', ordered: true, items: ['步骤1', '步骤2'] },
    ];
    const { container } = render(<StructuredContentRenderer content={content} />);
    expect(screen.getByText('步骤1')).toBeInTheDocument();
    expect(screen.getByText('步骤2')).toBeInTheDocument();
    const ol = container.querySelector('ol');
    expect(ol).toBeInTheDocument();
  });

  it('renders code node', () => {
    const content: StructuredContentNode[] = [
      { type: 'code', language: 'python', code: "print('hello')" },
    ];
    const { container } = render(<StructuredContentRenderer content={content} />);
    expect(screen.getByText("print('hello')")).toBeInTheDocument();
    const codeBlock = container.querySelector('pre');
    expect(codeBlock).toBeInTheDocument();
    const codeEl = container.querySelector('code');
    expect(codeEl).toBeInTheDocument();
  });
});
