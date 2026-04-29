#!/usr/bin/env node

import { readFile, writeFile } from 'node:fs/promises'

const COMMENT_VERSION_FILES = [
  'fishpi-auto-red-packet.js',
  'gulpfile.js',
  'src/main/resources/js/activity.js',
  'src/main/resources/js/add-article.js',
  'src/main/resources/js/article.js',
  'src/main/resources/js/breezemoon.js',
  'src/main/resources/js/channel-2.js',
  'src/main/resources/js/channel.js',
  'src/main/resources/js/chat-room-2.js',
  'src/main/resources/js/chat-room.js',
  'src/main/resources/js/common.js',
  'src/main/resources/js/eating-snake.js',
  'src/main/resources/js/gobang.js',
  'src/main/resources/js/m-article.js',
  'src/main/resources/js/settings.js',
  'src/main/resources/js/shop.js',
  'src/main/resources/js/symbol-defs.js',
  'src/main/resources/js/verify.js',
]

const TEXT_REPLACEMENTS = [
  {
    file: 'src/main/resources/games/catchTheCat/catch-the-cat.js',
    replacements: [
      [/<\?xml version="1\.0" encoding="UTF-8" standalone="no"\?>\\n/g, ''],
    ],
  },
  {
    file: 'src/main/resources/games/catchTheCat/phaser.min.js',
    replacements: [
      [/\bVERSION:"[^"]+"/g, 'VERSION:""'],
      [/\bversion:"[^"]+"/g, 'version:""'],
      [/\bversion="[^"]+"/g, 'version=""'],
      [/@\^[0-9]+\.[0-9]+\.[0-9]+/g, ''],
    ],
  },
  {
    file: 'src/main/resources/games/emojiPair/game.js',
    replacements: [
      [/^\*[ \t]*VERSION:.*\r?\n/m, ''],
      [/\bversion:"[^"]+"/g, 'version:""'],
      [/\bversion="[^"]+"/g, 'version=""'],
    ],
  },
  {
    file: 'src/main/resources/js/lib/aplayer/APlayer.min.js',
    replacements: [[/\s+version="1\.1"/g, '']],
  },
  {
    file: 'src/main/resources/js/lib/diff2html/diff2html.min.js',
    replacements: [
      [/\bVERSION="[^"]+"/g, 'VERSION=""'],
      [/\s+version="1\.1"/g, ''],
    ],
  },
  {
    file: 'src/main/resources/js/lib/lottie.min.js',
    replacements: [[/\bversion="[^"]+"/g, 'version=""']],
  },
  {
    file: 'src/main/resources/js/lib/livephotoskit.js',
    replacements: [
      [/t\.a="[0-9]+\.[0-9]+\.[0-9]+"/g, 't.a=""'],
      [/\n?\/\/# sourceMappingURL=.*\.map\s*$/m, ''],
    ],
  },
  {
    file: 'src/main/resources/js/symbol-defs.js',
    replacements: [[/\s+version="1\.1"/g, '']],
  },
]

function removeCommentVersions(text) {
  return text.replace(/^\s*(?:\/\/|\*)\s*@version\b.*\r?\n/gm, '')
}

function applyReplacements(text, replacements) {
  return replacements.reduce((current, [pattern, replacement]) => {
    return current.replace(pattern, replacement)
  }, text)
}

async function rewriteFile(file, transform) {
  const original = await readFile(file, 'utf8')
  const updated = transform(original)
  if (updated === original) {
    return false
  }
  await writeFile(file, updated, 'utf8')
  return true
}

const changed = []
for (const file of COMMENT_VERSION_FILES) {
  if (await rewriteFile(file, removeCommentVersions)) {
    changed.push(file)
  }
}
for (const { file, replacements } of TEXT_REPLACEMENTS) {
  if (await rewriteFile(file, (text) => applyReplacements(text, replacements))) {
    changed.push(file)
  }
}
console.log(`已清理文件数：${changed.length}`)
changed.forEach((file) => console.log(file))
