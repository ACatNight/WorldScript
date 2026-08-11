#!/usr/bin/env python3
"""Validate Minecraft HUD quality textures and optionally build a contact sheet."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageOps
except ImportError as exc:
    raise SystemExit("Pillow is required: python -m pip install Pillow") from exc


TIERS = ("common", "fine", "rare", "epic", "legendary")
IGNORED_DIRS = {"shell", "source"}


@dataclass
class Finding:
    severity: str
    code: str
    path: str
    message: str


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def alpha_bbox(image: Image.Image):
    return image.getchannel("A").getbbox()


def grayscale_signature(image: Image.Image, size: int = 32) -> bytes:
    rgba = image.convert("RGBA")
    background = Image.new("RGBA", rgba.size, (0, 0, 0, 255))
    background.alpha_composite(rgba)
    gray = ImageOps.grayscale(background.convert("RGB"))
    gray = gray.resize((size, size), Image.Resampling.LANCZOS)
    return bytes(1 if value >= 96 else 0 for value in gray.tobytes())


def signature_similarity(left: bytes, right: bytes) -> float:
    if not left or len(left) != len(right):
        return 0.0
    equal = sum(a == b for a, b in zip(left, right))
    return equal / len(left)


def discover_materials(root: Path) -> list[Path]:
    materials = []
    for child in sorted(root.iterdir()):
        if not child.is_dir() or child.name.lower() in IGNORED_DIRS:
            continue
        if any((child / f"{tier}.png").exists() for tier in TIERS):
            materials.append(child)
    return materials


def validate_support_texture(
    path: Path,
    expected_size: int,
    margin: int,
    findings: list[Finding],
) -> None:
    try:
        with Image.open(path) as opened:
            image = opened.convert("RGBA")
    except Exception as exc:
        findings.append(Finding("error", "invalid-image", str(path), str(exc)))
        return

    width, height = image.size
    bbox = alpha_bbox(image)
    if (width, height) != (expected_size, expected_size):
        findings.append(
            Finding(
                "error",
                "wrong-size",
                str(path),
                f"Expected {expected_size}x{expected_size}, got {width}x{height}.",
            )
        )
    if bbox is None:
        findings.append(Finding("error", "empty-alpha", str(path), "Texture is fully transparent."))
        return

    left, top, right, bottom = bbox
    if left < margin or top < margin or right > width - margin or bottom > height - margin:
        findings.append(
            Finding(
                "warning",
                "unsafe-margin",
                str(path),
                f"Opaque bounds {bbox} enter the {margin}px safety margin.",
            )
        )


def validate(root: Path, expected_size: int, margin: int) -> tuple[list[Finding], dict]:
    findings: list[Finding] = []
    records: dict[str, dict[str, dict]] = {}
    materials = discover_materials(root)
    if not materials:
        findings.append(Finding("error", "no-materials", str(root), "No material tier directories found."))
        return findings, records

    for material_dir in materials:
        material = material_dir.name
        records[material] = {}
        previous_signature = None
        previous_tier = None
        for tier in TIERS:
            path = material_dir / f"{tier}.png"
            if not path.exists():
                findings.append(Finding("error", "missing-tier", str(path), f"Missing {tier} texture."))
                continue
            try:
                with Image.open(path) as opened:
                    image = opened.convert("RGBA")
            except Exception as exc:
                findings.append(Finding("error", "invalid-image", str(path), str(exc)))
                continue

            width, height = image.size
            bbox = alpha_bbox(image)
            digest = file_sha256(path)
            signature = grayscale_signature(image)
            records[material][tier] = {
                "path": path,
                "image": image,
                "hash": digest,
                "signature": signature,
                "bbox": bbox,
            }

            if (width, height) != (expected_size, expected_size):
                findings.append(
                    Finding(
                        "error",
                        "wrong-size",
                        str(path),
                        f"Expected {expected_size}x{expected_size}, got {width}x{height}.",
                    )
                )
            if bbox is None:
                findings.append(Finding("error", "empty-alpha", str(path), "Texture is fully transparent."))
            else:
                left, top, right, bottom = bbox
                if left < margin or top < margin or right > width - margin or bottom > height - margin:
                    findings.append(
                        Finding(
                            "warning",
                            "unsafe-margin",
                            str(path),
                            f"Opaque bounds {bbox} enter the {margin}px safety margin.",
                        )
                    )

            if previous_signature is not None:
                similarity = signature_similarity(previous_signature, signature)
                if similarity >= 0.975:
                    findings.append(
                        Finding(
                            "warning",
                            "weak-grayscale-tier-change",
                            str(path),
                            f"{previous_tier} -> {tier} grayscale similarity is {similarity:.1%}; "
                            "strengthen silhouette, facets or symbols.",
                        )
                    )
            previous_signature = signature
            previous_tier = tier

    for tier in TIERS:
        seen: dict[str, tuple[str, Path]] = {}
        for material, tier_records in records.items():
            record = tier_records.get(tier)
            if not record:
                continue
            digest = record["hash"]
            if digest in seen:
                first_material, first_path = seen[digest]
                findings.append(
                    Finding(
                        "error",
                        "duplicate-material-texture",
                        str(record["path"]),
                        f"Exact duplicate of {first_material}/{tier}: {first_path}",
                    )
                )
            else:
                seen[digest] = (material, record["path"])

    shell_dir = root / "shell"
    if shell_dir.is_dir():
        for tier in TIERS:
            shell_path = shell_dir / f"{tier}.png"
            if not shell_path.exists():
                findings.append(Finding("error", "missing-shell-tier", str(shell_path), f"Missing {tier} shell."))
            else:
                validate_support_texture(shell_path, expected_size, margin, findings)

    for support_path in sorted(root.glob("*.png")):
        validate_support_texture(support_path, expected_size, margin, findings)

    return findings, records


def create_contact_sheet(records: dict, output: Path, preview_size: int) -> None:
    materials = sorted(records)
    label_height = 24
    padding = 12
    columns = len(TIERS)
    cell_width = preview_size + padding * 2
    cell_height = preview_size * 2 + label_height * 2 + padding * 3
    sheet = Image.new(
        "RGBA",
        (cell_width * columns, cell_height * len(materials)),
        (18, 21, 25, 255),
    )
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()

    for row, material in enumerate(materials):
        for column, tier in enumerate(TIERS):
            record = records[material].get(tier)
            x = column * cell_width
            y = row * cell_height
            draw.text((x + padding, y + 5), f"{material} / {tier}", fill=(235, 239, 244), font=font)
            if not record:
                draw.text((x + padding, y + label_height + padding), "MISSING", fill=(255, 90, 90), font=font)
                continue
            image = record["image"].resize((preview_size, preview_size), Image.Resampling.LANCZOS)
            checker = Image.new("RGBA", image.size, (45, 49, 55, 255))
            checker.alpha_composite(image)
            sheet.alpha_composite(checker, (x + padding, y + label_height))

            gray = ImageOps.grayscale(checker.convert("RGB")).convert("RGBA")
            sheet.alpha_composite(gray, (x + padding, y + label_height + preview_size + padding))
            draw.text(
                (x + padding, y + label_height + preview_size * 2 + padding + 2),
                f"{preview_size}px + grayscale",
                fill=(170, 178, 188),
                font=font,
            )

    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="Quality texture root directory.")
    parser.add_argument("--expected-size", type=int, default=128)
    parser.add_argument("--preview-size", type=int, default=56)
    parser.add_argument("--margin", type=int, default=4)
    parser.add_argument("--contact-sheet", type=Path)
    parser.add_argument("--json", dest="json_path", type=Path)
    args = parser.parse_args()

    root = args.root.resolve()
    if not root.is_dir():
        print(f"ERROR: not a directory: {root}", file=sys.stderr)
        return 2

    findings, records = validate(root, args.expected_size, args.margin)
    if args.contact_sheet:
        create_contact_sheet(records, args.contact_sheet.resolve(), args.preview_size)
        print(f"Contact sheet: {args.contact_sheet.resolve()}")

    for finding in findings:
        print(f"{finding.severity.upper():7} {finding.code:27} {finding.path} - {finding.message}")

    errors = sum(item.severity == "error" for item in findings)
    warnings = sum(item.severity == "warning" for item in findings)
    print(f"Validated {len(records)} materials: {errors} error(s), {warnings} warning(s).")

    if args.json_path:
        args.json_path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "root": str(root),
            "materials": sorted(records),
            "errors": errors,
            "warnings": warnings,
            "findings": [asdict(item) for item in findings],
        }
        args.json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
