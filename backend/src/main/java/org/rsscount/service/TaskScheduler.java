package org.rsscount.service;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.rsscount.entity.Report;
import org.rsscount.entity.RssSource;
import org.rsscount.entity.Settings;
import org.rsscount.entity.Task;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled task creator — periodically creates tasks based on Settings.
 *
 * If taskIntervalHours is 0, the scheduler is disabled.
 * Skips if there's already a RUNNING task to prevent overlap.
 * Skips if the last task was created less than taskIntervalHours ago.
 */
@ApplicationScoped
public class TaskScheduler {

    @Inject
    TaskExecutor taskExecutor;

    /** Guard to skip the first scheduled invocation after startup */
    private volatile boolean startupSkipped = false;

    /**
     * Cron expression evaluated every hour. Inside the method, we check
     * whether the configured interval has elapsed since the last task.
     * First invocation after startup is skipped to avoid racing with
     * resumeRunningTasks and to prevent creating duplicate tasks.
     */
    @Scheduled(every = "1h")
    public void scheduledTaskCreation() {
        // Skip the very first tick after startup — avoid racing with resumeRunningTasks
        if (!startupSkipped) {
            startupSkipped = true;
            Log.info("TaskScheduler: Skipping first tick after startup");
            return;
        }

        try {
            Settings settings = QuarkusTransaction.call(Settings::getOrCreate);

            // Check if scheduler is disabled
            if (settings.taskIntervalHours <= 0) {
                return;
            }

            // Check interval: skip if last non-running task was too recent
            Task lastTask = QuarkusTransaction.call(() ->
                Task.find("status != ?1 order by createdAt desc", Task.STATUS_RUNNING).firstResult());
            if (lastTask != null && lastTask.createdAt != null) {
                long hoursSinceLast = ChronoUnit.HOURS.between(lastTask.createdAt, LocalDateTime.now());
                if (hoursSinceLast < settings.taskIntervalHours) {
                    Log.debugf("TaskScheduler: Skipping — last task was %d hours ago (< %d)",
                        hoursSinceLast, settings.taskIntervalHours);
                    return;
                }
            }

            // Check for overlapping RUNNING tasks
            long runningCount = QuarkusTransaction.call(() ->
                Task.count("status", Task.STATUS_RUNNING));
            if (runningCount > 0) {
                Log.debug("TaskScheduler: Skipping — there are running tasks");
                return;
            }

            // Skip if no RSS sources available
            long sourceCount = QuarkusTransaction.call(() ->
                RssSource.count("isActive", true));
            if (sourceCount == 0) {
                Log.debug("TaskScheduler: Skipping — no RSS sources available");
                return;
            }

            // Determine time range: use last completed task's endedAt if it's
            // more than 1 hour ago (to pick up where we left off), otherwise
            // default to last 24 hours.
            LocalDateTime now = LocalDateTime.now();
            Task lastCompleted = QuarkusTransaction.call(() ->
                Task.find("status = ?1 order by endedAt desc", Task.STATUS_COMPLETED).firstResult());

            LocalDateTime timeRangeStart;
            if (lastCompleted != null && lastCompleted.endedAt != null
                && lastCompleted.endedAt.isBefore(now.minusHours(1))) {
                timeRangeStart = lastCompleted.endedAt;
            } else {
                timeRangeStart = now.minusHours(24);
            }
            LocalDateTime timeRangeEnd = now;

            // Determine source type
            String sourceType = Task.SOURCE_ALL;
            if (settings.defaultGroup != null) {
                sourceType = Task.SOURCE_GROUP;
            }

            // Generate task name — use max sequence number for today, not count
            String todayStr = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
            String todayPrefix = todayStr + "-第";
            Task latestToday = QuarkusTransaction.call(() ->
                Task.find("name like ?1 order by createdAt desc", todayPrefix + "%").firstResult());
            int nextSeq = 1;
            if (latestToday != null) {
                String n = latestToday.name;
                int startIdx = n.indexOf(todayPrefix) + todayPrefix.length();
                int endIdx = n.indexOf("次", startIdx);
                if (endIdx > startIdx) {
                    try {
                        nextSeq = Integer.parseInt(n.substring(startIdx, endIdx)) + 1;
                    } catch (NumberFormatException e) {
                        nextSeq = 2;
                    }
                }
            }
            String taskName = todayStr + "-第" + nextSeq + "次自动任务";

            // Build sourceConfig for GROUP type
            String sourceConfig = null;
            if (settings.defaultGroup != null) {
                sourceConfig = "{\"groupIds\":[" + settings.defaultGroup.id + "],\"sourceIds\":[]}";
            }

            // Create Task and Report (persisted in a single transaction)
            Task task = new Task();
            task.name = taskName;
            task.timeRangeStart = timeRangeStart;
            task.timeRangeEnd = timeRangeEnd;
            task.status = Task.STATUS_RUNNING;
            task.sourceType = sourceType;
            task.sourceConfig = sourceConfig;
            task.startedAt = LocalDateTime.now();

            Report report = new Report();
            report.task = task;
            report.name = taskName.replace("任务", "报告");
            report.timeRangeStart = timeRangeStart;
            report.timeRangeEnd = timeRangeEnd;

            QuarkusTransaction.run(() -> {
                task.persist();
                report.persist();
            });

            // Execute (task.id is now set after persist)
            taskExecutor.execute(task);

            Log.infof("TaskScheduler: Created and started task %d: %s", task.id, taskName);
        } catch (Exception e) {
            Log.errorf("TaskScheduler: Failed to create scheduled task: %s", e.getMessage());
        }
    }
}
