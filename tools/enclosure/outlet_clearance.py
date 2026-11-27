"""User-requested central gripper access in the six fuse outlets.

This is an intentional simulator CAD modification, not original school geometry.
The same BRep cut drives rendering, voxel collision and exact contact surfaces.
Terminal seats outside the central 40 mm strip are preserved.
"""
OUTLETS={216:(-426.1129150390625,39.),196:(-425.1129150390625,-110.),
         199:(-426.1129150390625,-259.),197:(421.1129150390625,39.),
         233:(421.1129150390625,-110.),198:(421.1129150390625,-259.)}
WIDTH=72.0
DEPTH=40.0
BOTTOM=25.0
TOP=130.0

def widen(shape,index):
    if index not in OUTLETS:return shape
    from OCP.BRepPrimAPI import BRepPrimAPI_MakeBox
    from OCP.BRepAlgoAPI import BRepAlgoAPI_Cut
    from OCP.BRepCheck import BRepCheck_Analyzer
    from OCP.gp import gp_Pnt
    x,z=OUTLETS[index]
    # Inverse ASSEMBLY_TO_CAD; all dimensions in millimetres.
    cutter=BRepPrimAPI_MakeBox(gp_Pnt(-z-DEPTH/2-827.5,BOTTOM,x-WIDTH/2+700),DEPTH,TOP-BOTTOM,WIDTH).Shape()
    # Keep the original central seat beneath the fuse body. Fingers enter on
    # either side of this 12 mm pedestal; removing it would leave the fuse floating.
    seat=BRepPrimAPI_MakeBox(gp_Pnt(-z-DEPTH/2-827.5,BOTTOM,x-6+700),DEPTH,35.0-BOTTOM,12.0).Shape()
    cutter=BRepAlgoAPI_Cut(cutter,seat).Shape()
    cut=BRepAlgoAPI_Cut(shape,cutter)
    cut.Build()
    assert cut.IsDone() and not cut.Shape().IsNull(),index
    assert BRepCheck_Analyzer(cut.Shape()).IsValid(),index
    return cut.Shape()
