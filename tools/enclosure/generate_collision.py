"""Generate sparse 2 mm enclosure occupancy without filling its open interior."""
import sys,json,gzip,base64,hashlib
from pathlib import Path
import numpy as np
import trimesh
from package_enclosure import ASSEMBLY_TO_CAD,EXCLUDED
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'c4'))
from generate_j4_collision_hull import fill_solid
CELL=2.

def surface(tris,origin,shape):
 cells=np.zeros(tuple(shape),dtype=bool)
 for tri in tris:
  low=np.maximum(0,np.floor((tri.min(0)-origin)/CELL-1e-7).astype(int))
  high=np.minimum(shape-1,np.floor((tri.max(0)-origin)/CELL+1e-7).astype(int))
  if np.any(high<low):continue
  grid=np.indices(tuple(high-low+1)).reshape(3,-1).T+low
  centers=origin+(grid+.5)*CELL
  edges=[tri[1]-tri[0],tri[2]-tri[1],tri[0]-tri[2]]
  axes=[np.cross(edges[0],edges[1])]+[np.cross(e,b) for e in edges for b in np.eye(3)]
  valid=np.ones(len(grid),dtype=bool)
  for axis in axes:
   length=np.linalg.norm(axis)
   if length<1e-10:continue
   axis=axis/length;p=tri@axis;c=centers@axis;r=CELL/2*np.abs(axis).sum()
   valid&=(p.min()-c<=r+1e-7)&(p.max()-c>=-r-1e-7)
  hit=grid[valid];cells[tuple(hit.T)]=True
 return cells

def morton(points):
 p=points.astype(np.uint64);code=np.zeros(len(p),dtype=np.uint64)
 for bit in range(11):
  for axis in range(3):code|=((p[:,axis]>>bit)&1)<<(3*bit+axis)
 return code

def encode(codes,width):
 out=bytearray()
 def visit(lo,hi,start,n):
  if hi==lo:out.append(0);return
  if hi-lo==n**3:out.append(1);return
  out.append(2);half=n//2;stride=half**3
  boundaries=[lo]+[int(np.searchsorted(codes,np.uint64(start+i*stride))) for i in range(1,8)]+[hi]
  for i in range(8):visit(boundaries[i],boundaries[i+1],start+i*stride,half)
 visit(0,len(codes),0,width)
 return bytes(out)

if __name__=='__main__':
 source=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
 manifest=json.loads((out/'manifest.json').read_text());parts=manifest['parts']
 low=np.min([p['boundsCadMm'][0] for p in parts],axis=0);high=np.max([p['boundsCadMm'][1] for p in parts],axis=0)
 origin=np.floor(low/CELL)*CELL-CELL;size=1
 while size*CELL<max(high-origin)+CELL:size*=2
 chunks=[]
 for part in parts:
  i=part['index'];cache=out/f'occupancy-{i:03}.npy'
  if cache.exists():codes=np.load(cache)
  else:
   m=trimesh.load(source/f'part-{i:03}.ply',process=False);m.apply_transform(ASSEMBLY_TO_CAD)
   lo=np.floor((m.bounds[0]-origin)/CELL+1e-5).astype(int)
   hi=np.ceil((m.bounds[1]-origin)/CELL-1e-5).astype(int)
   hi=np.maximum(hi,lo+1)
   # Pad each rectangular component for exterior flood fill. Its closed interiors
   # become solid; the empty space between separate enclosure parts stays empty.
   raw=surface(m.triangles,origin+lo*CELL,hi-lo)
   padded=np.pad(raw,1);solid=fill_solid(padded)[1:-1,1:-1,1:-1]
   indices=np.argwhere(solid)+lo
   codes=morton(indices);np.save(cache,codes)
  chunks.append(codes);print(i,len(codes),flush=True)
 codes=np.unique(np.concatenate(chunks));code=encode(codes,size)
 (out/'occupancy.bin.gz').write_bytes(gzip.compress(code,mtime=0))
 meta=dict(cellMm=CELL,origin=origin.tolist(),gridSize=size,minimum=low.tolist(),maximum=high.tolist(),occupied=len(codes),treeBytes=len(code),sha256=hashlib.sha256(code).hexdigest())
 (out/'collision.json').write_text(json.dumps(meta,indent=2));print(meta,flush=True)
