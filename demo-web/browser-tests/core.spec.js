import {test, expect} from '@playwright/test';
import {demoUrl} from './urls.js';
import {resolve} from 'node:path';

test('real Wasm core keeps interleaved terminals independent across memory growth and reuse', async ({page}, testInfo) => {
  await testInfo.attach('release-packages', {path: resolve(import.meta.dirname, '../build/browser-test-packages/manifest.json'), contentType: 'application/json'});
  await page.goto(demoUrl(testInfo));
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  const result = await page.evaluate(() => {
    const core = globalThis.__libghosttyWasm;
    const encoder = new TextEncoder();
    const decoder = new TextDecoder();
    function call(op, handle = 0, a = 0, b = 0, c = 0, d = 0, e = 0, f = 0, text = '') {
      const data = encoder.encode(text);
      const ptr = data.length ? core.ghostty_alloc(data.length) : 0;
      try {
        if (data.length) {
          new Uint8Array(core.memory.buffer, ptr, data.length).set(data);
        }
        const value = core.ghostty_call(op, handle, a, b, c, d, e, f, ptr, data.length);
        const bytes = new Uint8Array(core.memory.buffer, core.ghostty_result_ptr(), core.ghostty_result_len()).slice();
        return {value, bytes, text: decoder.decode(bytes)};
      } finally {
        if (ptr) {
          core.ghostty_free(ptr, data.length);
        }
      }
    }
    const first = call(0, 0, 80, 24, 20000, 0, 0, 1, 'browser-test\0xterm-ghostty').value >>> 0;
    const second = call(0, 0, 80, 24, 1000, 0, 0, 1, 'browser-test\0xterm-ghostty').value >>> 0;
    if (!first || !second) {
      throw new Error('Cannot create real test terminals');
    }
    const initialMemory = core.memory.buffer.byteLength;
    let firstFreed = false;
    try {
      call(2, first, 0, 0, 0, 0, 0, 0, '\x1b]2;First\x07first 中文\r\n');
      call(2, second, 0, 0, 0, 0, 0, 0, '\x1b]2;Second\x07second 😀\r\n');
      const large = Array.from({length: 16000}, (_, i) => `line-${i}: ` + 'abcdef'.repeat(10)).join('\r\n');
      call(2, first, 0, 0, 0, 0, 0, 0, large + '\r\nlast-first');
      call(3, first, 100, 30, 8, 16);
      call(2, second, 0, 0, 0, 0, 0, 0, 'last-second');
      const firstText = call(33, first).text;
      const secondText = call(33, second).text;
      const snap = call(20, first, 1024 * 1024, 1);
      const snapshotVersion = new DataView(snap.bytes.buffer).getInt32(0, true);
      const titles = [call(5, first).text, call(5, second).text];
      const grown = core.memory.buffer.byteLength > initialMemory;
      call(1, first);
      firstFreed = true;
      const fresh = call(0, 0, 40, 10, 100, 0, 0, 1, 'browser-test\0xterm-ghostty').value >>> 0;
      try {
        call(2, fresh, 0, 0, 0, 0, 0, 0, 'fresh terminal');
        return {
          firstTail: firstText.endsWith('last-first'),
          secondText,
          titles,
          grown,
          snapshotVersion,
          snapshotResult: snap.value,
          fresh: call(33, fresh).text,
        };
      } finally {
        call(1, fresh);
      }
    } finally {
      if (!firstFreed) {
        call(1, first);
      }
      call(1, second);
    }
  });
  expect(result.firstTail).toBe(true);
  expect(result.secondText).toContain('second 😀');
  expect(result.secondText).toContain('last-second');
  expect(result.secondText).not.toContain('line-');
  expect(result.titles).toEqual(['First', 'Second']);
  expect(result.grown).toBe(true);
  expect(result.snapshotVersion).toBe(3);
  expect(result.snapshotResult).toBeGreaterThan(0);
  expect(result.fresh).toBe('fresh terminal');
  await testInfo.attach('core-results', {body: JSON.stringify(result, null, 2), contentType: 'application/json'});
});

test('standalone package starts and accepts input with networking disabled', async ({page}, testInfo) => {
  await page.context().setOffline(true);
  await page.goto(demoUrl(testInfo));
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('#demo-terminal')).toHaveAttribute('aria-label', /Type help/);
  await page.locator('canvas').click({position: {x: 80, y: 300}});
  await expect(page.locator('textarea')).toBeFocused();
  await page.keyboard.insertText('echo offline');
  await page.evaluate(() => new Promise(requestAnimationFrame));
  await page.keyboard.press('Enter');
  await expect(page.locator('#demo-terminal')).toHaveAttribute('aria-label', /\noffline\n/);
  await page.screenshot({path: `test-results/offline-${testInfo.project.name}.png`});
});

test('failed core initialization clears its pending load and a reload recovers', async ({page}, testInfo) => {
  let attempts = 0;
  await page.exposeFunction('failFirstGhosttyLoad', () => attempts++ === 0);
  await page.addInitScript(() => {
    const instantiate = WebAssembly.instantiate.bind(WebAssembly);
    WebAssembly.instantiate = async (...args) => {
      const result = await instantiate(...args);
      const instance = result.instance || result;
      if (instance.exports.ghostty_abi_version && await globalThis.failFirstGhosttyLoad()) {
        throw new Error('Injected core initialization failure');
      }
      return result;
    };
  });
  await page.goto(demoUrl(testInfo));
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await expect(page.getByText(/Unable to start:.*Injected core initialization failure/)).toBeVisible();
  expect(await page.evaluate(() => globalThis.__libghosttyWasmLoading === undefined && globalThis.__libghosttyWasm === undefined)).toBe(true);
  await page.reload();
  await expect(page.locator('body')).toHaveAttribute('data-ready', 'true');
  await expect(page.locator('#demo-terminal')).toHaveAttribute('aria-label', /Type help/);
  expect(attempts).toBe(2);
});
