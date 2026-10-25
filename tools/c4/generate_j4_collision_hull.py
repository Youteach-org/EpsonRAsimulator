"""Generate conservative 2 mm robot occupancy trees from the shipped CAD.

Requires numpy for offline generation only. Every triangle-intersecting cell is
retained. Flood filling the exterior retains enclosed solid interiors while
preserving exterior-connected clearances. Runtime boxes are clipped to the CAD
bounds, so existing swept-motion bounds remain valid. Resolution is explicit:
maximum surface expansion is sqrt(3)*2 mm, never a whole-link bounding box.
"""
import base64
import argparse
import gzip
import hashlib
import itertools
import json
from pathlib import Path
import struct
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/assets/models/robots/c4-a601s/C4_J4.glb'
TARGET = ROOT / 'app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/motion/C4J4CollisionHull.kt'
CELL = 2.0

def read_triangles(source=None):
    data = (source or SOURCE).read_bytes()
    magic, version, total = struct.unpack_from('<III', data)
    assert magic == 0x46546c67 and version == 2 and total == len(data)
    size, kind = struct.unpack_from('<II', data, 12)
    assert kind == 0x4e4f534a
    doc = json.loads(data[20:20+size])
    binary = 20 + size + 8
    identity = [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
    for node in doc.get('nodes', []):
        assert node.get('matrix', identity) == identity
        assert node.get('translation', [0,0,0]) == [0,0,0]
        assert node.get('rotation', [0,0,0,1]) == [0,0,0,1]
        assert node.get('scale', [1,1,1]) == [1,1,1]
    def accessor(index):
        a = doc['accessors'][index]
        view = doc['bufferViews'][a['bufferView']]
        dtype = np.dtype({5126:'<f4',5123:'<u2',5125:'<u4'}[a['componentType']])
        cols = {'VEC3':3,'SCALAR':1}[a['type']]
        return np.ndarray((a['count'], cols), dtype=dtype, buffer=data,
            offset=binary+view.get('byteOffset',0)+a.get('byteOffset',0),
            strides=(view.get('byteStride',dtype.itemsize*cols),dtype.itemsize))
    triangles = []
    for mesh in doc['meshes']:
        for primitive in mesh['primitives']:
            assert primitive.get('mode',4) == 4
            vertices = accessor(primitive['attributes']['POSITION']).astype(float)*1000
            indices = accessor(primitive['indices']).reshape(-1,3)
            triangles.append(vertices[indices])
    return np.concatenate(triangles), hashlib.sha256(data).hexdigest()

def mark_surface(triangles, origin, count):
    cells = np.zeros((count,count,count), dtype=bool)
    for index, tri in enumerate(triangles):
        lo = np.floor((tri.min(0)-origin)/CELL-1e-8).astype(int)
        hi = np.floor((tri.max(0)-origin)/CELL+1e-8).astype(int)
        grid = np.array(list(itertools.product(*(range(a,b+1) for a,b in zip(lo,hi)))),dtype=int)
        centers = origin + (grid+0.5)*CELL
        edges = [tri[1]-tri[0],tri[2]-tri[1],tri[0]-tri[2]]
        axes = [np.cross(edges[0],edges[1])]
        axes += [np.cross(edge,basis) for edge in edges for basis in np.eye(3)]
        valid = np.ones(len(grid),dtype=bool)
        for axis in axes:
            length = np.linalg.norm(axis)
            if length < 1e-10: continue
            axis = axis/length
            projection = tri@axis
            center = centers@axis
            radius = CELL/2*np.abs(axis).sum()
            valid &= (projection.min()-center <= radius+1e-7) & (projection.max()-center >= -radius-1e-7)
        touched = grid[valid]
        assert np.all(touched>=0) and np.all(touched<count)
        cells[tuple(touched.T)] = True
        if index%10000 == 0: print(f'surface {index}/{len(triangles)}',flush=True)
    return cells

def fill_solid(surface):
    free = ~surface
    exterior = np.zeros_like(surface)
    for dim in range(3):
        for edge in (0,-1):
            sl = [slice(None)]*3;sl[dim]=edge;sl=tuple(sl)
            exterior[sl] = free[sl]
    while True:
        previous = np.count_nonzero(exterior)
        for dim in range(3):
            a=[slice(None)]*3;b=a.copy();a[dim]=slice(1,None);b[dim]=slice(None,-1)
            a=tuple(a);b=tuple(b)
            exterior[a] |= exterior[b]&free[a]
            exterior[b] |= exterior[a]&free[b]
        if np.count_nonzero(exterior) == previous: break
    return ~exterior

def encode(cells):
    output=bytearray()
    def visit(x,y,z,n):
        block=cells[x:x+n,y:y+n,z:z+n]
        if not np.any(block): output.append(0)
        elif np.all(block): output.append(1)
        else:
            output.append(2);half=n//2
            for octant in range(8):
                visit(x+(octant&1)*half,y+((octant>>1)&1)*half,z+((octant>>2)&1)*half,half)
    visit(0,0,0,cells.shape[0])
    return bytes(output)

def generate(name):
    source = SOURCE.with_name(f'C4_{name}.glb')
    object_name = 'C4' + ('Base' if name == 'BASE' else name) + 'CollisionHull'
    target = TARGET.with_name(object_name + '.kt')
    triangles, source_hash=read_triangles(source)
    low=triangles.min((0,1));high=triangles.max((0,1))
    origin=np.floor(low/CELL)*CELL-CELL*2
    count=1
    while count*CELL <= np.max(high-origin)+CELL*2: count*=2
    surface=mark_surface(triangles,origin,count)
    solid=fill_solid(surface)
    # An independently inspected free corridor in J4, >35 mm from triangle bounds.
    if name == 'J4':
        point=np.floor((np.array([0,58,-220])-origin)/CELL).astype(int)
        assert not solid[tuple(point)], 'J4 side clearance must stay exterior'
    code=encode(solid)
    compressed=gzip.compress(code,mtime=0)
    text=base64.b64encode(compressed).decode('ascii')
    chunks=[text[i:i+8000] for i in range(0,len(text),8000)]
    content='''package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.Vector3

// Generated by tools/c4/generate_j4_collision_hull.py from SOURCE_FILE.
// Conservative 2 mm surface cells plus enclosed interior; exterior holes stay free.
internal object OBJECT_NAME {
'''
    content=content.replace('SOURCE_FILE',source.name).replace('OBJECT_NAME',object_name)
    content+=f'    val origin = Vector3({origin[0]}, {origin[1]}, {origin[2]})\n'
    content+=f'    const val cellMm = {CELL}\n    const val gridSize = {count}\n'
    content+='    val encoded = listOf(\n'+',\n'.join('        "'+c+'"' for c in chunks)+'\n    ).joinToString("")\n}\n'
    target.write_text(content,encoding='utf-8')
    metadata=dict(source=source.name,sourceSha256=source_hash,cellMm=CELL,origin=origin.tolist(),gridSize=count,
        occupiedCells=int(solid.sum()),surfaceCells=int(surface.sum()),treeBytes=len(code),compressedBytes=len(compressed),
        maxSurfaceExpansionMm=3**0.5*CELL,treeSha256=hashlib.sha256(code).hexdigest())
    (ROOT/f'tools/c4/{name.lower()}-collision-hull.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(metadata),flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--all',action='store_true',help='Generate BASE and J1 through J6')
    args=parser.parse_args()
    for name in (['BASE']+[f'J{i}' for i in range(1,7)] if args.all else ['J4']):
        print(f'Generating {name}',flush=True)
        generate(name)

if __name__=='__main__': main()
