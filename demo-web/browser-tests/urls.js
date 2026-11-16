import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export function demoUrl(testInfo) {
  const target = testInfo.project.metadata.target;
  return process.env[`GHOSTTY_DEMO_${target.toUpperCase()}_URL`] ||
    pathToFileURL(resolve(import.meta.dirname, `../build/browser-test-packages/${target}/index.html`)).href;
}
export const pagesUrl = process.env.GHOSTTY_PAGES_URL ||
  pathToFileURL(resolve(import.meta.dirname, '../build/pages/index.html')).href;
