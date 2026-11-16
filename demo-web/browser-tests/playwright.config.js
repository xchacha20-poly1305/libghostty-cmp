import {defineConfig} from '@playwright/test';
export default defineConfig({
  testDir: '.',
  reporter: [['list'], ['html', {open: 'never'}], ['json', {outputFile: 'test-results/results.json'}]],
  testMatch: '*.spec.js',
  timeout: 60000,
  expect: {timeout: 12000},
  workers: 1,
  use: {viewport: {width: 1280, height: 900}, locale: 'en-US', colorScheme: 'dark', trace: 'retain-on-failure'},
  projects: ['js', 'wasm'].flatMap(target => [
    {name: `${target}-chromium`, metadata: {target}, use: {browserName: 'chromium'}},
    {name: `${target}-firefox`, metadata: {target}, use: {browserName: 'firefox', headless: !process.env.GHOSTTY_FIREFOX_HEADED, launchOptions: {firefoxUserPrefs: {'webgl.force-enabled': true}}}},
    {name: `${target}-webkit`, metadata: {target}, use: {browserName: 'webkit', launchOptions: process.env.GHOSTTY_WEBKIT_EXECUTABLE ? {executablePath: process.env.GHOSTTY_WEBKIT_EXECUTABLE} : {}}},
  ]),
});
