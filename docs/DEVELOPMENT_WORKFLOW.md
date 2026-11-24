# Development Workflow

This project follows a **trunk-based development** workflow. This document outlines the branch strategy, development practices, and CI/CD processes.

## Branch Strategy

### Trunk Branch: `master`

- **Always releasable** - The `master` branch should always be in a deployable state
- **Protected** - Direct pushes should be avoided; use pull requests
- **Main integration point** - All changes merge into `master`
- **Production-ready** - Code on `master` can be released at any time

### Short-Lived Branches

All feature work happens in short-lived branches that are merged back to `master` quickly (typically within days, not weeks).

#### Branch Types

1. **`feature/<slug>`** - New features and enhancements
   - Examples: `feature/new-player-ui`, `feature/playlist-import`
   - Use for: Adding new functionality, UI improvements, major enhancements

2. **`fix/<slug>`** - Bug fixes
   - Examples: `fix/youtube-oauth`, `fix/memory-leak`
   - Use for: Fixing bugs, resolving issues, patching vulnerabilities

3. **`chore/<slug>`** - Maintenance tasks
   - Examples: `chore/update-dependencies`, `chore/refactor-audio-handler`
   - Use for: Code cleanup, refactoring, dependency updates, documentation

4. **`deps/<slug>`** - Dependency experiments
   - Examples: `deps/youtube-source-pr195`, `deps/test-jda-6.4`
   - Use for: Testing dependency updates, experimenting with forks, evaluating new libraries

5. **`release/<version>`** - Release stabilization (optional)
   - Examples: `release/0.6.3`, `release/0.6.3-rc1`
   - Use for: Stabilizing a release, release candidates, hotfixes for specific versions
   - **Note**: Only create when you need to stabilize a release. Most releases can go directly from `master`

### Branch Naming Rules

- **Format**: `<type>/<descriptive-slug>`
- **Slug requirements**:
  - Lowercase letters, numbers, and hyphens only
  - Descriptive and concise (e.g., `new-player-ui`, not `ui` or `new-feature`)
  - No underscores or special characters
- **Examples**:
  - Γ£à `feature/new-player-ui`
  - Γ£à `fix/youtube-oauth-error`
  - Γ£à `chore/update-maven-plugins`
  - Γ£à `deps/test-lavaplayer-2.3`
  - Γ£à `release/0.6.3`
  - Γ¥î `feature/newFeature` (uppercase)
  - Γ¥î `fix/bug_123` (underscore)
  - Γ¥î `new-feature` (missing type prefix)
  - Γ¥î `feature/new feature` (spaces)

## Development Process

### 1. Starting Work

```bash
# Create a branch from master
git checkout master
git pull origin master
git checkout -b feature/my-new-feature

# Or for a bug fix
git checkout -b fix/bug-description
```

### 2. Making Changes

- Make small, focused commits
- Write clear commit messages
- Keep the branch up-to-date with `master`:
  ```bash
  git checkout master
  git pull origin master
  git checkout feature/my-new-feature
  git rebase master  # or git merge master
  ```

### 3. Testing Locally

- Run tests: `mvn verify`
- Test Docker build: `docker build -t jmusicbot:test .`
- Verify the bot works as expected

### 4. Creating a Pull Request

- Push your branch: `git push origin feature/my-new-feature`
- Create a PR targeting `master`
- The CI will:
  - Γ£à Validate branch naming
  - Γ£à Run tests and build
  - Γ£à Build Docker image (tagged with branch name)
- Wait for CI to pass and code review

### 5. Merging

- Once approved and CI passes, merge the PR
- **Prefer squash merge** to keep history clean
- Delete the branch after merging (GitHub can do this automatically)

## CI/CD Workflows

### Branch Validation

The `validate-branch-naming.yml` workflow automatically validates branch names on:
- Pull requests (when opened, updated, or edited)
- Direct pushes to non-master branches

**What it checks:**
- Branch name matches allowed patterns
- Slug uses only lowercase, numbers, and hyphens
- Proper type prefix is used

### Build and Test

The `build-and-test.yml` workflow runs on:
- Pushes to `master` and all short-lived branches
- Pull requests targeting `master`

**What it does:**
- Compiles the project
- Runs unit and integration tests
- Generates code coverage reports
- Uploads coverage to Codecov

