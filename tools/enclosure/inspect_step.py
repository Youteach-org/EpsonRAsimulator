from pathlib import Path
import json,sys
from OCP.STEPCAFControl import STEPCAFControl_Reader
from OCP.TCollection import TCollection_ExtendedString
from OCP.TDataStd import TDataStd_Name
from OCP.TDF import TDF_Label
from OCP.collections import Sequence_TDF_Label as TDF_LabelSequence
from OCP.TDocStd import TDocStd_Document
from OCP.XCAFDoc import XCAFDoc_DocumentTool,XCAFDoc_ShapeTool
from OCP.Bnd import Bnd_Box
from OCP.BRepBndLib import BRepBndLib
from OCP.TopLoc import TopLoc_Location

def name(l):
 a=TDataStd_Name()
 return a.Get().ToExtString() if l.FindAttribute(TDataStd_Name.GetID_s(),a) else ''
def load(path):
 doc=TDocStd_Document(TCollection_ExtendedString('MDTV-XCAF')); reader=STEPCAFControl_Reader();reader.SetNameMode(True)
 print('read',reader.ReadFile(str(path)),flush=True);print('transfer',reader.Transfer(doc),flush=True)
 st=XCAFDoc_DocumentTool.ShapeTool_s(doc.Main());roots=TDF_LabelSequence();st.GetFreeShapes(roots)
 rows=[]
 def walk(label,loc,path):
  ref=TDF_Label();isref=st.GetReferredShape_s(label,ref); definition=ref if isref else label
  loc=loc.Multiplied(st.GetLocation_s(label)); n=name(definition);path=path+[name(label) or n]
  comps=TDF_LabelSequence();st.GetComponents_s(definition,comps,False)
  if comps.Length():
   for i in range(1,comps.Length()+1):walk(comps.Value(i),loc,path)
  else:
   shape=st.GetShape_s(definition).Moved(loc);b=Bnd_Box();BRepBndLib.Add_s(shape,b)
   t=loc.Transformation(); rows.append(dict(name=n,path=path,boundsMm=[b.CornerMin().X(),b.CornerMin().Y(),b.CornerMin().Z(),b.CornerMax().X(),b.CornerMax().Y(),b.CornerMax().Z()],matrix=[[t.Value(i,j) for j in range(1,5)] for i in range(1,4)],shape=shape))
 for i in range(1,roots.Length()+1):walk(roots.Value(i),TopLoc_Location(),[])
 return rows
if __name__=='__main__':
 rows=load(sys.argv[1]);Path(sys.argv[2]).write_text(json.dumps([{k:v for k,v in r.items() if k!='shape'} for r in rows],indent=2));print('leaves',len(rows))
