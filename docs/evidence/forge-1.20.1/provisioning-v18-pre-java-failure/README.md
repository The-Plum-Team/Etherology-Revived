# Forge client v18 provisioning failure

Status: quarantined and consumed. Never provision or launch this profile again.

The repository-owned Forge 1.20.1 client profile v18 stopped during
provisioning on 2026-09-05 before the Forge installer Java process was spawned.
The controller reported that cleanup lacked an authenticated terminal `ERROR`,
retained the global installer interlock, retained the staging runtime, and did
not create the final v18 runtime.

## Bound operation

- Source commit: `ed6bee11fb59eb91633a87438ca3e6546ddf8057`
- Profile: `etherology-e2e-forge-1.20.1-v18`
- Run ID: `3482139838aae2d3769f5892dd1822c485fa29611fc26d1396b1ce1f6195cecf`
- Controller PID: `11723`
- Staging runtime: `.etherology-e2e-forge-1.20.1-v18.mxjxq8hw`
- Supervisor runtime: `.forge-installer-supervisor.s5o_56v9`

The exact retained interlock and staging profile marker are archived beside
this report. Their SHA-256 digests are:

- `installer-operation.pending.json`:
  `2bd7415532991a85897c0782cdc7627363ce6624c4e41936019d26617a84b824`
- `staging-profile-marker.json`:
  `1919e79384fd78c6d76d7de24b339803c849854ab58508065943422204860ac6`

## Absence proof

The retained staging runtime is owner-private (`0700`), contains 3,787 regular
files, and occupied 825,328 KiB at inspection. Its owner-private supervisor
runtime is empty. `installer-output.log` is created before the installer Java
spawn, so the absence of that file proves execution never reached
`start_installer`. The Forge version directory and
`launcher/libraries/net/minecraftforge` are both absent. The downloaded pinned
Forge 47.4.9 installer remains intact at 6,077,090 bytes with SHA-256
`58fc5db6e3dc47745475375be6fa275e68320563c05d29b4203e0d2ca57a50c4`.

Independent repeated process samples found the controller PID absent and found
no Java, installer supervisor, memory monitor, or Forge client controller.
Aggregate Java RSS was exactly 0 MiB.

## Root cause

Homebrew framework Python starts through
`.../bin/python3.11` and re-executes as
`.../Resources/Python.app/Contents/MacOS/Python`. `sys.executable` retains the
launcher path while macOS `proc_pidpath` reports the final kernel image. The
controller could bind during the transition, but the supervisor rejected its
own final identity before receiving the activation frame. That early error
therefore carried the unknown run ID and could not authenticate cleanup.

The fix derives the allowed Python image from the controller's own
kernel-observed identity, waits through the child's re-exec transition, binds
the supervisor and monitor to that predetermined image, and receives the run ID
before supervisor self-binding so a binding failure can be authenticated.

## Recovery disposition

The global interlock may be released only after revalidating the exact archived
bytes and inode, repeating the process-absence checks, proving the supervisor
runtime is still empty, and preserving the staging runtime in place. v18 stays
quarantined regardless; the next native attempt must use a new profile ID.
