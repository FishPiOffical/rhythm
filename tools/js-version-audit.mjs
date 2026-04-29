#!/usr/bin/env node

import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join, relative, sep } from 'node:path'

const DEFAULT_EXCLUDES = new Set([
  '.git',
  '.codex-tasks',
  'node_modules',
  'target',
])
const MAX_SNIPPET_LENGTH = 180
const SEMVER = String.raw`v?\d+\.\d+(?:\.\d+){0,3}(?:[-+._][0-9A-Za-z][0-9A-Za-z.-]*)?`
const STRICT_SEMVER = String.raw`(?:v\d+\.\d+(?:\.\d+){0,3}|\d+\.\d+\.\d+(?:\.\d+){0,2})(?:[-+._][0-9A-Za-z][0-9A-Za-z.-]*)?`
const FILE_NAME_VERSION = new RegExp(String.raw`(?:^|[._@-])${SEMVER}(?=(?:\.min)?\.js$|[._-])`, 'i')
const CONTENT_PATTERNS = [
  {
    type: 'comment-version',
    pattern: new RegExp(String.raw`@version\s+${SEMVER}`, 'i'),
  },
  {
    type: 'label-version',
    pattern: new RegExp(String.raw`\bversion\b\s*[:=]\s*['"]?${SEMVER}`, 'i'),
  },
  {
    type: 'constant-version',
    pattern: new RegExp(String.raw`\b[A-Z0-9_]*VERSION[A-Z0-9_]*\b\s*[:=]\s*['"]?${SEMVER}`),
  },
  {
    type: 'banner-version',
    pattern: new RegExp(String.raw`\b(?:jQuery|Zepto|Vue|React|Angular|Bootstrap|Swagger|Vditor|ECharts|APlayer|NProgress|Cropper|Viewer|Lottie|SweetAlert2|Algolia|Autocomplete|Diff2Html|UAParser|Phaser)\b[^\\n]{0,80}\b${STRICT_SEMVER}`, 'i'),
  },
  {
    type: 'source-map-version',
    pattern: /sourceMappingURL=.*\.map/i,
  },
  {
    type: 'known-library-version',
    pattern: new RegExp(String.raw`\bLivePhotosKit\b.*\bt\.a\s*=\s*['"]${SEMVER}['"]`, 'i'),
  },
]

function parseArgs(argv) {
  const args = { root: process.cwd(), jsonPath: '', text: true }
  for (let index = 2; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--root') {
      args.root = argv[index + 1]
      index += 1
      continue
    }
    if (arg === '--json') {
      args.jsonPath = argv[index + 1]
      index += 1
      continue
    }
    if (arg === '--quiet') {
      args.text = false
      continue
    }
    throw new Error(`未知参数：${arg}`)
  }
  return args
}

function toRelativePath(root, filePath) {
  return relative(root, filePath).split(sep).join('/')
}

function isExcluded(pathPart) {
  return DEFAULT_EXCLUDES.has(pathPart)
}

async function collectJsFiles(root, dir = root) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    if (entry.isDirectory()) {
      if (isExcluded(entry.name)) {
        continue
      }
      files.push(...await collectJsFiles(root, join(dir, entry.name)))
      continue
    }
    if (entry.isFile() && entry.name.endsWith('.js')) {
      files.push(join(dir, entry.name))
    }
  }
  return files
}

function splitLogicalLines(content) {
  if (content.includes('\n')) {
    return content.split(/\r?\n/u)
  }
  return [content]
}

function trimSnippet(value) {
  const singleLine = value.replace(/\s+/gu, ' ').trim()
  if (singleLine.length <= MAX_SNIPPET_LENGTH) {
    return singleLine
  }
  return `${singleLine.slice(0, MAX_SNIPPET_LENGTH)}...`
}

function isProtocolVersion(match, line) {
  if (/version[:=]?["']?1\.0/i.test(match) && (line.includes('addXmlDeclaration') || line.includes('<?xml version='))) {
    return true
  }
  return match.includes('version=3.1.0') && line.includes('application/vnd.oai.openapi')
}

function scanFileName(root, filePath) {
  const relPath = toRelativePath(root, filePath)
  const name = relPath.slice(relPath.lastIndexOf('/') + 1)
  const match = name.match(FILE_NAME_VERSION)
  if (!match) {
    return []
  }
  return [{
    path: relPath,
    type: 'file-name-version',
    line: 0,
    match: match[0],
    snippet: name,
  }]
}

function scanContent(root, filePath, content) {
  const relPath = toRelativePath(root, filePath)
  const lines = splitLogicalLines(content)
  const findings = []
  const seen = new Set()
  lines.forEach((line, index) => {
    for (const { type, pattern } of CONTENT_PATTERNS) {
      const globalPattern = new RegExp(pattern.source, pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`)
      for (const match of line.matchAll(globalPattern)) {
        const key = `${index + 1}:${match[0]}`
      if (seen.has(key)) {
        continue
      }
      if (isProtocolVersion(match[0], line)) {
        continue
      }
      seen.add(key)
        findings.push({
          path: relPath,
          type,
          line: index + 1,
          match: match[0],
          snippet: trimSnippet(line),
        })
      }
    }
  })
  return findings
}

async function scan(root) {
  const files = await collectJsFiles(root)
  const findings = []
  for (const filePath of files) {
    findings.push(...scanFileName(root, filePath))
    const content = await readFile(filePath, 'utf8')
    findings.push(...scanContent(root, filePath, content))
  }
  return {
    root,
    scannedFileCount: files.length,
    findingCount: findings.length,
    findings,
  }
}

async function writeJsonReport(path, report) {
  await mkdir(dirname(path), { recursive: true })
  await writeFile(path, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
}

function printSummary(report) {
  const byType = report.findings.reduce((counts, finding) => {
    counts[finding.type] = (counts[finding.type] || 0) + 1
    return counts
  }, {})
  console.log(`扫描 JS 文件数：${report.scannedFileCount}`)
  console.log(`版本号命中数：${report.findingCount}`)
  Object.entries(byType).forEach(([type, count]) => {
    console.log(`${type}: ${count}`)
  })
  report.findings.slice(0, 30).forEach((finding) => {
    const location = finding.line > 0 ? `${finding.path}:${finding.line}` : finding.path
    console.log(`${location} [${finding.type}] ${finding.match}`)
  })
}

const args = parseArgs(process.argv)
const report = await scan(args.root)
if (args.jsonPath) {
  await writeJsonReport(args.jsonPath, report)
}
if (args.text) {
  printSummary(report)
}
