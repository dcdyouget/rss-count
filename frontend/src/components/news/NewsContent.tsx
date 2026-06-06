import React from 'react';
import { Typography, Tag, Button, Space, Flex, Divider, theme } from 'antd';
import {
  ArrowLeftOutlined,
  LinkOutlined,
  CheckOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { CleanedHtmlRenderer } from '@/components/shared/CleanedHtmlRenderer';
import type { NewsDetail } from '@/types';

const { Title, Text } = Typography;

export interface NewsContentProps {
  news: NewsDetail;
  backUrl?: string;
  showSidebar?: boolean;
  onMarkRead?: (id: number) => void;
  onAddToMaterialPile?: (id: number) => void;
}

const NewsContent: React.FC<NewsContentProps> = ({
  news,
  backUrl,
  showSidebar = true,
  onMarkRead,
  onAddToMaterialPile,
}) => {
  const { token } = theme.useToken();

  return (
    <div
      style={{
        display: 'flex',
        gap: token.marginLG,
        height: 'calc(100vh - 48px)',
        overflow: 'hidden',
      }}
    >
      {/* Left column: header fixed + body scrollable */}
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
          minWidth: 0,
          gap: token.marginLG,
          overflow: 'hidden',
        }}
      >
        {/* Fixed header: back button + title + meta */}
        <div style={{ flexShrink: 0 }}>
          <Flex vertical gap={token.marginXS}>
            {backUrl && (
              <Link
                to={backUrl}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: token.marginXS,
                  color: token.colorTextSecondary,
                  fontSize: token.fontSizeSM,
                  marginBottom: token.marginSM,
                  width: 'fit-content',
                }}
              >
                <ArrowLeftOutlined />
                返回
              </Link>
            )}

            <Title
              level={2}
              style={{ margin: 0, fontSize: token.fontSizeHeading2 }}
            >
              {news.title}
            </Title>

            <Flex gap={token.marginSM} align="center" wrap="wrap">
              <Text
                style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}
              >
                来源：{news.sourceRssName}
              </Text>
              {news.publishedAt && (
                <Text
                  style={{ fontSize: token.fontSizeSM, color: token.colorTextTertiary }}
                >
                  发布时间：{news.publishedAt}
                </Text>
              )}
            </Flex>
          </Flex>
        </div>

        {/* Scrollable body area */}
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          <div style={{ maxWidth: 720, margin: '0 auto' }}>
            <CleanedHtmlRenderer html={news.structuredContent} />
          </div>
        </div>
      </div>

      {/* Right sidebar: fixed, does not scroll */}
      {showSidebar && (
        <div
          style={{
            width: 250,
            flexShrink: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: token.marginMD,
            alignSelf: 'flex-start',
          }}
        >
          {/* Author info */}
          {news.author && (
            <div>
              <Text
                strong
                style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}
              >
                作者
              </Text>
              <div style={{ marginTop: token.marginXS }}>
                <Text>{news.author}</Text>
              </div>
            </div>
          )}

          {/* Tags */}
          {Array.isArray(news.tags) && news.tags.length > 0 && (
            <div>
              <Text
                strong
                style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}
              >
                标签
              </Text>
              <Flex gap={token.marginXS} wrap="wrap" style={{ marginTop: token.marginXS }}>
                {news.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </Flex>
            </div>
          )}

          <Divider style={{ margin: 0 }} />

          {/* Source link */}
          {news.sourceUrl && (
            <Button
              type="link"
              icon={<LinkOutlined />}
              href={news.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              style={{ padding: 0, justifyContent: 'flex-start' }}
            >
              查看原文
            </Button>
          )}

          {/* Action buttons */}
          <Space direction="vertical" style={{ width: '100%' }}>
            {onMarkRead && (
              <Button
                icon={<CheckOutlined />}
                onClick={() => onMarkRead(news.id)}
                disabled={news.isRead}
                block
              >
                {news.isRead ? '已读' : '标记已读'}
              </Button>
            )}

            {onAddToMaterialPile && (
              <Button
                icon={<InboxOutlined />}
                onClick={() => onAddToMaterialPile(news.id)}
                disabled={news.inMaterialPile}
                block
              >
                {news.inMaterialPile ? '已在素材堆' : '加入素材堆'}
              </Button>
            )}
          </Space>
        </div>
      )}
    </div>
  );
};

export default NewsContent;
