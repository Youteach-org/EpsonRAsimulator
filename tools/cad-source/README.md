# Original school CAD inputs

school-enclosure-step.zip contains the original MK1.STEP. gripper-original.xvl and gripper-obj/component_*.obj preserve the supplied gripper and exported surfaces. They are source inputs, not the modified simulator geometry.

For enclosure regeneration, extract MK1.STEP to a working directory, run tools/enclosure/convert_enclosure.py to create its meshes directory, then prepare_clearance.py. Run generate_collision.py on meshes-clearance/prepared-clearance, generate_outlet_surfaces.py and generate_outlet_contact.py with the working directory, verify_outlet_contact.py, then install_assets.py with prepared-clearance. The scripts require Python, numpy, trimesh and OCP. Existing runtime GLBs and generated Kotlin are committed, so normal Android builds do not require CAD regeneration.

The source gripper conversion is tools/gripper/convert_obj.py. Its 1 mm/unit scale is provisional. Preserve the original inputs when changing simulator clearance.
