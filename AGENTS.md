# Agent rules — VPN Switcher

Android/Kotlin (Gradle). There is **no** `package.json` / npm in this repo.

## Dependencies

Before adding or bumping a library:

1. Resolve the **current latest stable** from the real registry (Google Maven / Maven Central) — never invent a version from memory.
2. Prefer an already-declared dependency or AndroidX / Kotlin stdlib over a new one.
3. After changing `app/build.gradle.kts` (or root plugins), **keep `deps-scan/pom.xml` in sync** (same coordinates + versions) so CI can scan them.
4. Run a vulnerability scan on the declared set:

```bash
osv-scanner scan source --lockfile=deps-scan/pom.xml
```

Fail the change if critical/high issues appear (or ask the human before shipping).

5. Periodically check for outdated libs (manual equivalent of npm-check-updates): compare pinned versions in `app/build.gradle.kts` against Google Maven / Maven Central metadata. Bump only with a reason; do not drive-by upgrade AGP/Kotlin major lines.

Do **not** add npm, Node, or unrelated package managers to this project.

## Secrets

- Never commit `keystore.properties`, `keystore/`, or real signing material (already gitignored).
- Do not hardcode tokens, API keys, or passwords.
- Pre-commit runs gitleaks (see `.githooks/`). CI also runs gitleaks on every push/PR.

## Local hooks

```bash
git config core.hooksPath .githooks
```

Requires `gitleaks` on PATH (`brew install gitleaks`).
