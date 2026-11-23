"""Project small text decals onto the actual CAD triangles; ordinary depth-tested GLBs."""
from pathlib import Path
import json, struct, io
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app/src/main/assets/models/robots/c4-a601s'

def mesh(path):
    raw=path.read_bytes(); size=struct.unpack_from('<I',raw,12)[0]
    doc=json.loads(raw[20:20+size]); binary=raw[28+size:]
    def accessor(index):
        a=doc['accessors'][index];v=doc['bufferViews'][a['bufferView']]
        dtype={5126:'<f4',5125:'<u4',5123:'<u2'}[a['componentType']]
        width={'SCALAR':1,'VEC3':3}[a['type']]
        off=v.get('byteOffset',0)+a.get('byteOffset',0)
        return np.ndarray((a['count'],width),dtype=dtype,buffer=binary,offset=off,
                          strides=(v.get('byteStride',np.dtype(dtype).itemsize*width),np.dtype(dtype).itemsize))
    result=[]
    for m in doc['meshes']:
        for p in m['primitives']:
            pts=accessor(p['attributes']['POSITION'])
            result.append(pts[accessor(p['indices']).reshape(-1)].reshape(-1,3,3))
    return np.concatenate(result)

def generate(j,axis,sign,center,width,height,u=None,v=None):
    tri=mesh(ASSETS/f'C4_J{j}.glb').astype(float)
    # u goes right and v goes up as viewed from the outward face.
    u=(0 if axis==2 else 2) if u is None else u
    v=1 if v is None else v
    usign=sign if (axis,u,v) in [(2,0,1),(0,1,2)] else -sign
    a=tri[:,0];b=tri[:,1]-a;c=tri[:,2]-a
    det=b[:,u]*c[:,v]-b[:,v]*c[:,u]
    good=np.abs(det)>1e-12
    a=a[good];b=b[good];c=c[good];det=det[good]
    positions=[];uv=[];valid=[];n=16
    for row in range(n+1):
        for col in range(n+1):
            x=center[0]+usign*(col/n-.5)*width;y=center[1]+(.5-row/n)*height
            du=x-a[:,u];dv=y-a[:,v]
            s=(du*c[:,v]-dv*c[:,u])/det;t=(b[:,u]*dv-b[:,v]*du)/det
            inside=(s>=-1e-7)&(t>=-1e-7)&(s+t<=1.0000001)
            hits=a[:,axis]+s*b[:,axis]+t*c[:,axis]
            value=(np.max(hits[inside]) if sign>0 else np.min(hits[inside])) if inside.any() else 0
            p=[0.,0.,0.];p[u]=x;p[v]=y;p[axis]=value+sign*.00025
            positions.append(p);uv.append((col/n,row/n));valid.append(bool(inside.any()))
    indices=[]
    for row in range(n):
        for col in range(n):
            a0=row*(n+1)+col;b0=a0+1;c0=a0+n+1;d0=c0+1
            if all(valid[i] for i in (a0,b0,c0,d0)):
                # Reject discontinuities across holes/edges, so the sticker never bridges empty space.
                depths=[positions[i][axis] for i in (a0,b0,c0,d0)]
                if max(depths)-min(depths)<.006: indices += [a0,c0,b0,b0,c0,d0]
    assert len(indices)>n*n*3,(j,'insufficient surface coverage')
    for selected in (False,True):
        vertical=height>width
        im=Image.new('RGBA',(144,720) if vertical else (720,144),(0,0,0,0));d=ImageDraw.Draw(im)
        w,h=im.size
        # White printed strips, like the laboratory photographs. Selection keeps
        # the print unchanged and adds only a thin gold outline.
        d.rounded_rectangle((2,2,w-3,h-3),radius=5,fill='#f5f4ed',outline='#d4a642' if selected else '#bcbcb5',width=4 if selected else 2)
        font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',86)
        def text_at(x,y,text):d.text((x,y),text,font=font,anchor='mm',fill='#151515')
        def arrow(cx,cy,dx,dy):
            # Outline arrow: local direction is +x before applying rotation.
            poly=[(-43,-13),(8,-13),(8,-26),(44,0),(8,26),(8,13),(-43,13)]
            pts=[(cx+x*dx-y*dy,cy+x*dy+y*dx) for x,y in poly]
            d.line(pts+[pts[0]],fill='#151515',width=5,joint='curve')
        if vertical:
            # For X-axis joints viewed from -Z, positive rotation goes upward.
            positive_up=sign<0
            text_at(w/2,60,'+' if positive_up else '−')
            arrow(w/2,185,0,-1)
            text_at(w/2,h/2,f'J{j}')
            arrow(w/2,h-185,0,1)
            text_at(w/2,h-60,'−' if positive_up else '+')
        else:
            text_at(60,h/2,'−');arrow(205,h/2,-1,0)
            text_at(w/2,h/2,f'J{j}')
            arrow(w-205,h/2,1,0);text_at(w-60,h/2,'+')
        png=io.BytesIO();im.save(png,format='PNG')
        blobs=[np.array(positions,dtype='<f4').tobytes(),np.array(uv,dtype='<f4').tobytes(),np.array(indices,dtype='<u2').tobytes(),png.getvalue()]
        buf=b'';views=[]
        for blob in blobs:
            views.append({'buffer':0,'byteOffset':len(buf),'byteLength':len(blob)})
            buf+=blob;buf+=b'\0'*((-len(buf))%4)
        doc={'asset':{'version':'2.0'},'extensionsUsed':['KHR_materials_unlit'],
             'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'mesh':0}],
             'buffers':[{'byteLength':len(buf)}],'bufferViews':views,
             'accessors':[{'bufferView':0,'componentType':5126,'count':len(positions),'type':'VEC3','min':np.min(positions,axis=0).tolist(),'max':np.max(positions,axis=0).tolist()},
                          {'bufferView':1,'componentType':5126,'count':len(uv),'type':'VEC2'},
                          {'bufferView':2,'componentType':5123,'count':len(indices),'type':'SCALAR'}],
             'images':[{'bufferView':3,'mimeType':'image/png'}],'textures':[{'source':0}],
             'materials':[{'extensions':{'KHR_materials_unlit':{}},'doubleSided':True,'alphaMode':'MASK','alphaCutoff':.5,'pbrMetallicRoughness':{'baseColorTexture':{'index':0},'metallicFactor':0,'roughnessFactor':1}}],
             'meshes':[{'primitives':[{'attributes':{'POSITION':0,'TEXCOORD_0':1},'indices':2,'material':0}]}]}
        js=json.dumps(doc,separators=(',',':')).encode();js+=b' '*((-len(js))%4)
        raw=struct.pack('<III',0x46546c67,2,28+len(js)+len(buf))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(buf),0x004e4942)+buf
        (ASSETS/f'J{j}_sticker{"_other" if sign<0 else ""}{"_active" if selected else ""}.glb').write_bytes(raw)
    print(f'J{j}: {len(indices)//3} surface triangles')
    return len(indices)//3

if __name__=='__main__':
    # axis, (u,v), centre in that plane, strip dimensions in metres.
    for j,axis,u,v,center,width,height in [
        (1,0,2,1,(0.0,.250),.070,.012),
        (2,2,0,1,(-.045,0.0),.012,.060),
        (3,2,0,1,(-.050,0.0),.012,.060),
        (4,0,1,2,(.006,-.093),.055,.011),
        (5,2,0,1,(.024,0.0),.006,.024),
        (6,0,1,2,(0.0,-.0625),.035,.004)]:
        for sign in (1,-1):
            generate(j,axis,sign,(-.019,0.0) if j==5 and sign<0 else center,width,height,u,v)
