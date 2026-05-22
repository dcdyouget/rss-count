import { useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Typography, Table, Button, Timeline, Skeleton, Alert,
  Card, Empty, theme, Flex,
} from 'antd';
import {
  FileTextOutlined, ArrowLeftOutlined,
} from '@ant-design/icons';
import { useTask } from '@/api/tasks';
import { useSSE } from '@/hooks/useSSE';
import { StatusTag } from '@/components/shared/StatusTag';
import ThreeStepProgress from '@/components/task/ThreeStepProgress';
import NewsCardGrid from '@/components/news/NewsCardGrid';
import { formatDateTime, formatTimeRange } from '@/utils/format';

const { Title, Text } = Typography;

export default function TaskDetail() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const taskId = id ? Number(id) : null;

  const logEndRef = useRef<HTMLDivElement>(null);

  const { data: task, isLoading } = useTask(taskId);

  const isRunning = task?.status === 'RUNNING';
  const sseTaskId = isRunning ? taskId : null;
  const sse = useSSE(sseTaskId);

  const currentPhase: 'FETCHING' | 'FORMATTING' | undefined =
    sse.progress && !sse.progress.pulling.done
      ? 'FETCHING'
      : sse.progress && !sse.progress.formatting.done
        ? 'FORMATTING'
        : undefined;

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [sse.logs]);

  const sourceColumns = [
    { title: '源名称', dataIndex: 'name', key: 'name' },
    { title: '获取新闻数', dataIndex: 'fetchedCount', key: 'fetchedCount', width: 120 },
  ];

  if (isLoading) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }

  if (!task) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Empty description="任务不存在" />
      </div>
    );
  }

  return (
    <div>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/tasks')}
        style={{ marginBottom: token.marginMD, color: token.colorTextSecondary }}
      >
        返回任务列表
      </Button>

      <Flex vertical gap={token.marginSM} style={{ marginBottom: token.marginLG }}>
        <Flex align="center" gap={token.marginSM}>
          <Title level={2} style={{ margin: 0 }}>{task.name}</Title>
          <StatusTag status={task.status} />
        </Flex>
        <Text style={{ color: token.colorTextSecondary }}>
          {formatTimeRange(task.timeRangeStart, task.timeRangeEnd)}
        </Text>
        <Flex gap={token.marginLG} wrap="wrap">
          <Text style={{ color: token.colorTextTertiary, fontSize: token.fontSizeSM }}>
            开始时间：{formatDateTime(task.startedAt)}
          </Text>
          {task.endedAt && (
            <Text style={{ color: token.colorTextTertiary, fontSize: token.fontSizeSM }}>
              结束时间：{formatDateTime(task.endedAt)}
            </Text>
          )}
        </Flex>
      </Flex>

      {task.status === 'RUNNING' && (
        <>
          {/* SSE Connection indicator */}
          {!sse.isConnected && !sse.isComplete && (
            <Alert
              message="连接断开，正在重连..."
              type="warning"
              showIcon
              style={{ marginBottom: token.marginMD, borderRadius: token.borderRadius }}
            />
          )}
          {sse.error && (
            <Alert
              message={sse.error}
              type="error"
              showIcon
              style={{ marginBottom: token.marginMD, borderRadius: token.borderRadius }}
            />
          )}

          {/* Progress */}
          <Card
            style={{
              marginBottom: token.marginLG,
              borderRadius: token.borderRadiusLG,
            }}
          >
            <ThreeStepProgress status="RUNNING" currentPhase={currentPhase} />

            {/* Real-time status */}
            <div
              style={{
                padding: token.paddingMD,
                background: token.colorBgLayout,
                borderRadius: token.borderRadius,
                marginTop: token.marginSM,
              }}
            >
              {sse.progress && (
                <Flex vertical gap={token.marginXS}>
                  {sse.progress.pulling.currentSource && (
                    <Text style={{ fontSize: token.fontSizeSM }}>
                      正在拉取：{sse.progress.pulling.currentSource}
                      {sse.progress.pulling.sourceProgress
                        ? ` (${sse.progress.pulling.sourceProgress})`
                        : ''}
                    </Text>
                  )}
                  {sse.progress.pulling.totalFetched !== undefined && (
                    <Text style={{ fontSize: token.fontSizeSM }}>
                      已获取新闻：{sse.progress.pulling.totalFetched} 篇
                    </Text>
                  )}
                  {sse.progress.formatting.currentAction && (
                    <Text style={{ fontSize: token.fontSizeSM }}>
                      {!sse.progress.pulling.done
                        ? `${sse.progress.formatting.currentAction} ${sse.progress.pulling.sourceProgress ?? ''}`
                        : `${sse.progress.formatting.currentAction} (${sse.progress.formatting.formatted ?? 0}${sse.progress.formatting.total ? ` / ${sse.progress.formatting.total}` : ''})`
                      }
                    </Text>
                  )}
                </Flex>
              )}
            </div>
          </Card>

          {/* Log timeline */}
          <Card
            title="执行日志"
            size="small"
            style={{
              borderRadius: token.borderRadiusLG,
              marginBottom: token.marginLG,
            }}
          >
            {sse.logs.length > 0 ? (
              <div style={{ maxHeight: 300, overflow: 'auto' }}>
                <Timeline
                  items={sse.logs.map((log) => ({
                    children: (
                      <Text style={{ fontSize: token.fontSizeSM }}>{log}</Text>
                    ),
                    color: log.includes('错误')
                      ? 'red'
                      : log.includes('完成')
                        ? 'green'
                        : 'blue',
                  }))}
                />
                <div ref={logEndRef} />
              </div>
            ) : (
              <Empty
                description="等待日志..."
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </Card>
        </>
      )}

      {task.status === 'COMPLETED' && (
        <>
          <Card
            style={{
              marginBottom: token.marginLG,
              borderRadius: token.borderRadiusLG,
            }}
          >
            <ThreeStepProgress status="COMPLETED" />

            {sse.reportId && (
              <div style={{ textAlign: 'center', marginTop: token.marginMD }}>
                <Button
                  type="primary"
                  size="large"
                  icon={<FileTextOutlined />}
                  onClick={() =>
                    navigate(`/reports/${sse.reportId}`)
                  }
                >
                  查看报告
                </Button>
              </div>
            )}

            {!sse.reportId && task.reportId && (
              <div style={{ textAlign: 'center', marginTop: token.marginMD }}>
                <Button
                  type="primary"
                  size="large"
                  icon={<FileTextOutlined />}
                  onClick={() =>
                    navigate(`/reports/${task.reportId}`)
                  }
                >
                  查看报告
                </Button>
              </div>
            )}
          </Card>

          {/* Sources table */}
          {task.sources && task.sources.length > 0 && (
            <Card
              title="源拉取概况"
              size="small"
              style={{
                borderRadius: token.borderRadiusLG,
                marginBottom: token.marginLG,
              }}
            >
              <Table
                dataSource={task.sources}
                columns={sourceColumns}
                rowKey="id"
                pagination={false}
                size="small"
                locale={{
                  emptyText: <Empty description="暂无源数据" />,
                }}
              />
            </Card>
          )}

          {/* News list */}
          {task.news && task.news.length > 0 && (
            <div>
              <Title level={4} style={{ marginBottom: token.marginMD }}>
                获取的新闻 ({task.news.length})
              </Title>
              <NewsCardGrid news={task.news} />
            </div>
          )}

          {(!task.news || task.news.length === 0) && (
            <Empty description="暂无新闻数据" />
          )}
        </>
      )}

      {task.status === 'FAILED' && (
        <>
          <Card
            style={{
              marginBottom: token.marginLG,
              borderRadius: token.borderRadiusLG,
            }}
          >
            <ThreeStepProgress status="FAILED" />
          </Card>

          {task.errorMessage && (
            <Alert
              message="任务失败"
              description={task.errorMessage}
              type="error"
              showIcon
              style={{ borderRadius: token.borderRadius }}
            />
          )}

          {!task.errorMessage && (
            <Alert
              message="任务执行失败"
              description="任务在執行过程中遇到错误，请检查配置后重试。"
              type="error"
              showIcon
              style={{ borderRadius: token.borderRadius }}
            />
          )}
        </>
      )}
    </div>
  );
}
