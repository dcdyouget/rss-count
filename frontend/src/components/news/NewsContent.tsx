import React from 'react';
import { Button, Tag } from 'antd';
import {
  ArrowLeftOutlined,
  CheckOutlined,
  ExportOutlined,
  InboxOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import dayjs from 'dayjs';
import { CleanedHtmlRenderer } from '@/components/shared/CleanedHtmlRenderer';
import type { NewsDetail } from '@/types';
import './NewsContent.css';

export interface NewsContentProps {
  news: NewsDetail;
  backUrl?: string;
  showSidebar?: boolean;
  compact?: boolean;
  onMarkRead?: (id: number) => void;
  onAddToMaterialPile?: (id: number) => void;
  onToggleMaterialPile?: (id: number) => void;
}

const NewsContent: React.FC<NewsContentProps> = ({
  news,
  backUrl,
  showSidebar = true,
  compact = false,
  onMarkRead,
  onAddToMaterialPile,
  onToggleMaterialPile,
}) => {
  const materialAction = onToggleMaterialPile ?? onAddToMaterialPile;
  const publishedAt = news.publishedAt
    ? dayjs(news.publishedAt).format('YYYY年M月D日 HH:mm')
    : null;

  return (
    <div className={`news-article-view ${compact ? 'news-article-view--compact' : ''}`}>
      {compact && (
        <div className="news-article-toolbar">
          <div className={`news-read-status ${news.isRead ? 'is-read' : ''}`}>
            <span />
            {news.isRead ? '已读' : '未读'}
          </div>

          {onMarkRead && (
            <Button
              type="text"
              icon={<CheckOutlined />}
              onClick={() => onMarkRead(news.id)}
              disabled={news.isRead}
            >
              {news.isRead ? '已标记' : '标记已读'}
            </Button>
          )}

          {materialAction && (
            <Button
              type="text"
              icon={<InboxOutlined />}
              onClick={() => materialAction(news.id)}
              disabled={!onToggleMaterialPile && news.inMaterialPile}
            >
              {onToggleMaterialPile && news.inMaterialPile
                ? '移出素材堆'
                : news.inMaterialPile
                  ? '已在素材堆'
                  : '加入素材堆'}
            </Button>
          )}

          {news.sourceUrl && (
            <Button
              type="text"
              icon={<ExportOutlined />}
              href={news.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              原文
            </Button>
          )}
        </div>
      )}

      <div className="news-article-scroll">
        <article className="news-article">
          {backUrl && (
            <Link className="news-article-back" to={backUrl}>
              <ArrowLeftOutlined />
              返回
            </Link>
          )}

          <div className="news-article-kicker">
            {Array.isArray(news.tags) && news.tags.map((tag) => (
              <Tag key={tag} bordered={false}>{tag}</Tag>
            ))}
            <strong>{news.sourceRssName}</strong>
          </div>

          <h1>{news.title}</h1>

          <div className="news-article-info">
            {news.author && (
              <span>
                作者：<span>{news.author}</span>
              </span>
            )}
            {publishedAt && <span>{publishedAt}</span>}
            {news.reportName && <span>来自报告：{news.reportName}</span>}
          </div>

          {compact && news.summary && (
            <div className="news-article-lead">{news.summary}</div>
          )}

          <CleanedHtmlRenderer html={news.structuredContent} />

          {news.sourceUrl && (
            <a
              className="news-article-source-link"
              href={news.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              <LinkOutlined />
              阅读原文
            </a>
          )}
        </article>
      </div>

      {!compact && showSidebar && (
        <aside className="news-article-sidecard">
          {news.author && (
            <>
              <strong>作者</strong>
              <span>作者信息：{news.author}</span>
            </>
          )}
          {Array.isArray(news.tags) && news.tags.length > 0 && <strong>标签</strong>}
          <span>来源：{news.sourceRssName}</span>
          {publishedAt && <span>发布：{publishedAt}</span>}
          {news.sourceUrl && (
            <a href={news.sourceUrl} target="_blank" rel="noopener noreferrer">
              查看原文
            </a>
          )}
          {onMarkRead && (
            <Button
              icon={<CheckOutlined />}
              onClick={() => onMarkRead(news.id)}
              disabled={news.isRead}
            >
              {news.isRead ? '已读' : '标记已读'}
            </Button>
          )}
          {materialAction && (
            <Button
              icon={<InboxOutlined />}
              onClick={() => materialAction(news.id)}
              disabled={!onToggleMaterialPile && news.inMaterialPile}
            >
              {onToggleMaterialPile && news.inMaterialPile
                ? '移出素材堆'
                : news.inMaterialPile
                  ? '已在素材堆'
                  : '加入素材堆'}
            </Button>
          )}
        </aside>
      )}
    </div>
  );
};

export default NewsContent;
