#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const args = process.argv.slice(2);
const ignoredDirs = new Set([
  '.git',
  '.idea',
  '.understand-anything',
  '.vscode',
  'frontend/node_modules',
  'node_modules',
  'target',
  'dist',
]);

function isIgnored(relativePath) {
  return relativePath
    .split(path.sep)
    .some((part, index, parts) => ignoredDirs.has(parts.slice(0, index + 1).join(path.sep)) || ignoredDirs.has(part));
}

function collectMarkdownFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const absolute = path.join(dir, entry.name);
    const relative = path.relative(root, absolute);

    if (isIgnored(relative)) {
      continue;
    }

    if (entry.isDirectory()) {
      files.push(...collectMarkdownFiles(absolute));
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      files.push(absolute);
    }
  }

  return files;
}

function stripFencedCode(markdown) {
  return markdown.replace(/```[\s\S]*?```/g, '');
}

function extractTarget(rawTarget) {
  const trimmed = rawTarget.trim();

  if (trimmed.startsWith('<')) {
    const end = trimmed.indexOf('>');
    return end === -1 ? trimmed.slice(1) : trimmed.slice(1, end);
  }

  return trimmed.split(/\s+/)[0];
}

function isExternal(target) {
  return /^(https?:|mailto:|tel:|data:|javascript:)/i.test(target);
}

function removeFragmentAndQuery(target) {
  const withoutFragment = target.split('#')[0];
  return withoutFragment.split('?')[0];
}

function resolveTarget(file, target) {
  const cleaned = removeFragmentAndQuery(target);

  if (!cleaned || cleaned.startsWith('#') || isExternal(cleaned)) {
    return null;
  }

  const decoded = decodeURIComponent(cleaned);
  if (decoded.startsWith('/')) {
    return null;
  }

  return path.resolve(path.dirname(file), decoded);
}

const files = args.length > 0
  ? args.map((file) => path.resolve(root, file))
  : collectMarkdownFiles(root);

const missing = [];
const linkPattern = /!?\[[^\]]*]\(([^)]+)\)/g;

for (const file of files) {
  const markdown = stripFencedCode(fs.readFileSync(file, 'utf8'));
  let match;

  while ((match = linkPattern.exec(markdown)) !== null) {
    const target = extractTarget(match[1]);
    const resolved = resolveTarget(file, target);

    if (resolved && !fs.existsSync(resolved)) {
      missing.push({
        file: path.relative(root, file),
        target,
        resolved: path.relative(root, resolved),
      });
    }
  }
}

if (missing.length > 0) {
  console.error('Missing Markdown link targets:');
  for (const item of missing) {
    console.error(`- ${item.file}: ${item.target} -> ${item.resolved}`);
  }
  process.exit(1);
}

console.log(`Checked ${files.length} Markdown file(s). All relative link targets exist.`);
