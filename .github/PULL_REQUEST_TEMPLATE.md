## What this changes

<!-- One or two sentences. Link the issue if there is one. -->

## Checklist

- [ ] `mvn clean install` passes (tests and Javadoc lint)
- [ ] New public API has Javadoc with `@since` and works on Java 8 bytecode
- [ ] Version-specific code is guarded (see docs/architecture.md, "Version handling")
- [ ] Anything the feature creates is cleaned up when the owning plugin shuts down
- [ ] Docs updated (`docs/`, README feature table) and a line added to CHANGELOG.md under "Unreleased"
