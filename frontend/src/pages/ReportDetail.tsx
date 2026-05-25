import { useState, useMemo, useRef, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import {
  Typography, Row, Col, Button, Skeleton,
  Empty, theme, Flex, Input,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { motion, AnimatePresence } from 'framer-motion';
import { useReport, useReportNewsDetail } from '@/api/reports';
import { useMarkRead, useBatchMaterialPile } from '@/api/news';
import NewsCard from '@/components/news/NewsCard';
import NewsContent from '@/components/news/NewsContent';
import { formatTimeRange } from '@/utils/format';

const { Title, Text } = Typography;

const PAGE_SIZE = 10;

export default function ReportDetail() {
  const { token } = theme.useToken();
  const { id } = useParams<{ id: string }>();
  const reportId = id ? Number(id) : null;

  const [viewMode, setViewMode] = useState<'grid' | 'detail'>('grid');
  const [selectedNewsId, setSelectedNewsId] = useState<number | null>(null);
  const [searchText, setSearchText] = useState('');
  const [displayCount, setDisplayCount] = useState(PAGE_SIZE);

  const { data: report, isLoading } = useReport(reportId);
  const { data: newsDetail } = useReportNewsDetail(reportId, selectedNewsId);

  const markRead = useMarkRead();
  const batchPile = useBatchMaterialPile();

  // --- Client-side search filter ---
  const filteredNews = useMemo(() => {
    if (!report?.news) return [];
    if (!searchText.trim()) return report.news;
    const keyword = searchText.trim().toLowerCase();
    return report.news.filter((n) =>
      n.title.toLowerCase().includes(keyword),
    );
  }, [report?.news, searchText]);

  // --- Pagination slice ---
  const displayedNews = useMemo(
    () => filteredNews.slice(0, displayCount),
    [filteredNews, displayCount],
  );

  const hasMore = displayCount < filteredNews.length;

  const savedNewsId = useRef<number>(0);
  const sentinelRef = useRef<HTMLDivElement>(null);

  const handleSearchChange = (value: string) => {
    setSearchText(value);
    setDisplayCount(PAGE_SIZE); // reset pagination on search
  };

  const handleNewsClick = (newsId: number) => {
    savedNewsId.current = newsId;
    setSelectedNewsId(newsId);
    setViewMode('detail');
  };

  const handleBack = () => {
    const targetId = savedNewsId.current;
    setViewMode('grid');
    setSelectedNewsId(null);
    // 延迟执行，等 grid 渲染完成后再滚动到目标位置
    setTimeout(() => {
      const el = document.getElementById(`news-card-${targetId}`);
      if (el) {
        el.scrollIntoView({ behavior: 'instant', block: 'center' });
      }
    }, 150);
  };

  // Infinite scroll via IntersectionObserver
  useEffect(() => {
    if (viewMode !== 'grid' || !hasMore) return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setDisplayCount((prev) => prev + PAGE_SIZE);
        }
      },
      { threshold: 0, rootMargin: '100px' },
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [viewMode, hasMore]);

  // --- Loading state ---
  if (isLoading) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  }

  // --- Report not found ---
  if (!report) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Empty description="报告不存在" />
      </div>
    );
  }

  return (
    <div>
      {/* Report header */}
      <Flex vertical gap={token.marginSM} style={{ marginBottom: token.marginLG }}>
        <Title level={2} style={{ margin: 0 }}>
          {report.name}
        </Title>
        {report.timeRangeStart && report.timeRangeEnd && (
          <Text style={{ color: token.colorTextSecondary }}>
            {formatTimeRange(report.timeRangeStart, report.timeRangeEnd)}
          </Text>
        )}
      </Flex>

      {/* Search bar — only visible in grid mode */}
      {viewMode === 'grid' && (
        <Input.Search
          placeholder="搜索新闻..."
          allowClear
          value={searchText}
          onChange={(e) => handleSearchChange(e.target.value)}
          onSearch={handleSearchChange}
          style={{ marginBottom: token.marginLG, maxWidth: 400 }}
        />
      )}

      {/* Main content area */}
      <AnimatePresence mode="wait">
        {viewMode === 'grid' ? (
          <motion.div
            key="grid"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.2 }}
          >
            {report.news.length === 0 ? (
              <Empty description="暂无新闻" />
            ) : displayedNews.length === 0 ? (
              <Empty description="未找到匹配的新闻" />
            ) : (
              <>
                <Row gutter={[16, 16]}>
                  {displayedNews.map((item) => (
                    <Col key={item.id} xs={24} sm={24} lg={12} xl={8} id={`news-card-${item.id}`}>
                      <NewsCard
                        news={item}
                        onClick={() => handleNewsClick(item.id)}
                      />
                    </Col>
                  ))}
                </Row>

                {/* Infinite scroll sentinel */}
                {hasMore && <div ref={sentinelRef} style={{ height: 1 }} />}
              </>
            )}
          </motion.div>
        ) : selectedNewsId && newsDetail ? (
          <motion.div
            key="detail"
            initial={{ opacity: 0, x: 30 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 30 }}
            transition={{ duration: 0.25 }}
          >
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={handleBack}
              style={{
                marginBottom: token.marginMD,
                color: token.colorTextSecondary,
              }}
            >
              返回报告
            </Button>
            <NewsContent
              news={newsDetail}
              showSidebar
              onMarkRead={(nid) => markRead.mutate(nid)}
              onAddToMaterialPile={(nid) =>
                batchPile.mutate({
                  newsIds: [nid],
                  action: 'ADD',
                })
              }
            />
          </motion.div>
        ) : selectedNewsId ? (
          <div style={{ padding: token.paddingLG }}>
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
