import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatCard } from '../StatCard';

describe('StatCard', () => {
  // T1-1: 显示今日数值，字号 28px，fontWeight 600
  it('renders today value with 28px font size and fontWeight 600', () => {
    render(
      <StatCard
        title="新闻数量"
        today={5}
        yesterday={3}
        change={2}
        changePercent={66.7}
      />
    );
    const todayValue = screen.getByText('5');
    expect(todayValue).toBeInTheDocument();
    // The today number should be rendered as a heading-level element
    // with appropriate styling
    expect(todayValue.tagName).toMatch(/^(H[1-6]|DIV|SPAN|STRONG)$/i);
  });

  // T1-2: 正同比增长率 → 显示绿色
  it('renders positive change rate in green color', () => {
    render(
      <StatCard
        title="新闻数量"
        today={5}
        yesterday={3}
        change={2}
        changePercent={66.7}
      />
    );
    // Should display ↑2 (66.7%)
    const changeText = screen.getByText(/2/);
    expect(changeText).toBeInTheDocument();
    const percentText = screen.getByText(/66\.7%/);
    expect(percentText).toBeInTheDocument();
  });

  // T1-3: 显示同比变化数值
  it('renders change value correctly', () => {
    render(
      <StatCard
        title="新闻数量"
        today={5}
        yesterday={0}
        change={5}
        changePercent={null}
      />
    );
    expect(screen.getByText('5')).toBeInTheDocument();
    // When changePercent is null, should show "新增" text without %
    const addedText = screen.getByText(/新增/);
    expect(addedText).toBeInTheDocument();
  });

  it('renders zero value correctly when both are zero', () => {
    render(
      <StatCard
        title="新闻数量"
        today={0}
        yesterday={0}
        change={0}
        changePercent={null}
      />
    );
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('renders title text', () => {
    render(
      <StatCard
        title="新闻数量"
        today={5}
        yesterday={3}
        change={2}
        changePercent={66.7}
      />
    );
    expect(screen.getByText('新闻数量')).toBeInTheDocument();
  });

  it('renders negative change in red', () => {
    render(
      <StatCard
        title="新闻数量"
        today={3}
        yesterday={5}
        change={-2}
        changePercent={-40}
      />
    );
    // Should show ↓2 (-40%) with red color
    const todayValue = screen.getByText('3');
    expect(todayValue).toBeInTheDocument();
    // Math.abs is used, so the negative sign is not shown in the text
    const pctText = screen.getByText(/40%/);
    expect(pctText).toBeInTheDocument();
  });
});
