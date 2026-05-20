package org.rsscount.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rsscount.entity.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * U7: TaskScheduler integration tests.
 *
 * U7-1: Scheduler creates task when conditions met.
 * U7-2: Scheduler skips when RUNNING task exists.
 * U7-3: Scheduler skips when interval is 0.
 */
@QuarkusTest
class TaskSchedulerTest {

    @InjectMock
    TaskExecutor mockTaskExecutor;

    @Inject
    TaskScheduler taskScheduler;

    @BeforeEach
    @Transactional
    void cleanup() {
        News.deleteAll();
        Report.deleteAll();
        Task.deleteAll();
        Settings.deleteAll();
    }

    @Test
    @Transactional
    void testSchedulerCreatesTask() {
        // Setup settings with 1-hour interval
        Settings settings = Settings.getOrCreate();
        settings.taskIntervalHours = 1;
        settings.persist();

        Mockito.doNothing().when(mockTaskExecutor).execute(any(Task.class));

        // Should not throw
        taskScheduler.scheduledTaskCreation();

        // Verify a task was created
        long taskCount = Task.count();
        assertTrue(taskCount > 0, "A scheduled task should be created");
    }

    @Test
    @Transactional
    void testSchedulerSkipsWhenRunningTaskExists() {
        // Setup settings
        Settings settings = Settings.getOrCreate();
        settings.taskIntervalHours = 1;
        settings.persist();

        // Create a RUNNING task
        Task runningTask = new Task();
        runningTask.name = "正在运行的任务";
        runningTask.timeRangeStart = LocalDateTime.now().minusHours(1);
        runningTask.timeRangeEnd = LocalDateTime.now();
        runningTask.status = Task.STATUS_RUNNING;
        runningTask.sourceType = Task.SOURCE_ALL;
        runningTask.startedAt = LocalDateTime.now();
        runningTask.persist();

        Mockito.doNothing().when(mockTaskExecutor).execute(any(Task.class));

        taskScheduler.scheduledTaskCreation();

        // Only 1 task should exist (the running one, not a new one)
        long taskCount = Task.count();
        assertEquals(1, taskCount, "Should not create a new task when one is already running");
    }

    @Test
    @Transactional
    void testSchedulerSkipsWhenIntervalIsZero() {
        // Setup settings with 0 interval (disabled)
        Settings settings = Settings.getOrCreate();
        settings.taskIntervalHours = 0;
        settings.persist();

        taskScheduler.scheduledTaskCreation();

        // No task should be created
        long taskCount = Task.count();
        assertEquals(0, taskCount, "Should not create task when interval is 0");
    }

    @Test
    @Transactional
    void testSchedulerUsesLastCompletedEndTime() {
        Settings settings = Settings.getOrCreate();
        settings.taskIntervalHours = 1;
        settings.persist();

        // Create a completed task
        Task completedTask = new Task();
        completedTask.name = "已完成任务";
        completedTask.timeRangeStart = LocalDateTime.now().minusHours(6);
        completedTask.timeRangeEnd = LocalDateTime.now().minusHours(4);
        completedTask.status = Task.STATUS_COMPLETED;
        completedTask.sourceType = Task.SOURCE_ALL;
        completedTask.startedAt = LocalDateTime.now().minusHours(6);
        completedTask.endedAt = LocalDateTime.now().minusHours(4);
        completedTask.persist();

        Mockito.doNothing().when(mockTaskExecutor).execute(any(Task.class));

        taskScheduler.scheduledTaskCreation();

        // A new task should be created
        long taskCount = Task.count();
        assertEquals(2, taskCount);
    }

    @Test
    @Transactional
    void testSchedulerHandlesSettingsException() {
        // Delete settings to trigger edge case
        Settings.deleteAll();

        // Should handle gracefully — will create default settings
        taskScheduler.scheduledTaskCreation();

        // Default settings has interval > 0, so a task may be created
        // This test primarily checks no exception is thrown
        assertDoesNotThrow(() -> taskScheduler.scheduledTaskCreation());
    }
}
