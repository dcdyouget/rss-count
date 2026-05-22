import React from 'react';
import { Empty } from 'antd';
import './CleanedHtmlRenderer.css';

interface CleanedHtmlRendererProps {
  html: string | null | undefined;
}

export const CleanedHtmlRenderer: React.FC<CleanedHtmlRendererProps> = ({ html }) => {
  if (!html || html.trim().length === 0) {
    return <Empty description="暂无正文内容" />;
  }
  return (
    <div
      className="news-content-html"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
};
