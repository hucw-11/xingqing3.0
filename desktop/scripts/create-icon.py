"""Create the app's original sun/cloud icon using only Python's standard library."""
from pathlib import Path
import math
import struct
import zlib

size = 256
rows = bytearray()
for y in range(size):
    rows.append(0)
    for x in range(size):
        dx, dy = max(0, abs(x - 127.5) - 77), max(0, abs(y - 127.5) - 77)
        inside = dx * dx + dy * dy < 50 * 50
        rgba = (20, 112 + int(y / 8), 226, 255) if inside else (0, 0, 0, 0)
        if inside:
            if (x - 158) ** 2 + (y - 94) ** 2 < 31 ** 2:
                rgba = (255, 218, 123, 255)
            cloud = any((x - cx) ** 2 + (y - cy) ** 2 < r ** 2 for cx, cy, r in [(83, 143, 24), (112, 123, 35), (151, 142, 30)])
            if cloud or 79 <= x <= 154 and 142 <= y <= 166:
                rgba = (245, 250, 255, 255)
        rows.extend(rgba)

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))

png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')
out = Path(__file__).resolve().parents[1] / 'ui'
(out / 'icon.png').write_bytes(png)
(out / 'icon.ico').write_bytes(struct.pack('<HHH', 0, 1, 1) + struct.pack('<BBBBHHII', 0, 0, 0, 0, 1, 32, len(png), 22) + png)
