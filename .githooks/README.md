# .githooks

Versioned, shareable git hooks for this repo (plain `.git/hooks/*` is local-only and not
committed, so it can't be the source of truth for a team hook).

## One-time setup

Run once per machine/clone:

```sh
git config core.hooksPath .githooks
```

This makes git use `.githooks/` instead of `.git/hooks/` for hook lookup. It is a local
git config value — it is not (and cannot be) committed, so every contributor must run it once.

## What's here

| Hook | Purpose |
|---|---|
| `pre-commit` | Runs [gitleaks](https://github.com/gitleaks/gitleaks) against **staged** content and blocks the commit if it finds a likely secret. If `gitleaks` is not installed locally, it warns and lets the commit through — it is a fast local pre-check, not the real enforcement backstop. |

## Real enforcement

Local hooks can always be skipped (`git commit --no-verify`, hook not installed, etc.), so the
actual backstop is the `secret-scan` job in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml),
which runs unconditionally in CI regardless of any developer's local setup.

See [docs/08-security/OWASP.md](../docs/08-security/OWASP.md) section 2 for the full picture
(why GitHub's native Secret Scanning isn't available on this private repo, and what replaces it).

## Installing gitleaks locally (optional, recommended)

Download a prebuilt binary from the [gitleaks releases page](https://github.com/gitleaks/gitleaks/releases)
and put it on your `PATH`. No package manager integration is assumed here (none was available in
the environment this hook was authored/verified in) — a plain downloaded binary is enough.
