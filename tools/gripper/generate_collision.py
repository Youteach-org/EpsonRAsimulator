"""Build conservative 1 mm occupancy from the actual exported gripper meshes."""
import sys, json, base64, gzip
from pathlib import Path
import numpy as np
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/c4'))
import generate_j4_collision_hull as hull

hull.CELL = 1.0
asset = ROOT/'app/src/main/assets/models/tools/school-gripper'
out = ROOT/'app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/motion/SchoolGripperHulls.kt'
lines = ['package mx.youteachtk.epsonrasimulator.runtime.motion',
         'import mx.youteachtk.epsonrasimulator.kinematics.Vector3',
         '// Generated from original gripper GLBs; 1 mm surface cells plus enclosed interiors.',
         'internal object SchoolGripperHulls {', 'val values by lazy { mapOf(']
reports=[]
for source in sorted(asset.glob('*.glb')):
    triangles, digest = hull.read_triangles(source)
    low,high=triangles.min((0,1)),triangles.max((0,1))
    origin=np.floor(low)-2
    count=1
    while count <= max(high-origin)+2: count*=2
    surface=hull.mark_surface(triangles,origin,count)
    solid=hull.fill_solid(surface)
    encoded=base64.b64encode(gzip.compress(hull.encode(solid),mtime=0)).decode()
    chunks=',\n'.join('"'+encoded[i:i+8000]+'"' for i in range(0,len(encoded),8000))
    vec=lambda p:'Vector3('+','.join(str(float(x)) for x in p)+')'
    lines.append(f'"{source.stem}" to CadVoxelHull(listOf({chunks}).joinToString(""),{vec(origin)},1.0,{count},{vec(low)},{vec(high)}),')
    reports.append(dict(part=source.stem,sourceSha256=digest,cellMm=1.0,grid=count,occupied=int(solid.sum())))
lines += [') }','}']
out.write_text('\n'.join(lines)+'\n')
(asset/'collision-manifest.json').write_text(json.dumps(reports,indent=2)+'\n')
print('Generated',len(reports),'original-mesh collision hulls')
