# Security policy

## Supported versions

Security fixes are made for the latest minor release. Because MoonLib is shaded into each plugin,
a fix reaches servers when the plugins that use it are rebuilt, so plugin authors should update
promptly when an advisory is published.

| Version | Supported |
|---|---|
| 1.2.x | ✅ |
| < 1.4 | ❌ (please upgrade; there are no breaking changes within 1.x) |

## Reporting a vulnerability

Please **do not open a public issue**. Report privately through GitHub:
**Security → Report a vulnerability** on the repository
(<https://github.com/LunaMCDev/MoonLib/security/advisories/new>).

Include the MoonLib version, the server software and version, and the steps or code that show the
problem. You should get a first reply within 7 days. Once a fix is released, the advisory is
published with credit to you unless you prefer otherwise.

## Scope

In scope: anything in this repository that could let a player or another plugin do something they
should not, for example formatting injection through placeholders, menu item duplication, SQL
injection through MoonLib's own statements, path traversal in `DataStore` keys, or leaking data in
logs.

Out of scope: vulnerabilities in your own plugin code (for example building SQL with string
concatenation instead of `?` parameters), in the server software, or that need console or operator
access.
