import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type { RssSource, RssGroup, PaginatedResponse, OpmlImportResponse } from '@/types';

// ---------- RSS Sources ----------

export function useRssSourceList(groupId?: number) {
  return useQuery({
    queryKey: ['rss-sources', 'list', { groupId }],
    queryFn: () =>
      client
        .get<RssSource[]>('/rss-sources', { params: groupId ? { groupId } : {} })
        .then((r) => r.data),
    staleTime: 30 * 1000,
  });
}

export function useRssSourceSearch(keyword: string, page: number = 1, size: number = 20, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['rss-sources', 'search', { keyword, page, size }],
    queryFn: () =>
      client
        .get<PaginatedResponse<RssSource>>('/rss-sources/search', { params: { keyword, page, size } })
        .then((r) => r.data),
    enabled: options?.enabled ?? !!keyword,
  });
}

/** @deprecated use useRssSourceList instead */
export function useRssSources(groupId?: number) {
  return useRssSourceList(groupId);
}

export function useCreateRssSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { url: string; name?: string; groupIds?: number[] }) =>
      client.post<RssSource>('/rss-sources', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
      queryClient.invalidateQueries({ queryKey: ['rss-groups'] });
    },
  });
}

/** @deprecated use useCreateRssSource instead */
export function useAddRssSource() {
  return useCreateRssSource();
}

export function useUpdateRssSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: { name?: string; url?: string; groupIds?: number[] } }) =>
      client.put<RssSource>(`/rss-sources/${id}`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
    },
  });
}

export function useDeleteRssSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => client.delete(`/rss-sources/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
    },
  });
}

export function useImportOpml() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return client
        .post<OpmlImportResponse>('/rss-sources/import-opml', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        .then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
      queryClient.invalidateQueries({ queryKey: ['rss-groups'] });
    },
  });
}

/**
 * Export OPML file — optionally filter by group IDs.
 */
export function useExportOpml() {
  return useMutation({
    mutationFn: (groupIds?: number[]) =>
      client.get('/rss-sources/export-opml', {
        responseType: 'blob',
        params: groupIds && groupIds.length > 0 ? { groupIds: groupIds.join(',') } : {},
      }).then((r) => {
        // Create a downloadable URL from the blob
        const url = window.URL.createObjectURL(new Blob([r.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', 'rss-sources.opml');
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
      }),
  });
}

// ---------- RSS Groups ----------

export function useRssGroups() {
  return useQuery({
    queryKey: ['rss-groups', 'list'],
    queryFn: () => client.get<RssGroup[]>('/rss-groups').then((r) => r.data),
    staleTime: 30 * 1000,
  });
}

export function useCreateRssGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string }) =>
      client.post('/rss-groups', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-groups'] });
    },
  });
}

export function useUpdateRssGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: { name: string } }) =>
      client.put(`/rss-groups/${id}`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-groups'] });
    },
  });
}

export function useDeleteRssGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => client.delete(`/rss-groups/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-groups'] });
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
    },
  });
}

export function useAddSourcesToGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ groupId, sourceIds }: { groupId: number; sourceIds: number[] }) =>
      client.put(`/rss-groups/${groupId}/sources`, sourceIds).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
      queryClient.invalidateQueries({ queryKey: ['rss-groups'] });
    },
  });
}
