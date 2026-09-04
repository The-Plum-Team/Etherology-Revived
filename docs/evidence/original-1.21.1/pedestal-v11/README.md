# Pedestal v11 evidence contract — no capture yet

This is a contract placeholder, not runtime evidence. The reserved profile
`etherology-original-fabric-1.21.1-published-0.1.7-v11` has never been
provisioned or launched. There is no launch-attempt seal, world, controller
record, report, screenshot, completion marker, or archive manifest.

The fresh v1.4.0 harness is built, passed 47 Java tests, remapped, and is pinned
at `339,617` bytes with SHA-256
`09272e04b122b20da33d1964b4e1ca9f67af768fb0db0c0fa1f74f0579799e57`.
The manifest is `10,307` bytes with SHA-256
`34974855dd861c220915dd77ce694d3e5175c97e1c8f6edea0806601947e0cfc`.
No runtime has been provisioned. One successful native run is expected to
archive exactly this runtime layout:

```text
controller/
  original-client.log
  verification.json
reports/
  report.json
  done.marker
screenshots/
  pedestal-gallery.png
  pedestal-transition-drops.png
  pedestal-persistence-initial.png
  pedestal-persistence-reopened.png
archive-manifest.json
```

None of those paths exists in this fresh contract directory. Adding any of them
before the one v11 launch causes static validation to fail closed. The accepted
v10 Slitherite archive remains immutable history and is not a source for v11.
