import {test, expect} from '@playwright/test';
import {demoUrl} from './urls.js';

const screen = page => page.locator('#demo-terminal');
const size = page => page.getByText(/\d+\s*×\s*\d+/);

async function command(page, text) {
  await page.keyboard.insertText(text);
  // Firefox's insertText can produce an IME commit. Let Compose process that frame before Enter.
  await page.evaluate(() => new Promise(requestAnimationFrame));
  await page.keyboard.press('Enter');
}

async function focusTerminal(page, timeout) {
  const canvas = page.locator('canvas');
  const box = await canvas.boundingBox();
  await canvas.click({position: {x: 48, y: Math.round((box?.height || 900) * 0.55)}});
  await expect(page.locator('textarea')).toBeFocused({timeout});
}

// Compose draws chrome on the Skia canvas and exposes actions through its a11y nodes.
function chromeButton(page, name) {
  return page.getByRole('button', {name});
}

async function clickChrome(page, name) {
  // The a11y tree has pointer-events:none. A forced mouse click hits the canvas,
  // not this node; dispatch to the semantic action explicitly in every browser.
  await chromeButton(page, name).dispatchEvent('click');
}

test.beforeEach(async ({page}, testInfo) => {
  const remote = [];
  page.on('request', request => { if (/^https?:/.test(request.url())) remote.push(request.url()); });
  page.on('pageerror', error => { throw error; });
  await page.addInitScript(() => {
    try {
      localStorage.removeItem('ghostty-demo-language');
      localStorage.removeItem('ghostty-demo-theme');
    } catch (error) {}
  });
  // file:// still runs without a local server. CJK/emoji webfonts may be fetched from font CDNs.
  await page.goto(demoUrl(testInfo));
  await expect(page.locator('html')).toHaveAttribute('data-kotlin-target', testInfo.project.metadata.target);
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await expect(screen(page)).toHaveAttribute('aria-label', /Type help/);
  await page.bringToFront();
  await focusTerminal(page);
  expect(remote.filter(url => !/fonts\.googleapis\.com|fonts\.gstatic\.com|jsdelivr\.net/.test(url))).toEqual([]);
});

test('keyboard, Chinese text, clear focus and Tab completion', async ({page}) => {
  await command(page, 'echo keyboard 中文');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nkeyboard 中文\n/);
  await clickChrome(page, /^Clear$/);
  await expect(screen(page)).toHaveAttribute('aria-label', /^guest:~ \$$/);
  await page.keyboard.type('pw');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\n\/home\/guest\n/);
  await page.keyboard.type('cat examples/a');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /Bold.*Italic.*Underline/s);
});

test('showcase, emoji shaping, command history and control bytes', async ({page}) => {
  await clickChrome(page, /^Run example$/);
  await expect(screen(page)).toHaveAttribute('aria-label', /😀 😎 🚀 🎉 ❤️ 👍🏽 👨‍👩‍👧‍👦 🇨🇳 🇺🇳/);
  await page.screenshot({path: `test-results/showcase-${test.info().project.name}.png`});
  await clickChrome(page, /^Clear$/);
  await command(page, 'echo -e "12345\\rABC"');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nABC45\n/);
  await page.keyboard.press('ArrowUp');
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /ABC45\nguest:~ \$ echo -e/);
});

test('Run example menu opens the real paste protection dialog', async ({page}, testInfo) => {
  const menu = chromeButton(page, /^Example options$/);
  const action = page.getByText('Simulate paste protection', {exact: true});
  await expect(menu).toBeVisible();
  await expect(action).toBeHidden();
  const menuBounds = await menu.boundingBox();
  await page.mouse.click(menuBounds.x + menuBounds.width / 2, menuBounds.y + menuBounds.height / 2);
  await expect(action).toBeVisible();
  await page.screenshot({path: testInfo.outputPath('example-menu.png')});
  const actionBounds = await action.boundingBox();
  await page.mouse.click(actionBounds.x + actionBounds.width / 2, actionBounds.y + actionBounds.height / 2);
  await expect(page.getByText('Paste this text?', {exact: true})).toBeVisible();
  await expect(page.getByText(/echo 'Paste protection example: ␛\[201~'/)).toBeVisible();
  await page.screenshot({path: testInfo.outputPath('example-paste-protection.png')});
});

