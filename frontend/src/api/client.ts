import axios from 'axios';

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
client.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 响应拦截器
client.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response;
      switch (status) {
        case 400:
          console.error(data?.message || '请求参数有误');
          break;
        case 404:
          console.error('请求的资源不存在');
          break;
        case 409:
          console.error(data?.message || '资源冲突');
          break;
        case 422:
          console.error(data?.message || '请求参数无效');
          break;
        case 500:
          console.error('服务器内部错误');
          break;
        case 502:
          console.error('AI 服务不可用');
          break;
        default:
          console.error(data?.message || `请求失败 (${status})`);
      }
    } else if (error.request) {
      console.error('网络连接失败，请检查网络');
    } else {
      console.error('请求配置错误');
    }
    return Promise.reject(error);
  },
);

export default client;
