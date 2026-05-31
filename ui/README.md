# plugin-prov-outscale — Vue UI

Tool-level, i18n-only plugin (`service:prov:outscale`), the Outscale
provider for the `prov` service. Compiled to `webjars/prov-outscale/vue/`.

The legacy `outscale.js` was an empty `define({})`, parameter.csv is empty,
and the `prov` parent has no delegation hook, so this plugin ships only the
single `service:prov:outscale:name` label. `requires: ['prov']`.

```bash
npm install && npm run build && npm run lint && npm test
```
