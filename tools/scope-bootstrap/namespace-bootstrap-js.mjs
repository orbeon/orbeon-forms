// Transform a stock Bootstrap 5 dist JS bundle into the Orbeon-namespaced build (#7809), so that it can't clash with a
// Bootstrap copy loaded by the host page when Orbeon is embedded:
//
// - the data-bs- attribute prefix becomes data-orbeon-bs-, both in literal strings/selectors and in the Manipulator
//   dataset logic (dataset keys bs* become orbeonBs*);
// - the bundle is exposed as ORBEON.bootstrap instead of window.bootstrap;
// - the sourceMappingURL comment is dropped, as the transformed file no longer matches the dist map.
//
// Works on both bootstrap.bundle.js and bootstrap.bundle.min.js. Every replacement asserts its expected match count,
// so a Bootstrap version bump that changes these patterns fails loudly instead of silently producing a broken build,
// and the output is syntax-checked with `node --check`.
//
// Usage: node tools/scope-bootstrap/namespace-bootstrap-js.mjs <in.js> <out.js>

import fs from 'node:fs';
import { spawnSync } from 'node:child_process';

const ATTR_PREFIX    = 'data-orbeon-bs-'; // replaces data-bs-
const DATASET_PREFIX = 'orbeonBs';        // its camelCased dataset form, replaces bs

const [, , inFile, outFile] = process.argv;
if (!inFile || !outFile) {
  console.error('Usage: namespace-bootstrap-js.mjs <in.js> <out.js>');
  process.exit(1);
}

let src = fs.readFileSync(inFile, 'utf8');

const replaceCounted = (what, re, replacement, expected) => {
  const count = (src.match(re) ?? []).length;
  if (expected === 'many' ? count === 0 : count !== expected) {
    console.error(`error: expected ${expected} match(es) for ${what}, found ${count}: ${re}`);
    process.exit(1);
  }
  src = src.replace(re, replacement);
  console.log(`  ${what}: ${count} replacement(s)`);
};

// Attribute names in template literals, selector strings, and setAttribute/getAttribute calls
replaceCounted('data-bs- prefix', /data-bs-/g, ATTR_PREFIX, 'many');

// Manipulator.getDataAttributes reads element.dataset keys, where data-orbeon-bs-* camelCases to orbeonBs*
replaceCounted('dataset key filter', /startsWith\((['"])bs\1\)/g, `startsWith($1${DATASET_PREFIX}$1)`, 1);
replaceCounted('dataset key strip', /replace\(\/\^bs\/, ?(['"])\1\)/g, `replace(/^${DATASET_PREFIX}/, $1$1)`, 1);

// UMD footer: expose the bundle as ORBEON.bootstrap (full, then minified form)
if (src.includes('global.bootstrap = factory()'))
  replaceCounted('UMD global', /global\.bootstrap = factory\(\)/g, '(global.ORBEON = global.ORBEON || {}).bootstrap = factory()', 1);
else
  replaceCounted('UMD global', /:\((\w+)=("undefined"!=typeof globalThis\?globalThis:\1\|\|self)\)\.bootstrap=(\w+)\(\)/g, ':(($1=$2).ORBEON=$1.ORBEON||{}).bootstrap=$3()', 1);

src = src.replace(/\/\/# sourceMappingURL=\S+\n?$/, '');

if (src.includes('data-bs-') || /[^.\w]bootstrap ?=/.test(src)) {
  console.error('error: untransformed data-bs- or bootstrap global assignment remains');
  process.exit(1);
}

fs.writeFileSync(outFile, src);

const check = spawnSync(process.execPath, ['--check', outFile], { encoding: 'utf8' });
if (check.status !== 0) {
  fs.unlinkSync(outFile);
  console.error(`error: output is not valid JavaScript:\n${check.stderr}`);
  process.exit(1);
}

console.log(`wrote ${outFile}`);
