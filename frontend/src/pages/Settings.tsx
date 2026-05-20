import { useEffect } from 'react';
import {
  Card, Form, Input, InputNumber, Select, Button, Typography, theme, message, Spin,
} from 'antd';
import { useSettings, useUpdateSettings } from '@/api/settings';
import { useRssGroups } from '@/api/rssSources';

const { Text } = Typography;

export default function Settings() {
  const { token } = theme.useToken();
  const [form] = Form.useForm();
  const { data: settings, isLoading: settingsLoading } = useSettings();
  const { data: groups = [] } = useRssGroups();
  const updateSettings = useUpdateSettings();

  useEffect(() => {
    if (settings) {
      form.setFieldsValue({
        taskIntervalHours: settings.taskIntervalHours,
        aiApiUrl: settings.aiApiUrl,
        aiApiKey: settings.aiApiKey,
        aiModel: settings.aiModel,
        defaultGroupId: settings.defaultGroupId,
      });
    }
  }, [settings, form]);

  const handleFinish = (values: {
    aiApiUrl: string;
    aiApiKey: string;
    aiModel: string;
    taskIntervalHours: number;
    defaultGroupId: number | null;
  }) => {
    updateSettings.mutate(values, {
      onSuccess: () => message.success('设置已保存'),
    });
  };

  if (settingsLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 720 }}>
      <Text strong style={{ fontSize: token.fontSizeHeading3, display: 'block', marginBottom: token.marginLG }}>
        设置
      </Text>

      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        scrollToFirstError
      >
        {/* AI Configuration */}
        <Card
          title={<Text strong>AI 配置</Text>}
          style={{ borderRadius: token.borderRadiusLG, marginBottom: token.marginLG }}
        >
          <Form.Item
            label="API URL"
            name="aiApiUrl"
            rules={[{ required: true, message: '请输入 API URL' }]}
          >
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>

          <Form.Item
            label="API Key"
            name="aiApiKey"
            rules={[{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password
              placeholder="sk-..."
              visibilityToggle
            />
          </Form.Item>

          <Form.Item
            label="模型"
            name="aiModel"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input placeholder="gpt-4o" />
          </Form.Item>
        </Card>

        {/* Task Settings */}
        <Card
          title={<Text strong>任务设置</Text>}
          style={{ borderRadius: token.borderRadiusLG, marginBottom: token.marginLG }}
        >
          <Form.Item
            label="定时间隔（小时）"
            name="taskIntervalHours"
            rules={[{ required: true, message: '请输入间隔小时数' }]}
          >
            <InputNumber min={0} max={168} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            label="默认分组"
            name="defaultGroupId"
          >
            <Select
              allowClear
              placeholder="请选择默认分组"
              options={groups.map((g) => ({ label: g.name, value: g.id }))}
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={updateSettings.isPending}
            >
              保存设置
            </Button>
          </Form.Item>
        </Card>
      </Form>
    </div>
  );
}
