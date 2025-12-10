let loadedFont = null; // opentype.Font

// 读取 ArrayBuffer 并解析字体
async function loadFontFromArrayBuffer(ab) {
  try {
    // opentype.parse 接受 ArrayBuffer 或者 Uint8Array
    const font = opentype.parse(ab);
    loadedFont = font;
    const family = (font.names && font.names.fontFamily && font.names.fontFamily.en) || 'Unknown';
    console.log('字体加载成功：' + family);
  } catch (e) {
    console.log('解析字体失败：' + (e && e.message ? e.message : e));
    loadedFont = null;
  }
}

function loadFont(e) {
  return new Promise((resolve, reject) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    const reader = new FileReader();
    reader.onload = function(evt) {
      const ab = evt.target.result;
      loadFontFromArrayBuffer(ab);
      resolve(f);
    };
    reader.onerror = function(_, e) { reject(e); };
    reader.readAsArrayBuffer(f);
  });
}

// 将 ArrayBuffer 转为 Base64 data URL
function arrayBufferToBase64DataUrl(ab, mime = 'font/ttf') {
  const bytes = new Uint8Array(ab);
  // 分块以避免 apply 限制
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

// 深拷贝 path.commands（避免引用原对象）
function clonePathCommands(path) {
  if (!path || !path.commands) return [];
  return path.commands.map(cmd => Object.assign({}, cmd));
}

// 递归收集复合字形引用的所有 glyph indices（处理 components）
function collectReferencedGlyphIndexes(origGlyph, font, set) {
  // origGlyph.index 在 opentype.js 中是 glyph 的索引（如果存在）
  if (!origGlyph) return;
  if (typeof origGlyph.index === 'number') set.add(origGlyph.index);
  // components: some fonts store components in origGlyph.components (array)
  if (origGlyph.components && origGlyph.components.length) {
    for (const comp of origGlyph.components) {
      // comp.glyphIndex 或 comp.glyph 取决实现，在 opentype.js 解析后通常是 {glyphIndex: N, xScale..., ...}
      const gi = comp.glyphIndex || comp.glyph && comp.glyph.index;
      if (typeof gi === 'number' && !set.has(gi)) {
        const g = font.glyphs.glyphs[gi];
        if (g) collectReferencedGlyphIndexes(g, font, set);
      }
    }
  }
  // 有些字体把 components 存在 .references 或 .componentGlyphs, 尝试兼容
  if (origGlyph.references && origGlyph.references.length) {
    for (const r of origGlyph.references) {
      if (r.index != null && !set.has(r.index)) {
        const g = font.glyphs.glyphs[r.index];
        if (g) collectReferencedGlyphIndexes(g, font, set);
      }
    }
  }
}

// 根据原 glyph 创建一个可插入到新字体的 glyph 对象
function makeGlyphForNewFont(origGlyph) {
  // Create minimal glyph keeping name, unicode, advanceWidth, path
  const pathCommands = clonePathCommands(origGlyph.path);
  // Build new opentype.Path and new opentype.Glyph
  const newPath = new opentype.Path();
  newPath.commands = pathCommands;
  const newGlyph = new opentype.Glyph({
    name: origGlyph.name,
    unicode: origGlyph.unicode,
    advanceWidth: origGlyph.advanceWidth,
    path: newPath
  });
  return newGlyph;
}

// 构建子集字体（处理复合 glyph 的依赖）
function buildSubsetFont(originalFont, chars) {
  // unique chars
  const uniqueChars = Array.from(new Set(Array.from(chars)));
  // 要收集的 glyph 索引集合（包含 .notdef）
  const glyphIndexSet = new Set();

  // 保证包含 .notdef（glyph 0）
  if (originalFont.glyphs && originalFont.glyphs.glyphs && originalFont.glyphs.glyphs.length > 0) {
    glyphIndexSet.add(0);
  }

  // 对每个字符找到对应的 glyph index，并收集其引用的组件
  for (const ch of uniqueChars) {
    const glyph = originalFont.charToGlyph(ch);
    if (!glyph) continue;
    // glyph.index 可能为 undefined for some parsed fonts; try to find by unicode
    let gi = typeof glyph.index === 'number' ? glyph.index : null;
    if (gi === null) {
      // as fallback, find first glyph in font.glyphs.glyphs with same unicode
      if (originalFont.glyphs && originalFont.glyphs.glyphs) {
        for (let i = 0; i < originalFont.glyphs.glyphs.length; i++) {
          const g = originalFont.glyphs.glyphs[i];
          if (g && g.unicode === glyph.unicode) { gi = i; break; }
        }
      }
    }
    if (gi !== null && gi !== undefined) {
      if (!glyphIndexSet.has(gi)) {
        // recursively collect component references
        collectReferencedGlyphIndexes(originalFont.glyphs.glyphs[gi], originalFont, glyphIndexSet);
      }
    } else {
      // as a last resort add glyph itself (no index), will still copy using charToGlyph
      // but opentype.Font expects glyphs array with indices; we'll handle below by mapping unicodes
      // We'll add by unicode set later.
    }
  }

  // 保证包含空格（unicode 32）如果输入中没有
  const hasSpace = uniqueChars.includes(' ') || uniqueChars.some(c => c.charCodeAt(0) === 32);
  if (!hasSpace) {
    const spaceGlyph = originalFont.charToGlyph(' ');
    if (spaceGlyph && spaceGlyph.unicode === 32) {
      if (typeof spaceGlyph.index === 'number') glyphIndexSet.add(spaceGlyph.index);
    }
  }

  // Build new glyph list in index order from glyphIndexSet
  const glyphIndexes = Array.from(glyphIndexSet).sort((a, b) => a - b);
  const newGlyphs = [];

  // Map from old index to new index (for potential future use)
  const oldToNewIndex = Object.create(null);

  // Copy glyphs preserving basic fields
  glyphIndexes.forEach((oldIndex, newIndex) => {
    const origGlyph = originalFont.glyphs.glyphs[oldIndex];
    const g = makeGlyphForNewFont(origGlyph || {});
    newGlyphs.push(g);
    oldToNewIndex[oldIndex] = newIndex;
  });

  // For any requested characters whose glyph had no index, ensure their unicodes are present:
  // (We'll add glyphs found via charToGlyph if their unicode isn't present yet)
  const existingUnicodes = new Set(newGlyphs.map(g => g.unicode).filter(u => u != null));
  for (const ch of uniqueChars) {
    const gOrig = originalFont.charToGlyph(ch);
    if (!gOrig) continue;
    if (gOrig.unicode != null && !existingUnicodes.has(gOrig.unicode)) {
      // add it
      newGlyphs.push(makeGlyphForNewFont(gOrig));
      existingUnicodes.add(gOrig.unicode);
    }
  }

  // Construct new font object with metadata taken from original
  const family = (originalFont.names && originalFont.names.fontFamily && originalFont.names.fontFamily.en) || 'SubsetFont';
  const style = (originalFont.names && originalFont.names.fontSubfamily && originalFont.names.fontSubfamily.en) || 'Subset';
  const newFont = new opentype.Font({
    familyName: family + '-subset',
    styleName: style,
    unitsPerEm: originalFont.unitsPerEm || 1000,
    ascender: originalFont.ascender || 800,
    descender: originalFont.descender || -200,
    glyphs: newGlyphs
  });

  // 导出为 ArrayBuffer（TTF）
  const arrayBuffer = newFont.toArrayBuffer();
  return arrayBuffer;
}

function getFontDataUrlForText(text) {
  if (!loadedFont) return null;
  const subsetAb = buildSubsetFont(loadedFont, text);
  const dataUrl = arrayBufferToBase64DataUrl(subsetAb, 'font/ttf');
  return dataUrl;
}