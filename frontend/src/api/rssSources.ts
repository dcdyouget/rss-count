import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type { RssSource, RssGroup, OpmlImportResponse } from '@/types';

// ---------- RSS Sources ----------

export function useRssSourceList(groupId?: number) {
  return useQuery({
    queryKey: ['rss-sources', 'list', { groupId }],
    queryFn: () =>
      client
        .get<RssSource[]>('/rss-sources', { params: groupId ? { groupId } : {} })
        .then((r) => r.data),
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
 * Export OPML file — returns the download URL.
 * Usage: set window.location.href or use an <a> tag with this URL.
 */
export function useExportOpml() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      client.get('/rss-sources/export-opml', { responseType: 'blob' }).then((r) => {
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rss-sources'] });
    },
  });
}

// ---------- RSS Groups ----------

export function useRssGroups() {
  return useQuery({
    queryKey: ['rss-groups', 'list'],
    queryFn: () => client.get<RssGroup[]>('/rss-groups').then((r) => r.data),
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
    },
  });
}
