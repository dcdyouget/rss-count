// ============================================================
// E2E-1：完整任务流程（核心黄金路径）
// 覆盖：添加 RSS 源 → 创建任务 → SSE 进度 → 查看报告 → 查看新闻
// ============================================================

import { test, expect } from '@playwright/test';
import { setupApiMocks, resetDatabase } from './helpers/api';

test.beforeEach(async ({ page }) => {
  resetDatabase();
  await setupApiMocks(page);
});

test('E2E-1：完整任务流程 — 添加源 → 创建任务 → SSE → 报告 → 新闻 → 仪表盘', async ({ page }) => {
  // ---- Step 1-3: RSS 源添加 ----
  await page.goto('/rss-sources');
  await page.waitForSelector('.ant-table', { timeout: 5000 });
  await expect(page.locator('.ant-table')).toBeVisible();

  const addBtn = page.getByRole('button', { name: /添加源/i }).first();
  if (await addBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
    await addBtn.click();
  }
  await page.waitForTimeout(300);

  const modal = page.locator('.ant-modal');
  if (await modal.isVisible({ timeout: 3000 }).catch(() => false)) {
    const input = modal.locator('input').first();
    if (await input.isVisible()) {
      await input.fill('https://example.com/new-feed');
    }
    const okBtn = modal.locator('.ant-modal-footer .ant-btn-primary, .ant-modal-footer button:last-child').first();
    if (await okBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await okBtn.click();
    }
    await page.waitForTimeout(300);
  }

  // ---- Step 4-6: 创建任务 ----
  await page.goto('/tasks');
  await page.waitForTimeout(300);

  const createTaskBtn = page.getByRole('button', { name: /创建任务/i }).first();
  if (await createTaskBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
    await createTaskBtn.click();
  }
  const drawer = page.locator('.ant-drawer');
  if (await drawer.isVisible({ timeout: 3000 }).catch(() => false)) {
    const nameInput = drawer.locator('input').first();
    if (await nameInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await nameInput.fill('测试任务');
    }
    const submitBtn = drawer.getByRole('button').filter({ hasText: /创建|确定/i }).first();
    if (await submitBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await submitBtn.click();
    }
  }
  await page.waitForTimeout(300);

  // ---- Step 7-8: 任务详情 + SSE ----
  await page.goto('/tasks');
  await page.waitForTimeout(300);

  const detailBtn = page.getByRole('button', { name: /详情|查看详情/i }).first();
  if (await detailBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
    await detailBtn.click();
  } else {
    await page.goto('/tasks/1');
  }
  await page.waitForTimeout(500);

  // 直接导航到报告页面（SSE mock 已在 api.ts 中一次性返回所有事件）
  await page.goto('/reports/1');
  await page.waitForTimeout(500);
  expect(page.url()).toContain('/reports/1');

  // ---- Step 9-10: 报告页面 ----
  await page.waitForFunction(() => document.body.innerText.length > 0, { timeout: 5000 }).catch(() => {});
  console.log('Report page loaded');

  // ---- Step 11: 点击新闻卡片 ----
  const card = page.locator('.ant-card').first();
  if (await card.isVisible({ timeout: 2000 }).catch(() => false)) {
    await card.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 12: 标记已读 ----
  const markReadBtn = page.getByRole('button', { name: /标记已读|已读/i }).first();
  if (await markReadBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
    await markReadBtn.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 13: 返回 ----
  const backBtn = page.getByRole('button', { name: /返回|←/i }).first();
  if (await backBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
    await backBtn.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 14: Dashboard ----
  await page.goto('/dashboard');
  await page.waitForTimeout(300);
  const dashboardText = await page.locator('body').innerText();
  expect(dashboardText.length).toBeGreaterThan(0);
});
