"""Modified tower solids for precise jaw contact. Validate isolation regions first."""
from pathlib import Path
import sys,json,struct,gzip,base64
work=Path(sys.argv[1]);sys.path.insert(0,str(work/'python-libs'))
root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'tools/enclosure'))
import numpy as np
from inspect_step import load
from convert_enclosure import mesh
from package_enclosure import ASSEMBLY_TO_CAD,EXCLUDED
from OCP.TopExp import TopExp_Explorer
from OCP.TopAbs import TopAbs_SOLID
cad=load(str(work/'MK1.STEP'))
from outlet_clearance import widen
for i,row in enumerate(cad):row['shape']=widen(row['shape'],i)
rows=json.loads((work/'meshes/meshes.json').read_text())
outlets=[(-426.112915,39,216),(-425.112915,-110,196),(-426.112915,-259,199),(421.112915,39,197),(421.112915,-110,233),(421.112915,-259,198)]
regions=[];meta=[]
vec=lambda v:'Vector3('+','.join(str(float(x)) for x in v)+')'
for x,z,index in outlets:
 low=np.array([x-45,25,z-40]);high=np.array([x+45,130,z+40]);found=[]
 for row in rows:
  if row['index'] in EXCLUDED:continue
  bounds=np.array(row['boundsMm']);v=np.array([[a,b,c,1] for a in bounds[:,0] for b in bounds[:,1] for c in bounds[:,2]])@ASSEMBLY_TO_CAD.T
  if np.all(v[:,:3].max(0)>low) and np.all(v[:,:3].min(0)<high):found.append(row['index'])
 assert found==[index],(index,found)
 e=TopExp_Explorer(cad[index]['shape'],TopAbs_SOLID);parts=[];counts=[]
 while e.More():
  m=mesh(e.Current());m.apply_transform(ASSEMBLY_TO_CAD);tri=m.triangles
  data=struct.pack('>i',len(tri))+struct.pack('>'+str(tri.size)+'d',*tri.ravel())
  parts.append('CadTriangleSolid("'+base64.b64encode(gzip.compress(data,mtime=0)).decode()+'")');counts.append(len(tri));e.Next()
 regions.append('Region('+vec(low)+','+vec(high)+',{listOf('+','.join(parts)+')})')
 meta.append(dict(tower=index,low=low.tolist(),high=high.tolist(),onlySource=found,trianglesBySolid=counts))
 print(index,counts,flush=True)
(root/'app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/motion/OutletCadSurfaces.kt').write_text('package mx.youteachtk.epsonrasimulator.runtime.motion\nimport mx.youteachtk.epsonrasimulator.kinematics.Vector3\n// Widened independent closed tower solids; regions checked against all enclosure parts.\ninternal object OutletCadSurfaces { class Region(val low:Vector3,val high:Vector3,create:()->List<CadTriangleSolid>) {val solids by lazy(create)}\nval regions by lazy {listOf('+','.join(regions)+')} }\n')
(work/'outlet-surface-regions.json').write_text(json.dumps(meta,indent=2))
