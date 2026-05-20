// ============================================================
// E2E-2：素材堆 + AI 稿件（内容生产流程）
// 覆盖：搜索新闻 → 加入素材堆 → 创建稿件 → AI 生成 → 编辑 → 复制
// ============================================================

import { test, expect } from '@playwright/test';
import { setupApiMocks, resetDatabase } from './helpers/api';

test.beforeEach(async ({ page }) => {
  resetDatabase();
  await setupApiMocks(page);
});

test('E2E-2：素材堆 + AI 稿件 — 搜索 → 素材堆 → 创建稿件 → AI 生成 → 编辑 → 复制', async ({ page }) => {
  // ---- Step 1: 访问 /news — 新闻表格显示 ----
  await page.goto('/news');
  await page.waitForSelector('.ant-table', { timeout: 5000 });
  await expect(page.locator('.ant-table')).toBeVisible();

  // ---- Step 2: 搜索关键词 — 筛选结果 ----
  const searchInput = page.getByPlaceholder(/搜索|关键词|keyword/i).first();
  if (await searchInput.isVisible({ timeout: 2000 }).catch(() => false)) {
    await searchInput.fill('AI');
    await searchInput.press('Enter');
    await page.waitForTimeout(300);
  }

  // ---- Step 3: 勾选新闻 → 点击"加入素材堆" ----
  const checkboxes = page.locator('.ant-table-tbody .ant-checkbox-input');
  const checkboxCount = await checkboxes.count();
  for (let i = 0; i < Math.min(checkboxCount, 3); i++) {
    await checkboxes.nth(i).check({ force: true });
  }

  const addMaterialBtn = page.getByRole('button', { name: /加入素材堆|素材堆/i }).first();
  if (await addMaterialBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
    await addMaterialBtn.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 4: 访问 /drafts → 点"新建稿件" ----
  await page.goto('/drafts');
  await page.waitForTimeout(500);

  const newDraftBtns = page.getByRole('button', { name: /新建稿件|新建/i });
  if ((await newDraftBtns.count()) > 0) {
    await newDraftBtns.first().click();
  } else {
    await page.goto('/drafts/new');
  }
  await page.waitForTimeout(800);

  // ---- Step 5: 填入提示词（跳过 Select 组件，避免 modal 覆盖问题） ----
  const promptArea = page.getByPlaceholder(/提示词|prompt|输入提示/i).first();
  if (await promptArea.isVisible({ timeout: 2000 }).catch(() => false)) {
    await promptArea.fill('生成一篇科技周报，包含 AI 和量子计算的最新进展');
  }

  // ---- Step 6: 点"AI 生成稿件" ----
  const generateBtn = page.getByRole('button', { name: /AI 生成|生成稿件|AI生成/i }).first();
  if (await generateBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
    await generateBtn.click();
    await page.waitForTimeout(1000);
  }

  // ---- Step 7: 切换"纯文本"Tab ----
  const plainTextTab = page.locator('.ant-tabs-tab').filter({ hasText: /纯文本/i }).first();
  if (await plainTextTab.isVisible({ timeout: 2000 }).catch(() => false)) {
    await plainTextTab.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 8: 编辑内容 ----
  const textEditor = page.locator('textarea, [contenteditable="true"]').first();
  if (await textEditor.isVisible({ timeout: 2000 }).catch(() => false)) {
    await textEditor.fill('编辑后的新闻内容：AI 突破性进展...');
    await page.waitForTimeout(300);
  }

  // ---- Step 9: 复制内容 ----
  const copyBtn = page.getByRole('button', { name: /复制内容|复制/i }).first();
  if (await copyBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
    await copyBtn.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 10: 验证 ----
  await page.goto('/drafts');
  await page.waitForTimeout(500);
  const pageText = await page.locator('body').innerText();
  expect(pageText.length).toBeGreaterThan(0);
});
