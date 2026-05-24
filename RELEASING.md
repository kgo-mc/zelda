# Releasing Zelda

Releases are triggered by pushing a version tag. The CI pipeline handles everything else.

## Steps

1. **Ensure `main` is clean and all tests pass:**
   ```bash
   mvn clean verify
   ```

2. **Tag the release** (tag name drives the Maven version):
   ```bash
   git tag v1.2.3
   git push origin v1.2.3
   ```

3. **GitHub Actions takes over:**
    - Runs the full build + test suite
    - Sets the Maven version to `1.2.3` (strips the `v` prefix)
    - Deploys all modules to GitHub Packages
    - Artifacts are published under `net.kgomc.zelda:*:1.2.3`

## Versioning

Follow [Semantic Versioning](https://semver.org):

| Change | Version bump | Example |
|--------|-------------|---------|
| Bug fix | Patch | `1.2.2` → `1.2.3` |
| New feature, backwards compatible | Minor | `1.2.3` → `1.3.0` |
| Breaking API change | Major | `1.3.0` → `2.0.0` |

## For consumers — pulling Zelda

GitHub Packages requires authentication even for open-source packages.

1. Generate a [Classic PAT](https://github.com/settings/tokens) with `read:packages` scope
2. Copy `.github/consumer-settings.xml` to `~/.m2/settings.xml` (or merge if you already have one)
3. Replace `YOUR_GITHUB_USERNAME` with your GitHub username
4. Set the environment variable:
   ```bash
   export GITHUB_TOKEN=ghp_xxxxxxxxxxxx
   ```
5. Add the dependency to your `pom.xml`:
   ```xml
   <dependency>
       <groupId>net.kgomc.zelda</groupId>
       <artifactId>zelda-builder</artifactId>
       <version>1.2.3</version>
   </dependency>
   ```

## If a release fails mid-deploy

GitHub Packages is append-only — you cannot delete a published version.
If a deploy fails partway, bump to a patch version and retag:
```bash
git tag v1.2.4
git push origin v1.2.4
```