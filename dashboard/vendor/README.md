# Vendored dependency

`supabase.js` is `@supabase/supabase-js` 2.116.0's official UMD build
(`dist/umd/supabase.js` from the published npm package), vendored here
rather than loaded from a CDN at runtime (`<script src="vendor/supabase.js">`
exposes the global `supabase.createClient(...)` `app.js` uses).

Vendoring instead of a CDN keeps the dashboard fully self-contained (no
external script host to go down or change its response) and let it be
verified locally end-to-end during development, in an environment whose
network policy didn't allow reaching common CDN hosts (esm.sh,
jsdelivr) but did allow the npm registry the package itself comes from.

To upgrade: `npm install @supabase/supabase-js@<version>` inside
`scripts/push-scan/` (which already depends on it for the seed script),
then copy `node_modules/@supabase/supabase-js/dist/umd/supabase.js`
here, and update the version number in this note. `supabase-js.LICENSE`
is that package's own license file, included per its terms.
