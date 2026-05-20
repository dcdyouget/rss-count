import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Button, theme, Empty } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import type { Report } from '@/types';
import { useReportList } from '@/api/reports';
import { formatDateTime, formatTimeRange } from '@/utils/format';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';

export default function ReportManagement() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);

  const { data, isLoading } = useReportList({
    page,
    size: DEFAULT_PAGE_SIZE,
  });

  const columns = [
    {
      title: '报告名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: '时间范围',
      key: 'timeRange',
      render: (_: unknown, record: Report) => {
        if (record.timeRangeStart && record.timeRangeEnd) {
          return formatTimeRange(record.timeRangeStart, record.timeRangeEnd);
        }
        return '--';
      },
      ellipsis: true,
    },
    {
      title: '新闻数',
      dataIndex: 'newsCount',
      key: 'newsCount',
      width: 100,
      align: 'center' as const,
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: '生成时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: Report) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => navigate(`/reports/${record.id}`)}
        >
          查看
        </Button>
      ),
    },
  ];

  return (
    <div>
      <h2
        style={{
          margin: 0,
          marginBottom: token.marginLG,
          fontSize: token.fontSizeHeading2,
          fontWeight: 600,
        }}
      >
        报告管理
      </h2>

      <Table
        dataSource={data?.items}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: page,
          pageSize: DEFAULT_PAGE_SIZE,
          total: data?.total,
          onChange: setPage,
          showSizeChanger: false,
        }}
        locale={{ emptyText: <Empty description="暂无报告" /> }}
      />
    </div>
  );
}
