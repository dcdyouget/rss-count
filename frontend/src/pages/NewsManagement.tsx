import { useState, useMemo } from 'react';
import {
  Table, Input, Select, Button, theme,
  message, Space, Flex, Skeleton,
} from 'antd';
import {
  ArrowLeftOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import type { TableRowSelection } from 'antd/es/table/interface';
import NewsContent from '@/components/news/NewsContent';
import { formatDateTime } from '@/utils/format';
import { useNewsList, useBatchMaterialPile, useNewsDetail, useMarkRead } from '@/api/news';
import { useReportList } from '@/api/reports';
import type { NewsItem, NewsListParams } from '@/types';

export default function NewsManagement() {
  const { token } = theme.useToken();

  // View mode: list or detail (full-screen news view)
  const [viewMode, setViewMode] = useState<'list' | 'detail'>('list');

  // Filters
  const [params, setParams] = useState<NewsListParams>({
    page: 1,
    size: 20,
  });
  const [reportName, setReportName] = useState<string | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);

  // Data
  const { data: newsData, isLoading } = useNewsList(params);
  const { data: reportsData } = useReportList({ page: 1, size: 200 });
  const batchMut = useBatchMaterialPile();
  const markRead = useMarkRead();

  // Detail
  const [detailId, setDetailId] = useState<number | null>(null);
  const { data: detailData } = useNewsDetail(detailId);

  // Report name options
  const reportNameOptions = useMemo(() => {
    if (!reportsData?.items) return [];
    const names = new Set<string>();
    reportsData.items.forEach((r: { name: string }) => names.add(r.name));
    return Array.from(names).map((name) => ({ label: name, value: name }));
  }, [reportsData]);

  const handleSearch = (value: string) => {
    setParams((prev) => ({
      ...prev,
      keyword: value || undefined,
      page: 1,
    }));
  };

  const handleReportNameChange = (value?: string) => {
    setReportName(value);
    setParams((prev) => ({
      ...prev,
      reportName: value,
      page: 1,
    }));
  };

  const handleBatchAdd = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择新闻');
      return;
    }
    batchMut.mutateAsync(
      { newsIds: selectedRowKeys, action: 'ADD' },
    ).then(() => {
      message.success(`已将 ${selectedRowKeys.length} 条新闻加入素材堆`);
      setSelectedRowKeys([]);
    }).catch(() => {
      message.error('加入素材堆失败，请重试');
    });
  };

  const handleBack = () => {
    setViewMode('list');
    setDetailId(null);
  };

  const handleViewDetail = (record: NewsItem) => {
    setDetailId(record.id);
    setViewMode('detail');
  };

  const rowSelection: TableRowSelection<NewsItem> = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys as number[]),
  };

  const columns = [
    {
      title: '头图',
      dataIndex: 'headerImageHtml',
      key: 'headerImage',
      width: 60,
      render: (html: string | null) =>
        html ? (
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: token.borderRadiusSM,
              overflow: 'hidden',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            dangerouslySetInnerHTML={{ __html: html }}
          />
        ) : (
          <div
            style={{
              width: 40,
              height: 40,
              background: token.colorBgLayout,
              borderRadius: token.borderRadiusSM,
            }}
          />
        ),
    },
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
    },
    {
      title: '来源',
      dataIndex: 'sourceRssName',
      key: 'sourceRssName',
      width: 150,
      ellipsis: true,
    },
    {
      title: '所属报告',
      dataIndex: 'reportName',
      key: 'reportName',
      width: 200,
      ellipsis: true,
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 170,
      render: (t: string) => formatDateTime(t),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: NewsItem) => (
        <Button
          type="link"
          size="small"
          onClick={() => handleViewDetail(record)}
        >
          查看详情
        </Button>
      ),
    },
  ];

  // --- Full-screen detail view ---
  if (viewMode === 'detail') {
    return (
      <div>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={handleBack}
          style={{
            marginBottom: token.marginMD,
            color: token.colorTextSecondary,
          }}
        >
          返回列表
        </Button>
        {detailData ? (
          <NewsContent
            news={detailData}
            showSidebar
            onMarkRead={(nid) => markRead.mutate(nid)}
            onAddToMaterialPile={(nid) =>
              batchMut.mutate(
                { newsIds: [nid], action: 'ADD' },
                {
                  onSuccess: () => message.success('已加入素材堆'),
                  onError: () => message.error('加入素材堆失败'),
                },
              )
            }
          />
        ) : (
          <Skeleton active paragraph={{ rows: 6 }} />
        )}
      </div>
    );
  }

  // --- List view ---
  return (
    <Flex vertical gap={token.marginLG}>
      {/* Filter bar */}
      <Flex justify="space-between" align="center" wrap="wrap" gap={token.marginSM}>
        <Space>
          <Input.Search
            placeholder="搜索标题..."
            allowClear
            onSearch={handleSearch}
            style={{ width: 240 }}
          />
          <Select
            placeholder="报告筛选"
            allowClear
            value={reportName}
            onChange={handleReportNameChange}
            style={{ width: 180 }}
            options={reportNameOptions}
          />
        </Space>
        <Button
          type="primary"
          icon={<InboxOutlined />}
          onClick={handleBatchAdd}
          loading={batchMut.isPending}
        >
          加入素材堆{selectedRowKeys.length > 0 ? ` (${selectedRowKeys.length})` : ''}
        </Button>
      </Flex>

      {/* Table */}
      <Table
        rowSelection={rowSelection}
        columns={columns}
        dataSource={newsData?.items}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: params.page,
          pageSize: params.size,
          total: newsData?.total ?? 0,
          onChange: (page, size) =>
            setParams((prev) => ({ ...prev, page, size })),
          showSizeChanger: true,
          showTotal: (total: number) => `共 ${total} 条`,
        }}
      />
    </Flex>
  );
}
