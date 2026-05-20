import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SourceSelector from '../SourceSelector';

const mockGroups = [
  { id: 1, name: '科技' },
  { id: 2, name: '财经' },
  { id: 3, name: '体育' },
];

const mockSources = [
  { id: 1, name: '36氪', groupId: 1 },
  { id: 2, name: '虎嗅', groupId: 1 },
  { id: 3, name: '财新网', groupId: 2 },
  { id: 4, name: '新浪体育', groupId: 3 },
  { id: 5, name: '独立博客', groupId: undefined },
];

describe('SourceSelector', () => {
  // T6-1: Default is ALL mode, no extra selectors
  it('defaults to ALL mode with radio checked and no extra selectors', () => {
    render(<SourceSelector groups={mockGroups} sources={mockSources} />);

    // Radio for "全部源" should be checked
    const allRadio = screen.getByRole('radio', { name: /全部源/ });
    expect(allRadio).toBeChecked();

    // No select dropdowns should be visible initially (ALL mode)
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  // T6-2: Switch to GROUP mode shows group selector
  it('shows group selector when switched to GROUP mode', () => {
    const handleChange = vi.fn();
    render(
      <SourceSelector
        groups={mockGroups}
        sources={mockSources}
        onChange={handleChange}
      />,
    );

    // Click "按分组" radio
    const groupRadio = screen.getByRole('radio', { name: /按分组/ });
    fireEvent.click(groupRadio);

    // onChange should be called with GROUP type
    expect(handleChange).toHaveBeenCalledWith({
      sourceType: 'GROUP',
      sourceConfig: { groupIds: [], sourceIds: [] },
    });

    // Now re-render with the GROUP value
    const { container: container2 } = render(
      <SourceSelector
        value={{ sourceType: 'GROUP', sourceConfig: { groupIds: [], sourceIds: [] } }}
        groups={mockGroups}
        sources={mockSources}
      />,
    );

    // Should now show a group selector (Select with mode multiple)
    const selectors = container2.querySelectorAll('.ant-select');
    expect(selectors.length).toBeGreaterThanOrEqual(1);
  });

  // T6-3: Switch to MIXED mode shows both selectors
  it('shows both group and source selectors when switched to MIXED mode', () => {
    const { container } = render(
      <SourceSelector
        value={{ sourceType: 'MIXED', sourceConfig: { groupIds: [], sourceIds: [] } }}
        groups={mockGroups}
        sources={mockSources}
      />,
    );

    // Should show two select dropdowns (groups + sources)
    const selectors = container.querySelectorAll('.ant-select');
    expect(selectors.length).toBeGreaterThanOrEqual(2);
  });

  // SOURCE mode shows source selector
  it('shows source selector when switched to SOURCE mode', () => {
    const { container } = render(
      <SourceSelector
        value={{ sourceType: 'SOURCE', sourceConfig: { groupIds: [], sourceIds: [] } }}
        groups={mockGroups}
        sources={mockSources}
      />,
    );

    const selectors = container.querySelectorAll('.ant-select');
    expect(selectors.length).toBeGreaterThanOrEqual(1);
  });

  // Controlled component: value prop controls the mode
  it('respects the value prop for controlled mode', () => {
    const handleChange = vi.fn();
    render(
      <SourceSelector
        value={{ sourceType: 'GROUP', sourceConfig: { groupIds: [1, 2], sourceIds: [] } }}
        groups={mockGroups}
        sources={mockSources}
        onChange={handleChange}
      />,
    );

    const groupRadio = screen.getByRole('radio', { name: /按分组/ });
    expect(groupRadio).toBeChecked();
  });
});
