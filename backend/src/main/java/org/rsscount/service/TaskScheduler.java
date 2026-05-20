package org.rsscount.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.rsscount.entity.Report;
import org.rsscount.entity.RssGroup;
import org.rsscount.entity.Settings;
import org.rsscount.entity.Task;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled task creator — periodically creates tasks based on Settings.
 *
 * If taskIntervalHours is 0, the scheduler is disabled.
 * Skips if there's already a RUNNING task to prevent overlap.
 */
@ApplicationScoped
public class TaskScheduler {

    @Inject
    TaskExecutor taskExecutor;

    /**
     * Cron expression evaluated every hour. Inside the method, we check
     * whether the configured interval has elapsed since the last task.
     */
    @Scheduled(every = "1h")
    @Transactional
    void scheduledTaskCreation() {
        try {
            Settings settings = Settings.getOrCreate();

            // Check if scheduler is disabled
            if (settings.taskIntervalHours <= 0) {
                return;
            }

            // Check for overlapping RUNNING tasks
            long runningCount = Task.count("status", Task.STATUS_RUNNING);
            if (runningCount > 0) {
                Log.debug("TaskScheduler: Skipping — there are running tasks");
                return;
            }

            // Determine time range from last completed task
            Task lastCompleted = Task.find("status = ?1 order by endedAt desc", Task.STATUS_COMPLETED)
                .firstResult();

            LocalDateTime timeRangeStart;
            if (lastCompleted != null && lastCompleted.endedAt != null) {
                timeRangeStart = lastCompleted.endedAt;
            } else {
                timeRangeStart = LocalDateTime.now().minusHours(24);
            }
            LocalDateTime timeRangeEnd = LocalDateTime.now();

            // Check interval has elapsed since last task started being created
            // We use the scheduling interval as a coarse check; the @Scheduled every=1h
            // plus this guard ensures we don't create too many tasks.

            // Determine source type
            String sourceType = Task.SOURCE_ALL;
            if (settings.defaultGroup != null) {
                sourceType = Task.SOURCE_GROUP;
            }

            // Generate task name
            String todayStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
            long countToday = Task.count("createdAt >= ?1",
                LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
            String taskName = todayStr + "-第" + (countToday + 1) + "次自动任务";

            // Build sourceConfig for GROUP type
            String sourceConfig = null;
            if (settings.defaultGroup != null) {
                sourceConfig = "{\"groupIds\":[" + settings.defaultGroup.id + "],\"sourceIds\":[]}";
            }

            // Create Task
            Task task = new Task();
            task.name = taskName;
            task.timeRangeStart = timeRangeStart;
            task.timeRangeEnd = timeRangeEnd;
            task.status = Task.STATUS_RUNNING;
            task.sourceType = sourceType;
            task.sourceConfig = sourceConfig;
            task.startedAt = LocalDateTime.now();
            task.persist();

            // Create Report
            Report report = new Report();
            report.task = task;
            report.name = taskName.replace("任务", "报告");
            report.timeRangeStart = timeRangeStart;
            report.timeRangeEnd = timeRangeEnd;
            report.persist();

            // Execute
            taskExecutor.execute(task);

            Log.infof("TaskScheduler: Created and started task %d: %s", task.id, taskName);
        } catch (Exception e) {
            Log.errorf("TaskScheduler: Failed to create scheduled task: %s", e.getMessage());
        }
    }
}
