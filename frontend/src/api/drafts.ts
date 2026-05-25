import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type {
  DraftVersion,
  DraftWithNews,
  DraftListSummary,
  DraftListParams,
  CreateDraftRequest,
  UpdateDraftRequest,
  GenerateResponse,
  MaterialPileItem,
} from '@/types';

export function useDraftList(params: DraftListParams = {}) {
  return useQuery({
    queryKey: ['drafts', 'list', params],
    queryFn: () =>
      client
        .get<{ total: number; items: DraftListSummary[] }>('/drafts', { params })
        .then((r) => r.data),
  });
}

/** @deprecated use useDraftList instead */
export function useDrafts(params: DraftListParams = {}) {
  return useDraftList(params);
}

export function useDraft(id: number | null) {
  return useQuery({
    queryKey: ['drafts', 'detail', id],
    queryFn: () => client.get<DraftWithNews>(`/drafts/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateDraftRequest) =>
      client.post<DraftWithNews>('/drafts', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useUpdateDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateDraftRequest }) =>
      client.put<DraftWithNews>(`/drafts/${id}`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useDeleteDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => client.delete(`/drafts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drafts'] });
    },
  });
}

export function useGenerateDraft() {
  return useMutation({
    mutationFn: (id: number) =>
      client.post<GenerateResponse>(`/drafts/${id}/generate`).then((r) => r.data),
  });
}

/**
 * Material pile query — fetches news items added to the material pile.
 * Endpoint: GET /api/v1/news/material-pile
 */
export function useMaterialPile(params: { page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ['drafts', 'material-pile', params],
    queryFn: () =>
      client
        .get<{ total: number; items: MaterialPileItem[] }>('/news/material-pile', { params })
        .then((r) => r.data),
  });
}

export function useDraftVersions(id: number | null) {
  return useQuery({
    queryKey: ['drafts', id, 'versions'],
    queryFn: () =>
      client.get<DraftVersion[]>(`/drafts/${id}/versions`).then((r) => r.data),
    enabled: !!id,
  });
}
