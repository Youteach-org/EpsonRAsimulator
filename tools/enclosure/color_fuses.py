"""Color only the central straight body between the original CAD shoulder planes.
Input/output geometry remains unchanged; all terminal triangles stay metallic.
"""
import sys
from pathlib import Path
work=Path(sys.argv[1])
sys.path.insert(0,str(work/'python-libs'))
import numpy as np
import trimesh
root=Path(__file__).resolve().parents[2]
source=trimesh.load(work/'movable/fuse-original.glb').to_geometry()
# Preserve the exported CAD face boundaries; no optional scipy dependency.
tri=source.triangles
normals=np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0])
vertex_normals=np.zeros_like(source.vertices)
for corner in range(3):np.add.at(vertex_normals,source.faces[:,corner],normals)
vertex_normals/=np.maximum(np.linalg.norm(vertex_normals,axis=1)[:,None],1e-15)
source.vertex_normals=vertex_normals
# Original shoulder planes in this centered metre asset are z=+-8.25 mm.
central=np.max(np.abs(source.triangles[:,:,2]),axis=1)<=.00825001
assert central.any() and (~central).any()
target=root/'app/src/main/assets/models/workcells/school-enclosure'
for index,color in enumerate(([55,131,214,255],[225,139,48,255],[66,173,119,255])):
 scene=trimesh.Scene()
 for name,mask,c,metal in [('body',central,color,.05),('terminals',~central,[185,193,202,255],.75)]:
  faces=source.faces[mask]
  used,inverse=np.unique(faces,return_inverse=True)
  mesh=trimesh.Trimesh(vertices=source.vertices[used],faces=inverse.reshape((-1,3)),vertex_normals=source.vertex_normals[used],process=False)
  mesh.visual=trimesh.visual.TextureVisuals(material=trimesh.visual.material.PBRMaterial(name=name,baseColorFactor=c,metallicFactor=metal,roughnessFactor=.4))
  scene.add_geometry(mesh,node_name=name)
 assert sum(len(m.faces) for m in scene.geometry.values())==len(source.faces)
 assert np.allclose(scene.bounds,source.bounds)
 scene.export(target/f'fuse-{index}.glb',include_normals=True)
print('Three body colors; preserved all',len(source.faces),'triangles and original bounds. Central faces:',int(central.sum()))