test('composition commits once and resize preserves terminal content', async ({page}) => {
  await page.keyboard.type('echo ');
  const editor = page.locator('textarea');
  await editor.evaluate(element => {
    element.dispatchEvent(new CompositionEvent('compositionstart', {data: '', bubbles: true}));
    element.dispatchEvent(new InputEvent('beforeinput', {inputType: 'insertCompositionText', data: '你好', isComposing: true, bubbles: true, cancelable: true}));
    element.dispatchEvent(new CompositionEvent('compositionupdate', {data: '你好', bubbles: true}));
    element.dispatchEvent(new CompositionEvent('compositionend', {data: '你好', bubbles: true}));
  });
  // Compose batches native IME events per animation frame; submit only after composition has committed.
  await expect(screen(page)).toHaveAttribute('aria-label', /echo 你好$/);
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\n你好\n/);
  const oldSize = await size(page).textContent();
  await page.setViewportSize({width: 600, height: 850});
  await expect(size(page)).not.toHaveText(oldSize);
  await expect(screen(page)).toHaveAttribute('aria-label', /你好/);
  await focusTerminal(page);
  await command(page, 'echo resized');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nresized\n/);
});

async function pasteText(page, text) {
  await page.evaluate(text => {
    const clipboardData = new DataTransfer();
    clipboardData.setData('text/plain', text);
    const event = new ClipboardEvent('paste', {clipboardData, bubbles: true, cancelable: true, composed: true});
    // Firefox discards the constructor's data on synthetic clipboard events.
    Object.defineProperty(event, 'clipboardData', {value: clipboardData});
    document.dispatchEvent(event);
  }, text);
}

test('paste events send text through terminal paste encoding', async ({page}) => {
  await pasteText(page, 'echo pasted');
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\npasted\n/);
});

test('paste confirmation follows bracketed mode and sends only approved text', async ({page}, testInfo) => {
  const cancelButton = chromeButton(page, /^Cancel$/);
  // The modal replaces the page's accessibility tree. Read the demo's live terminal through
  // Ghostty's viewport operation so assertions also work while the dialog is open.
  await page.evaluate(() => {
    const core = globalThis.__libghosttyWasm;
    let terminal;
    globalThis.__libghosttyWasm = {
      ...core,
      ghostty_call(op, handle, ...args) {
        if (op === 2) terminal = handle; // write
        return core.ghostty_call(op, handle, ...args);
      },
    };
    globalThis.readPasteTestViewport = () => {
      if (!terminal) return '';
      core.ghostty_call(30, terminal, 0, 0, 0, 0, 0, 0, 0, 0); // viewport_text
      return new TextDecoder().decode(new Uint8Array(
        core.memory.buffer, core.ghostty_result_ptr(), core.ghostty_result_len(),
      )).trimEnd();
    };
  });
  const viewport = () => page.evaluate(() => globalThis.readPasteTestViewport());
  async function clickDialogButton(name) {
    const button = chromeButton(page, name);
    await expect(button).toBeVisible();
    // Compose positions the accessibility node over the corresponding button on the dialog canvas.
    const box = await button.boundingBox();
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  }

  await pasteText(page, 'echo bracketed\npaste');
  await expect(screen(page)).toHaveAttribute('aria-label', /echo bracketed paste$/);
  await expect(cancelButton).toHaveCount(0);
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nbracketed paste\n/);

  const beforeCancel = await viewport();
  await pasteText(page, 'echo cancelled\x1b[201~\n');
  await expect(cancelButton).toBeVisible();
  await expect(page.getByText(/echo cancelled␛\[201~/)).toBeVisible();
  expect(await viewport()).toBe(beforeCancel);
  await clickDialogButton(/^Cancel$/);
  // A closing Compose dialog can still intercept the first pointer event during its exit animation.
  await expect(() => focusTerminal(page, 500)).toPass({timeout: 5000});
  expect(await viewport()).toBe(beforeCancel);

  await command(page, 'echo -e "\\e[?2004l"');
  await expect.poll(viewport).toMatch(/2004l"\n\nguest:~ \$$/);
  const beforeApproval = await viewport();
  await pasteText(page, 'echo approved one\necho approved two\r');
  await expect(cancelButton).toBeVisible();
  expect(await viewport()).toBe(beforeApproval);
  await page.screenshot({path: testInfo.outputPath('paste-confirmation.png')});
  await clickDialogButton(/^Paste$/);
  await expect.poll(viewport).toMatch(/\napproved one\n.*\napproved two\n/);
  await page.screenshot({path: testInfo.outputPath('paste-approved.png')});
});

test('OSC 52 clipboard writes require confirmation', async ({page}) => {
  const dialog = page.waitForEvent('dialog');
  const typing = command(page, 'echo -e "\\e]52;c;SGVsbG8=\\x07"');
  const prompt = await dialog;
  expect(prompt.type()).toBe('confirm');
  expect(prompt.message()).toContain('replace your clipboard');
  await prompt.dismiss();
  await typing;
  await command(page, 'echo still running');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nstill running\n/);
});

