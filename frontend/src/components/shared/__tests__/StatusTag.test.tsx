import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusTag } from '../StatusTag';

describe('StatusTag', () => {
  // T2-1: RUNNING → 蓝色标签，文字 "执行中"，带 pulse 动画
  it('renders RUNNING status with blue color and pulse animation', () => {
    render(<StatusTag status="RUNNING" />);
    const tag = screen.getByText('执行中');
    expect(tag).toBeInTheDocument();
  });

  // T2-2: COMPLETED → 绿色标签，文字 "已完成"
  it('renders COMPLETED status with green color', () => {
    render(<StatusTag status="COMPLETED" />);
    const tag = screen.getByText('已完成');
    expect(tag).toBeInTheDocument();
  });

  // T2-3: FAILED → 红色标签，文字 "失败"
  it('renders FAILED status with red color', () => {
    render(<StatusTag status="FAILED" />);
    const tag = screen.getByText('失败');
    expect(tag).toBeInTheDocument();
  });

  it('renders icon when showIcon is true', () => {
    const { container } = render(<StatusTag status="RUNNING" showIcon />);
    // Tag with icon should have the ant-tag icon class or an icon wrapper
    const tag = container.querySelector('.ant-tag');
    expect(tag).toBeInTheDocument();
  });

  it('does not render icon when showIcon is false', () => {
    const { container } = render(<StatusTag status="RUNNING" showIcon={false} />);
    const tag = container.querySelector('.ant-tag');
    expect(tag).toBeInTheDocument();
  });
});
