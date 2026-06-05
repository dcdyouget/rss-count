import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  AppstoreOutlined,
  CheckOutlined,
  FileTextOutlined,
  InboxOutlined,
  LeftOutlined,
  LoadingOutlined,
  RightOutlined,
  SearchOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Button, Empty, Input, Skeleton, message } from 'antd';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import NewsContent from '@/components/news/NewsContent';
import { useNewsDetail, useNewsList, useMarkRead, useBatchMaterialPile } from '@/api/news';
import { useRssGroups, useRssSourceList } from '@/api/rssSources';
import type { NewsItem, NewsListParams, RssSource } from '@/types';
import './NewsManagement.css';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

type WorkspaceFilter = 'all' | 'unread' | 'material';

const PAGE_SIZE = 30;

function extractImageUrl(html: string | null) {
  if (!html) return null;
  const match = html.match(/<img[^>]+src=["']([^"']+)["']/i);
  return match?.[1] ?? null;
}

function sourceInitial(name: string) {
  const trimmed = name.trim();
  if (!trimmed) return 'R';
  const latin = trimmed.match(/[A-Za-z0-9]+/);
  return latin ? latin[0].slice(0, 2).toUpperCase() : trimmed.slice(0, 1);
}

export default function NewsManagement() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialKeyword = searchParams.get('q') ?? '';

  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [searchValue, setSearchValue] = useState(initialKeyword);
  const [filter, setFilter] = useState<WorkspaceFilter>('all');
  const [selectedSource, setSelectedSource] = useState<string | null>(null);
  const [selectedNewsId, setSelectedNewsId] = useState<number | null>(null);

  const params = useMemo<NewsListParams>(() => ({
    page,
    size: PAGE_SIZE,
    keyword: keyword || undefined,
    isRead: filter === 'unread' ? false : undefined,
  }), [filter, keyword, page]);

  const { data: newsData, isLoading, isFetching } = useNewsList(params);
  const { data: detailData, isLoading: detailLoading } = useNewsDetail(selectedNewsId);
  const { data: groups = [] } = useRssGroups();
  const { data: sources = [] } = useRssSourceList();
  const markRead = useMarkRead();
  const batchMaterialPile = useBatchMaterialPile();

  const loadedNews = newsData?.items ?? [];
  const visibleNews = useMemo(() => loadedNews.filter((item) => {
    if (filter === 'material' && !item.inMaterialPile) return false;
    if (selectedSource && item.sourceRssName !== selectedSource) return false;
    return true;
  }), [filter, loadedNews, selectedSource]);

  const unreadCount = loadedNews.filter((item) => !item.isRead).length;
  const materialCount = loadedNews.filter((item) => item.inMaterialPile).length;
  const totalPages = Math.max(1, Math.ceil((newsData?.total ?? 0) / PAGE_SIZE));

  const sourceNewsCounts = useMemo(() => {
    const counts = new Map<string, number>();
    loadedNews.forEach((item) => {
      counts.set(item.sourceRssName, (counts.get(item.sourceRssName) ?? 0) + 1);
    });
    return counts;
  }, [loadedNews]);

  const groupedSources = useMemo(() => {
    const byGroup = groups.map((group) => ({
      group,
      sources: sources.filter((source) => source.groupIds.includes(group.id)),
    })).filter((entry) => entry.sources.length > 0);
    const ungrouped = sources.filter((source) => source.groupIds.length === 0);
    return { byGroup, ungrouped };
  }, [groups, sources]);

  useEffect(() => {
    if (visibleNews.length === 0) {
      setSelectedNewsId(null);
      return;
    }
    if (!selectedNewsId || !visibleNews.some((item) => item.id === selectedNewsId)) {
      setSelectedNewsId(visibleNews[0].id);
    }
  }, [selectedNewsId, visibleNews]);

  useEffect(() => {
    const query = searchParams.get('q') ?? '';
    setSearchValue(query);
    setKeyword(query);
    setPage(1);
  }, [searchParams]);

  const applySearch = (value: string) => {
    const nextKeyword = value.trim();
    setKeyword(nextKeyword);
    setPage(1);
    setSearchParams(nextKeyword ? { q: nextKeyword } : {});
  };

  const selectWorkspaceFilter = (nextFilter: WorkspaceFilter) => {
    setFilter(nextFilter);
    setSelectedSource(null);
    setPage(1);
  };

  const handleSelectSource = (source: RssSource) => {
    setSelectedSource((current) => current === source.name ? null : source.name);
    setFilter('all');
    setPage(1);
  };

  const handleMarkRead = async (id: number) => {
    try {
      await markRead.mutateAsync(id);
      message.success('已标记为已读');
    } catch {
      message.error('标记失败，请重试');
    }
  };

  const handleMaterialPile = async (news: NewsItem) => {
    try {
      await batchMaterialPile.mutateAsync({
        newsIds: [news.id],
        action: news.inMaterialPile ? 'REMOVE' : 'ADD',
      });
      message.success(news.inMaterialPile ? '已移出素材堆' : '已加入素材堆');
    } catch {
      message.error('操作失败，请重试');
    }
  };

  const selectedSummary = loadedNews.find((item) => item.id === selectedNewsId);

  return (
    <div className="news-workspace">
      <aside className="news-sources" aria-label="资讯来源">
        <div className="news-sidebar-section-title">工作区</div>
        <button
          className={`news-source-item ${filter === 'all' && !selectedSource ? 'is-active' : ''}`}
          onClick={() => selectWorkspaceFilter('all')}
        >
          <span className="news-source-icon"><AppstoreOutlined /></span>
          <span>全部资讯</span>
          <span className="news-source-count">{newsData?.total ?? 0}</span>
        </button>
        <button
          className={`news-source-item ${filter === 'unread' ? 'is-active' : ''}`}
          onClick={() => selectWorkspaceFilter('unread')}
        >
          <span className="news-source-icon"><CheckOutlined /></span>
          <span>未读</span>
          <span className="news-source-count">{unreadCount}</span>
        </button>
        <button
          className={`news-source-item ${filter === 'material' ? 'is-active' : ''}`}
          onClick={() => selectWorkspaceFilter('material')}
        >
          <span className="news-source-icon"><InboxOutlined /></span>
          <span>素材堆</span>
          <span className="news-source-count">{materialCount}</span>
        </button>

        <div className="news-source-groups">
          {groupedSources.byGroup.map(({ group, sources: groupSources }) => (
            <section key={group.id}>
              <div className="news-source-group-title">
                <span>{group.name}</span>
                <span>{group.sourceCount}</span>
              </div>
              {groupSources.map((source) => (
                <button
                  key={source.id}
                  className={`news-source-item ${selectedSource === source.name ? 'is-active' : ''}`}
                  onClick={() => handleSelectSource(source)}
                >
                  <span className="news-source-icon news-source-icon--text">
                    {sourceInitial(source.name)}
                  </span>
                  <span className="news-source-name">{source.name}</span>
                  <span className="news-source-count">
                    {sourceNewsCounts.get(source.name) ?? 0}
                  </span>
                </button>
              ))}
            </section>
          ))}

          {groupedSources.ungrouped.length > 0 && (
            <section>
              <div className="news-source-group-title">
                <span>未分组</span>
                <span>{groupedSources.ungrouped.length}</span>
              </div>
              {groupedSources.ungrouped.map((source) => (
                <button
                  key={source.id}
                  className={`news-source-item ${selectedSource === source.name ? 'is-active' : ''}`}
                  onClick={() => handleSelectSource(source)}
                >
                  <span className="news-source-icon news-source-icon--text">
                    {sourceInitial(source.name)}
                  </span>
                  <span className="news-source-name">{source.name}</span>
                  <span className="news-source-count">
                    {sourceNewsCounts.get(source.name) ?? 0}
                  </span>
                </button>
              ))}
            </section>
          )}
        </div>

        <div className="news-source-footer">
          <Button block icon={<UnorderedListOutlined />} onClick={() => navigate('/rss-sources')}>
            管理 RSS 源
          </Button>
        </div>
      </aside>

      <section className="news-feed">
        <header className="news-feed-header">
          <div className="news-feed-title-row">
            <div>
              <h2>{selectedSource ?? (filter === 'unread' ? '未读资讯' : filter === 'material' ? '素材堆' : '全部资讯')}</h2>
              <span>{isFetching ? '正在更新…' : `共 ${newsData?.total ?? 0} 条`}</span>
            </div>
          </div>

          <Input.Search
            className="news-feed-search"
            prefix={<SearchOutlined />}
            placeholder="搜索当前资讯"
            allowClear
            value={searchValue}
            onChange={(event) => setSearchValue(event.target.value)}
            onSearch={applySearch}
          />

          <div className="news-feed-filters">
            <button
              className={filter === 'all' ? 'is-active' : ''}
              onClick={() => selectWorkspaceFilter('all')}
            >
              全部
            </button>
            <button
              className={filter === 'unread' ? 'is-active' : ''}
              onClick={() => selectWorkspaceFilter('unread')}
            >
              未读
            </button>
            <button
              className={filter === 'material' ? 'is-active' : ''}
              onClick={() => selectWorkspaceFilter('material')}
            >
              已加入素材
            </button>
          </div>
        </header>

        <div className="news-feed-list">
          {isLoading ? (
            <div className="news-feed-loading">
              <Skeleton active paragraph={{ rows: 10 }} />
            </div>
          ) : visibleNews.length === 0 ? (
            <Empty description="当前筛选下暂无资讯" />
          ) : (
            visibleNews.map((news) => {
              const imageUrl = extractImageUrl(news.headerImageHtml);
              return (
                <article
                  key={news.id}
                  className={`news-feed-item ${selectedNewsId === news.id ? 'is-selected' : ''} ${
                    news.isRead ? 'is-read' : ''
                  }`}
                  onClick={() => setSelectedNewsId(news.id)}
                >
                  <div className="news-feed-item-content">
                    <div className="news-feed-meta">
                      {!news.isRead && <span className="news-unread-dot" />}
                      <strong>{news.sourceRssName}</strong>
                      <span>{dayjs(news.publishedAt).fromNow()}</span>
                      {news.inMaterialPile && <span className="news-material-mark">素材</span>}
                    </div>
                    <h3>{news.title}</h3>
                    <p>{news.summary || '暂无摘要'}</p>
                  </div>
                  <div className="news-feed-thumbnail">
                    {imageUrl ? <img src={imageUrl} alt="" loading="lazy" /> : <FileTextOutlined />}
                  </div>
                </article>
              );
            })
          )}
        </div>

        <footer className="news-feed-pagination">
          <Button
            type="text"
            icon={<LeftOutlined />}
            disabled={page <= 1}
            onClick={() => setPage((value) => Math.max(1, value - 1))}
          />
          <span>{page} / {totalPages}</span>
          <Button
            type="text"
            icon={<RightOutlined />}
            disabled={page >= totalPages}
            onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
          />
        </footer>
      </section>

      <section className="news-reader">
        {selectedNewsId && (detailLoading || !detailData) ? (
          <div className="news-reader-loading">
            <LoadingOutlined />
            <span>正在加载正文</span>
          </div>
        ) : detailData ? (
          <NewsContent
            news={detailData}
            compact
            onMarkRead={handleMarkRead}
            onToggleMaterialPile={() => handleMaterialPile(detailData)}
          />
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={selectedSummary ? '正文暂不可用' : '选择一条资讯开始阅读'}
          />
        )}
      </section>
    </div>
  );
}
