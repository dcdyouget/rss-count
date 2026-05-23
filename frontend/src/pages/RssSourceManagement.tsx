import { useState, useMemo } from 'react';
import {
  Table, Button, Modal, Form, Input, Select, Transfer, Upload, Popconfirm,
  Typography, theme, message, Image, Avatar, Tabs, Space, Flex, Checkbox,
} from 'antd';
import {
  PlusOutlined, UploadOutlined, DownloadOutlined, SettingOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import {
  useRssSourceList, useCreateRssSource, useDeleteRssSource,
  useImportOpml, useExportOpml, useRssGroups, useCreateRssGroup,
  useDeleteRssGroup,
  useRssSourceSearch, useAddSourcesToGroup,
} from '@/api/rssSources';
import type { RssSource, RssGroup } from '@/types';

const { Text } = Typography;

export default function RssSourceManagement() {
  const { token } = theme.useToken();
  const [activeGroupId, setActiveGroupId] = useState<number | undefined>(undefined);

  const { data: sources = [], isLoading: sourcesLoading } = useRssSourceList(activeGroupId);
  const { data: groups = [], isLoading: groupsLoading } = useRssGroups();
  const createSource = useCreateRssSource();
  const deleteSource = useDeleteRssSource();
  const importOpml = useImportOpml();
  const exportOpml = useExportOpml();
  const createGroup = useCreateRssGroup();
  const deleteGroup = useDeleteRssGroup();

  // Search
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchPage, setSearchPage] = useState(1);

  // Manage sources modal
  const [manageModalOpen, setManageModalOpen] = useState(false);
  const [manageGroup, setManageGroup] = useState<RssGroup | null>(null);
  const [manageTargetKeys, setManageTargetKeys] = useState<string[]>([]);

  const keyword = searchKeyword.trim();
  const { data: searchResult, isLoading: searchLoading } = useRssSourceSearch(keyword, searchPage, 20, { enabled: !!keyword });
  const { data: allSources = [] } = useRssSourceList();
  const addSourcesToGroup = useAddSourcesToGroup();

  const displaySources = keyword ? (searchResult?.items ?? []) : sources;
  const tableLoading = keyword ? searchLoading : sourcesLoading;

  const transferDataSource = useMemo(() =>
    allSources.map(s => ({
      key: String(s.id),
      title: s.name || s.url,
    })),
    [allSources]
  );

  // Add source modal
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addForm] = Form.useForm();

  // Add group modal
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [groupForm] = Form.useForm();

  // Export modal
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportGroupIds, setExportGroupIds] = useState<number[]>([]);

  // Import result modal
  const [resultModalOpen, setResultModalOpen] = useState(false);
  const [importResult, setImportResult] = useState<{
    created: number;
    skipped: number;
    errors: string[];
    total: number;
  } | null>(null);

  const formatTime = (t: string | null) => {
    if (!t) return '从未';
    const d = new Date(t);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  };

  const handleAddSource = async () => {
    try {
      const values = await addForm.validateFields();
      await createSource.mutateAsync({
        url: values.url,
        name: values.name || undefined,
        groupIds: values.groupIds || undefined,
      });
      message.success('添加成功');
      setAddModalOpen(false);
      addForm.resetFields();
    } catch {
      // handled
    }
  };

  const handleDeleteSource = async (id: number) => {
    await deleteSource.mutateAsync(id);
    message.success('已删除');
  };

  const handleDeleteGroup = async (group: RssGroup) => {
    await deleteGroup.mutateAsync(group.id);
    message.success(`分组 "${group.name}" 已删除`);
    if (activeGroupId === group.id) {
      setActiveGroupId(undefined);
    }
  };

  const handleAddGroup = async () => {
    try {
      const values = await groupForm.validateFields();
      await createGroup.mutateAsync({ name: values.name });
      message.success('分组已创建');
      setGroupModalOpen(false);
      groupForm.resetFields();
    } catch {
      // handled
    }
  };

  const handleSearch = (value: string) => {
    setSearchKeyword(value);
    setSearchPage(1);
  };

  const handleOpenManageSources = (group: RssGroup) => {
    setManageGroup(group);
    setManageTargetKeys(
      allSources
        .filter(s => s.groupIds.includes(group.id))
        .map(s => String(s.id))
    );
    setManageModalOpen(true);
  };

  const handleSaveManageSources = async () => {
    if (!manageGroup) return;
    await addSourcesToGroup.mutateAsync({
      groupId: manageGroup.id,
      sourceIds: manageTargetKeys.map(Number),
    });
    message.success('分组源已更新');
    setManageModalOpen(false);
  };

  const columns = [
    {
      title: '',
      dataIndex: 'iconPath',
      key: 'icon',
      width: 48,
      render: (iconPath: string | null) =>
        iconPath ? (
          <Image src={iconPath} width={24} height={24} preview={false} style={{ borderRadius: 4 }} />
        ) : (
          <Avatar size={24} shape="square" style={{ fontSize: 12 }}>
            RSS
          </Avatar>
        ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: '添加时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (t: string) => formatTime(t),
    },
    {
      title: '最近拉取',
      dataIndex: 'lastFetchAt',
      key: 'lastFetchAt',
      width: 170,
      render: (t: string | null) => (
        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          {formatTime(t)}
        </Text>
      ),
    },
    {
      title: '拉取总数',
      dataIndex: 'totalFetched',
      key: 'totalFetched',
      width: 100,
      align: 'right' as const,
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: RssSource) => (
        <Popconfirm
          title="确定删除？"
          description="历史新闻不受影响"
          onConfirm={() => handleDeleteSource(record.id)}
          okText="确定"
          cancelText="取消"
        >
          <Button type="link" danger size="small">
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const uploadProps: UploadProps = {
    accept: '.opml,.xml',
    showUploadList: false,
    beforeUpload: (file) => {
      importOpml.mutateAsync(file).then((result) => {
        setImportResult(result);
        setResultModalOpen(true);
      });
      return false;
    },
  };

  return (
    <Flex vertical gap={token.marginLG}>
      {/* Header */}
      <Flex justify="space-between" align="center">
        <Text strong style={{ fontSize: token.fontSizeHeading3 }}>
          RSS 源管理
        </Text>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setAddModalOpen(true)}
          >
            添加源
          </Button>
          <Upload {...uploadProps}>
            <Button icon={<UploadOutlined />} loading={importOpml.isPending}>
              导入 OPML
            </Button>
          </Upload>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => {
              setExportGroupIds([]);
              setExportModalOpen(true);
            }}
            loading={exportOpml.isPending}
          >
            导出 OPML
          </Button>
        </Space>
      </Flex>

      {/* Search */}
      <Input.Search
        placeholder="搜索RSS源..."
        allowClear
        onSearch={handleSearch}
        style={{ marginBottom: 16, maxWidth: 400 }}
      />

      {/* Group tabs */}
      <Tabs
        activeKey={activeGroupId === undefined ? 'all' : String(activeGroupId)}
        onChange={(key) => {
          if (key === 'add-group') {
            setGroupModalOpen(true);
            return;
          }
          setActiveGroupId(key === 'all' ? undefined : Number(key));
        }}
        items={[
          { key: 'all', label: '全部' },
          ...groups.map((g: RssGroup) => ({
            key: String(g.id),
            label: (
              <span>
                {g.name} ({g.sourceCount})
                <Popconfirm
                  title="确定删除分组？"
                  description={`删除分组 "${g.name}" 不会删除其中的源`}
                  onConfirm={() => handleDeleteGroup(g)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<CloseOutlined />}
                    onClick={(e) => e.stopPropagation()}
                    style={{ marginLeft: 2 }}
                  />
                </Popconfirm>
                <Button
                  type="text"
                  size="small"
                  icon={<SettingOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleOpenManageSources(g);
                  }}
                  style={{ marginLeft: 4 }}
                />
              </span>
            ),
          })),
          { key: 'add-group', label: '+', disabled: groupsLoading },
        ]}
      />

      {/* Table */}
      <Table
        columns={columns}
        dataSource={displaySources}
        rowKey="id"
        loading={tableLoading}
        pagination={keyword ? {
          pageSize: 20,
          current: searchPage,
          total: searchResult?.total ?? 0,
          onChange: (page: number) => setSearchPage(page),
          showTotal: (total: number) => `共 ${total} 条`,
        } : {
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (total: number) => `共 ${total} 条`,
        }}
      />

      {/* Add source modal */}
      <Modal
        title="添加 RSS 源"
        open={addModalOpen}
        onCancel={() => {
          setAddModalOpen(false);
          addForm.resetFields();
        }}
        onOk={handleAddSource}
        confirmLoading={createSource.isPending}
      >
        <Form form={addForm} layout="vertical">
          <Form.Item
            label="RSS 地址"
            name="url"
            rules={[
              { required: true, message: '请输入 RSS 地址' },
              { type: 'url', message: '请输入有效的 URL' },
            ]}
          >
            <Input placeholder="https://example.com/rss" />
          </Form.Item>
          <Form.Item label="名称（可选）" name="name">
            <Input placeholder="留空则自动获取" />
          </Form.Item>
          <Form.Item label="分组" name="groupIds">
            <Select
              mode="multiple"
              placeholder="选择分组"
              options={groups.map((g: RssGroup) => ({
                label: g.name,
                value: g.id,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Add group modal */}
      <Modal
        title="新建分组"
        open={groupModalOpen}
        onCancel={() => {
          setGroupModalOpen(false);
          groupForm.resetFields();
        }}
        onOk={handleAddGroup}
        confirmLoading={createGroup.isPending}
      >
        <Form form={groupForm} layout="vertical">
          <Form.Item
            label="分组名称"
            name="name"
            rules={[{ required: true, message: '请输入分组名称' }]}
          >
            <Input placeholder="输入分组名称" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Import result modal */}
      <Modal
        title="导入结果"
        open={resultModalOpen}
        onCancel={() => setResultModalOpen(false)}
        footer={<Button onClick={() => setResultModalOpen(false)}>确定</Button>}
      >
        {importResult && (
          <div>
            <p>创建：{importResult.created} 个</p>
            <p>跳过：{importResult.skipped} 个</p>
            <p>总计：{importResult.total} 个</p>
            {importResult.errors.length > 0 && (
              <div style={{ marginTop: token.marginSM }}>
                <Text type="danger" style={{ fontSize: token.fontSizeSM }}>
                  错误：
                </Text>
                {importResult.errors.map((err: string, i: number) => (
                  <p key={i}>
                    <Text type="danger" style={{ fontSize: token.fontSizeSM }}>
                      {err}
                    </Text>
                  </p>
                ))}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Export modal */}
      <Modal
        title="选择导出分组"
        open={exportModalOpen}
        onCancel={() => setExportModalOpen(false)}
        onOk={() => {
          exportOpml.mutate(exportGroupIds.length > 0 ? exportGroupIds : undefined);
          setExportModalOpen(false);
        }}
        okText="导出"
        cancelText="取消"
      >
        <div style={{ marginBottom: 12 }}>
          <Checkbox
            checked={exportGroupIds.length === 0}
            onChange={(e) => {
              if (e.target.checked) setExportGroupIds([]);
            }}
          >
            全部（不分组的也会包含）
          </Checkbox>
        </div>
        <Checkbox.Group
          value={exportGroupIds}
          onChange={(values) => setExportGroupIds(values as number[])}
        >
          <Flex wrap="wrap" gap={8}>
            {groups.map((g: RssGroup) => (
              <Checkbox key={g.id} value={g.id}>
                {g.name} ({g.sourceCount})
              </Checkbox>
            ))}
          </Flex>
        </Checkbox.Group>
      </Modal>

      {/* Manage sources modal */}
      <Modal
        title={manageGroup ? `管理分组源：${manageGroup.name}` : '管理分组源'}
        open={manageModalOpen}
        onCancel={() => {
          setManageModalOpen(false);
          setManageGroup(null);
          setManageTargetKeys([]);
        }}
        onOk={handleSaveManageSources}
        confirmLoading={addSourcesToGroup.isPending}
        width={640}
      >
        <Transfer
          dataSource={transferDataSource}
          targetKeys={manageTargetKeys}
          onChange={(nextKeys) => setManageTargetKeys(nextKeys as string[])}
          render={(item) => item.title}
          titles={['全部源', '已选源']}
          listStyle={{ width: 270, height: 400 }}
          showSearch
          filterOption={(inputValue, item) =>
            item.title.toLowerCase().includes(inputValue.toLowerCase())
          }
        />
      </Modal>
    </Flex>
  );
}
