import { Radio, Select, Space, Empty } from 'antd';
import { theme } from 'antd';
import type { SourceType, SourceConfig } from '@/types';

interface SourceSelectorValue {
  sourceType: SourceType;
  sourceConfig: SourceConfig;
}

interface GroupOption {
  id: number;
  name: string;
}

interface SourceOption {
  id: number;
  name: string;
  groupId?: number;
}

interface SourceSelectorProps {
  value?: SourceSelectorValue;
  onChange?: (value: SourceSelectorValue) => void;
  groups?: GroupOption[];
  sources?: SourceOption[];
}

const SOURCE_TYPE_OPTIONS = [
  { label: '全部源', value: 'ALL' as const },
  { label: '按分组', value: 'GROUP' as const },
  { label: '按源', value: 'SOURCE' as const },
  { label: '混合', value: 'MIXED' as const },
];

/**
 * SourceSelector — RSS 源选择组件
 *
 * 用于创建任务 Drawer 中选择 RSS 源范围：
 * - ALL   → 全部源，不显示额外选择器
 * - GROUP → 按分组选择，显示分组多项选择器
 * - SOURCE → 按源选择，显示源多项选择器
 * - MIXED → 混合模式，同时显示分组和源选择器
 */
export default function SourceSelector({
  value,
  onChange,
  groups = [],
  sources = [],
}: SourceSelectorProps) {
  const { token } = theme.useToken();

  const sourceType = value?.sourceType ?? 'ALL';
  const sourceConfig = value?.sourceConfig ?? { groupIds: [], sourceIds: [] };

  const handleTypeChange = (newType: SourceType) => {
    onChange?.({
      sourceType: newType,
      sourceConfig: { groupIds: [], sourceIds: [] },
    });
  };

  const handleGroupChange = (groupIds: number[]) => {
    onChange?.({
      sourceType,
      sourceConfig: { ...sourceConfig, groupIds },
    });
  };

  const handleSourceChange = (sourceIds: number[]) => {
    onChange?.({
      sourceType,
      sourceConfig: { ...sourceConfig, sourceIds },
    });
  };

  const showGroupSelect = sourceType === 'GROUP' || sourceType === 'MIXED';
  const showSourceSelect = sourceType === 'SOURCE' || sourceType === 'MIXED';

  return (
    <div>
      <div style={{ marginBottom: token.marginMD }}>
        <Radio.Group
          options={SOURCE_TYPE_OPTIONS}
          value={sourceType}
          onChange={(e) => handleTypeChange(e.target.value)}
          optionType="button"
          buttonStyle="solid"
        />
      </div>

      {(showGroupSelect || showSourceSelect) && (
        <Space direction="vertical" style={{ width: '100%' }} size={token.marginSM}>
          {showGroupSelect && (
            <div>
              <div
                style={{
                  fontSize: token.fontSizeSM,
                  color: token.colorTextSecondary,
                  marginBottom: token.marginXS,
                }}
              >
                选择分组
              </div>
              {groups.length > 0 ? (
                <Select
                  mode="multiple"
                  style={{ width: '100%' }}
                  placeholder="请选择分组"
                  value={sourceConfig.groupIds}
                  onChange={handleGroupChange}
                  options={groups.map((g) => ({ label: g.name, value: g.id }))}
                />
              ) : (
                <Empty
                  description="暂无可用分组"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )}
            </div>
          )}

          {showSourceSelect && (
            <div>
              <div
                style={{
                  fontSize: token.fontSizeSM,
                  color: token.colorTextSecondary,
                  marginBottom: token.marginXS,
                }}
              >
                选择源
              </div>
              {sources.length > 0 ? (
                <Select
                  mode="multiple"
                  style={{ width: '100%' }}
                  placeholder="请选择源"
                  value={sourceConfig.sourceIds}
                  onChange={handleSourceChange}
                  options={sources.map((s) => ({ label: s.name, value: s.id }))}
                />
              ) : (
                <Empty
                  description="暂无可用源"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )}
            </div>
          )}
        </Space>
      )}
    </div>
  );
}
