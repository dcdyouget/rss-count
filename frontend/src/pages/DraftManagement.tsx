import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  Row, Col, Card, Select, Button, Input, Slider, Tabs, List,
  Modal, Form, Space, Typography, Empty, message, Checkbox,
  theme, Flex, Spin, Tooltip,
} from 'antd';
import {
  PlusOutlined, SaveOutlined, CopyOutlined, DeleteOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { DraftListSummary, MaterialPileItem } from '@/types';
import {
  useDraftList, useDraft, useCreateDraft, useUpdateDraft,
  useDeleteDraft, useGenerateDraft, useMaterialPile,
} from '@/api/drafts';
import { useBatchMaterialPile } from '@/api/news';
import { useAutoSave } from '@/hooks/useAutoSave';
import { formatDateTime } from '@/utils/format';
import {
  DRAFT_STYLES, DRAFT_PLATFORMS,
  DRAFT_TEMPERATURE_MIN, DRAFT_TEMPERATURE_MAX,
  DRAFT_TEMPERATURE_STEP, DRAFT_TEMPERATURE_DEFAULT,
} from '@/utils/constants';

const { Text } = Typography;
const { TextArea } = Input;

export default function DraftManagement() {
  const { token } = theme.useToken();
  const params = useParams<{ id?: string }>();

  const [selectedDraftId, setSelectedDraftId] = useState<number | null>(null);
  const [draftContent, setDraftContent] = useState('');
  const [prompt, setPrompt] = useState('');
  const [temperature, setTemperature] = useState(DRAFT_TEMPERATURE_DEFAULT);
  const [style, setStyle] = useState(DRAFT_STYLES[0].value);
  const [platform, setPlatform] = useState(DRAFT_PLATFORMS[4].value);
  const [materialNews, setMaterialNews] = useState<MaterialPileItem[]>([]);
  const [editorTab, setEditorTab] = useState('rich');

  const [newModalOpen, setNewModalOpen] = useState(false);
  const [newDraftName, setNewDraftName] = useState('');
  const [newDraftNewsIds, setNewDraftNewsIds] = useState<number[]>([]);

  const { data: draftListData, isLoading: listLoading } = useDraftList({});
  const { data: draft, isLoading: draftLoading } = useDraft(selectedDraftId);
  const { data: materialPile } = useMaterialPile({});

  const createDraft = useCreateDraft();
  const updateDraft = useUpdateDraft();
  const deleteDraft = useDeleteDraft();
  const generateDraft = useGenerateDraft();
  const batchMaterialPile = useBatchMaterialPile();

  // Auto-select draft from URL param
  useEffect(() => {
    if (params.id) {
      setSelectedDraftId(Number(params.id));
    }
  }, [params.id]);

  // Sync local state when draft loads
  useEffect(() => {
    if (draft) {
      setDraftContent(draft.latestContent || '');
      setPrompt(draft.prompt || '');
      setTemperature(draft.temperature ?? DRAFT_TEMPERATURE_DEFAULT);
      setStyle(draft.style || DRAFT_STYLES[0].value);
      setPlatform(draft.targetPlatform || DRAFT_PLATFORMS[4].value);
      setMaterialNews(
        (draft.news ?? []).map((n) => ({
          id: n.id,
          title: n.title,
          materialPileAddedAt: n.publishedAt,
        })),
      );
    }
  }, [draft]);

  // Save function for auto-save
  const saveContent = useCallback(
    async (content: string) => {
      if (!selectedDraftId || !draft) return;
      await updateDraft.mutateAsync({
        id: selectedDraftId,
        data: {
          name: draft.name,
          newsIds: materialNews.map((n) => n.id),
          prompt,
          temperature,
          style,
          targetPlatform: platform,
          latestContent: content,
        } as any,
      });
    },
    [selectedDraftId, draft, materialNews, prompt, temperature, style, platform, updateDraft],
  );

  const { isSaving, lastSaved, dirty, forceSave } = useAutoSave(
    draftContent,
    saveContent,
    2000,
  );

  const handleSelectDraft = (id: number) => {
    setSelectedDraftId(id);
  };

  const handleOpenNewModal = () => {
    setNewDraftName(`稿件-${dayjs().format('YYYY-MM-DD HH:mm')}`);
    setNewModalOpen(true);
  };

  const handleNewDraft = async () => {
    if (!newDraftName.trim()) {
      message.warning('请输入稿件名称');
      return;
    }
    try {
      const result = await createDraft.mutateAsync({
        name: newDraftName.trim(),
        newsIds: newDraftNewsIds,
        prompt: '',
        temperature: DRAFT_TEMPERATURE_DEFAULT,
        style: DRAFT_STYLES[0].value,
        targetPlatform: DRAFT_PLATFORMS[4].value,
      });
      setSelectedDraftId(result.id);
      setNewModalOpen(false);
      setNewDraftName('');
      setNewDraftNewsIds([]);
      message.success('稿件创建成功');
    } catch {
      // handled by interceptor
    }
  };

  const handleDeleteDraft = async () => {
    if (!selectedDraftId) return;
    try {
      await deleteDraft.mutateAsync(selectedDraftId);
      setSelectedDraftId(null);
      setDraftContent('');
      setPrompt('');
      setTemperature(DRAFT_TEMPERATURE_DEFAULT);
      setStyle(DRAFT_STYLES[0].value);
      setPlatform(DRAFT_PLATFORMS[4].value);
      setMaterialNews([]);
      message.success('稿件已删除');
    } catch {
      // handled by interceptor
    }
  };

  const handleGenerate = async () => {
    if (!selectedDraftId) return;
    if (materialNews.length === 0) {
      message.warning('素材堆为空，请先添加新闻素材');
      return;
    }
    try {
      const result = await generateDraft.mutateAsync(selectedDraftId);
      setDraftContent(result.content);
      message.success('稿件生成成功');
    } catch {
      // handled by interceptor
    }
  };

  const handleCopy = () => {
    if (!draftContent) {
      message.warning('没有可复制的内容');
      return;
    }
    navigator.clipboard.writeText(draftContent);
    message.success('内容已复制');
  };

  const handleRemoveMaterial = async (newsId: number) => {
    if (!selectedDraftId || !draft) return;
    const newIds = materialNews.filter((n) => n.id !== newsId).map((n) => n.id);
    try {
      await updateDraft.mutateAsync({
        id: selectedDraftId,
        data: {
          name: draft.name,
          newsIds: newIds,
          prompt,
          temperature,
          style,
          targetPlatform: platform,
        } as any,
      });
      setMaterialNews((prev) => prev.filter((n) => n.id !== newsId));
    } catch {
      // handled by interceptor
    }
  };

  const handleRemoveFromMaterialPile = async (newsId: number) => {
    try {
      await batchMaterialPile.mutateAsync({
        newsIds: [newsId],
        action: 'REMOVE',
      });
      setNewDraftNewsIds((prev) => prev.filter((id) => id !== newsId));
      message.success('已从素材堆移除');
    } catch {
      // handled by interceptor
    }
  };

  const draftOptions = (draftListData?.items ?? []).map((d: DraftListSummary) => ({
    label: d.name,
    value: d.id,
  }));

  const canGenerate = materialNews.length > 0 && !!selectedDraftId;

  const saveStatusText = isSaving
    ? '保存中...'
    : lastSaved
      ? `已保存 ${formatDateTime(lastSaved.toISOString())}`
      : dirty
        ? '未保存'
        : '';

  return (
    <div>
      {/* Top bar */}
      <Flex
        justify="space-between"
        align="center"
        style={{ marginBottom: token.marginLG }}
      >
        <Space size={token.marginSM}>
          <Select
            placeholder="选择稿件"
            style={{ width: 240 }}
            value={selectedDraftId}
            onChange={handleSelectDraft}
            options={draftOptions}
            loading={listLoading}
            allowClear
            notFoundContent={<Empty description="暂无稿件" />}
          />
          <Button icon={<PlusOutlined />} onClick={handleOpenNewModal}>
            新建稿件
          </Button>
          {selectedDraftId && (
            <Button
              danger
              icon={<DeleteOutlined />}
              onClick={handleDeleteDraft}
              loading={deleteDraft.isPending}
            >
              删除
            </Button>
          )}
        </Space>

        <Space size={token.marginSM}>
          {saveStatusText && (
            <Text
              style={{
                fontSize: token.fontSizeSM,
                color: isSaving ? token.colorWarning : token.colorTextTertiary,
              }}
            >
              {saveStatusText}
            </Text>
          )}
          <Button
            type="primary"
            icon={<SaveOutlined />}
            onClick={() => { forceSave(); }}
            loading={isSaving}
          >
            保存
          </Button>
        </Space>
      </Flex>

      {/* Loading state */}
      {draftLoading && selectedDraftId && (
        <Spin tip="加载稿件中..." style={{ display: 'block', textAlign: 'center', padding: 80 }}>
          <div />
        </Spin>
      )}

      {/* Empty state */}
      {!selectedDraftId && !draftLoading && (
        <Empty
          description="请选择或创建一个稿件"
          style={{ padding: 80 }}
        >
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenNewModal}
          >
            新建稿件
          </Button>
        </Empty>
      )}

      {/* Editor — left-right split */}
      {selectedDraftId && !draftLoading && (
        <Row gutter={16}>
          {/* Left panel */}
          <Col span={10}>
            <Flex vertical gap={token.marginMD}>
              {/* Material pile */}
              <Card
                title={
                  <Text strong style={{ fontSize: token.fontSize }}>
                    素材堆 ({materialNews.length})
                  </Text>
                }
                size="small"
                styles={{ body: { padding: 0 } }}
              >
                <List
                  dataSource={materialNews}
                  locale={{
                    emptyText: (
                      <Empty
                        description="素材堆为空"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                      />
                    ),
                  }}
                  renderItem={(item) => (
                    <List.Item
                      style={{ padding: `${token.paddingSM}px ${token.paddingMD}px` }}
                      actions={[
                        <Button
                          key="remove"
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => handleRemoveMaterial(item.id)}
                        />,
                      ]}
                    >
                      <List.Item.Meta
                        title={
                          <Text
                            ellipsis
                            style={{ maxWidth: 220, fontSize: token.fontSize }}
                          >
                            {item.title}
                          </Text>
                        }
                        description={
                          <Text
                            style={{
                              fontSize: token.fontSizeSM,
                              color: token.colorTextTertiary,
                            }}
                          >
                            {formatDateTime(item.materialPileAddedAt)}
                          </Text>
                        }
                      />
                    </List.Item>
                  )}
                />
              </Card>

              {/* Prompt */}
              <div>
                <Text
                  strong
                  style={{
                    display: 'block',
                    marginBottom: token.marginXS,
                    fontSize: token.fontSize,
                  }}
                >
                  提示词
                </Text>
                <TextArea
                  rows={4}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  placeholder="输入写作提示词..."
                />
              </div>

              {/* Temperature */}
              <div>
                <Flex
                  justify="space-between"
                  style={{ marginBottom: token.marginXS }}
                >
                  <Text strong style={{ fontSize: token.fontSize }}>
                    温度
                  </Text>
                  <Text style={{ fontSize: token.fontSizeSM, color: token.colorTextTertiary }}>
                    {temperature.toFixed(1)}
                  </Text>
                </Flex>
                <Slider
                  min={DRAFT_TEMPERATURE_MIN}
                  max={DRAFT_TEMPERATURE_MAX}
                  step={DRAFT_TEMPERATURE_STEP}
                  value={temperature}
                  onChange={setTemperature}
                />
              </div>

              {/* Style */}
              <div>
                <Text
                  strong
                  style={{
                    display: 'block',
                    marginBottom: token.marginXS,
                    fontSize: token.fontSize,
                  }}
                >
                  风格
                </Text>
                <Select
                  style={{ width: '100%' }}
                  value={style}
                  onChange={setStyle}
                  options={DRAFT_STYLES.map((s) => ({
                    label: s.label,
                    value: s.value,
                  }))}
                />
              </div>

              {/* Platform */}
              <div>
                <Text
                  strong
                  style={{
                    display: 'block',
                    marginBottom: token.marginXS,
                    fontSize: token.fontSize,
                  }}
                >
                  平台
                </Text>
                <Select
                  style={{ width: '100%' }}
                  value={platform}
                  onChange={setPlatform}
                  options={DRAFT_PLATFORMS.map((p) => ({
                    label: p.label,
                    value: p.value,
                  }))}
                />
              </div>

              {/* Generate button */}
              <Tooltip
                title={
                  !canGenerate
                    ? '请先在新闻管理中添加素材'
                    : undefined
                }
              >
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  block
                  size="large"
                  disabled={!canGenerate}
                  loading={generateDraft.isPending}
                  onClick={handleGenerate}
                >
                  AI 生成稿件
                </Button>
              </Tooltip>
            </Flex>
          </Col>

          {/* Right panel */}
          <Col span={14}>
            <Card
              styles={{ body: { padding: 0 } }}
              style={{ borderRadius: token.borderRadiusLG, overflow: 'hidden' }}
            >
              <Tabs
                activeKey={editorTab}
                onChange={setEditorTab}
                tabBarStyle={{ paddingLeft: token.paddingMD, marginBottom: 0 }}
                tabBarExtraContent={
                  <Button
                    icon={<CopyOutlined />}
                    onClick={handleCopy}
                    style={{ marginRight: token.marginMD }}
                  >
                    复制内容
                  </Button>
                }
                items={[
                  {
                    key: 'rich',
                    label: '富文本',
                    children: (
                      <div style={{ padding: token.paddingMD }}>
                        <TextArea
                          value={draftContent}
                          onChange={(e) => setDraftContent(e.target.value)}
                          rows={22}
                          style={{
                            fontFamily:
                              '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
                            fontSize: token.fontSize,
                            lineHeight: token.lineHeight,
                          }}
                          placeholder="AI 生成的内容将显示在这里..."
                        />
                      </div>
                    ),
                  },
                  {
                    key: 'plain',
                    label: '纯文本',
                    children: (
                      <div style={{ padding: token.paddingMD }}>
                        <TextArea
                          value={draftContent}
                          onChange={(e) => setDraftContent(e.target.value)}
                          rows={22}
                          style={{
                            fontFamily:
                              '"Courier New", Courier, "Source Code Pro", monospace',
                            fontSize: token.fontSize,
                            lineHeight: token.lineHeight,
                          }}
                          placeholder="AI 生成的内容将显示在这里..."
                        />
                      </div>
                    ),
                  },
                  {
                    key: 'preview',
                    label: '预览',
                    children: (
                      <div
                        style={{
                          padding: token.paddingLG,
                          minHeight: 400,
                          lineHeight: token.lineHeight,
                        }}
                      >
                        {draftContent ? (
                          <div
                            dangerouslySetInnerHTML={{
                              __html: draftContent,
                            }}
                          />
                        ) : (
                          <Empty description="暂无内容" />
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            </Card>
          </Col>
        </Row>
      )}

      {/* New draft modal */}
      <Modal
        title="新建稿件"
        open={newModalOpen}
        onOk={handleNewDraft}
        onCancel={() => {
          setNewModalOpen(false);
          setNewDraftName('');
          setNewDraftNewsIds([]);
        }}
        confirmLoading={createDraft.isPending}
        okText="创建"
        cancelText="取消"
      >
        <Form layout="vertical">
          <Form.Item label="稿件名称" required>
            <Input
              value={newDraftName}
              onChange={(e) => setNewDraftName(e.target.value)}
              placeholder="输入稿件名称"
            />
          </Form.Item>
          <Form.Item label="素材堆 (勾选加入稿件，点击删除移出素材堆)">
            <List
              dataSource={materialPile?.items ?? []}
              locale={{
                emptyText: (
                  <Empty
                    description="素材堆为空，请先在新闻管理中添加素材"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  />
                ),
              }}
              style={{ maxHeight: 300, overflow: 'auto' }}
              renderItem={(item: MaterialPileItem) => (
                <List.Item
                  style={{
                    padding: `${token.paddingSM}px ${token.paddingMD}px`,
                  }}
                  actions={[
                    <Button
                      key="delete"
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      loading={batchMaterialPile.isPending}
                      onClick={() => handleRemoveFromMaterialPile(item.id)}
                    />,
                  ]}
                >
                  <Checkbox
                    checked={newDraftNewsIds.includes(item.id)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setNewDraftNewsIds((prev) => [...prev, item.id]);
                      } else {
                        setNewDraftNewsIds((prev) =>
                          prev.filter((id) => id !== item.id),
                        );
                      }
                    }}
                  >
                    <Space direction="vertical" size={0}>
                      <Text
                        ellipsis
                        style={{ maxWidth: 220, fontSize: token.fontSize }}
                      >
                        {item.title}
                      </Text>
                      <Text
                        style={{
                          fontSize: token.fontSizeSM,
                          color: token.colorTextTertiary,
                        }}
                      >
                        {formatDateTime(item.materialPileAddedAt)}
                      </Text>
                    </Space>
                  </Checkbox>
                </List.Item>
              )}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
