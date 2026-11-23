import sys,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
work=Path(sys.argv[1])
stem='outlet-gripper' if '--gripper' in sys.argv else 'outlet-exact'
sys.path.insert(0,str(work/'python-libs'));sys.path.insert(0,str(root/'tools/enclosure'))
from inspect_step import load
from OCP.BRepPrimAPI import BRepPrimAPI_MakeBox
from OCP.gp import gp_Pnt
from OCP.BRepAlgoAPI import BRepAlgoAPI_Common
from OCP.GProp import GProp_GProps
from OCP.BRepGProp import BRepGProp
from OCP.BRep import BRep_Builder
from OCP.TopoDS import TopoDS_Compound
from OCP.TopExp import TopExp_Explorer
from OCP.TopAbs import TopAbs_SOLID
rows=load(str(work/'MK1.STEP'))
from outlet_clearance import widen
for i,row in enumerate(rows):row['shape']=widen(row['shape'],i)
def box(a,b):return BRepPrimAPI_MakeBox(gp_Pnt(-b[2]-827.5,a[1],a[0]+700),gp_Pnt(-a[2]-827.5,b[1],b[0]+700)).Shape()
def volume(s):
 p=GProp_GProps();BRepGProp.VolumeProperties_s(s,p);return p.Mass()
results=[]
for r in json.load(open(str(work/f'{stem}-boxes.json'))):
 source=rows[r['tower']]['shape'];clipped=BRepAlgoAPI_Common(source,box(r['low'],r['high'])).Shape()
 compound=TopoDS_Compound();builder=BRep_Builder();builder.MakeCompound(compound)
 for a,b in r['boxes']:builder.Add(compound,box(a,b))
 actual=volume(clipped);generated=volume(compound);common=0.0
 e=TopExp_Explorer(source,TopAbs_SOLID)
 while e.More():
  for a,b in r['boxes']: common+=volume(BRepAlgoAPI_Common(e.Current(),box(a,b)).Shape())
  e.Next()
 result=dict(tower=r['tower'],cadVolume=actual,boxesVolume=generated,commonVolume=common);print(result,flush=True)
 # Float32 tessellation coordinates accumulate a small volume error over the
 # larger jaw region (about 0.04 mm^3 on the right towers, <1 ppm of volume).
 tolerance=.1 if stem=='outlet-gripper' else .02
 assert abs(actual-generated)<tolerance and abs(common-generated)<tolerance,result
 results.append(result)
json.dump(results,open(str(work/f'{stem}-verification.json'),'w'),indent=2)
