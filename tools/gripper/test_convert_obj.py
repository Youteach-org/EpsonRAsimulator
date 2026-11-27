import importlib.util
import struct
import json
import unittest
from pathlib import Path


class ConversionTests(unittest.TestCase):
    def setUp(self):
        path = Path(__file__).with_name('convert_obj.py')
        spec = importlib.util.spec_from_file_location('converter', path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_duplicate_faces_removed_without_changing_vertex_coordinates(self):
        vertices, faces = self.module.parse_obj('v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\nf 3 2 1\n')
        self.assertEqual(vertices, [(0., 0., 0.), (1., 0., 0.), (0., 1., 0.)])
        self.assertEqual(len(faces), 1)

    def test_glb_contains_original_geometry_in_metres_and_normals(self):
        vertices = [(0., 0., 0.), (100., 0., 0.), (0., 100., 0.)]
        data = self.module.make_glb(vertices, [(0, 1, 2)], 'test', [0.5, 0.5, 0.5, 1.])
        magic, version, length = struct.unpack_from('<III', data)
        self.assertEqual((magic, version, length), (0x46546c67, 2, len(data)))
        count, kind = struct.unpack_from('<II', data, 12)
        self.assertEqual(kind, 0x4e4f534a)
        doc = json.loads(data[20:20+count])
        self.assertEqual(doc['accessors'][0]['max'], [0.1, 0.1, 0.])
        self.assertIn('NORMAL', doc['meshes'][0]['primitives'][0]['attributes'])
        self.assertEqual(doc['accessors'][2]['count'], 3)

    def test_zero_area_export_artifacts_do_not_generate_invalid_normals(self):
        data = self.module.make_glb([(0.,0.,0.), (1.,0.,0.), (0.,1.,0.)],
                                    [(0,0,1), (0,1,2)], 'test', [1.,1.,1.,1.])
        count = struct.unpack_from('<I', data, 12)[0]
        self.assertEqual(json.loads(data[20:20+count])['accessors'][0]['count'], 3)


if __name__ == '__main__':
    unittest.main()
