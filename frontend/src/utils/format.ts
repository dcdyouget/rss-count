import dayjs from 'dayjs';

/**
 * 格式化 ISO 日期为 "YYYY-MM-DD HH:mm" 显示格式
 */
export function formatDateTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '--';
  return dayjs(isoStr).format('YYYY-MM-DD HH:mm');
}

/**
 * 格式化 ISO 日期为 "MM-DD HH:mm" 短格式
 */
export function formatShortDateTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '--';
  return dayjs(isoStr).format('MM-DD HH:mm');
}

/**
 * 格式化 ISO 日期为时间 "HH:mm"
 */
export function formatTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '--';
  return dayjs(isoStr).format('HH:mm');
}

/**
 * 格式化数字，大于 9999 显示 "10k+"
 */
export function formatCount(num: number | null | undefined): string {
  if (num == null) return '--';
  if (num >= 10000) {
    return `${(num / 1000).toFixed(0)}k+`;
  }
  return num.toLocaleString();
}

/**
 * 格式化百分比，昨天为 0 时返回 null 提示 "新增"
 */
export function formatChangePercent(
  changePercent: number | null | undefined,
  change: number,
): string {
  if (changePercent == null) {
    return change > 0 ? `新增 ${change}` : '';
  }
  const sign = changePercent >= 0 ? '+' : '';
  return `${sign}${changePercent.toFixed(1)}%`;
}

/**
 * 格式化时间范围为 "2026-05-20 08:00 ~ 14:00"
 */
export function formatTimeRange(start: string, end: string): string {
  const startDate = dayjs(start).format('YYYY-MM-DD');
  const startTime = dayjs(start).format('HH:mm');
  const endTime = dayjs(end).format('HH:mm');
  return `${startDate} ${startTime} ~ ${endTime}`;
}

/**
 * 相对时间（如 "3分钟前"、"2小时前"）
 */
export function formatRelativeTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '--';
  const now = dayjs();
  const then = dayjs(isoStr);
  const diffMin = now.diff(then, 'minute');
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;
  const diffHour = now.diff(then, 'hour');
  if (diffHour < 24) return `${diffHour}小时前`;
  const diffDay = now.diff(then, 'day');
  if (diffDay < 30) return `${diffDay}天前`;
  return then.format('YYYY-MM-DD');
}
