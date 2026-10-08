# Third-party asset credits

## Earth 3D model — Attack globe

The dashboard **Attack globe** (`frontend/public/models/earth.glb`) is built from the
**"Earth Photorealistic 2K"** model (free preview of the *Earth Photorealistic* series).

- **Author:** gerhald3d (Jadevich)
- **Source / preview:** <https://sketchfab.com/models/f972fb1858324bac80e31440ef7b1f5a>
- **Full series:** <https://www.turbosquid.com/Search/Index.cfm?keyword=gerhald3d+earth>
- **Author's portfolio:** <https://www.turbosquid.com/Search/Index.cfm?keyword=gerhald3d>
- **License:** distributed by the author as a free model via TurboSquid; used here under the
  TurboSquid Royalty-Free License with attribution to the author. All rights to the original
  model and textures remain with gerhald3d (Jadevich).

### What we ship and how it was produced

The original download (`.obj` / `.fbx` / `.blend` + 2K PNG textures, delivered as a `.rar`) was
converted to a single self-contained **glTF binary (`.glb`)** for the web, with **no changes to the
model's geometry, colours or texture resolution**:

- Geometry, UVs and normals are the author's originals (three concentric meshes: earth / clouds /
  atmosphere), uniformly scaled and centred only.
- The earth surface uses the author's original 2K **Diffuse** texture unmodified.
- The cloud layer keeps the author's exact cloud shape; its mask was moved into the texture's alpha
  channel (white cloud over transparent sky) so it renders correctly in a standard glTF PBR
  pipeline — the shape itself is untouched.
- Textures are embedded at their original resolution; the `.glb` is well under the size budget, so
  no down-scaling or re-compression was applied.

Lighting and the starfield in the scene are backdrop only and do not alter the model's own
materials. Geographic calibration (marker/arc placement) was verified against India, the US, Brazil,
the UK, Russia and China — see `docs/screenshots/attack-globe*.png`.

The source archive is **not** committed to the repository (`*.rar` / `*.zip` are git-ignored).
