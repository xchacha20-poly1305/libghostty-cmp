Bundled font (SIL Open Font License 1.1)

NotoSansMono-Latin.woff2
Family: Noto Sans Mono CJK SC (Latin, marks, punctuation, box drawing)
Source: NotoSansMonoCJKsc-Regular.otf, subsetted then compressed to WOFF2.
Noto Sans Mono CJK SC: https://github.com/notofonts/noto-cjk
See OFL.txt for the license.

Regenerate:
  pyftsubset NotoSansMonoCJKsc-Regular.otf \
    --unicodes=U+0020-007E,U+00A0-00FF,U+0100-024F,U+02C6-02DA,U+02DC,U+0300-036F,U+2000-206F,U+2070-209F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2200-22FF,U+2300-23FF,U+2500-259F,U+25A0-25FF,U+2600-26FF,U+FB00-FB04 \
    --layout-features='*' --output-file=NotoSansMono-Latin.otf
  woff2_compress NotoSansMono-Latin.otf

CJK (Noto Sans SC/JP/KR) and Noto Color Emoji are fetched at runtime from
jsDelivr and Google Fonts. They are also SIL OFL. Without a network the demo
keeps this Latin face; Compose may still download Noto subsets for chrome text.
