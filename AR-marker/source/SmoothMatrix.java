
// SmoothMatrix.java
// Andrew Davison, ad@fivedots.coe.psu.ac.th, April 2010

/* To reduce shaking of model due to slight variations in
   calculaed rotations and positions in the transformation
   matrix.
*/

import java.util.*;

import javax.media.j3d.*;
import javax.vecmath.*;

import jp.nyatla.nyartoolkit.core.transmat.NyARTransMatResult;



public class SmoothMatrix
{
  private final static int MAX_SIZE = 10;

  private ArrayList<Matrix4d> matsStore;
  private int numMats = 0;


  public SmoothMatrix() 
  {
    matsStore = new ArrayList<Matrix4d>();

  } // end of SmoothMatrix()



  public boolean add(NyARTransMatResult transMat)
  {

    Matrix4d mat = new Matrix4d(-transMat.m00, -transMat.m01, -transMat.m02, -transMat.m03,
                                -transMat.m10, -transMat.m11, -transMat.m12, -transMat.m13, 
                                 transMat.m20,  transMat.m21,  transMat.m22,  transMat.m23,
                                 0,             0,             0,             1             );
    Transform3D t3d = new Transform3D(mat);

    int flags = t3d.getType();
    if ((flags & Transform3D.AFFINE) == 0) {
      System.out.println("Not adding a non-affine matrix");
      return false;
    }
    else {
      if (numMats == MAX_SIZE) {
        matsStore.remove(0);   // remove oldest
        numMats--;
      }
      matsStore.add(mat);  // add at end of list
      numMats++;
      return true;
    }
  }  // end of add()


  public Matrix4d get()
  // average matricies in store
  {
    if (numMats == 0)
      return null;

    Matrix4d avMat = new Matrix4d();
    for(Matrix4d mat : matsStore)
      avMat.add(mat);
    avMat.mul( 1.0/numMats );

    return avMat;
  }  // end of get()

}  // end of SmoothMatrix class

