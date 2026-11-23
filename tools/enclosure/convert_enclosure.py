from pathlib import Path
import sys,json,numpy as np,trimesh
from inspect_step import load
from OCP.BRepMesh import BRepMesh_IncrementalMesh
from OCP.TopExp import TopExp_Explorer
from OCP.TopAbs import TopAbs_FACE,TopAbs_REVERSED
from OCP.TopoDS import TopoDS
from OCP.TopLoc import TopLoc_Location
from OCP.BRep import BRep_Tool

def mesh(shape):
 BRepMesh_IncrementalMesh(shape,0.35,False,0.35,True)
 verts=[];faces=[];e=TopExp_Explorer(shape,TopAbs_FACE)
 while e.More():
  f=TopoDS.Face(e.Current());loc=TopLoc_Location();tri=BRep_Tool.Triangulation_s(f,loc)
  if tri is not None:
   start=len(verts);t=loc.Transformation()
   for i in range(1,tri.NbNodes()+1):
    v=tri.Node(i).Transformed(t);verts.append([v.X(),v.Y(),v.Z()])
   for i in range(1,tri.NbTriangles()+1):
    a,b,c=[tri.Triangle(i).Value(k) for k in (1,2,3)]
    if f.Orientation()==TopAbs_REVERSED:b,c=c,b
    faces.append([start+a-1,start+b-1,start+c-1])
  e.Next()
 return trimesh.Trimesh(vertices=np.array(verts),faces=np.array(faces),process=False)
if __name__=='__main__':
 out=Path(sys.argv[2]);out.mkdir(exist_ok=True,parents=True)
 rows=load(sys.argv[1]);scene=trimesh.Scene();metadata=[]
 for i,row in enumerate(rows):
  m=mesh(row['shape']);m.export(out/f'part-{i:03}.ply')
  metadata.append(dict(index=i,name=row['name'],matrix=row['matrix'],boundsMm=m.bounds.tolist(),triangles=len(m.faces)))
  m.apply_scale(0.001);scene.add_geometry(m,node_name=f"{i}_{row['name']}")
  print(i,ascii(row['name']),len(m.faces),flush=True)
 (out/'meshes.json').write_text(json.dumps(metadata,indent=2))
 scene.export(out/'assembly.glb')
