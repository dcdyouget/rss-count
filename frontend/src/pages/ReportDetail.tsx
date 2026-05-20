import { useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Typography, Row, Col, Anchor, Button, Skeleton,
  Empty, theme, Flex,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { motion, AnimatePresence } from 'framer-motion';
import { useReport, useReportNewsDetail } from '@/api/reports';
import { useMarkRead, useBatchMaterialPile } from '@/api/news';
import NewsCard from '@/components/news/NewsCard';
import NewsContent from '@/components/news/NewsContent';
import { formatTimeRange } from '@/utils/format';

const { Title, Text } = Typography;

export default function ReportDetail() {
  const { token } = theme.useToken();
  const { id } = useParams<{ id: string }>();
  const reportId = id ? Number(id) : null;

  const [viewMode, setViewMode] = useState<'grid' | 'detail'>('grid');
  const [selectedNewsId, setSelectedNewsId] = useState<number | null>(null);

  const { data: report, isLoading } = useReport(reportId);
  const { data: newsDetail } = useReportNewsDetail(reportId, selectedNewsId);

  const markRead = useMarkRead();
  const batchPile = useBatchMaterialPile();

  const handleNewsClick = (newsId: number) => {
    setSelectedNewsId(newsId);
    setViewMode('detail');
  };

  const handleBack = () => {
    setViewMode('grid');
    setSelectedNewsId(null);
  };

  if (isLoading) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    );
  }

  if (!report) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Empty description="报告不存在" />
      </div>
    );
  }

  return (
    <div>
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

      {report.news.length === 0 ? (
        <Empty description="暂无新闻" />
      ) : (
        <div style={{ display: 'flex', gap: token.marginLG }}>
          {/* Left TOC — only in grid mode */}
          {viewMode === 'grid' && (
            <div
              style={{
                width: 200,
                flexShrink: 0,
                position: 'sticky',
                top: 24,
                alignSelf: 'flex-start',
                maxHeight: 'calc(100vh - 120px)',
                overflow: 'auto',
              }}
            >
              <Text
                strong
                style={{
                  fontSize: token.fontSizeSM,
                  color: token.colorTextSecondary,
                  display: 'block',
                  marginBottom: token.marginSM,
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                }}
              >
                目录
              </Text>
              <Anchor
                items={report.news.map((n) => ({
                  key: n.id,
                  href: `#news-${n.id}`,
                  title: n.title,
                }))}
                replace
              />
            </div>
          )}

          {/* Content area */}
          <div style={{ flex: 1, minWidth: 0 }}>
            <AnimatePresence mode="wait">
              {viewMode === 'grid' ? (
                <motion.div
                  key="grid"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ duration: 0.2 }}
                >
                  <Row gutter={[16, 16]}>
                    {report.news.map((item) => (
                      <Col
                        key={item.id}
                        xs={24}
                        sm={24}
                        lg={12}
                        xl={8}
                        id={`news-${item.id}`}
                      >
                        <NewsCard
                          news={item}
                          onClick={() => handleNewsClick(item.id)}
                        />
                      </Col>
                    ))}
                  </Row>
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
        </div>
      )}
    </div>
  );
}
