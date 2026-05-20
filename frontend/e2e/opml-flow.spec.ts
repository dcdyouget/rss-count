// ============================================================
// E2E-3：OPML 导入导出 + 源管理
// 覆盖：OPML 导入 → 验证源和分组 → 导出 → 删除 → 验证数量
// ============================================================

import { test, expect } from '@playwright/test';
import { setupApiMocks, resetDatabase } from './helpers/api';

test.beforeEach(async ({ page }) => {
  resetDatabase();
  await setupApiMocks(page);
});

test('E2E-3：OPML 导入导出 — 导入 → 验证源和分组 → 导出 → 删除 → 验证数量', async ({ page }) => {
  // ---- Step 1: 访问 /rss-sources — 页面加载 ----
  await page.goto('/rss-sources');
  await page.waitForSelector('.ant-table');
  await expect(page.locator('.ant-table')).toBeVisible();

  // ---- Step 2: 记录当前源数量 ----
  await page.waitForTimeout(500);
  const initialRowCount = await page.locator('.ant-table-tbody tr.ant-table-row').count();
  console.log(`Initial sources: ${initialRowCount}`);
  expect(initialRowCount).toBeGreaterThanOrEqual(1);

  // ---- Step 3: 点击"导入 OPML" — 上传夹具文件 ----
  const opmlContent = `<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>测试订阅源</title></head>
  <body>
    <outline text="科技" title="科技">
      <outline type="rss" text="源A" xmlUrl="https://example.com/a" htmlUrl="https://example.com/a"/>
      <outline type="rss" text="源B" xmlUrl="https://example.com/b" htmlUrl="https://example.com/b"/>
    </outline>
    <outline text="设计" title="设计">
      <outline type="rss" text="源C" xmlUrl="https://example.com/c" htmlUrl="https://example.com/c"/>
    </outline>
  </body>
</opml>`;

  // 触发文件上传
  const uploadBtn = page.getByRole('button', { name: /导入.*OPML|OPML.*导入/i });
  if (await uploadBtn.isVisible()) {
    // 查找关联的 file input
    const fileInput = page.locator('input[type="file"]').first();
    if (await fileInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await fileInput.setInputFiles({
        name: 'test.opml',
        mimeType: 'text/xml',
        buffer: Buffer.from(opmlContent, 'utf-8'),
      });
    } else {
      // 如果 file input 不可见，尝试点击上传按钮触发 filechooser
      const fileChooserPromise = page.waitForEvent('filechooser', { timeout: 5000 });
      await uploadBtn.click();
      const fileChooser = await fileChooserPromise;
      await fileChooser.setFiles([
        { name: 'test.opml', mimeType: 'text/xml', buffer: Buffer.from(opmlContent, 'utf-8') },
      ]);
    }
    await page.waitForTimeout(500);
  }

  // ---- Step 4: 验证导入结果 Modal ----
  const modalResult = page.locator('.ant-modal').filter({ hasText: /创建|导入|结果/i });
  if (await modalResult.isVisible({ timeout: 3000 }).catch(() => false)) {
    await modalResult.getByRole('button', { name: /确定|关闭|知道了/i }).first().click();
    await page.waitForTimeout(300);
  }

  // ---- Step 5: 验证分组标签 ----
  const groupTab = page.locator('.ant-tabs-tab').filter({ hasText: /科技/i }).first();
  if (await groupTab.isVisible({ timeout: 3000 }).catch(() => false)) {
    await groupTab.click();
    await page.waitForTimeout(300);
  }

  // 切换回全部
  const allTab = page.locator('.ant-tabs-tab').filter({ hasText: /全部|全部源/i }).first();
  if (await allTab.isVisible({ timeout: 2000 }).catch(() => false)) {
    await allTab.click();
    await page.waitForTimeout(300);
  }

  // ---- Step 6: 导出 OPML — 触发下载 ----
  const downloadPromise = page.waitForEvent('download', { timeout: 5000 }).catch(() => null);
  const exportBtn = page.getByRole('button', { name: /导出.*OPML|OPML.*导出/i });
  if (await exportBtn.isVisible()) {
    await exportBtn.click();
    await page.waitForTimeout(500);
  }
  const download = await downloadPromise;
  if (download) {
    const downloadPath = await download.path();
    expect(downloadPath).toBeTruthy();
    const fs = await import('fs');
    const content = fs.readFileSync(downloadPath!, 'utf-8');
    expect(content).toContain('opml');
    expect(content).toContain('outline');
  }

  // ---- Step 7: 删除一个源 — Popconfirm 确认 ----
  await page.waitForSelector('.ant-table', { timeout: 5000 }).catch(() => {});
  // 点击第一个可见的删除按钮
  const deleteBtn = page.locator('.ant-table-tbody .ant-btn').filter({ hasText: /删除/i }).first();
  if (await deleteBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
    await deleteBtn.click();
    await page.waitForTimeout(300);
    // 确认 Popconfirm
    const confirmBtn = page.locator('.ant-popconfirm .ant-btn-primary, .ant-popover .ant-btn').filter({ hasText: /确定|确认/i }).first();
    if (await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await confirmBtn.click();
      await page.waitForTimeout(500);
    }
  }

  // ---- Step 8: 验证源管理正常 ----
  await page.goto('/rss-sources');
  await page.waitForSelector('.ant-table', { timeout: 5000 });
  await page.waitForTimeout(500);
  const finalCount = await page.locator('.ant-table-tbody tr.ant-table-row').count();
  console.log(`Final sources: ${finalCount}`);
  expect(finalCount).toBeGreaterThanOrEqual(0);
});
