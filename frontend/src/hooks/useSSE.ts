import { useState, useEffect, useRef, useCallback } from 'react';
import type { SSEProgress } from '@/types';

interface UseSSEOptions {
  maxLogs?: number;
  maxReconnectAttempts?: number;
  reconnectWindowMs?: number;
}

interface UseSSEResult {
  progress: SSEProgress | null;
  logs: string[];
  isComplete: boolean;
  reportId: number | null;
  error: string | null;
  isConnected: boolean;
  close: () => void;
}

export function useSSE(
  taskId: number | null,
  options: UseSSEOptions = {},
): UseSSEResult {
  const {
    maxLogs = 200,
    maxReconnectAttempts = 3,
    reconnectWindowMs = 30_000,
  } = options;

  const [progress, setProgress] = useState<SSEProgress | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [isComplete, setIsComplete] = useState(false);
  const [reportId, setReportId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectStartRef = useRef<number>(0);
  const isMountedRef = useRef(true);
  const isCompleteRef = useRef(false);
  const userDisconnectedRef = useRef(false);

  const addLogs = useCallback(
    (newLogs: string[]) => {
      setLogs((prev) => {
        const updated = [...prev, ...newLogs];
        return updated.length > maxLogs
          ? updated.slice(updated.length - maxLogs)
          : updated;
      });
    },
    [maxLogs],
  );

  const closeConnection = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    setIsConnected(false);
  }, []);

  const close = useCallback(() => {
    closeConnection();
    userDisconnectedRef.current = true;
    reconnectAttemptsRef.current = 0;
    reconnectStartRef.current = 0;
  }, [closeConnection]);

  const connect = useCallback(() => {
    if (!taskId) return;

    // Close any existing connection before opening new one
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const url = `/api/v1/tasks/${taskId}/stream`;
    const es = new EventSource(url);
    eventSourceRef.current = es;

    es.addEventListener('open', () => {
      if (!isMountedRef.current) return;
      setIsConnected(true);
      setError(null);
      reconnectAttemptsRef.current = 0;
      reconnectStartRef.current = 0;
    });

    es.addEventListener('progress', (event) => {
      if (!isMountedRef.current) return;
      try {
        const data: SSEProgress = JSON.parse(event.data);
        setProgress(data);

        const newLogs: string[] = [];
        if (data.pulling.currentSource) {
          newLogs.push(
            `正在拉取: ${data.pulling.currentSource} (${data.pulling.sourceProgress ?? '...'})`,
          );
        }
        if (data.formatting.currentAction) {
          newLogs.push(
            `${data.formatting.currentAction} (${data.formatting.formatted ?? 0}/${data.formatting.total ?? '?'})`,
          );
        }
        if (newLogs.length > 0) {
          addLogs(newLogs);
        }
      } catch {
        // ignore parse errors
      }
    });

    es.addEventListener('complete', (event) => {
      if (!isMountedRef.current) return;
      try {
        const data = JSON.parse(event.data);
        setIsComplete(true);
        isCompleteRef.current = true;
        setReportId(data.reportId);
        addLogs([`任务完成，报告 ID: ${data.reportId}`]);
      } catch {
        setIsComplete(true);
        isCompleteRef.current = true;
      }
      es.close();

      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      reconnectAttemptsRef.current = 0;
      reconnectStartRef.current = 0;

      setIsConnected(false);
    });

    es.addEventListener('error', (msgEvent) => {
      if (!isMountedRef.current) return;

      // Check if this is a custom error event with data
      const event = msgEvent as Event & { data?: string };
      if (event.data) {
        try {
          const data = JSON.parse(event.data);
          setError(data.message);
          addLogs([`错误: ${data.message}`]);
        } catch {
          // not JSON data — likely native error
        }
      }

      es.close();
      setIsConnected(false);

      // Don't reconnect if already complete
      if (isCompleteRef.current) return;

      // Exponential backoff reconnect logic
      const now = Date.now();
      if (reconnectStartRef.current === 0) {
        reconnectStartRef.current = now;
      }

      const elapsedSinceWindowStart = now - reconnectStartRef.current;

      if (elapsedSinceWindowStart < reconnectWindowMs) {
        if (reconnectAttemptsRef.current < maxReconnectAttempts) {
          reconnectAttemptsRef.current += 1;
          const attempt = reconnectAttemptsRef.current;
          // Exponential backoff: 2s, 4s, 8s (within 30s window)
          const delay = Math.min(2000 * Math.pow(2, attempt - 1), 10000);
          addLogs([
            `连接断开，正在重连... (${attempt}/${maxReconnectAttempts})`,
          ]);
          reconnectTimerRef.current = setTimeout(() => {
            if (isMountedRef.current && !userDisconnectedRef.current) {
              connect();
            }
          }, delay);
        } else {
          addLogs(['重连失败，请手动刷新页面']);
        }
      } else {
        // Reset reconnect window
        reconnectStartRef.current = now;
        reconnectAttemptsRef.current = 1;
        addLogs(['连接断开，正在重连... (1/3)']);
        reconnectTimerRef.current = setTimeout(() => {
          if (isMountedRef.current && !userDisconnectedRef.current) {
            connect();
          }
        }, 2000);
      }
    });
  }, [taskId, addLogs, maxReconnectAttempts, reconnectWindowMs]);

  useEffect(() => {
    isMountedRef.current = true;

    if (taskId) {
      // Reset state when taskId changes
      setProgress(null);
      setLogs([]);
      setIsComplete(false);
      isCompleteRef.current = false;
      setReportId(null);
      setError(null);
      setIsConnected(false);
      reconnectAttemptsRef.current = 0;
      reconnectStartRef.current = 0;

      connect();
    }

    return () => {
      isMountedRef.current = false;
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
    };
  }, [taskId, connect]);

  return { progress, logs, isComplete, reportId, error, isConnected, close };
}