### Docker Build

`docker-publish.yml` publishes images to `ghcr.io/arif-banai/musicbot`:
- Release tags (e.g., `v0.9.0`, pushed when a release is published): `:0.9.0`, plus `:latest` when that tag is
  the repository's latest GitHub release. Pre-releases and rebuilds of older tags never move `:latest`.
- Pushes to `master` (after Build and Test passes): `:master`. Master builds never get a version tag, so they
  can't overwrite a released version.
- Every build also gets `:sha-<short sha>`.

`publish-preview-image.yml` (run by hand) publishes any branch, tag or SHA as `:preview-<ref>`.

## Best Practices

### Keep Branches Short-Lived

- **Goal**: Merge within days, not weeks
- **Why**: Reduces merge conflicts, keeps code fresh, enables faster feedback
- **If stuck**: Break work into smaller PRs

### Keep `master` Releasable

- **Never** push broken code to `master`
- **Always** ensure tests pass before merging
- **Use** feature flags if needed for incomplete features
- **Consider** draft PRs for work-in-progress

### Small, Focused PRs

- **One feature/fix per PR** when possible
- **Easier to review** and understand
- **Faster to merge** and deploy
- **Less risk** of conflicts

### Regular Integration

- **Rebase or merge** `master` into your branch regularly
- **Run tests** locally before pushing
- **Fix CI failures** promptly

### Clear Commit Messages

- **Format**: `<type>: <description>`
- **Types**: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`
- **Example**: `feat: add playlist import from YouTube`
- **Why**: Makes history readable and enables automated changelogs

## Release Process

### Standard Release (from master)

1. **Ensure `master` is stable**
   - All tests passing
   - No known critical bugs
   - Documentation updated

2. **Update version in `pom.xml`**
   - Set the release version (e.g., `0.6.3`)
   - Commit: `git commit -m "chore: bump version to 0.6.3"`

3. **Create release tag**
   ```bash
   git tag v0.6.3
   git push origin v0.6.3
   ```

4. **Use "Make Release" workflow** (optional)
   - Or manually create GitHub release
   - Attach JAR file from workflow artifacts

5. **Docker image is automatically built** from the tag
   - Tagged as `ghcr.io/arif-banai/musicbot:0.6.3`, and `:latest` if it's the latest release

### Release Branch (for stabilization)

Only use if you need to stabilize a release while continuing development:

1. **Create release branch**
   ```bash
   git checkout -b release/0.6.3
   git push origin release/0.6.3
   ```

2. **Stabilize on release branch**
   - Fix critical bugs
   - Run extensive testing
   - Cherry-pick fixes from `master` if needed

3. **Tag from release branch**
   ```bash
   git tag v0.6.3
   git push origin v0.6.3
   ```

4. **Merge back to master** (if needed)
   - Merge any fixes back to `master`
   - Delete release branch after release

## FAQ

### Q: Can I push directly to master?

**A**: Not recommended. Use pull requests for all changes to ensure:
- Code review
- CI validation
- Better history tracking

### Q: How long should branches live?

**A**: Ideally less than a week. If work takes longer, consider:
- Breaking into smaller PRs
- Using feature flags
- Creating a release branch if needed

### Q: What if I need to experiment?

**A**: Use `deps/<slug>` branches for dependency experiments, or create a personal fork for major experiments.

### Q: Can I use different branch names?

**A**: The CI will reject branches that don't match the allowed patterns. This ensures consistency and makes it easier to understand what each branch is for.

### Q: What about hotfixes?

**A**: For urgent production fixes:
1. Create `fix/<description>` branch from `master`
2. Fix the issue
3. Create PR and merge quickly
4. Tag a new patch version (e.g., `v0.6.3` ΓåÆ `v0.6.4`)

## Summary

- **Trunk**: `master` is always releasable
- **Branches**: Short-lived, type-prefixed (`feature/`, `fix/`, `chore/`, `deps/`, `release/`)
- **Process**: Branch ΓåÆ Develop ΓåÆ Test ΓåÆ PR ΓåÆ Merge ΓåÆ Release
- **CI**: Automatic validation, testing, and Docker builds
- **Goal**: Fast, safe, continuous integration and deployment
