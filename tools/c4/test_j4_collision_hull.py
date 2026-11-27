import unittest
import numpy as np
from generate_j4_collision_hull import mark_surface, fill_solid

class CadOccupancyTests(unittest.TestCase):
    def test_triangle_does_not_fill_empty_corner_of_its_bounding_rectangle(self):
        triangles=np.array([[[0.,0.,0.],[4.,0.,0.],[0.,4.,0.]]])
        surface=mark_surface(triangles,np.array([-2.,-2.,-2.]),8)
        self.assertTrue(surface[1,1,1])
        self.assertTrue(surface[2,2,1])  # hypotenuse contact is retained
        self.assertFalse(surface[3,3,1])  # rectangle corner has no triangle

    def test_exterior_connected_hole_stays_empty(self):
        wall=np.zeros((8,8,8),dtype=bool)
        for axis in range(3):
            for edge in (1,5):
                sl=[slice(1,6)]*3;sl[axis]=edge;wall[tuple(sl)]=True
        self.assertTrue(fill_solid(wall)[3,3,3])
        wall[1,3,3]=False
        wall[5,3,3]=False
        self.assertFalse(fill_solid(wall)[3,3,3])
        self.assertTrue(fill_solid(wall)[3,1,3])

if __name__=='__main__': unittest.main()
