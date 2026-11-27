"""Reproducible widened outlet meshes, preserving the original STEP and PLYs."""
from pathlib import Path
import sys,json,shutil
work=Path(sys.argv[1]);sys.path.insert(0,str(work/'python-libs'))
from inspect_step import load
from convert_enclosure import mesh
from outlet_clearance import widen,OUTLETS,WIDTH,DEPTH,BOTTOM,TOP
from package_enclosure import package
from OCP.GProp import GProp_GProps
from OCP.BRepGProp import BRepGProp

def volume(shape):
    props=GProp_GProps();BRepGProp.VolumeProperties_s(shape,props);return props.Mass()

cad=load(work/'MK1.STEP')
source=work/'meshes';target=work/'meshes-clearance';target.mkdir(exist_ok=True)
rows=json.loads((source/'meshes.json').read_text());changes=[]
for row in rows:
    i=row['index'];name=f'part-{i:03}.ply'
    if i in OUTLETS:
        old=cad[i]['shape'];new=widen(old,i)
        before=volume(old);after=volume(new)
        assert 0<after<before,(i,before,after)
        m=mesh(new);m.export(target/name)
        row['boundsMm']=m.bounds.tolist();row['triangles']=len(m.faces)
        changes.append(dict(index=i,beforeVolumeMm3=before,afterVolumeMm3=after))
        print(i,before,after,flush=True)
    else:shutil.copyfile(source/name,target/name)
(target/'meshes.json').write_text(json.dumps(rows,indent=2))
out=work/'prepared-clearance';out.mkdir(exist_ok=True)
# Discard only known derived caches for the six changed components.
for i in OUTLETS:
    (out/f'occupancy-{i:03}.npy').unlink(missing_ok=True)
package(target,out)
manifest=json.loads((out/'manifest.json').read_text())
manifest['simulatorModification']=dict(request='Widen central fuse pickup opening',widthMm=WIDTH,depthMm=DEPTH,bottomMm=BOTTOM,topMm=TOP,parts=changes)
manifest['status']='Intentional user-requested central outlet widening; original STEP preserved'
(out/'manifest.json').write_text(json.dumps(manifest,indent=2))
# Reuse only unaffected components; changed towers must be voxelized anew.
for p in (work/'prepared').glob('occupancy-*.npy'):
    if int(p.stem.split('-')[1]) not in OUTLETS:shutil.copyfile(p,out/p.name)
(work/'outlet-clearance-validation.json').write_text(json.dumps(manifest['simulatorModification'],indent=2))
