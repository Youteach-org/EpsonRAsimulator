import sys,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
work=Path(sys.argv[1])
gripper='--gripper' in sys.argv
stem='outlet-gripper' if gripper else 'outlet-exact'
object_name='OutletGripperCadBoxes' if gripper else 'OutletCadBoxes'
sys.path.insert(0,str(work/'python-libs'));sys.path.insert(0,str(root/'tools/enclosure'))
import numpy as np,trimesh
from package_enclosure import ASSEMBLY_TO_CAD,EXCLUDED
from inspect_step import load
from OCP.BRepClass3d import BRepClass3d_SolidClassifier
from OCP.gp import gp_Pnt
from OCP.TopAbs import TopAbs_IN,TopAbs_SOLID
from OCP.TopExp import TopExp_Explorer
def solids(shape):
 e=TopExp_Explorer(shape,TopAbs_SOLID);out=[]
 while e.More():
  out.append(e.Current());e.Next()
 return out
cad=load(str(work/'MK1.STEP'))
from outlet_clearance import widen
for i,row in enumerate(cad):row['shape']=widen(row['shape'],i)
base=work/'meshes';rows=json.loads((base/'meshes.json').read_text())
outlets=[(-426.1129150390625,39,216),(-425.1129150390625,-110,196),(-426.1129150390625,-259,199),(421.1129150390625,39,197),(421.1129150390625,-110,233),(421.1129150390625,-259,198)]
# Classify the union of independent BRep solids; accept only axis-aligned local faces.
zones=[]
for x,z,tower in outlets:
 low=np.array([x-(24 if x<0 else 45),25,z-40]) if gripper else np.array([x-13,25,z-40])
 high=np.array([x+(45 if x<0 else 24),130,z+40]) if gripper else np.array([x+13,90,z+40])
 sources=[];planes=[[low[i],high[i]] for i in range(3)]
 for row in rows:
  if row['index'] in EXCLUDED:continue
  bounds=np.array(row['boundsMm']);corners=np.array([[a,b,c,1] for a in bounds[:,0] for b in bounds[:,1] for c in bounds[:,2]])@ASSEMBLY_TO_CAD.T
  if np.any(corners[:,:3].max(0)<=low) or np.any(corners[:,:3].min(0)>=high):continue
  from convert_enclosure import mesh
  m=mesh(cad[row['index']]['shape']);m.apply_transform(ASSEMBLY_TO_CAD);t=m.triangles
  hit=np.all(t.max(1)>low+1e-6,axis=1)&np.all(t.min(1)<high-1e-6,axis=1)
  for tri,n in zip(t[hit],m.face_normals[hit]):
   if np.linalg.norm(n)<.5:continue
   axis=int(np.argmax(abs(n)));assert abs(n[axis])>1-1e-8,(tower,row['index'],n)
   plane=float(tri[:,axis].mean())
   if low[axis]+1e-6<plane<high[axis]-1e-6:planes[axis].append(plane)
  sources.extend(solids(cad[row['index']]['shape']))
 coords=[np.unique(np.round(p,6)) for p in planes];boxes=[]
 for i in range(len(coords[0])-1):
  for j in range(len(coords[1])-1):
   for k in range(len(coords[2])-1):
    a=np.array([coords[0][i],coords[1][j],coords[2][k]]);b=np.array([coords[0][i+1],coords[1][j+1],coords[2][k+1]])
    p=(a+b)/2
    if any(BRepClass3d_SolidClassifier(s,gp_Pnt(-p[2]-827.5,p[1],p[0]+700),1e-7).State()==TopAbs_IN for s in sources):boxes.append([a.tolist(),b.tolist()])
 zones.append(dict(low=low.tolist(),high=high.tolist(),boxes=boxes,tower=tower));print(tower,len(sources),[len(c) for c in coords],len(boxes),flush=True)
(work/f'{stem}-boxes.json').write_text(json.dumps(zones,indent=2))
vec=lambda v:'Vector3('+','.join(str(float(x)) for x in v)+')'
code='package mx.youteachtk.epsonrasimulator.runtime.motion\nimport mx.youteachtk.epsonrasimulator.kinematics.Vector3\n// User-widened CAD outlet faces: exact plane partition of the modified solid.\ninternal object OutletCadBoxes {\ndata class Region(val low:Vector3,val high:Vector3,val boxes:List<Pair<Vector3,Vector3>>)\nval regions=listOf(\n'
code=code.replace('OutletCadBoxes',object_name)
code+=',\n'.join('Region('+vec(r['low'])+','+vec(r['high'])+',listOf('+','.join(vec(a)+' to '+vec(b) for a,b in r['boxes'])+'))' for r in zones)+'\n)\n}\n'
(root/f'app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/motion/{object_name}.kt').write_text(code,encoding='utf8')
