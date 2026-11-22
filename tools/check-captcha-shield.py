#!/usr/bin/env python3
"""Read-only source lock check. Baseline changes require Ada's explicit approval."""

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
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


LOCK_FILE = 'tools/captcha-shield-lock.json'
JAVA_ROOT = 'src/main/java'
CORE = JAVA_ROOT + '/org/b3log/symphony/util/AnonymousCaptchaShield.java'
PROTECTED_REFERENCES = re.compile(r'\b(?:AnonymousCaptchaShield\s*(?:\.|::)|UserIpLeaseService\s*(?:\.|::)\s*renew\b)')


def indexed_sources(root):
    def git(*args, data=None):
        return subprocess.run(['git', '-C', str(root), *args], input=data,
                              stdout=subprocess.PIPE, check=True).stdout

    entries = []
    for record in git('ls-files', '--stage', '-z', '--', LOCK_FILE, JAVA_ROOT).split(b'\0'):
        if not record:
            continue
        metadata, name = record.split(b'\t', 1)
        if name != LOCK_FILE.encode() and not name.endswith(b'.java'):
            continue
        mode, oid, stage = metadata.split()
        if stage != b'0' or mode not in (b'100644', b'100755'):
            raise ValueError('Unmerged or non-regular index entry: ' + name.decode('utf-8'))
        entries.append((name.decode('utf-8'), oid))

    blobs = io.BytesIO(git('cat-file', '--batch', data=b''.join(oid + b'\n' for _, oid in entries)))
    sources = {}
    for name, oid in entries:
        header = blobs.readline().split()
        if len(header) != 3 or header[:2] != [oid, b'blob']:
            raise ValueError('Invalid index blob: ' + name)
        size = int(header[2])
        content = blobs.read(size)
        if len(content) != size or blobs.read(1) != b'\n':
            raise ValueError('Incomplete index blob: ' + name)
        sources[name] = normalize(content.decode('utf-8'))
    return sources


def check(root, staged=False):
    if staged:
        sources = indexed_sources(root)
        read = sources.__getitem__
        java_files = (name for name in sources if name.startswith(JAVA_ROOT + '/'))
    else:
        def read(name):
            path = (root / name).resolve()
            if not path.is_relative_to(root.resolve()):
                raise ValueError('Lock entry outside repository')
            return normalize(path.read_text(encoding='utf-8'))

        java_files = (path.relative_to(root).as_posix() for path in (root / JAVA_ROOT).rglob('*.java'))

    baseline = json.loads(read(LOCK_FILE))
    if baseline['formatVersion'] != 1:
        raise ValueError('Unsupported source lock format')
    coverage = {}
    failures = []
    for item in baseline['checks']:
        name = item['file']
        source = read(name)
        if 'method' in item:
            start, end = method_span(source, item['method'])
        else:
            start, end = 0, len(source)
        if digest(source[start:end]) != item['sha256']:
            failures.append(item['file'] + (' :: ' + item['method'] if 'method' in item else ''))
        coverage.setdefault(name, []).append((start, end))

    version = re.search(r'public static final int POLICY_VERSION = (\d+);', read(CORE))
    if version is None or int(version[1]) != baseline['policyVersion']:
        failures.append('Policy version mismatch')

    for name in java_files:
        code = mask_java(read(name))
        for match in PROTECTED_REFERENCES.finditer(code):
            if not any(start <= match.start() < end for start, end in coverage.get(name, [])):
                failures.append(name + ' :: unprotected shield or lease renewal reference')

    if failures:
        raise ValueError('Captcha protection changed. Explicit Ada approval is required; do not regenerate the baseline automatically:\n'
                         + '\n'.join(sorted(set(failures))))
    print('PASS (%s): captcha policy version %d, %d protected source entries; shield and lease renewal references covered'
          % ('index' if staged else 'worktree', baseline['policyVersion'], len(baseline['checks'])))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staged', action='store_true', help='check Git index blobs instead of working files')
    try:
        check(Path(__file__).resolve().parents[1], parser.parse_args().staged)
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        sys.exit(1)
