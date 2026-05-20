import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type { Settings, UpdateSettingsRequest } from '@/types';

export function useSettings() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: () => client.get<Settings>('/settings').then((r) => r.data),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateSettingsRequest) =>
      client.put<Settings>('/settings', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
  });
}
