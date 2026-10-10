"""Decode chunked PNGs emitted by isolated Android acceptance instrumentation."""
import base64
import pathlib
import re
import sys

folder = pathlib.Path(sys.argv[1])
output = folder / "streamed-screenshots"
for log in folder.glob("*.txt"):
    screenshots = {}
    for name, index, chunk in re.findall(r"EVIDENCE_PNG=([a-z0-9-]+):(\d+):([A-Za-z0-9+/=]+)", log.read_text(errors="replace")):
        chunks = screenshots.setdefault(name, {})
        index = int(index)
        if index in chunks:
            raise ValueError(f"Duplicate screenshot chunk: {name}/{index}")
        chunks[index] = chunk
    for name, chunks in screenshots.items():
        if sorted(chunks) != list(range(len(chunks))):
            raise ValueError(f"Missing screenshot chunks: {name}")
        data = base64.b64decode("".join(chunks[i] for i in range(len(chunks))), validate=True)
        if not data.startswith(b"\x89PNG\r\n\x1a\n") or not data.endswith(b"\x00\x00\x00\x00IEND\xaeB`\x82"):
            raise ValueError(f"Incomplete PNG: {name}")
        output.mkdir(exist_ok=True)
        (output / f"{name}.png").write_bytes(data)
        print(f"Recovered screenshot: {name}")
