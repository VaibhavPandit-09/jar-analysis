# Known Limitations

1. Dependency-Check DB freshness
   Dependency-Check findings depend on the local database state. Cold-start updates can be slow.
2. Optional NVD API key
   An NVD API key speeds up updates, but it is optional. If configured, it is stored locally and masked in UI and logs.
3. Private Maven repositories
   Private repositories require external Maven credential configuration such as mounted `settings.xml`.
4. Unused dependency analysis is evidence-based
   Java dependencies may be activated through reflection, configuration, ServiceLoader, Spring auto-configuration, servlet containers, logging frameworks, JDBC drivers, or runtime plugins.
5. Not source-code vulnerability scanning
   JARScan may inspect source files, resources, and bytecode only to reason about dependencies and packaging.
6. Standalone archives have limited graph reconstruction
   Without usable POM metadata, full Maven graph reconstruction may not be possible.
7. Source-only project ZIPs are less reliable
   If compiled classes are absent, usage analysis falls back to weaker hints.
8. Suggested exclusions require testing
   Generated exclusions, narrowing suggestions, and dependencyManagement snippets are recommendations only.
9. SBOM import limitations
   Imported SBOM quality depends on the completeness and correctness of the source SBOM.
10. Policy and suppression conventions for some findings
   Suppressing policy-only or duplicate-class style findings relies on lightweight matching conventions because those findings are not fully normalized into relational tables.
