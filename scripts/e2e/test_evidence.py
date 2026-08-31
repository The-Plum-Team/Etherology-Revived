from __future__ import annotations

import binascii
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zlib


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client
import evidence


def png_chunk(chunk_type: bytes, content: bytes) -> bytes:
    crc = binascii.crc32(chunk_type)
    crc = binascii.crc32(content, crc) & 0xFFFFFFFF
    return struct.pack(">I", len(content)) + chunk_type + content + struct.pack(">I", crc)


def rgb_png(width: int, height: int, pixels: bytes) -> bytes:
    if len(pixels) != width * height * 3:
        raise ValueError("RGB fixture has the wrong pixel inventory")
    rows = b"".join(
        b"\x00" + pixels[row * width * 3 : (row + 1) * width * 3]
        for row in range(height)
    )
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"".join(
        (
            evidence.PNG_SIGNATURE,
            png_chunk(b"IHDR", header),
            png_chunk(b"IDAT", zlib.compress(rows)),
            png_chunk(b"IEND", b""),
        )
    )


class PngEvidenceTests(unittest.TestCase):
    def test_decodes_nonblank_rgb_png_and_preserves_pixels(self) -> None:
        width = 64
        height = 32
        pixels = bytes(
            channel
            for y in range(height)
            for x in range(width)
            for channel in (x * 4 % 256, y * 8 % 256, (x + y) * 3 % 256)
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "fixture.png"
            path.write_bytes(rgb_png(width, height, pixels))

            image = evidence.decode_png(path)
            evidence.assert_image_is_not_blank(image, "fixture")

        self.assertEqual((width, height), (image.width, image.height))
        self.assertEqual(pixels, image.pixels)

    def test_rejects_uniform_screenshot(self) -> None:
        image = evidence.PngImage(32, 32, bytes((20, 20, 20)) * (32 * 32))

        with self.assertRaisesRegex(client.E2EError, "blank or near-uniform"):
            evidence.assert_image_is_not_blank(image, "uniform")

    def test_changed_pixel_ratio_has_a_declared_threshold(self) -> None:
        left = evidence.PngImage(10, 10, bytes((0, 0, 0)) * 100)
        right_pixels = bytearray(left.pixels)
        for pixel_index in range(10):
            right_pixels[pixel_index * 3] = 255
        right = evidence.PngImage(10, 10, bytes(right_pixels))

        self.assertEqual(0.1, evidence.changed_pixel_ratio(left, right))
        self.assertGreater(0.1, evidence.MINIMUM_CHANGED_PIXEL_RATIO)

    def test_rejects_escaped_screenshot_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario_root = Path(temporary_directory)

            with self.assertRaisesRegex(client.E2EError, "unsafe"):
                evidence.safe_screenshot_path(
                    scenario_root,
                    "screenshots/../outside.png",
                )


if __name__ == "__main__":
    unittest.main()
