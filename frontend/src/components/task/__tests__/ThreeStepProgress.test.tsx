import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ThreeStepProgress from '../ThreeStepProgress';

describe('ThreeStepProgress', () => {
  // T3-1: RUNNING + FETCHING → step1 process, step2-3 wait
  it('shows step1 as process and step2-3 as wait when RUNNING in FETCHING phase', () => {
    render(<ThreeStepProgress status="RUNNING" currentPhase="FETCHING" />);

    // All three step titles should be visible
    expect(screen.getByText('拉取新闻中')).toBeInTheDocument();
    expect(screen.getByText('格式化新闻中')).toBeInTheDocument();
    expect(screen.getByText('完成')).toBeInTheDocument();

    // Check step icons: step1 should be a process (ant-steps-item-process)
    const steps = document.querySelectorAll('.ant-steps-item');
    expect(steps.length).toBe(3);

    expect(steps[0].classList.contains('ant-steps-item-process')).toBe(true);
    expect(steps[1].classList.contains('ant-steps-item-wait')).toBe(true);
    expect(steps[2].classList.contains('ant-steps-item-wait')).toBe(true);
  });

  // T3-2: RUNNING + FORMATTING → step1 finish, step2 process, step3 wait
  it('shows step1 finish, step2 process, step3 wait when RUNNING in FORMATTING phase', () => {
    render(<ThreeStepProgress status="RUNNING" currentPhase="FORMATTING" />);

    const steps = document.querySelectorAll('.ant-steps-item');
    expect(steps.length).toBe(3);

    expect(steps[0].classList.contains('ant-steps-item-finish')).toBe(true);
    expect(steps[1].classList.contains('ant-steps-item-process')).toBe(true);
    expect(steps[2].classList.contains('ant-steps-item-wait')).toBe(true);
  });

  // T3-3: COMPLETED → 全部 finish（绿色）
  it('shows all three steps as finish when COMPLETED', () => {
    render(<ThreeStepProgress status="COMPLETED" />);

    const steps = document.querySelectorAll('.ant-steps-item');
    expect(steps.length).toBe(3);

    expect(steps[0].classList.contains('ant-steps-item-finish')).toBe(true);
    expect(steps[1].classList.contains('ant-steps-item-finish')).toBe(true);
    expect(steps[2].classList.contains('ant-steps-item-finish')).toBe(true);
  });

  // T3-4: FAILED → step1-2 finish, step3 error（红色）
  it('shows step1-2 finish and step3 error when FAILED', () => {
    render(<ThreeStepProgress status="FAILED" />);

    const steps = document.querySelectorAll('.ant-steps-item');
    expect(steps.length).toBe(3);

    expect(steps[0].classList.contains('ant-steps-item-finish')).toBe(true);
    expect(steps[1].classList.contains('ant-steps-item-finish')).toBe(true);
    expect(steps[2].classList.contains('ant-steps-item-error')).toBe(true);
  });

  // Default: when no currentPhase is given but RUNNING, should default to FETCHING
  it('defaults to FETCHING phase when RUNNING and no currentPhase specified', () => {
    render(<ThreeStepProgress status="RUNNING" />);

    const steps = document.querySelectorAll('.ant-steps-item');
    expect(steps.length).toBe(3);

    expect(steps[0].classList.contains('ant-steps-item-process')).toBe(true);
    expect(steps[1].classList.contains('ant-steps-item-wait')).toBe(true);
    expect(steps[2].classList.contains('ant-steps-item-wait')).toBe(true);
  });
});
