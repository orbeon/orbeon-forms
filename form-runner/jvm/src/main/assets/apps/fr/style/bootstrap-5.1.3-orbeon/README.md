# Orbeon-scoped Bootstrap 5 build

Generated from the pristine `../bootstrap-5.1.3-dist/css/bootstrap[.min].css` by
`tools/scope-bootstrap/scope-bootstrap.mjs`:

- every selector is scoped under `.orbeon`, so nothing leaks into a host page when Orbeon is
  embedded (like the Bootstrap 2 era `.orbeon`-scoped build);
- `:root` and `body` map onto `.orbeon` itself, which also makes the `--bs-*` variables visible
  to the PDF renderer (it doesn't resolve `:root`-defined variables);
- all `rem` values are converted to `px` at the 13px base, so no root font-size rescale is needed.

To regenerate (e.g. after a Bootstrap version bump), from the repo root:

    node tools/scope-bootstrap/scope-bootstrap.mjs \
      form-runner/jvm/src/main/assets/apps/fr/style/bootstrap-5.1.3-dist/css/bootstrap.css \
      form-runner/jvm/src/main/assets/apps/fr/style/bootstrap-5.1.3-orbeon/css/bootstrap.css

(and the same for `bootstrap.min.css`), then verify with `tools/scope-bootstrap/verify-scoped.mjs`.
