import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table, Button, Select, DatePicker, Drawer, Form, Input, Radio,
  Space, message, theme, Empty,
} from 'antd';
import { PlusOutlined, EyeOutlined, FileTextOutlined } from '@ant-design/icons';
import type { Task, TaskStatus, CreateTaskRequest } from '@/types';
import { useTaskList, useCreateTask, useSuggestTaskName, useLastEndTime } from '@/api/tasks';
import { useRssSourceList, useRssGroups } from '@/api/rssSources';
import { StatusTag } from '@/components/shared/StatusTag';
import SourceSelector from '@/components/task/SourceSelector';
import { formatDateTime, formatTimeRange } from '@/utils/format';
import { TIME_PRESETS, DEFAULT_PAGE_SIZE } from '@/utils/constants';
import dayjs from 'dayjs';

export default function TaskManagement() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const [form] = Form.useForm();

  const [statusFilter, setStatusFilter] = useState<TaskStatus | undefined>();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [page, setPage] = useState(1);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [timePreset, setTimePreset] = useState<string>('1h');

  const { data: taskData, isLoading } = useTaskList({
    page,
    size: DEFAULT_PAGE_SIZE,
    status: statusFilter,
    createdAfter: dateRange?.[0]?.tz('Asia/Shanghai')?.format('YYYY-MM-DDTHH:mm:ss'),
    createdBefore: dateRange?.[1]?.tz('Asia/Shanghai')?.format('YYYY-MM-DDTHH:mm:ss'),
  });
  const { data: suggestName } = useSuggestTaskName();
  const { data: lastEndTime } = useLastEndTime();
  const { data: groups } = useRssGroups();
  const { data: sources } = useRssSourceList();
  const createTask = useCreateTask();

  const handleOpenDrawer = () => {
    form.resetFields();
    form.setFieldsValue({
      name: suggestName ?? '',
      sourceSelector: { sourceType: 'ALL', sourceConfig: { groupIds: [], sourceIds: [] } },
    });
    setTimePreset('1h');
    setDrawerOpen(true);
  };

  const handleCreateTask = async (values: {
    name: string;
    sourceSelector?: { sourceType: CreateTaskRequest['sourceType']; sourceConfig?: CreateTaskRequest['sourceConfig'] };
    customRange?: [dayjs.Dayjs, dayjs.Dayjs];
  }) => {
    const now = dayjs().tz('Asia/Shanghai');
    let start: string;
    let end: string = now.format('YYYY-MM-DDTHH:mm:ss');

    if (timePreset === '1h') {
      start = now.subtract(1, 'hour').format('YYYY-MM-DDTHH:mm:ss');
    } else if (timePreset === '6h') {
      start = now.subtract(6, 'hour').format('YYYY-MM-DDTHH:mm:ss');
    } else if (timePreset === 'last_end') {
      if (lastEndTime?.endedAt) {
        start = lastEndTime.endedAt;
      } else {
        message.warning('暂无历史任务记录，请选择其他时间范围');
        return;
      }
    } else if (timePreset === 'custom') {
      if (!values.customRange) {
        message.warning('请选择自定义时间范围');
        return;
      }
      start = values.customRange[0].tz('Asia/Shanghai').format('YYYY-MM-DDTHH:mm:ss');
      end = values.customRange[1].tz('Asia/Shanghai').format('YYYY-MM-DDTHH:mm:ss');
    } else {
      start = dayjs().subtract(1, 'hour').toISOString();
    }

    const payload: CreateTaskRequest = {
      name: values.name,
      timeRangeStart: start,
      timeRangeEnd: end,
      sourceType: values.sourceSelector?.sourceType ?? 'ALL',
      sourceConfig: values.sourceSelector?.sourceConfig,
    };

    try {
      await createTask.mutateAsync(payload);
      message.success('任务创建成功');
      setDrawerOpen(false);
    } catch {
      // error handled by axios interceptor
    }
  };

  const columns = [
    {
      title: '任务名',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: '时间范围',
      key: 'timeRange',
      render: (_: unknown, record: Task) =>
        formatTimeRange(record.timeRangeStart, record.timeRangeEnd),
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: TaskStatus) => <StatusTag status={status} />,
      width: 110,
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (v: string) => formatDateTime(v),
      width: 170,
    },
    {
      title: '结束时间',
      dataIndex: 'endedAt',
      key: 'endedAt',
      render: (v: string | undefined) => (v ? formatDateTime(v) : '--'),
      width: 170,
    },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      render: (_: unknown, record: Task) => (
        <Space>
          {record.status === 'COMPLETED' && record.reportId && (
            <Button
              type="link"
              size="small"
              icon={<FileTextOutlined />}
              onClick={() => navigate(`/reports/${record.reportId}`)}
            >
              查看报告
            </Button>
          )}
          {record.status !== 'COMPLETED' && (
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/tasks/${record.id}`)}
            >
              查看详情
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: token.marginLG,
        }}
      >
        <h2 style={{ margin: 0, fontSize: token.fontSizeHeading2, fontWeight: 600 }}>
          任务管理
        </h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenDrawer}>
          创建任务
        </Button>
      </div>

      <Space style={{ marginBottom: token.marginMD }} wrap>
        <Select
          placeholder="状态筛选"
          allowClear
          style={{ width: 140 }}
          value={statusFilter}
          onChange={(val) => {
            setStatusFilter(val);
            setPage(1);
          }}
          options={[
            { label: '进行中', value: 'RUNNING' },
            { label: '已完成', value: 'COMPLETED' },
            { label: '失败', value: 'FAILED' },
          ]}
        />
        <DatePicker.RangePicker
          value={dateRange}
          onChange={(dates) => {
            setDateRange(dates as [dayjs.Dayjs, dayjs.Dayjs] | null);
            setPage(1);
          }}
        />
      </Space>

      <Table
        dataSource={taskData?.items}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        pagination={{
          current: page,
          pageSize: DEFAULT_PAGE_SIZE,
          total: taskData?.total,
          onChange: setPage,
          showSizeChanger: false,
        }}
        locale={{ emptyText: <Empty description="暂无任务" /> }}
      />

      <Drawer
        title="创建任务"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateTask}
          initialValues={{
            name: '',
            sourceSelector: {
              sourceType: 'ALL',
              sourceConfig: { groupIds: [], sourceIds: [] },
            },
          }}
        >
          <Form.Item
            label="任务名称"
            name="name"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder="输入任务名称或使用推荐名称" />
          </Form.Item>

          <Form.Item label="时间范围">
            <div>
              <Radio.Group
                value={timePreset}
                onChange={(e) => setTimePreset(e.target.value)}
              >
                {TIME_PRESETS.map((p) => (
                  <Radio.Button
                    key={p.value}
                    value={p.value}
                    disabled={p.value === 'last_end' && !lastEndTime?.endedAt}
                  >
                    {p.label}
                  </Radio.Button>
                ))}
              </Radio.Group>
              {timePreset === 'custom' && (
                <Form.Item name="customRange" style={{ marginTop: token.marginSM, marginBottom: 0 }}>
                  <DatePicker.RangePicker
                    showTime
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              )}
              {timePreset === 'last_end' && lastEndTime?.endedAt && (
                <div
                  style={{
                    fontSize: token.fontSizeSM,
                    color: token.colorTextSecondary,
                    marginTop: token.marginXS,
                  }}
                >
                  截止 {formatDateTime(lastEndTime.endedAt)}
                </div>
              )}
              {timePreset === 'last_end' && !lastEndTime?.endedAt && (
                <div
                  style={{
                    fontSize: token.fontSizeSM,
                    color: token.colorTextTertiary,
                    marginTop: token.marginXS,
                  }}
                >
                  暂无历史任务记录
                </div>
              )}
            </div>
          </Form.Item>

          <Form.Item label="RSS 源" name="sourceSelector">
            <SourceSelector
              groups={
                groups?.map((g) => ({ id: g.id, name: g.name })) ?? []
              }
              sources={
                sources?.map((s) => ({ id: s.id, name: s.name })) ?? []
              }
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={createTask.isPending}
              block
              size="large"
            >
              创建
            </Button>
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}
