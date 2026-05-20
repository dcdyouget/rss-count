import { Row, Col, Skeleton, Card, Empty } from 'antd';
import NewsCard from './NewsCard';
import type { NewsSummary } from '@/types';

export interface NewsCardGridProps {
  news: NewsSummary[];
  loading?: boolean;
  onNewsClick?: (id: number) => void;
}

/**
 * 新闻卡片响应式网格容器
 *
 * 断点：
 * - xl (≥1200px): 3 列（每列 colSpan=8）
 * - lg (≥992px):  2 列（每列 colSpan=12）
 * - sm (≥576px):  1 列（每列 colSpan=24）
 */
export default function NewsCardGrid({
  news,
  loading = false,
  onNewsClick,
}: NewsCardGridProps) {
  // 加载中：渲染 3 个骨架卡片
  if (loading) {
    return (
      <Row gutter={[16, 16]}>
        {[1, 2, 3].map((i) => (
          <Col key={i} xs={24} sm={24} lg={12} xl={8}>
            <Card>
              <Skeleton active paragraph={{ rows: 4 }} />
            </Card>
          </Col>
        ))}
      </Row>
    );
  }

  // 空状态
  if (news.length === 0) {
    return <Empty description="暂无新闻" />;
  }

  return (
    <Row gutter={[16, 16]}>
      {news.map((item) => (
        <Col key={item.id} xs={24} sm={24} lg={12} xl={8}>
          <NewsCard
            news={item}
            onClick={onNewsClick ? () => onNewsClick(item.id) : undefined}
          />
        </Col>
      ))}
    </Row>
  );
}
