"""Install validated conversion products into app assets and generated hull data."""
from pathlib import Path
import json,base64,shutil,sys
root=Path(__file__).resolve().parents[2];source=Path(sys.argv[1])
meta=json.loads((source/'collision.json').read_text())
encoded=base64.b64encode((source/'occupancy.bin.gz').read_bytes()).decode()
vec=lambda values:'Vector3('+','.join(str(float(x)) for x in values)+')'
chunks=',\n'.join('"'+encoded[i:i+8000]+'"' for i in range(0,len(encoded),8000))
text='''package mx.youteachtk.epsonrasimulator.runtime.motion
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
// Generated from MK1.STEP, excluding its embedded robot/tool. Preserve exterior gaps.
internal object EnclosureCadData {
'''
text+=f'val minimum={vec(meta["minimum"])}\nval maximum={vec(meta["maximum"])}\n'
text+=f'val origin={vec(meta["origin"])}\nconst val cellMm={meta["cellMm"]}\nconst val gridSize={meta["gridSize"]}\n'
text+=f'val encoded by lazy {{ listOf({chunks}).joinToString("") }}\n}}\n'
(root/'app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/motion/EnclosureCadData.kt').write_text(text)
asset=root/'app/src/main/assets/models/workcells/school-enclosure';asset.mkdir(parents=True,exist_ok=True)
for name in ['enclosure.glb','manifest.json','collision.json']:shutil.copyfile(source/name,asset/name)
print('Installed converted mesh and sparse occupancy',len(encoded),'base64 bytes')