for (const waitForApproval of [false, true]) {
  test(`paste button uses the asynchronous clipboard host${waitForApproval ? ' after approval' : ''}`, async ({page}) => {
    const beforePaste = await screen(page).getAttribute('aria-label');
    await page.evaluate(waitForApproval => {
      globalThis.clipboardReadCount = 0;
      Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {
        readText: () => {
          globalThis.clipboardReadCount++;
          // Firefox's native Paste menu leaves readText pending until the user approves.
          // It is browser UI, outside Playwright's page locators, so model that boundary here.
          return waitForApproval
            ? new Promise(resolve => { globalThis.approveClipboardRead = resolve; })
            : Promise.resolve('echo async paste');
        }
      }});
    }, waitForApproval);
    await clickChrome(page, /^Paste$/);
    await expect.poll(() => page.evaluate(() => globalThis.clipboardReadCount)).toBe(1);
    if (waitForApproval) {
      await expect(screen(page)).toHaveAttribute('aria-label', beforePaste);
      await page.evaluate(() => globalThis.approveClipboardRead('echo async paste'));
    }
    await expect(screen(page)).toHaveAttribute('aria-label', /echo async paste$/);
    await expect(page.locator('textarea')).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(screen(page)).toHaveAttribute('aria-label', /\nasync paste\n/);
    expect(await page.evaluate(() => globalThis.clipboardReadCount)).toBe(1);
  });
}

test('clipboard unavailability and denied access recover before a successful paste', async ({page}) => {
  for (const mode of ['unavailable', 'unsupported', 'denied']) {
    await page.evaluate(mode => {
      const clipboard = mode === 'unavailable' ? undefined
        : mode === 'unsupported' ? {}
        : {readText: () => Promise.reject(new DOMException('Denied', 'NotAllowedError'))};
      Object.defineProperty(navigator, 'clipboard', {configurable: true, value: clipboard});
    }, mode);
    await clickChrome(page, /^Paste$/);
    await expect(page.locator('textarea')).toBeFocused();
    await command(page, `echo recovered-${mode}`);
    await expect(screen(page)).toHaveAttribute('aria-label', new RegExp(`\\nrecovered-${mode}\\n`));
  }
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {
      readText: () => Promise.resolve('echo clipboard recovered')
    }});
  });
  await clickChrome(page, /^Paste$/);
  await expect(screen(page)).toHaveAttribute('aria-label', /echo clipboard recovered$/);
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nclipboard recovered\n/);
});

test('browser paste shortcut does not also read the clipboard API', async ({page}) => {
  await page.evaluate(() => {
    globalThis.clipboardReadCount = 0;
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {
      readText: () => { globalThis.clipboardReadCount++; return Promise.resolve('duplicate'); }
    }});
  });
  await page.locator('textarea').evaluate(editor => {
    editor.dispatchEvent(new KeyboardEvent('keydown', {key: 'V', code: 'KeyV', ctrlKey: true, shiftKey: true, bubbles: true, composed: true}));
    const clipboardData = new DataTransfer();
    clipboardData.setData('text/plain', 'echo once');
    const event = new ClipboardEvent('paste', {clipboardData, bubbles: true, cancelable: true, composed: true});
    // Firefox discards the constructor's data on synthetic clipboard events.
    Object.defineProperty(event, 'clipboardData', {value: clipboardData});
    editor.dispatchEvent(event);
    editor.dispatchEvent(new KeyboardEvent('keyup', {key: 'V', code: 'KeyV', ctrlKey: true, shiftKey: true, bubbles: true, composed: true}));
  });
  await expect(screen(page)).toHaveAttribute('aria-label', /echo once$/);
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nonce\n/);
  expect(await page.evaluate(() => globalThis.clipboardReadCount)).toBe(0);
});

