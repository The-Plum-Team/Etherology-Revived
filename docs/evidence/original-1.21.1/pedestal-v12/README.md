# Pedestal v12 evidence contract — fresh, not launched

This directory is the fail-closed placeholder for the fresh repository-owned
profile `etherology-original-fabric-1.21.1-published-0.1.7-v12`. It has not been
provisioned or launched. There is no runtime, launch seal, world, report,
completion marker, controller verification, screenshot, or accepted archive.

The v1.4.1 harness keeps all 74 Pedestal assertions and the four-capture
1920x1080 contract defined for v11. It additionally clears client input and restores
the exact first-person camera after each client tick and at the start of every
capture render. The render-ready watchdog is now monotonic instead of returning
to a stage that resets its timer. Its two clean builds each produced the same
`340,250`-byte JAR with SHA-256
`a99809d6443a4757c860e98d2f09e1d5775667a69e331a7e631930eb5728c7eb`.

The v12 manifest is `10,307` bytes with SHA-256
`bcf54994a6245284292adb4056a22b24c29fdaaec60a90579d2c1eac95c10c6a`.
Before the one permitted launch, this directory must contain only this README.
A successful controller-verified run will replace the placeholder claim with
four native screenshots, an exact report and completion marker, controller
verification, and an archive manifest. The consumed v11 diagnostic remains
separate and is never a source of accepted v12 evidence.
