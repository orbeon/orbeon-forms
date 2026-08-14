// Transform a stock Bootstrap 5 dist CSS file into the Orbeon-scoped, px-valued build:
//
// - every selector is scoped under .orbeon, so nothing leaks into a host page when Orbeon is embedded (like the
//   Bootstrap 2 era .orbeon-scoped build);
// - :root, body, and html selectors are mapped onto .orbeon itself, so the wrapper element carries the base styles and
//   CSS variables (this also makes the --bs-* variables visible to the PDF renderer, which doesn't resolve
//   :root-defined variables);
// - a selector starting with * also gets a variant for .orbeon itself;
// - all rem values are converted to px at the legacy 13px base, removing the need to rescale the root font size (which
//   isn't possible when embedded);
// - data-bs-* attribute selectors become data-orbeon-bs-*, matching the namespaced JS bundle produced by
//   namespace-bootstrap-js.mjs (#7809);
// - the sourceMappingURL comment is dropped, as no source map is shipped for the transformed build.
//
// Usage: node tools/scope-bootstrap/scope-bootstrap.mjs <in.css> <out.css>
//
// Uses the repo's own node_modules (postcss, postcss-selector-parser).

import fs from 'node:fs';
import postcss from 'postcss';
import selectorParser from 'postcss-selector-parser';

const SCOPE = '.orbeon';
const BASE_PX = 13;

const [, , inFile, outFile] = process.argv;
if (!inFile || !outFile) {
  console.error('Usage: scope-bootstrap.mjs <in.css> <out.css>');
  process.exit(1);
}

const remToPx = (value) =>
  value.replace(/(\d*\.?\d+)rem\b/g, (_, n) => `${+(parseFloat(n) * BASE_PX).toFixed(4)}px`);

const isKeyframes = (node) => {
  for (let p = node.parent; p; p = p.parent)
    if (p.type === 'atrule' && /-?keyframes$/.test(p.name)) return true;
  return false;
};

const scopeClassName = (lead = '') => {
  const node = selectorParser.className({ value: SCOPE.slice(1) });
  node.spaces.before = lead;
  return node;
};

const transformSelector = selectorParser((selectors) => {
  selectors.walkAttributes((attr) => {
    if (attr.attribute.startsWith('data-bs-'))
      attr.attribute = attr.attribute.replace(/^data-bs-/, 'data-orbeon-bs-');
  });
  selectors.each((selector) => {
    const first = selector.first;
    const lead = first.spaces.before; // keep the dist's one-selector-per-line formatting
    first.spaces.before = '';
    if (
      (first.type === 'pseudo' && first.value === ':root') ||
      (first.type === 'tag' && (first.value === 'body' || first.value === 'html'))
    ) {
      // :root / body / html become the wrapper itself
      first.replaceWith(scopeClassName(lead));
    } else if (first.type === 'universal') {
      // *... gets a variant for the wrapper itself, plus the scoped original
      const wrapperVariant = selector.clone();
      wrapperVariant.first.replaceWith(scopeClassName(lead));
      selector.parent.insertBefore(selector, wrapperVariant);
      selector.prepend(selectorParser.combinator({ value: ' ' }));
      selector.prepend(scopeClassName(lead));
    } else {
      selector.prepend(selectorParser.combinator({ value: ' ' }));
      selector.prepend(scopeClassName(lead));
    }
  });
});

const root = postcss.parse(fs.readFileSync(inFile, 'utf8'), { from: inFile });

root.walkRules((rule) => {
  if (!isKeyframes(rule)) rule.selector = transformSelector.processSync(rule.selector);
});
root.walkDecls((decl) => {
  decl.value = remToPx(decl.value);
});
root.walkComments((comment) => {
  if (comment.text.startsWith('# sourceMappingURL=')) comment.remove();
});

fs.writeFileSync(outFile, root.toString());
console.log(`wrote ${outFile}`);