test.describe('browser language and color scheme defaults', () => {
  test.use({locale: 'zh-CN', colorScheme: 'light'});
  test('chrome follows Chinese locale and light appearance', async ({page}) => {
    await expect(chromeButton(page, /^清屏$/)).toBeVisible();
    await expect(chromeButton(page, /^运行示例$/)).toBeVisible();
    await expect(chromeButton(page, /^跟随系统$/)).toBeVisible();
    await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe('rgb(250, 250, 251)');
  });
});


test('compact layout extra keys complete, recall and cancel input without losing focus', async ({page}) => {
  await expect(chromeButton(page, /^TAB$/)).toHaveCount(0);
  await page.setViewportSize({width: 412, height: 850});
  await expect(chromeButton(page, /^TAB$/)).toBeVisible();
  await expect(page.locator('canvas')).toHaveJSProperty('clientHeight', 850);
  await focusTerminal(page);
  await page.keyboard.type('pw');
  await expect(screen(page)).toHaveAttribute('aria-label', /\$ pw$/);
  const tab = await chromeButton(page, /^TAB$/).boundingBox();
  await page.mouse.click(tab.x + tab.width / 2, tab.y + tab.height / 2);
  await expect(page.locator('textarea')).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(screen(page)).toHaveAttribute('aria-label', /\n\/home\/guest\n/);
  await clickChrome(page, /^▲$/);
  await expect(screen(page)).toHaveAttribute('aria-label', /\$ pwd$/);
  await clickChrome(page, /^CTRL$/);
  await page.keyboard.type('c');
  await expect(screen(page)).toHaveAttribute('aria-label', /\^C\nguest:~ \$$/);
  await command(page, 'echo ordinary');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nordinary\n/);
  await page.setViewportSize({width: 1280, height: 900});
  await expect(chromeButton(page, /^TAB$/)).toHaveCount(0);
});


test('Ctrl hover underlines links and releasing Ctrl restores the text', async ({page}) => {
  await clickChrome(page, /^Clear$/);
  await command(page, 'echo https://example.com');
  await expect(screen(page)).toHaveAttribute('aria-label', /\nhttps:\/\/example\.com\n/);
  const box = await screen(page).boundingBox();
  const dimensions = (await size(page).textContent()).match(/(\d+)\s*×\s*(\d+)/);
  const cellWidth = (box.width - 36) / Number(dimensions[1]);
  const cellHeight = (box.height - 36) / Number(dimensions[2]);
  // The URL is on row 1 after clearing. Capture its right half, away from the prompt cursor.
  const clip = {
    x: Math.ceil(box.x + 18 + 12 * cellWidth),
    y: Math.ceil(box.y + 18 + cellHeight / 2),
    width: Math.floor(5 * cellWidth),
    height: Math.floor(2 * cellHeight),
  };
  await page.mouse.move(box.x + 18 + 5 * cellWidth, box.y + 18 + 1.5 * cellHeight);
  const original = await page.screenshot({clip});
  // The pointer stays still: a modifier key alone must invalidate the decoration.
  await page.keyboard.down('Control');
  await expect.poll(async () => (await page.screenshot({clip})).equals(original)).toBe(false);
  await page.keyboard.up('Control');
  await expect.poll(async () => (await page.screenshot({clip})).equals(original)).toBe(true);
  await page.keyboard.down('Control');
  await expect.poll(async () => (await page.screenshot({clip})).equals(original)).toBe(false);
  await page.mouse.move(1, 1);
  await expect.poll(async () => (await page.screenshot({clip})).equals(original)).toBe(true);
  await page.keyboard.up('Control');
});
