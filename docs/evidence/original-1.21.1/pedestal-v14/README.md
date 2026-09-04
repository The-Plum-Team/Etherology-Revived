# Pedestal v14 evidence contract — fresh, not launched

This directory is the exclusive evidence target for the repository-owned
`etherology-original-fabric-1.21.1-published-0.1.7-v14` profile. It is a fresh
lane: the runtime has not been provisioned or launched, and this README is the
only permitted prelaunch entry.

The v1.4.3 harness retains the 74-assertion Pedestal contract, four native
1920×1080 captures, one explicit vanilla scheduled dispenser tick per fixture,
and the bounded stage and failed-shutdown behavior prepared for v13. It keeps
the authoritative server drop assertions, exact client block-state snapshot,
and all five client stale-block-entity and replacement-air checks for the
transition phase.

V13 proved all five client transition checks but timed out because capture
readiness additionally demanded exact equality between a transient client item-
entity snapshot and the already-proven server drop snapshot. The retained v13
artifacts do not record the client map, so the reason for that mismatch remains
unknown. V14 removes only that transient equality from pass/fail readiness. It
records the expected and observed client drop maps at mirror readiness and
immediately before capture, explicit booleans proving that both checkpoints
were actually observed, and whether each observation equals the server map.
The equality results remain diagnostic data that cannot affect the 74
assertion outcomes.

Before the one allowed native launch, the v14 verifier must prove the immutable
v11, v12, and v13 consumed-run diagnostic archives, the exact v14 manifest,
harness, contract sources, this README-only target, and the absence of the v14
runtime. After a successful run this placeholder will be replaced by the
verified report, four screenshots, world evidence, and archive manifest. No
result from v11, v12, or v13 is accepted as Pedestal behavior evidence, and no
consumed profile may ever be launched again.
