#!/usr/bin/env python3
"""Read-only source lock check. Baseline changes require Ada's explicit approval."""

import hashlib
import json
from pathlib import Path
import re
import sys


def normalize(text):
    return text.replace('\r\n', '\n').replace('\r', '\n')


def digest(text):
    return hashlib.sha256(normalize(text).encode('utf-8')).hexdigest()


def mask_java(text):
    # Keep source offsets while ignoring braces inside Java comments/literals.
    tokens = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(tokens, lambda match: ''.join('\n' if c == '\n' else ' ' for c in match[0]), text)


def method_span(source, signature):
    masked = mask_java(source)
    if masked.count(signature) != 1:
        raise ValueError('Method signature missing or ambiguous: ' + signature)
    start = masked.index(signature)
    opening = masked.index('{', start)
    depth = 1
    for pos in range(opening + 1, len(masked)):
        if masked[pos] == '{':
            depth += 1
        elif masked[pos] == '}':
            depth -= 1
            if depth == 0:
                return start, pos + 1
    raise ValueError('Unclosed method: ' + signature)


def check(root):
    baseline = json.loads((root / 'tools/captcha-shield-lock.json').read_text(encoding='utf-8'))
    if baseline['formatVersion'] != 1:
        raise ValueError('Unsupported source lock format')
    coverage = {}
    failures = []
    for item in baseline['checks']:
        path = (root / item['file']).resolve()
        if not path.is_relative_to(root.resolve()):
            raise ValueError('Lock entry outside repository')
        source = normalize(path.read_text(encoding='utf-8'))
        if 'method' in item:
            start, end = method_span(source, item['method'])
        else:
            start, end = 0, len(source)
        if digest(source[start:end]) != item['sha256']:
            failures.append(item['file'] + (' :: ' + item['method'] if 'method' in item else ''))
        coverage.setdefault(path, []).append((start, end))

    core = root / 'src/main/java/org/b3log/symphony/util/AnonymousCaptchaShield.java'
    version = re.search(r'public static final int POLICY_VERSION = (\d+);', core.read_text(encoding='utf-8'))
    if version is None or int(version[1]) != baseline['policyVersion']:
        failures.append('Policy version mismatch')

    for path in (root / 'src/main/java').rglob('*.java'):
        code = mask_java(normalize(path.read_text(encoding='utf-8')))
        for match in re.finditer(r'\bAnonymousCaptchaShield\s*\.', code):
            if not any(start <= match.start() < end for start, end in coverage.get(path.resolve(), [])):
                failures.append(str(path.relative_to(root)) + ' :: unprotected shield reference')

    if failures:
        raise ValueError('Captcha protection changed. Explicit Ada approval is required; do not regenerate the baseline automatically:\n'
                         + '\n'.join(sorted(set(failures))))
    print('PASS: captcha policy version %d, %d protected source entries; all production shield references covered'
          % (baseline['policyVersion'], len(baseline['checks'])))


if __name__ == '__main__':
    try:
        check(Path(__file__).resolve().parents[1])
    except (OSError, ValueError, KeyError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        sys.exit(1)
