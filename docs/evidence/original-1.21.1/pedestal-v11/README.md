# Pedestal v11 consumed-run diagnostic — not accepted evidence

The repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v11` consumed its only native
launch on 2026-09-04. The original published `0.1.7` client reached a fresh
integrated world, but the controller terminated it after its exact 1,800-second
deadline and returned exit code `2`. The harness published no report,
completion marker, controller verification, or screenshot. This directory is
therefore diagnostic history only and establishes no native Pedestal mechanic.

The v1.4.0 harness used for that run is pinned at `339,617` bytes with SHA-256
`09272e04b122b20da33d1964b4e1ca9f67af768fb0db0c0fa1f74f0579799e57`.
The v11 manifest is `10,307` bytes with SHA-256
`34974855dd861c220915dd77ce694d3e5175c97e1c8f6edea0806601947e0cfc`.
The exact launch seal, runtime locks, controller identity, source identities,
and copied-file hashes are recorded in
[`diagnostic-manifest.json`](diagnostic-manifest.json), whose top-level
`accepted` value is `false`.

## Camera-loop diagnosis

The scenario contract required the exact first-person camera pose
`x=0.5;y=121.0;z=-15.5;yaw=0.0;pitch=10.0`. Both the prior autosave at
15:56:19 Europe/Madrid and the final post-timeout save at 16:01:00 persisted
the same different pose:

```text
x=-0.6990341339148626
y=121.0
z=-13.862614973527577
yaw=-7.282553195953369
pitch=6.941411018371582
motion=[0.0,-0.0784000015258789,0.0]
on_ground=true
```

Source inspection of the exact hash-pinned harness shows that a failed camera
check sends `WAITING_FOR_RENDERS` back to `WAITING_FOR_CLIENT_MIRROR`, while
every stage transition resets the per-stage timeout. The stable displaced pose,
absence of a harness failure record, and eventual outer-controller timeout make
that loop the diagnosis. It remains an inference because v11 emitted no direct
camera telemetry or report.

The retained diagnostic payload is deliberately small:

- [`original-client.log`](controller/original-client.log) is the complete
  14,007-byte controller log. The runtime's `latest.log` is its exact suffix and
  is not duplicated here.
- [`playerdata.dat`](diagnostics/playerdata.dat) is the 1,211-byte final player
  snapshot.
- [`playerdata.dat_old`](diagnostics/playerdata.dat_old) is the 1,211-byte prior
  autosave. Its pose matches the final snapshot.

The ignored 946 MiB runtime and its 17 MiB world remain preserved in place and
must never be launched again. They are not copied into Git. A fresh v12 profile
will retry the Pedestal baseline with a bounded camera-readiness path; it must
earn its own report and captures before any native mechanic is accepted.
