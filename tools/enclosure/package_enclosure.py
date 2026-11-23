"""Package the measured enclosure, omitting the embedded C3 and its tool.

Input PLY geometry is already in STEP assembly millimetres. The C3's measured
vertical cylinders share axis X=-827.5, Z=700. The mounting plate is Y=0.
Rotate assembly +X (robot front) onto the simulator's CAD -Z; keep Y up.
No guessed resizing, simplified replacement geometry, or moving robot included.
"""
from pathlib import Path
import argparse, json, hashlib
import numpy as np
import trimesh

EXCLUDED = {58, 287, 288, 289, 290, 291, 292, 215, 232, 249}  # outlet fuses become dynamic
ASSEMBLY_TO_CAD = np.array([[0.,0.,1.,-700.], [0.,1.,0.,0.], [-1.,0.,0.,-827.5], [0.,0.,0.,1.]])

def package(source, target):
    target.mkdir(parents=True,exist_ok=True)
    rows=json.loads((source/'meshes.json').read_text())
    assert len(rows)==308
    assert rows[58]['name']=='C3_A600S_ASM2'
    scene=trimesh.Scene();included=[]
    for row in rows:
        i=row['index']
        if i in EXCLUDED: continue
        mesh=trimesh.load(source/f'part-{i:03}.ply',process=False)
        assert isinstance(mesh,trimesh.Trimesh) and len(mesh.faces)>0
        mesh.apply_transform(ASSEMBLY_TO_CAD)
        bounds=mesh.bounds.tolist()
        mesh.apply_scale(.001)
        # Average only vertices shared by a STEP face; sharp CAD face boundaries
        # already have separate vertices. Emit normals explicitly for Filament.
        triangles=mesh.triangles
        normals=np.cross(triangles[:,1]-triangles[:,0],triangles[:,2]-triangles[:,0])
        vertex_normals=np.zeros_like(mesh.vertices)
        for corner in range(3):np.add.at(vertex_normals,mesh.faces[:,corner],normals)
        lengths=np.linalg.norm(vertex_normals,axis=1)
        vertex_normals/=np.maximum(lengths[:,None],1e-15)
        mesh.vertex_normals=vertex_normals
        mesh.visual=trimesh.visual.TextureVisuals(material=trimesh.visual.material.PBRMaterial(
            baseColorFactor=[170,178,186,255],metallicFactor=.2,roughnessFactor=.65))
        scene.add_geometry(mesh,node_name=f'part-{i:03}')
        included.append(dict(index=i,name=row['name'],boundsCadMm=bounds,triangles=len(mesh.faces)))
    assert len(included)==298
    result=target/'enclosure.glb';scene.export(result,include_normals=True)
    manifest=dict(source='MK1.STEP from sistema Epson.zip',unit='metre',
        assemblyToCadMm=ASSEMBLY_TO_CAD.tolist(),mountAxisAssemblyMm=[-827.5,0.,700.],
        mountEvidence='Coaxial Y cylinders R72/R80/R85/R86 from C3 BRep; plate top Y=0',
        excluded=[r for r in rows if r['index'] in EXCLUDED],parts=included,
        sha256=hashlib.sha256(result.read_bytes()).hexdigest(),
        status='Converted geometry; application placement and collision integration not yet validated')
    (target/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(f'Exported {len(included)} static parts; {result.stat().st_size} bytes')
    print('Bounds metres:',scene.bounds.tolist())

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path);parser.add_argument('target',type=Path)
    args=parser.parse_args();package(args.source,args.target)
