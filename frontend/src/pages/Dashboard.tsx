import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card, Row, Col, List, Drawer, Form, Button, Radio, Skeleton, Empty,
  Typography, theme, message, Input, DatePicker, Tooltip, Space, Flex,
} from 'antd';
import {
  PlusOutlined,
} from '@ant-design/icons';
import { StatCard } from '@/components/shared/StatCard';
import { StatusTag } from '@/components/shared/StatusTag';
import SourceSelector from '@/components/task/SourceSelector';
import { useDashboardStats, useRecentTasks, useRecentReports } from '@/api/dashboard';
import { useSuggestTaskName, useLastEndTime, useCreateTask } from '@/api/tasks';
import { useRssGroups, useRssSourceList } from '@/api/rssSources';
import { formatDateTime } from '@/utils/format';
import type { SourceType, SourceConfig } from '@/types';

const { Text } = Typography;

export default function Dashboard() {
  const { token } = theme.useToken();
  const navigate = useNavigate();

  const { data: stats, isLoading: statsLoading } = useDashboardStats();
  const { data: recentTasks = [], isLoading: tasksLoading } = useRecentTasks();
  const { data: recentReports = [], isLoading: reportsLoading } = useRecentReports();
  const { data: suggestName } = useSuggestTaskName();
  const { data: lastEndTime } = useLastEndTime();
  const createTask = useCreateTask();

  const { data: groups } = useRssGroups();
  const { data: sources } = useRssSourceList();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [form] = Form.useForm();
  const timeRange = Form.useWatch('timeRange', form);
  const [sourceType, setSourceType] = useState<SourceType>('ALL');
  const [sourceConfig, setSourceConfig] = useState<SourceConfig>({ groupIds: [], sourceIds: [] });

  // Auto-fill task name from suggestion when drawer opens
  useEffect(() => {
    if (drawerOpen && suggestName) {
      form.setFieldsValue({ name: suggestName });
    }
  }, [drawerOpen, suggestName, form]);

  const handleCreateTask = async () => {
    try {
      const values = await form.validateFields();
      const now = new Date();
      let timeRangeStart: string;
      let timeRangeEnd: string;

      if (values.timeRange === 'last') {
        if (!lastEndTime?.endedAt) {
          message.warning('暂无历史任务记录');
          return;
        }
        timeRangeStart = lastEndTime.endedAt;
        timeRangeEnd = now.toISOString();
      } else if (values.timeRange === 'custom') {
        timeRangeStart = values.customRange[0].toISOString();
        timeRangeEnd = values.customRange[1].toISOString();
      } else {
        const hours = values.timeRange === '1h' ? 1 : 6;
        timeRangeEnd = now.toISOString();
        timeRangeStart = new Date(now.getTime() - hours * 3600000).toISOString();
      }

      await createTask.mutateAsync({
        name: values.name,
        timeRangeStart,
        timeRangeEnd,
        sourceType,
        sourceConfig,
      });

      message.success('任务创建成功');
      setDrawerOpen(false);
      form.resetFields();
      setSourceType('ALL');
      setSourceConfig({ groupIds: [], sourceIds: [] });
    } catch {
      // handled by antd validation or axios interceptor
    }
  };

  return (
    <Flex vertical gap={token.marginLG}>
      {/* Create task button row */}
      <Flex justify="flex-end">
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setDrawerOpen(true)}
          size="large"
        >
          创建任务
        </Button>
      </Flex>

      {/* Stat cards */}
      <Row gutter={[16, 16]}>
        <Col span={8}>
          {statsLoading ? (
            <Card style={{ borderRadius: token.borderRadiusLG }}>
              <Skeleton active paragraph={false} />
            </Card>
          ) : (
            <div onClick={() => navigate('/tasks')} style={{ cursor: 'pointer' }}>
              <StatCard
                title="任务"
                today={stats?.taskCount?.today ?? 0}
                yesterday={stats?.taskCount?.yesterday ?? 0}
                change={stats?.taskCount?.change ?? 0}
                changePercent={stats?.taskCount?.changePercent ?? null}
              />
            </div>
          )}
        </Col>
        <Col span={8}>
          {statsLoading ? (
            <Card style={{ borderRadius: token.borderRadiusLG }}>
              <Skeleton active paragraph={false} />
            </Card>
          ) : (
            <div onClick={() => navigate('/reports')} style={{ cursor: 'pointer' }}>
              <StatCard
                title="报告"
                today={stats?.reportCount?.today ?? 0}
                yesterday={stats?.reportCount?.yesterday ?? 0}
                change={stats?.reportCount?.change ?? 0}
                changePercent={stats?.reportCount?.changePercent ?? null}
              />
            </div>
          )}
        </Col>
        <Col span={8}>
          {statsLoading ? (
            <Card style={{ borderRadius: token.borderRadiusLG }}>
              <Skeleton active paragraph={false} />
            </Card>
          ) : (
            <div onClick={() => navigate('/news')} style={{ cursor: 'pointer' }}>
              <StatCard
                title="新闻"
                today={stats?.newsCount?.today ?? 0}
                yesterday={stats?.newsCount?.yesterday ?? 0}
                change={stats?.newsCount?.change ?? 0}
                changePercent={stats?.newsCount?.changePercent ?? null}
              />
            </div>
          )}
        </Col>
      </Row>

      {/* Recent tasks + recent reports */}
      <Row gutter={[16, 16]}>
        <Col span={14}>
          <Card
            title={<Text strong>最近任务</Text>}
            style={{ borderRadius: token.borderRadiusLG }}
          >
            {tasksLoading ? (
              <Skeleton active />
            ) : recentTasks.length === 0 ? (
              <Empty description="暂无任务记录">
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setDrawerOpen(true)}
                >
                  创建第一个任务
                </Button>
              </Empty>
            ) : (
              <List
                dataSource={recentTasks.slice(0, 5)}
                renderItem={(task) => (
                  <List.Item
                    actions={[
                      task.status === 'COMPLETED' && task.reportId ? (
                        <Button
                          type="link"
                          size="small"
                          onClick={() => navigate(`/reports/${task.reportId}`)}
                        >
                          查看报告 →
                        </Button>
                      ) : (
                        <Button
                          type="link"
                          size="small"
                          onClick={() => navigate('/tasks')}
                        >
                          查看详情 →
                        </Button>
                      ),
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <Space>
                          <Text strong>{task.name}</Text>
                          <StatusTag status={task.status} />
                        </Space>
                      }
                      description={
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                          {formatDateTime(task.timeRangeStart)} ~ {formatDateTime(task.timeRangeEnd)}
                          <br />
                          开始: {formatDateTime(task.startedAt)}
                          {task.endedAt && ` | 结束: ${formatDateTime(task.endedAt)}`}
                        </Text>
                      }
                    />
                  </List.Item>
                )}
              />
            )}
            {recentTasks.length > 0 && (
              <div style={{ textAlign: 'center', marginTop: token.marginMD }}>
                <Button type="link" onClick={() => navigate('/tasks')}>
                  查看全部任务 →
                </Button>
              </div>
            )}
          </Card>
        </Col>

        <Col span={10}>
          <Card
            title={<Text strong>最近报告</Text>}
            style={{ borderRadius: token.borderRadiusLG }}
          >
            {reportsLoading ? (
              <Skeleton active />
            ) : recentReports.length === 0 ? (
              <Empty description="暂无报告记录" />
            ) : (
              <List
                dataSource={recentReports.slice(0, 3)}
                renderItem={(report) => (
                  <List.Item
                    actions={[
                      <Button
                        type="link"
                        size="small"
                        onClick={() => navigate(`/reports/${report.id}`)}
                        key="view"
                      >
                        查看 →
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={<Text strong>{report.name}</Text>}
                      description={
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                          {report.newsCount} 条新闻 | {formatDateTime(report.createdAt)}
                        </Text>
                      }
                    />
                  </List.Item>
                )}
              />
            )}
            {recentReports.length > 0 && (
              <div style={{ textAlign: 'center', marginTop: token.marginMD }}>
                <Button type="link" onClick={() => navigate('/reports')}>
                  查看全部报告 →
                </Button>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* Create task Drawer */}
      <Drawer
        title="创建任务"
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          form.resetFields();
        }}
        width={480}
        extra={
          <Space>
            <Button onClick={() => { setDrawerOpen(false); form.resetFields(); }}>取消</Button>
            <Button type="primary" onClick={handleCreateTask} loading={createTask.isPending}>
              提交
            </Button>
          </Space>
        }
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ timeRange: '1h' }}
        >
          <Form.Item
            label="任务名称"
            name="name"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder={suggestName || '输入任务名称'} />
          </Form.Item>

          <Form.Item label="时间范围" name="timeRange">
            <Radio.Group>
              <Radio.Button value="1h">1小时前</Radio.Button>
              <Radio.Button value="6h">6小时前</Radio.Button>
              <Tooltip
                title={
                  lastEndTime?.endedAt
                    ? `截止 ${formatDateTime(lastEndTime.endedAt)}`
                    : '暂无历史任务'
                }
              >
                <Radio.Button value="last" disabled={!lastEndTime?.endedAt}>
                  截止上次任务结束
                </Radio.Button>
              </Tooltip>
              <Radio.Button value="custom">自定义</Radio.Button>
            </Radio.Group>
          </Form.Item>

          {timeRange === 'custom' && (
            <Form.Item
              label="自定义时间范围"
              name="customRange"
              rules={[{ required: true, message: '请选择时间范围' }]}
            >
              <DatePicker.RangePicker
                showTime
                style={{ width: '100%' }}
              />
            </Form.Item>
          )}

          <Form.Item label="RSS 源选择">
            <SourceSelector
              value={{ sourceType, sourceConfig }}
              onChange={(val) => {
                setSourceType(val.sourceType);
                setSourceConfig(val.sourceConfig);
              }}
              groups={groups?.map((g) => ({ id: g.id, name: g.name })) ?? []}
              sources={sources?.map((s) => ({ id: s.id, name: s.name })) ?? []}
            />
          </Form.Item>
        </Form>
      </Drawer>
    </Flex>
  );
}
