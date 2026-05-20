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
  });
}

/**
 * Get the news list from a report's detail (extracts `news` from ReportWithNews).
 * This reuses the same query as useReport — no separate API call.
 */
export function useReportNews(reportId: number | null) {
  return useQuery({
    queryKey: ['reports', 'detail', reportId, 'news'],
    queryFn: () =>
      client
        .get<{ news: NewsSummary[] }>(`/reports/${reportId}`)
        .then((r) => r.data.news),
    enabled: !!reportId,
  });
}

export function useReportNewsDetail(reportId: number | null, newsId: number | null) {
  return useQuery({
    queryKey: ['reports', reportId, 'news', newsId],
    queryFn: () =>
      client.get<NewsDetail>(`/reports/${reportId}/news/${newsId}`).then((r) => r.data),
    enabled: !!reportId && !!newsId,
  });
}
