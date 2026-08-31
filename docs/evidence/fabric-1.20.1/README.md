# Fabric 1.20.1 runtime evidence

This directory contains frozen evidence captured from real Minecraft clients on
the baseline Mac. Every run uses a new repository-owned profile under
`scripts/e2e/.state/`; no external launcher profile is read, modified, or used as
a source.

## Phase 0 smoke

- Profile: `etherology-e2e-fabric-1.20.1-v12`
- Minecraft: `1.20.1`
- Fabric Loader: `0.17.3`
- Production JAR SHA-256:
  `06c47e738e5c45b753c67b65bf6ac12abef4b810f1308eeb21c18cbe7961a776`
- Harness JAR SHA-256:
  `473c75d0b1a86cf744d8cd0ebcaac7e77232bbc47a2b5b2b5b74f8348be31c29`
- Report status: `passed`
- Assertions: `42` passed, `0` failed
- Client ticks: `200`
- Changed-pixel ratio (title to world): `0.979585`
- Screenshot: native composed Minecraft framebuffer, `1920x1080`

Frozen file digests:

- `phase0-smoke/reports/report.json`:
  `d84e599ea5fb048ede30d47874874c09fd6ee0dff73e84b66387cc8b2a54fe50`
- `phase0-smoke/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `phase0-smoke/screenshots/phase0-smoke-title.png`:
  `b20c49adbc1f21c6f4f3ac53a1a7eef0f0fabe8618bdb98a3399091f1760957d`
- `phase0-smoke/screenshots/phase0-smoke-world.png`:
  `febba7a32d35e8049e065761eb5b3bc168467d99b33a88c2b6d55bd135ddab07`

The machine-readable assertions and artifact provenance are in
`phase0-smoke/reports/report.json`. The run verifies all 88,420 registered
Etherology block states have non-negative network IDs, creates and joins a real
integrated world, arranges a four-machine fixture on the server thread, verifies
the client mirror, round-trips block-entity NBT, and force-saves the isolated
world. Both PNGs are captured from Minecraft's own framebuffer after two completed
renders; neither is an operating-system screenshot or crop.

The frozen runtime passed the deterministic repository verifier:

```text
Validated phase0-smoke: 42 assertions, 2 screenshots, changed-pixel ratio 0.979585
```

That verifier rechecks the schema-2 artifact lock and staged JAR bytes, assertion
inventory, PNG CRCs and decoded dimensions, blank-image probe, visual change,
save directory, crash inventory, fatal log markers, normal shutdown, evidence
size bound, and `done.marker` publication order.

## Storage and utilities

- Profile: `etherology-e2e-fabric-1.20.1-v13`
- Production JAR SHA-256:
  `06c47e738e5c45b753c67b65bf6ac12abef4b810f1308eeb21c18cbe7961a776`
- Harness JAR SHA-256:
  `473c75d0b1a86cf744d8cd0ebcaac7e77232bbc47a2b5b2b5b74f8348be31c29`
- Report status: `passed`
- Assertions: `42` passed, `0` failed
- Client ticks: `201`
- Changed-pixel ratio: `0.100598`
- Screenshot pair: native composed Minecraft framebuffers, `1920x1080`

Frozen file digests:

- `storage-utilities/reports/report.json`:
  `0a8665da2fe012e0762bfa5c14c7ca444acd1bd50d733318baa9acd9790b2472`
- `storage-utilities/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `storage-utilities/screenshots/storage-utilities-before.png`:
  `a38e1b539a721d32804351016b8cc34132e76d51a7bca07482c81c75a5c5c23f`
- `storage-utilities/screenshots/storage-utilities-after.png`:
  `bcbbd2f67de2ede5c8315b4f403998f6ffe0b84f6f716db14d448c399d4f9b84`

This run uses real server interactions to exercise the Crate, Shelf, Spill
Barrel, and Tuning Fork, then verifies client mirrors, four block-entity NBT
reconstructions, and the forced world save.

```text
Validated storage-utilities: 42 assertions, 2 screenshots, changed-pixel ratio 0.100598
```

## Ether network and Levitator (v18)

- Profile: `etherology-e2e-fabric-1.20.1-v18`
- Production JAR SHA-256:
  `acc23d2432ff84c54f3732cdcaf57439fcb0b12b5701926b8a7c38d1cf64aee5`
- Harness JAR SHA-256:
  `b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef`
- Report status: `passed`
- Assertions: `46` passed, `0` failed
- Client ticks: `232`
- Changed-pixel ratio: `0.481575`
- Screenshot pair: native composed Minecraft framebuffers, `1920x1080`

Frozen file digests:

- `ether-network/reports/report.json`:
  `237c03e27c17b59472394a846b6cd7eaedf3366117af4b4a8048b6886cc127da`
