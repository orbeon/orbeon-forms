# Orbeon-scoped Bootstrap 5 build

## CSS

Generated from the pristine `../bootstrap-5.3.8-dist/css/bootstrap[.min].css` by
`tools/scope-bootstrap/scope-bootstrap.mjs`:

- every selector is scoped under `.orbeon`, so nothing leaks into a host page when Orbeon is
  embedded (like the Bootstrap 2 era `.orbeon`-scoped build);
- `:root` and `body` map onto `.orbeon` itself, which also makes the `--bs-*` variables visible
  to the PDF renderer (it doesn't resolve `:root`-defined variables);
- all `rem` values are converted to `px` at the 13px base, so no root font-size rescale is needed;
- `data-bs-*` attribute selectors become `data-orbeon-bs-*`, matching the namespaced JS bundle
  below (#7809); this includes the `data-bs-theme` color mode selectors (new in 5.3), which stay
  inert as Orbeon has its own color scheme mechanism (`fr-color-scheme-*`);
- the `sourceMappingURL` comment is dropped, as no source map is shipped for the transformed build.

## JS

Generated from the pristine `../bootstrap-5.3.8-dist/js/bootstrap.bundle[.min].js` by
`tools/scope-bootstrap/namespace-bootstrap-js.mjs`:

- the `data-bs-` attribute prefix becomes `data-orbeon-bs-` (and dataset keys `bs*` become
  `orbeonBs*`, including the `bsConfig` exclusion added in 5.2), so a Bootstrap copy loaded by
  the host page doesn't handle Orbeon's markup, and vice versa (#7809);
- the bundle is exposed as `ORBEON.bootstrap` instead of `window.bootstrap`, so it doesn't
  overwrite the host page's copy.

## Regenerating

To regenerate (e.g. after a Bootstrap version bump), from the repo root:

    node tools/scope-bootstrap/scope-bootstrap.mjs \
      form-runner/jvm/src/main/assets/apps/fr/style/bootstrap-5.3.8-dist/css/bootstrap.css \
      form-runner/jvm/src/main/assets/apps/fr/style/bootstrap-5.3.8-orbeon/css/bootstrap.css

    node tools/scope-bootstrap/namespace-bootstrap-js.mjs \
      form-runner/jvm/src/main/assets/apps/fr/style/bootstrap-5.3.8-dist/js/bootstrap.bundle.js \
      form-runner/jvm/src/main/assets/apps/fr/style/bootstrap-5.3.8-orbeon/js/bootstrap.bundle.js

(and the same for the `.min` variants), then verify the CSS with `tools/scope-bootstrap/verify-scoped.mjs`.
