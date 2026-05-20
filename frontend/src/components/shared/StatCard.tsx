import React from 'react';
import { Card, theme, Typography } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';

interface StatCardProps {
  title: string;
  today: number;
  yesterday: number;
  change: number;
  changePercent: number | null;
  icon?: React.ReactNode;
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  today,
  yesterday,
  change,
  changePercent,
  icon,
}) => {
  const { token } = theme.useToken();

  const isPositive = change > 0;
  const isNegative = change < 0;
  const hasChange = change !== 0;
  const arrow = isPositive ? (
    <ArrowUpOutlined style={{ fontSize: token.fontSizeSM }} />
  ) : isNegative ? (
    <ArrowDownOutlined style={{ fontSize: token.fontSizeSM }} />
  ) : null;

  const changeColor = isPositive
    ? token.colorSuccess
    : isNegative
      ? token.colorError
      : token.colorTextSecondary;

  const renderChangeText = () => {
    if (!hasChange && yesterday === 0 && today === 0) {
      return null;
    }
    if (changePercent !== null) {
      return (
        <span style={{ color: changeColor, fontSize: token.fontSizeSM }}>
          {arrow} {Math.abs(change)} ({Math.abs(changePercent)}%)
        </span>
      );
    }
    if (yesterday === 0 && today > 0) {
      return (
        <span style={{ color: token.colorSuccess, fontSize: token.fontSizeSM }}>
          {arrow} 新增 {change}
        </span>
      );
    }
    if (hasChange) {
      return (
        <span style={{ color: changeColor, fontSize: token.fontSizeSM }}>
          {arrow} {Math.abs(change)}
        </span>
      );
    }
    return null;
  };

  return (
    <Card
      style={{ borderRadius: token.borderRadiusLG }}
      styles={{ body: { padding: token.paddingContentVertical, paddingBottom: token.paddingContentVertical - 8 } }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: token.marginSM }}>
        <Typography.Text
          type="secondary"
          style={{ fontSize: token.fontSizeSM }}
        >
          {title}
        </Typography.Text>
        {icon && (
          <span style={{ color: token.colorTextSecondary, fontSize: 20 }}>
            {icon}
          </span>
        )}
      </div>

      <Typography.Title
        level={1}
        style={{
          fontSize: token.fontSizeHeading1,
          fontWeight: 600,
          marginBottom: token.marginXS,
          lineHeight: 1.3,
          marginTop: 0,
        }}
      >
        {today}
      </Typography.Title>

      <div style={{ display: 'flex', alignItems: 'center', gap: token.marginXS }}>
        {renderChangeText()}
        {!hasChange && yesterday === 0 && today === 0 && (
          <Typography.Text
            type="secondary"
            style={{ fontSize: token.fontSizeSM }}
          >
            暂无数据
          </Typography.Text>
        )}
      </div>
    </Card>
  );
};
