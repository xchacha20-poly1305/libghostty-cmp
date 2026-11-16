import {test, expect} from '@playwright/test';
import {pagesUrl} from './urls.js';

const transport = pagesUrl.startsWith('http') ? 'http' : 'file';

test('Pages offers two demo routes without downloads', async ({page}, testInfo) => {
  const target = testInfo.project.metadata.target;
  const requested = [];
  page.on('request', request => requested.push(request.url()));
  await page.goto(pagesUrl);
  await expect(page.locator('h1')).toHaveText('Choose a terminal demo');
  await expect(page.locator('a')).toHaveCount(2);
  await expect(page.locator('a[download], a[href$=".zip"]')).toHaveCount(0);
  expect(requested.some(url => url.endsWith('.wasm') || url.endsWith('demo-web.js'))).toBe(false);
  // Explicit index.html also works when Pages is inspected directly through file://.
  const link = page.locator(`a[href="${target}/"]`);
  const destination = await link.evaluate(a => a.href);
  await page.goto(destination + 'index.html');
  await expect(page.locator('html')).toHaveAttribute('data-kotlin-target', target);
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await page.reload();
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await page.getByRole('button', {name: /^Run example$/}).dispatchEvent('click');
  await expect(page.locator('#demo-terminal')).toHaveAttribute('aria-label', /True-color gradient/);
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await page.screenshot({path: `test-results/pages-demo-${transport}-${testInfo.project.name}.png`});
  await page.getByRole('button', {name: /Home/}).dispatchEvent('click');
  await page.waitForURL('**/index.html');
  await expect(page.locator('h1')).toHaveText('Choose a terminal demo');
  await page.screenshot({path: `test-results/pages-${transport}-${testInfo.project.name}.png`});
});

test('Pages follows Chinese browser language and system appearance', async ({browser}, testInfo) => {
  const context = await browser.newContext({locale: 'zh-CN', colorScheme: 'light'});
  const page = await context.newPage();
  try {
    await page.goto(pagesUrl);
    await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
    await expect(page.locator('h1')).toHaveText('选择终端演示版本');
    const light = await page.locator('html').evaluate(el => getComputedStyle(el).backgroundColor);
    await page.emulateMedia({colorScheme: 'dark'});
    const dark = await page.locator('html').evaluate(el => getComputedStyle(el).backgroundColor);
    expect(dark).not.toBe(light);
    await page.screenshot({path: `test-results/pages-zh-${transport}-${testInfo.project.name}.png`});
  } finally {
    await context.close();
  }
});
