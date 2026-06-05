import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client from './client';
import type {
  Task,
  TaskWithDetails,
  CreateTaskRequest,
  TaskResponse,
  TaskListParams,
  PaginatedResponse,
  LastEndTimeResponse,
} from '@/types';

/** Alias for useTasks — returns task list with pagination */
export function useTaskList(params: TaskListParams = {}) {
  return useQuery({
    queryKey: ['tasks', 'list', params],
    queryFn: () =>
      client.get<PaginatedResponse<Task>>('/tasks', { params }).then((r) => r.data),
    staleTime: 5 * 1000,
  });
}

/** @deprecated use useTaskList instead */
export function useTasks(params: TaskListParams = {}) {
  return useTaskList(params);
}

export function useTask(id: number | null) {
  return useQuery({
    queryKey: ['tasks', 'detail', id],
    queryFn: () => client.get<TaskWithDetails>(`/tasks/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateTaskRequest) =>
      client.post<TaskResponse>('/tasks', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useSuggestTaskName() {
  return useQuery({
    queryKey: ['tasks', 'suggest-name'],
    queryFn: () =>
      client.get<string>('/tasks/suggest-name').then((r) => r.data),
    staleTime: 60_000,
  });
}

/** @deprecated use useSuggestTaskName instead */
export function useSuggestName() {
  return useSuggestTaskName();
}

export function useLastEndTime() {
  return useQuery({
    queryKey: ['tasks', 'last-end-time'],
    queryFn: () =>
      client.get<LastEndTimeResponse>('/tasks/last-end-time').then((r) => r.data),
    staleTime: 60_000,
  });
}
