"""Encode the original jaw surface triangles, without voxel inflation."""
from pathlib import Path
import sys,struct,gzip,base64
work=Path(sys.argv[1]);sys.path.insert(0,str(work/'python-libs'))
import trimesh
root=Path(__file__).resolve().parents[2]
entries=[]
for name in ['jaw_left','jaw_right']:
 mesh=trimesh.load(root/f'app/src/main/assets/models/tools/school-gripper/{name}.glb').to_geometry()
 tris=mesh.triangles*1000
 data=struct.pack('>i',len(tris))+struct.pack('>'+str(tris.size)+'d',*tris.ravel())
 encoded=base64.b64encode(gzip.compress(data,mtime=0)).decode()
 entries.append(f'"{name}" to CadTriangleSolid("{encoded}")')
 print(name,len(tris),len(encoded))
(root/'app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/motion/SchoolJawSurfaces.kt').write_text('package mx.youteachtk.epsonrasimulator.runtime.motion\n// Original exported CAD triangles in millimetres.\ninternal object SchoolJawSurfaces { val values by lazy {mapOf('+','.join(entries)+')} }\n')
