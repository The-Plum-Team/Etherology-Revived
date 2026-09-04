# Pedestal v13 evidence contract — fresh, not launched

This directory is the exclusive evidence target for the repository-owned
`etherology-original-fabric-1.21.1-published-0.1.7-v13` profile. It is a fresh
lane: the runtime has not been provisioned or launched, and this README is the
only permitted prelaunch entry.

The v1.4.2 harness retains the 74-assertion Pedestal contract and four native
1920×1080 captures. It replaces the v12 redstone-scheduler fixture with one
explicit vanilla `BlockState.scheduledTick` call per placed dispenser. That call
enters the real `DispenserBlock.dispense` method and the published Etherology
dispenser mixin exactly once, while excluding unrelated chunk-ticking timing
from the Pedestal behavior probe. The harness also bounds a missing screenshot
callback and records every scenario-stage transition.

The controller now recognizes the harness's atomically published failed marker.
If Minecraft does not exit within the 15-second failure-shutdown grace period,
the controller terminates only the profile-owned process group instead of
waiting for the full launch timeout. Passed evidence still requires Minecraft's
normal clean exit and the complete strict verifier.

Before the one allowed native launch, the v13 verifier must prove the immutable
v11 and v12 consumed-run diagnostic archives, the exact v13 manifest, harness,
contract sources, this README-only target, and the absence of the v13 runtime.
After a successful run this placeholder will be replaced by the verified report,
four screenshots, world evidence, and archive manifest. No result from v11 or
v12 is accepted as Pedestal behavior evidence, and neither consumed profile may
ever be launched again.
