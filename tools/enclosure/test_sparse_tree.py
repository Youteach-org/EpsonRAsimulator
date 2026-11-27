import unittest
import numpy as np
from generate_collision import morton,encode

class SparseTreeTest(unittest.TestCase):
 def decode(self,data,width):
  points=[];index=0
  def walk(origin,n):
   nonlocal index
   kind=data[index];index+=1
   if kind==0:return
   if kind==1:
    points.extend(tuple(v) for v in np.indices((n,n,n)).reshape(3,-1).T+origin);return
   self.assertEqual(2,kind);self.assertGreater(n,1)
   half=n//2
   for octant in range(8):walk(origin+np.array([octant&1,(octant>>1)&1,(octant>>2)&1])*half,half)
  walk(np.array([0,0,0]),width);self.assertEqual(len(data),index)
  return set(points)
 def test_sparse_roundtrip_keeps_gaps_and_octant_order(self):
  points=np.array([[0,0,0],[7,7,7],[2,3,4],[6,1,5],[0,7,0]])
  data=encode(np.unique(morton(points)),8)
  self.assertEqual(set(map(tuple,points)),self.decode(data,8))
 def test_full_block_compacts_without_filling_other_blocks(self):
  points=np.indices((4,4,4)).reshape(3,-1).T+np.array([4,0,4])
  data=encode(np.unique(morton(points)),8)
  self.assertEqual(9,len(data))
  self.assertEqual(set(map(tuple,points)),self.decode(data,8))
 def test_empty_volume(self):
  self.assertEqual(bytes([0]),encode(np.array([],dtype=np.uint64),8))

if __name__=='__main__':unittest.main()
