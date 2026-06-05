import { useQuery } from '@tanstack/react-query';
import client from './client';
import type {
  Report,
  ReportWithNews,
  ReportListParams,
  NewsSummary,
  NewsDetail,
  PaginatedResponse,
} from '@/types';

export function useReportList(params: ReportListParams = {}) {
  return useQuery({
    queryKey: ['reports', 'list', params],
    queryFn: () =>
      client.get<PaginatedResponse<Report>>('/reports', { params }).then((r) => r.data),
    staleTime: 30 * 1000,
  });
}

/** @deprecated use useReportList instead */
export function useReports(params: ReportListParams = {}) {
  return useReportList(params);
}

export function useReport(id: number | null) {
  return useQuery({
    queryKey: ['reports', 'detail', id],
    queryFn: () => client.get<ReportWithNews>(`/reports/${id}`).then((r) => r.data),
    enabled: !!id,
    staleTime: 30 * 1000,
  });
}

/**
 * Fetch paginated news summary list under a report.
 *
 * Currently the backend returns all news at once; client-side pagination
 * is applied via `select` with the requested `page`/`size`. When the
 * backend adds pagination to `GET /reports/:id/news`, update the
 * `queryKey` and `queryFn` while keeping the return type unchanged.
 */
export function useReportNews(
  reportId: number | null,
  params?: { page?: number; size?: number },
) {
  const page = params?.page ?? 1;
  const size = params?.size ?? 12;

  return useQuery({
    queryKey: ['reports', 'detail', reportId, 'all-news'],
    queryFn: () =>
      client
        .get<{ news: NewsSummary[] }>(`/reports/${reportId}`)
        .then((r) => r.data.news),
    enabled: !!reportId,
    staleTime: 30 * 1000,
    select: (allNews) => {
      const total = allNews.length;
      const start = (page - 1) * size;
      const items = allNews.slice(start, start + size);
      return { items, total, page, size };
    },
  });
}

export function useReportNewsDetail(reportId: number | null, newsId: number | null) {
  return useQuery({
    queryKey: ['reports', reportId, 'news', newsId],
    queryFn: () =>
      client.get<NewsDetail>(`/reports/${reportId}/news/${newsId}`).then((r) => r.data),
    enabled: !!reportId && !!newsId,
    staleTime: 30 * 1000,
  });
}
