"""Local-only, hash-guarded full-sphere star patch. Never touches instance configuration."""
from pathlib import Path
import argparse
import hashlib
import shutil
import zipfile

ORIGINAL = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a"
PATCHED = "78c80543179c75cd6beccb93dc86c283cf045a65d5e7fc5cf96e1829db51aede"
STAR = "shaders/lib/atmospherics/stars.glsl"

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def patch(path):
    current = digest(path)
    if current == PATCHED:
        print("Already patched:", path)
        return
    if current != ORIGINAL:
        raise SystemExit("Unsupported input SHA-256: " + current)
    backup = path.with_suffix(".original.zip.bak")
    if backup.exists() and digest(backup) != ORIGINAL:
        raise SystemExit("Backup path contains unrelated data: " + str(backup))
    if not backup.exists():
        shutil.copy2(path, backup)
    with zipfile.ZipFile(path) as archive:
        stars = archive.read(STAR).decode()
        newline = "\r\n" if "\r\n" in stars else "\n"
        helper = """// OverHeaven full-sphere stars. Leave GetStarCoord unchanged for moon and nebula.
vec2 GetFullSphereStarCoord(vec3 viewPos) {
    vec3 direction = normalize((gbufferModelViewInverse * vec4(viewPos, 0.0)).xyz);
    float divisor = max(abs(direction.y) + length(direction.xz) * 0.5, 0.0001);
    vec2 coordinate = direction.xz / divisor;
    coordinate.x += 0.173 * pow(min(direction.y, 0.0), 2.0) + 0.006 * syncedTime;
    return coordinate;
}

""".replace("\n", newline)
        stars = stars.replace("vec3 GetStars(", helper + "vec3 GetStars(", 1)
        stars = stars.replace("    if (VdotU < 0.0) return vec3(0.0);", "    // OverHeaven: stars remain visible below the horizon.")
        stars = stars.replace("min1(VdotU * 3.0)", "(0.5 + 0.5 * smoothstep(0.0, 0.35, abs(VdotU)))")
        changes = {STAR: stars.encode()}
        for name, view in [("shaders/program/gbuffers_skybasic.glsl", "viewPos.xyz"), ("shaders/lib/materials/materialMethods/reflectionBackground.glsl", "nViewPosR")]:
            original = archive.read(name)
            updated = original.replace(("GetStarCoord(" + view + ", 0.5)").encode(), ("GetFullSphereStarCoord(" + view + ")").encode())
            if original == updated:
                raise SystemExit("Expected star call missing: " + name)
            changes[name] = updated
        temporary = path.with_suffix(".overheaven.tmp")
        if temporary.exists():
            raise SystemExit("Temporary path already exists: " + str(temporary))
        with zipfile.ZipFile(temporary, "w") as result:
            for item in archive.infolist():
                result.writestr(item, changes.get(item.filename, archive.read(item.filename)))
    if digest(temporary) != PATCHED:
        raise SystemExit("Output checksum mismatch; original retained, inspect " + str(temporary))
    temporary.replace(path)
    print("Patched:", path, "SHA-256:", PATCHED)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path)
    patch(parser.parse_args().pack.resolve())