- `ether-network/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `ether-network/screenshots/ether-network-before.png`:
  `e85c5c2d7b8a9cba30ae6f1b47461309c14df16bbf03d61600c3489d8725f1b5`
- `ether-network/screenshots/ether-network-after.png`:
  `681e5d284656e6dd2469fcc0c37284e77df52c22521cf749756db6bd30546d18`

The real integrated-world fixture completes four Spinner generations through
directional channels. Its first unit fuels the Levitator for 100 ticks and
moves a visible armor stand `0.7676404465781816` blocks. After the redstone gate
closes, one unit remains in the Levitator, one in the output channel, and one
in Ethereal Storage. The run also reconstructs five block entities from NBT,
checks the client mirror, and force-saves the retained network.

```text
Validated ether-network: 46 assertions, 2 screenshots, changed-pixel ratio 0.481575
```

## Ether network current-artifact rerun (v19)

The accepted Common storage-core work changed the packaged Fabric JAR bytes, so
the Ether-network scenario was repeated in a new profile instead of reusing or
altering `v18`. The behavior and harness are unchanged; this record binds the
same mechanic proof to the current production artifact.

- Profile: `etherology-e2e-fabric-1.20.1-v19`
- Production JAR SHA-256:
  `c0bc7c54d5d2efd3f9632efc4e047e4694a03e7d0725b3e1ca3ab2517454b3c0`
- Harness JAR SHA-256:
  `b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef`
- Report status: `passed`
- Assertions: `46` passed, `0` failed
- Client ticks: `223`
- Changed-pixel ratio: `0.432843`
- Screenshot pair: native composed Minecraft framebuffers, `1920x1080`

Frozen file digests:

- `ether-network-v19/reports/report.json`:
  `d211329f4ace85d9cb3c276d62343b7788518eaf2b6928cc674f1c7190100fe0`
- `ether-network-v19/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `ether-network-v19/screenshots/ether-network-before.png`:
  `5e0293c53a0b6f9f219866512e6a9c3a250509afbd7577fc44f638fbacc9b090`
- `ether-network-v19/screenshots/ether-network-after.png`:
  `dc1bb5f6eab2d42e76f7c1981f9391b02a5090aa7ce4e6ade39466d75a5bf2ec`

The rerun again records four Spinner cycles, 100 initial Levitator fuel ticks,
`0.7676404465781816` blocks of armor-stand displacement, and one final Ether
unit each in the Levitator, output channel, and storage. It passed the same
deterministic verifier:

```text
Validated ether-network: 46 assertions, 2 screenshots, changed-pixel ratio 0.432843
```

## SharedSounds current-artifact smoke (v20)

The shared sound-registry checkpoint replaced Fabric's eager `EtherSounds`
owner with the same deferred `SharedSounds` declaration used by Forge. A fresh
profile reran the packaged Phase 0 scenario to prove that this exact Fabric JAR
registers successfully, reaches the title screen, creates and joins an
integrated world, mirrors the fixture, saves, and shuts down normally. This is
loader/startup evidence; it does not claim native playback of all 14 events.

- Profile: `etherology-e2e-fabric-1.20.1-v20`
- Profile manifest SHA-256:
  `77e2319ce711aa6c62de5aba4107f62d29ab96411c3fe2fba557e08e52444a8b`
- Profile manifest size: `6963` bytes
- Source checkpoint: `117167b6d1c47eaa8523b44516f05a821e1c51b8`
- Production JAR SHA-256:
  `999bf12e166c7d4ea67376373171e486c80779e85e4b281d4bac1f37776a7d37`
- Harness JAR SHA-256:
  `b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef`
- Report status: `passed`
- Assertions: `42` passed, `0` failed
- Client ticks: `198`
- Changed-pixel ratio (title to world): `0.979626`
- Screenshot pair: native composed Minecraft framebuffers, `1920x1080`

Frozen file digests:

- `phase0-smoke-v20/archive-manifest.json`:
  `ccdb80fc9b45283ead171869ceca9327986d7613851ac86a4c07aeeb4e801e78`
- `phase0-smoke-v20/reports/report.json`:
  `c6b7714fb04624afab84e49ae9553f1f429bad51cc1a5f4bac179043d63d3ff2`
- `phase0-smoke-v20/reports/done.marker`:
  `37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1`
- `phase0-smoke-v20/screenshots/phase0-smoke-title.png`:
  `98f5f36f4518bba67f809ddba8bc2cc6ee7202ee47fb9f9b8be800296c9f5892`
- `phase0-smoke-v20/screenshots/phase0-smoke-world.png`:
  `981293857e3113bdea6eb7d707a120f2c63ad952353dffd46e553fced2b9f456`

The live runtime passed the deterministic verifier before these four payload
files were copied into the new archive:

```text
Validated phase0-smoke: 42 assertions, 2 screenshots, changed-pixel ratio 0.979626
Production SHA-256: 999bf12e166c7d4ea67376373171e486c80779e85e4b281d4bac1f37776a7d37
Harness SHA-256: b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef
```

The archive manifest was created only after the copied payloads were verified
byte-for-byte against that explicit `v20` runtime. It seals their inventory,
sizes, SHA-256 values, exact report contract, profile identity and digest,
capture metadata digest, artifact identities, framebuffer contract, and the
capture-time publication order. The archive-only command intentionally does not
compare later source or rebuilt artifacts:

```text
python3 -B scripts/e2e/evidence.py --archive docs/evidence/fabric-1.20.1/phase0-smoke-v20
Validated archived phase0-smoke (etherology-e2e-fabric-1.20.1-v20): 42 assertions, 2 screenshots, changed-pixel ratio 0.979626
```
