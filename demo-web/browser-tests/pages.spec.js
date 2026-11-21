import {test, expect} from '@playwright/test';
import {pagesUrl} from './urls.js';

test.use({locale: 'zh-CN'});

test('English documentation links to the real demo and back', async ({page, request}, testInfo) => {
  const target = testInfo.project.metadata.target;
  await page.goto(pagesUrl);
  const docsHome = new URL('.', page.url());
  const content = page.locator('#content');
  const demoLinks = page.locator('#leftColumn').getByRole('group', {name: 'Demos'});
  const apiPackage = 'io.github.xchacha20_poly1305.libghostty';
  const openReference = async (...names) => {
    await page.goto(docsHome.href);
    for (const name of names) {
      await content.getByRole('link', {name, exact: true}).first().click();
    }
  };

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#sideMenu').getByRole('link', {name: 'libghostty', exact: true})).toBeVisible();
  await expect(demoLinks.getByRole('link')).toHaveCount(2);
  await expect(content.getByRole('heading', {name: 'Quick start', exact: true})).toBeVisible();
  await expect(content.getByText('The API is unstable and may change in future releases.')).toBeVisible();
  await expect(content.locator('a[download], a[href$=".zip"]')).toHaveCount(0);

  await openReference('libghostty', apiPackage);
  for (const name of ['GhosttyKey', 'GhosttyMods']) {
    await expect(content.getByRole('link', {name, exact: true}).first()).toBeVisible();
  }
  await content.getByRole('link', {name: 'GhosttyWebAssembly', exact: true}).first().click();
  await expect(content.getByText('Since', {exact: true})).toBeVisible();
  await expect(content.getByText('0.5.0', {exact: true})).toBeVisible();
  await openReference('libghostty-compose', apiPackage + '.compose', 'GhosttyTerminalState', 'linkHoverUnderlineEnabled');
  await expect(content.getByText('Since', {exact: true})).toBeVisible();
  await expect(content.getByText('0.5.1', {exact: true})).toBeVisible();
  await openReference('libghostty', apiPackage, 'GhosttyTerminalSession', 'PasteProtection');
  await expect(content.getByText('Since', {exact: true}).first()).toBeVisible();
  await expect(content.getByText('0.5.2', {exact: true}).first()).toBeVisible();
  await openReference('libghostty-extras', apiPackage + '.extras', 'GhosttyThemeStore');
  await expect(content.locator('h1')).toHaveText('GhosttyThemeStore');

  for (const oldTarget of ['js', 'wasm']) {
    const response = await request.get(new URL(`${oldTarget}/`, docsHome).href);
    expect(response.status()).toBe(404);
  }

  await demoLinks.getByRole('link', {name: target === 'js' ? 'Kotlin/JS demo' : 'Kotlin/Wasm demo'}).click();
  await expect(page).toHaveURL(new URL(`demo/${target}/`, docsHome).href);
  await expect(page.locator('html')).toHaveAttribute('data-kotlin-target', target);
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  // Firefox keeps the pointer over the demo toolbar after the sidebar click.
  // Move it away so a tooltip does not cover the accessibility tree after reload.
  await page.mouse.move(0, 0);
  await page.reload();
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await page.getByRole('button', {name: /^运行示例$/}).dispatchEvent('click');
  await expect(page.locator('#demo-terminal')).toHaveAttribute('aria-label', /True-color gradient/);
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await page.screenshot({path: testInfo.outputPath('demo.png')});

  await page.getByRole('button', {name: /文档/}).dispatchEvent('click');
  await expect(page).toHaveURL(new URL('index.html', docsHome).href);
  await expect(content.getByRole('heading', {name: 'Quick start', exact: true})).toBeVisible();
  await page.screenshot({path: testInfo.outputPath('documentation.png')});
});
