// Parser-level verification of a scoped Bootstrap build produced by scope-bootstrap.mjs: every rule outside @keyframes
// must have every selector start with .orbeon, and no declaration may contain a rem value.
//
// Usage: node tools/scope-bootstrap/verify-scoped.mjs <file.css>

import fs from 'node:fs';
import postcss from 'postcss';
import selectorParser from 'postcss-selector-parser';

const [, , file] = process.argv;
const root = postcss.parse(fs.readFileSync(file, 'utf8'), { from: file });

const isKeyframes = (node) => {
  for (let p = node.parent; p; p = p.parent)
    if (p.type === 'atrule' && /-?keyframes$/.test(p.name)) return true;
  return false;
};

let rules = 0;
const badSelectors = [];
const badRems = [];

root.walkRules((rule) => {
  if (isKeyframes(rule)) return;
  rules++;
  selectorParser((selectors) => {
    selectors.each((selector) => {
      const first = selector.first;
      if (!(first.type === 'class' && first.value === 'orbeon'))
        badSelectors.push(selector.toString().trim());
    });
  }).processSync(rule.selector);
});

root.walkDecls((decl) => {
  if (/\brem\b|[0-9]rem\b/.test(decl.value)) badRems.push(`${decl.prop}: ${decl.value}`);
});

console.log(`${file}`);
console.log(`  rules checked        : ${rules}`);
console.log(`  unscoped selectors   : ${badSelectors.length}`);
badSelectors.slice(0, 10).forEach((s) => console.log(`    ! ${s}`));
console.log(`  rem values remaining : ${badRems.length}`);
badRems.slice(0, 10).forEach((s) => console.log(`    ! ${s}`));
process.exit(badSelectors.length || badRems.length ? 1 : 0);
