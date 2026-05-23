package org.rsscount.service;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.rsscount.entity.News;
import org.rsscount.entity.Report;
import org.rsscount.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘服务 — 提供首页统计概览和最近任务/报告数据。
 * 统计维度包括：任务数、报告数、新闻数的今日/昨日对比和变化百分比。
 */
@ApplicationScoped
public class DashboardService {

    /** 统计数据项（今日值 + 昨日值 + 变化量 + 变化百分比） */
    public record StatItem(long today, long yesterday, long change, Double changePercent) {}

    /** 仪表盘统计响应 */
    public record StatsResponse(
        StatItem taskCount,
        StatItem reportCount,
        StatItem newsCount
    ) {}

    /** 最近任务项 */
    public record RecentTaskItem(
        Long id,
        String name,
        LocalDateTime timeRangeStart,
        LocalDateTime timeRangeEnd,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long reportId
    ) {}

    /** 最近报告项 */
    public record RecentReportItem(
        Long id,
        String name,
        int newsCount,
        LocalDateTime createdAt
    ) {}

    /**
     * 获取仪表盘统计数据（今日 vs 昨日）。
     * @return 统计响应（任务数、报告数、新闻数各含变化趋势）
     */
    public StatsResponse getStats() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime yesterdayStart = yesterday.atStartOfDay();
        LocalDateTime yesterdayEnd = yesterday.atTime(LocalTime.MAX);

        long taskToday = Task.count("createdAt >= ?1 and createdAt <= ?2", todayStart, todayEnd);
        long taskYesterday = Task.count("createdAt >= ?1 and createdAt <= ?2", yesterdayStart, yesterdayEnd);

        long reportToday = Report.count("createdAt >= ?1 and createdAt <= ?2", todayStart, todayEnd);
        long reportYesterday = Report.count("createdAt >= ?1 and createdAt <= ?2", yesterdayStart, yesterdayEnd);

        long newsToday = News.count("createdAt >= ?1 and createdAt <= ?2", todayStart, todayEnd);
        long newsYesterday = News.count("createdAt >= ?1 and createdAt <= ?2", yesterdayStart, yesterdayEnd);

        return new StatsResponse(
            buildStatItem(taskToday, taskYesterday),
            buildStatItem(reportToday, reportYesterday),
            buildStatItem(newsToday, newsYesterday)
        );
    }

    /**
     * 获取最近 5 条任务记录（按创建时间降序）。
     * @return 最近任务列表（已完成的任务附有报告 ID）
     */
    public List<RecentTaskItem> getRecentTasks() {
        List<Task> tasks = Task.find("order by createdAt desc")
            .page(0, 5)
            .list();

        return tasks.stream().map(task -> {
            // Find the associated report for COMPLETED tasks
            Long reportId = null;
            if (Task.STATUS_COMPLETED.equals(task.status)) {
                Report report = Report.find("task.id", task.id).firstResult();
                if (report != null) {
                    reportId = report.id;
                }
            }
            return new RecentTaskItem(
                task.id,
                task.name,
                task.timeRangeStart,
                task.timeRangeEnd,
                task.status,
                task.startedAt,
                task.endedAt,
                reportId
            );
        }).collect(Collectors.toList());
    }

    /**
     * 获取最近 3 条报告记录（按创建时间降序）。
     * @return 最近报告列表
     */
    public List<RecentReportItem> getRecentReports() {
        List<Report> reports = Report.find("order by createdAt desc")
            .page(0, 3)
            .list();

        return reports.stream().map(report ->
            new RecentReportItem(
                report.id,
                report.name,
                report.newsCount,
                report.createdAt
            )
        ).collect(Collectors.toList());
    }

    /** 构建统计数据项：计算变化量和百分比（保留一位小数） */
    private StatItem buildStatItem(long today, long yesterday) {
        long change = today - yesterday;
        Double changePercent = null;
        if (yesterday > 0) {
            changePercent = ((double) change / yesterday) * 100.0;
            changePercent = Math.round(changePercent * 10.0) / 10.0;  // round to 1 decimal
        }
        return new StatItem(today, yesterday, change, changePercent);
    }
}
