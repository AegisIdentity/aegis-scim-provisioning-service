# aegis-scim-provisioning-service

SCIM 2.0 inbound and outbound user/group provisioning.

**Maturity: scaffold.** Buildable, secured resource-server skeleton — health endpoint, a protected
placeholder API, and the shared `aegis-security-commons` hardening baseline. Feature work goes here;
the intended contract is in
[`aegis-platform-docs/architecture/SERVICE-CATALOG.md`](../aegis-platform-docs/architecture/SERVICE-CATALOG.md).

- Port: `9106` · Required scope for `/api/**`: `scim:admin`
- Build: `./mvnw verify` (needs `aegis-platform-parent` + `aegis-platform-commons` installed to `~/.m2` first)
