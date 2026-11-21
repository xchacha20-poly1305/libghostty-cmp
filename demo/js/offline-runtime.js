// The package is a static file:// site. Resource bytes are loaded through classic scripts, not file Fetch.
(() => {
  const assets = globalThis.__ghosttyAssets;
  const aliases = globalThis.__ghosttyAssetAliases;
  const decoded = new Map();
  const keys = Object.keys(assets).sort((a, b) => b.length - a.length);
  const nativeFetch = globalThis.fetch.bind(globalThis);
  const isFontHost = hostname =>
    hostname === 'fonts.googleapis.com' || hostname === 'fonts.gstatic.com' ||
    hostname === 'jsdelivr.net' || hostname.endsWith('.jsdelivr.net');
  globalThis.__ghosttyAsset = name => {
    const key = aliases[name] || name;
    if (!assets[key]) throw new Error('Missing packaged resource: ' + key);
    if (!decoded.has(key)) {
      const raw = atob(assets[key][1]);
      decoded.set(key, Uint8Array.from(raw, ch => ch.charCodeAt(0)));
    }
    return decoded.get(key);
  };
  // Packaged assets stay local. CJK/emoji webfonts may be fetched from the font CDNs.
  globalThis.fetch = async input => {
    const url = new URL(typeof input === 'string' ? input : (input.url || String(input)), document.baseURI);
    const path = decodeURIComponent(url.pathname);
    const key = keys.find(key => path.endsWith('/' + key));
    if (key && ['file:', 'http:', 'https:'].includes(url.protocol) &&
        (url.protocol === 'file:' || url.origin === location.origin)) {
      return new Response(globalThis.__ghosttyAsset(key), {headers: {'Content-Type': assets[key][0]}});
    }
    if (url.protocol === 'https:' && isFontHost(url.hostname)) {
      return nativeFetch(input);
    }
    throw new Error('Resource is not included in this offline build: ' + url.href);
  };
})();
