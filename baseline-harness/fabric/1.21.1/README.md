# Original Fabric 1.21.1 phase-zero harness

This is a standalone, client-only evidence harness for the hash-pinned
`published-0.1.7` Etherology reference. It is deliberately separate from all
production source sets and has no compile dependency on Etherology classes.

Build, test, remap, and inspect the harness without launching Minecraft:

```bash
./gradlew -p baseline-harness/fabric/1.21.1 --no-daemon clean buildHarness
```

Expected remapped artifact:

```text
build/libs/Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.5.jar
size:   218402 bytes
sha256: 09e309f188da473b6038e35af4d1a7ed43409c0185c830e42dba506bfecb8489
```

The artifact metadata requires exact Minecraft `1.21.1`, Fabric Loader
`0.17.3`, Fabric API `0.110.0+1.21.1`, and Etherology `1.21-0.1.7`. The build
validator rejects nested JARs, classes outside the dedicated harness package,
and bytecode links to Etherology implementation classes.

`phase0-smoke` accepts no fallback scenario id. At runtime it also requires the
profile/evidence marker structure created by
`scripts/baseline/original_client.py`. The controller—not the harness in
isolation—provides the exact contained game directory and validates it before
launch. The harness independently refuses reused evidence and a reused world,
captures the Minecraft framebuffer directly only after an exact camera pose,
complete terrain, four rendering-ready fixture positions, and 120 consecutive
valid completed renders. It verifies exact block-entity types on both logical
sides, records the live world identity, publishes the JSON report before a
completion marker that contains the report SHA-256, and then schedules a clean
stop. Logical window sizing is derived from the live framebuffer backing scale
instead of assuming a fixed Retina multiplier.

Building this project is not runtime proof. The controller's `provision`,
`stage`, `check`, and `run` gates must remain distinct, and a successful native
run must pass the controller's post-process evidence verifier before it can be
cited as original behavior.
