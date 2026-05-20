import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import NewsContent from '../NewsContent';
import type { NewsDetail } from '@/types';

const mockNews: NewsDetail = {
  id: 1,
  title: 'AI 行业迎来新突破',
  author: '张三',
  sourceRssName: '36氪',
  sourceUrl: 'https://36kr.com/p/12345',
  publishedAt: '2026-05-20T10:30:00+08:00',
  tags: ['AI', '科技', '融资'],
  isRead: false,
  inMaterialPile: false,
  summary: '近日，多家科技公司发布了新的AI产品...',
  headerImageUrl: null,
  structuredContent: [
    { type: 'heading', level: 2, text: '行业动态' },
    { type: 'paragraph', text: '近日多家科技公司发布了新的AI产品，引起业界广泛关注。' },
    { type: 'paragraph', text: '这些产品展示了AI技术在各行各业的应用潜力。' },
  ],
};

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <ConfigProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </ConfigProvider>,
  );
}

describe('NewsContent', () => {
  it('renders the news title', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.getByText('AI 行业迎来新突破')).toBeInTheDocument();
  });

  it('renders the author name', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.getByText('张三')).toBeInTheDocument();
  });

  it('renders tags', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.getByText('AI')).toBeInTheDocument();
    expect(screen.getByText('科技')).toBeInTheDocument();
    expect(screen.getByText('融资')).toBeInTheDocument();
  });

  it('renders source name and publish time', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.getByText(/来源：36氪/)).toBeInTheDocument();
  });

  it('renders back button when backUrl is provided', () => {
    renderWithProviders(<NewsContent news={mockNews} backUrl="/reports/1" />);
    expect(screen.getByText('返回')).toBeInTheDocument();
  });

  it('does not render back button when backUrl is not provided', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.queryByText('返回')).not.toBeInTheDocument();
  });

  it('renders structured content', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.getByText('行业动态')).toBeInTheDocument();
    expect(screen.getByText(/多家科技公司发布了新的AI产品/)).toBeInTheDocument();
  });

  it('renders sidebar when showSidebar is true (default)', () => {
    renderWithProviders(<NewsContent news={mockNews} />);
    expect(screen.getByText('作者')).toBeInTheDocument();
    expect(screen.getByText('标签')).toBeInTheDocument();
    expect(screen.getByText('查看原文')).toBeInTheDocument();
  });

  it('hides sidebar when showSidebar is false', () => {
    renderWithProviders(<NewsContent news={mockNews} showSidebar={false} />);
    expect(screen.queryByText('作者')).not.toBeInTheDocument();
  });

  it('renders mark read button when onMarkRead is provided', () => {
    const onMarkRead = vi.fn();
    renderWithProviders(<NewsContent news={mockNews} onMarkRead={onMarkRead} />);
    expect(screen.getByText('标记已读')).toBeInTheDocument();
  });

  it('shows "已读" when news is already read', () => {
    const readNews = { ...mockNews, isRead: true };
    renderWithProviders(<NewsContent news={readNews} onMarkRead={vi.fn()} />);
    expect(screen.getByText('已读')).toBeInTheDocument();
    expect(screen.getByText('已读').closest('button')).toBeDisabled();
  });

  it('renders material pile button when onAddToMaterialPile is provided', () => {
    const onAdd = vi.fn();
    renderWithProviders(<NewsContent news={mockNews} onAddToMaterialPile={onAdd} />);
    expect(screen.getByText('加入素材堆')).toBeInTheDocument();
  });

  it('shows "已在素材堆" when news is already in material pile', () => {
    const inPileNews = { ...mockNews, inMaterialPile: true };
    renderWithProviders(<NewsContent news={inPileNews} onAddToMaterialPile={vi.fn()} />);
    expect(screen.getByText('已在素材堆')).toBeInTheDocument();
  });
});
