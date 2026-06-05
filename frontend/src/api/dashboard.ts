import { useQuery } from '@tanstack/react-query';
import client from './client';
import type { DashboardStats, RecentTask, RecentReport } from '@/types';

export function useDashboardStats() {
  return useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => client.get<DashboardStats>('/dashboard/stats').then((r) => r.data),
    staleTime: 15 * 1000,
  });
}

export function useRecentTasks() {
  return useQuery({
    queryKey: ['dashboard', 'recent-tasks'],
    queryFn: () => client.get<RecentTask[]>('/dashboard/recent-tasks').then((r) => r.data),
    staleTime: 15 * 1000,
  });
}

export function useRecentReports() {
  return useQuery({
    queryKey: ['dashboard', 'recent-reports'],
    queryFn: () => client.get<RecentReport[]>('/dashboard/recent-reports').then((r) => r.data),
    staleTime: 15 * 1000,
  });
}
