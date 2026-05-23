import { Card, Typography, theme } from 'antd';
import dayjs from 'dayjs';
import type { NewsSummary } from '@/types';

const { Paragraph, Text } = Typography;

/** 卡片默认阴影（设计规范） */
const CARD_BOX_SHADOW =
  '0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.02)';

/** 卡片悬停阴影（设计规范） */
const CARD_BOX_SHADOW_HOVER = '0 4px 12px rgba(0,0,0,0.06)';

/** 头图尺寸 */
const HEADER_IMAGE_WIDTH = 160;
const HEADER_IMAGE_HEIGHT = 120;

export interface NewsCardProps {
  news: NewsSummary;
  onClick?: () => void;
}

export default function NewsCard({ news, onClick }: NewsCardProps) {
  const { token } = theme.useToken();

  const formattedTime = dayjs(news.publishedAt).format('YYYY-MM-DD HH:mm');

  return (
    <Card
      hoverable={!!onClick}
      onClick={onClick}
      style={{
        borderRadius: token.borderRadiusLG,
        boxShadow: CARD_BOX_SHADOW,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'box-shadow 0.2s ease',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
      styles={{
        body: {
          padding: token.paddingLG,
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
        },
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLElement).style.boxShadow =
          CARD_BOX_SHADOW_HOVER;
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLElement).style.boxShadow = CARD_BOX_SHADOW;
      }}
    >
      {/* 头图 / 占位 */}
      {news.headerImageHtml ? (
        <div
          aria-label={news.title}
          style={{
            width: HEADER_IMAGE_WIDTH,
            height: HEADER_IMAGE_HEIGHT,
            borderRadius: token.borderRadius,
            marginBottom: token.marginMD,
            overflow: 'hidden',
          }}
          dangerouslySetInnerHTML={{ __html: news.headerImageHtml }}
        />
      ) : (
        <div
          aria-label="暂无头图"
          style={{
            width: HEADER_IMAGE_WIDTH,
            height: HEADER_IMAGE_HEIGHT,
            background: token.colorFillSecondary,
            borderRadius: token.borderRadius,
            marginBottom: token.marginMD,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        />
      )}

      {/* 标题：16px / 600 / 最多 2 行 */}
      <Paragraph
        ellipsis={{ rows: 2 }}
        style={{
          fontSize: token.fontSizeLG,
          fontWeight: 600,
          lineHeight: 1.4,
          marginBottom: token.marginSM,
          color: token.colorText,
        }}
      >
        {news.title}
      </Paragraph>

      {/* 概要：14px / 400 / 最多 3 行 */}
      <Paragraph
        ellipsis={{ rows: 3 }}
        style={{
          fontSize: token.fontSize,
          fontWeight: 400,
          lineHeight: 1.6,
          color: token.colorTextSecondary,
          marginBottom: token.marginMD,
          flex: 1,
        }}
      >
        {news.summary}
      </Paragraph>

      {/* 来源 + 发布时间：12px / 灰色 */}
      <Text
        style={{
          fontSize: token.fontSizeSM,
          color: token.colorTextTertiary,
          lineHeight: 1.5,
        }}
      >
        {news.sourceRssName} · {formattedTime}
      </Text>
    </Card>
  );
}
