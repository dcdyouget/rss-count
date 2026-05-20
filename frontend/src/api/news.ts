import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type {
  NewsItem,
  NewsDetail,
  NewsListParams,
  PaginatedResponse,
  BatchMaterialPileRequest,
  BatchMaterialPileResponse,
  MaterialPileItem,
} from '@/types';

export function useNewsList(params: NewsListParams = {}) {
  return useQuery({
    queryKey: ['news', 'list', params],
    queryFn: () =>
      client.get<PaginatedResponse<NewsItem>>('/news', { params }).then((r) => r.data),
  });
}

/** @deprecated use useNewsList instead */
export function useNews(params: NewsListParams = {}) {
  return useNewsList(params);
}

export function useNewsDetail(id: number | null) {
  return useQuery({
    queryKey: ['news', 'detail', id],
    queryFn: () => client.get<NewsDetail>(`/news/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      client.put(`/news/${id}/read`).then((r) => r.data),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['news', 'detail', id] });
      queryClient.invalidateQueries({ queryKey: ['news', 'list'] });
    },
  });
}

export function useBatchMaterialPile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: BatchMaterialPileRequest) =>
      client
        .post<BatchMaterialPileResponse>('/news/batch-material-pile', data)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] });
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useMaterialPile(params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ['news', 'material-pile', params],
    queryFn: () =>
      client
        .get<{ total: number; items: MaterialPileItem[] }>('/news/material-pile', { params })
        .then((r) => r.data),
  });
}
