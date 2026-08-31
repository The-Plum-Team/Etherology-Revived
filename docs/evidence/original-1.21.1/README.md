# Original Fabric 1.21.1 evidence

This archive preserves native evidence from the separately isolated original
Etherology runtime. It is the `published-0.1.7` binary reference, not the
unbound `source-0.1.8` tree.

## `phase0-smoke-v1`

The repository-owned profile
`etherology-original-fabric-1.21.1-published-0.1.7-v1` completed its one native
run on 2026-08-31 at 17:35 Europe/Madrid. The controller verified all 30 ordered
assertions, 120 consecutive ready renders, the unedited 1920x1080 framebuffer,
the forced world save, clean client shutdown, immutable launcher inputs, and
report-before-marker publication.

![Original 0.1.7 phase-zero fixture](phase0-smoke-v1/screenshots/phase0-smoke.png)

The image shows the live integrated-world fixture: Ethereal Storage on the
left, the Empowerment Table in the center, the Brewing Cauldron on the right,
and the animated Armillary Sphere behind the table. It proves the exact four
blocks and block-entity types were present on both server and client and that
their original models rendered. It does not by itself prove brewing,
empowerment, transmutation, inventory, or Ether-transfer behavior.

- Screenshot SHA-256:
  `aa69505119804f49936b6f0549566a25744010c96a0a5ac4f9064f3c4b0cafdb`
- [`report.json`](phase0-smoke-v1/reports/report.json): exact assertion record
- [`done.marker`](phase0-smoke-v1/reports/done.marker): report-hash-bound final
  publication marker
- [`verification.json`](phase0-smoke-v1/controller/verification.json):
  controller verification record
- [`original-client.log`](phase0-smoke-v1/controller/original-client.log):
  complete native client log
- [`archive-manifest.json`](phase0-smoke-v1/archive-manifest.json): hashes,
  capture times, controller identity, runtime locks, and artifact provenance

The client made the normal failed Realms authorization attempt associated with
its deliberately offline token; this appears in the complete log and did not
affect the integrated world or any of the 30 accepted assertions. The skin cache
remained absent.
