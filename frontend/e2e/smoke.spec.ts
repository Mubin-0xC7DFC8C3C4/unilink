import { test, expect } from '@playwright/test';
test('landing loads', async ({ page }) => { await page.goto('/'); await expect(page.getByRole('heading', { name: /UniLink/i }).first()).toBeVisible(); });
test('admin login loads', async ({ page }) => { await page.goto('/admin/login'); await expect(page.getByRole('heading', { name: /Admin log in/i }).first()).toBeVisible(); });
