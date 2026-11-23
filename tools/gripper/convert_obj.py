"""Convert the user's exported gripper components without remodelling them.

Input is the five connected component OBJ files prepared from the XVL export.
Coordinates remain in the export frame, converted from provisional mm to metres.
Mount placement and jaw travel deliberately are not baked into the mesh.
"""
import argparse
import hashlib
import json
import math
import struct
from pathlib import Path

PARTS = [('bracket', [0.25, 0.27, 0.29, 1.]),
         ('body', [0.72, 0.74, 0.77, 1.]),
         ('jaw_right', [0.18, 0.19, 0.21, 1.]),
         ('jaw_left', [0.18, 0.19, 0.21, 1.]),
         ('plate', [0.38, 0.40, 0.43, 1.])]


def parse_obj(text):
    vertices, faces, seen = [], [], set()
    for line in text.splitlines():
        words = line.split()
        if not words:
            continue
        if words[0] == 'v':
            point = tuple(map(float, words[1:4]))
            if len(point) != 3 or not all(map(math.isfinite, point)):
                raise ValueError('Invalid vertex')
            vertices.append(point)
        elif words[0] == 'f':
            if len(words) != 4:
                raise ValueError('Only triangular source faces are supported')
            face = tuple(int(word.split('/')[0])-1 for word in words[1:])
            if any(i < 0 or i >= len(vertices) for i in face):
                raise ValueError('Invalid face index')
            # Exact coordinates only: preserve near-coincident original surfaces.
            key = tuple(sorted(vertices[i] for i in face))
            if key not in seen:
                faces.append(face)
                seen.add(key)
    if not faces:
        raise ValueError('No triangular geometry')
    return vertices, faces


def make_glb(vertices, faces, name, color):
    positions, normals = [], []
    for face in faces:
        a, b, c = [vertices[i] for i in face]
        u, v = [b[i]-a[i] for i in range(3)], [c[i]-a[i] for i in range(3)]
        n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        length = math.sqrt(sum(x*x for x in n))
        if length <= 1e-12:
            continue  # Zero-area export artifacts have no visible surface.
        for point in (a, b, c):
            positions.extend(x/1000 for x in point)
            normals.extend(x/length for x in n)
    count = len(positions)//3
    if not count:
        raise ValueError('No non-degenerate geometry')
    pos = struct.pack('<'+'f'*len(positions), *positions)
    norm = struct.pack('<'+'f'*len(normals), *normals)
    indices = struct.pack('<'+'I'*count, *range(count))
    binary = pos + norm + indices
    doc = {
        'asset': {'version': '2.0', 'generator': 'Exact exported gripper conversion'},
        'scene': 0, 'scenes': [{'nodes': [0]}],
        'nodes': [{'name': name, 'mesh': 0}],
        'meshes': [{'primitives': [{'attributes': {'POSITION': 0, 'NORMAL': 1}, 'indices': 2, 'material': 0}]}],
        'materials': [{'doubleSided': True, 'pbrMetallicRoughness': {
            'baseColorFactor': color, 'metallicFactor': 0.25, 'roughnessFactor': 0.65}}],
        'buffers': [{'byteLength': len(binary)}],
        'bufferViews': [
            {'buffer': 0, 'byteOffset': 0, 'byteLength': len(pos), 'target': 34962},
            {'buffer': 0, 'byteOffset': len(pos), 'byteLength': len(norm), 'target': 34962},
            {'buffer': 0, 'byteOffset': len(pos)+len(norm), 'byteLength': len(indices), 'target': 34963}],
        'accessors': [
            {'bufferView': 0, 'componentType': 5126, 'count': count, 'type': 'VEC3',
             'min': [min(positions[i::3]) for i in range(3)], 'max': [max(positions[i::3]) for i in range(3)]},
            {'bufferView': 1, 'componentType': 5126, 'count': count, 'type': 'VEC3'},
            {'bufferView': 2, 'componentType': 5125, 'count': count, 'type': 'SCALAR'}]}
    encoded = json.dumps(doc, separators=(',', ':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    return (struct.pack('<III', 0x46546c67, 2, 28+len(encoded)+len(binary)) +
            struct.pack('<II', len(encoded), 0x4e4f534a) + encoded +
            struct.pack('<II', len(binary), 0x004e4942) + binary)


def convert(source, target):
    target.mkdir(parents=True, exist_ok=True)
    report = {'source': 'User XVL Studio OBJ export', 'provisionalMmPerUnit': 1.0,
              'materials': 'Display colours assigned; OBJ did not contain materials', 'parts': []}
    for index, (name, color) in enumerate(PARTS, 1):
        raw = (source / f'component_{index}.obj').read_bytes()
        vertices, faces = parse_obj(raw.decode())
        data = make_glb(vertices, faces, name, color)
        (target / f'{name}.glb').write_bytes(data)
        report['parts'].append({'name': name, 'triangles': len(faces),
            'note': 'GLB omits zero-area triangles; original nonzero surfaces retained.',
            'sourceSha256': hashlib.sha256(raw).hexdigest(), 'glbSha256': hashlib.sha256(data).hexdigest(),
            'minimumMm': [min(p[i] for p in vertices) for i in range(3)],
            'maximumMm': [max(p[i] for p in vertices) for i in range(3)]})
    (target / 'manifest.json').write_text(json.dumps(report, indent=2)+'\n')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('target', type=Path)
    args = parser.parse_args()
    print(json.dumps(convert(args.source, args.target), indent=2))
