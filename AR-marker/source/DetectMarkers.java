// DetectMarkers.java
// Andrew Davison, ad@fivedots.coe.psu.ac.th, April 2010

/* Collection of MarkerModel objects and a detector that finds
 markers in the camera's captured image. The new marker position
 is used to move its corresponding model
 */

import java.util.ArrayList;

import javax.vecmath.Point3d;

import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.core.NyARCode;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.core.transmat.NyARTransMatResult;
import jp.nyatla.nyartoolkit.detector.NyARDetectMarker;
import jp.nyatla.nyartoolkit.java3d.utils.J3dNyARRaster_RGB;

public class DetectMarkers {
	private final static double MIN_CONF = 0.3;
	// smallest confidence accepted for finding a marker

	private final static int CONF_SIZE = 1000;
	// for converting confidence level double <--> integer

	private final static int MAX_NO_DETECTIONS = 50;
	// number of times a marker goes undetected before being made invisible

	private ArrayList<MarkerModel> markerModels;
	private int numMarkers;

	private MultiNyAR top; // for reporting status
	private NyARDetectMarker detector;

	private NyARTransMatResult transMat = new NyARTransMatResult();

	// transformation matrix for a marker, which is used to move its model

	public DetectMarkers(MultiNyAR top) {
		this.top = top;
		markerModels = new ArrayList<MarkerModel>();
		numMarkers = 0;
	} // end of DetectMarkers()

	public void addMarker(MarkerModel mm) {
		markerModels.add(numMarkers, mm); // add to end of list
		numMarkers++;
	}

	public void createDetector(NyARParam params, J3dNyARRaster_RGB rasterRGB)
	// create a single detector for all the markers
	{
		NyARCode[] markersInfo = new NyARCode[numMarkers];
		double[] widths = new double[numMarkers];
		int i = 0;
		for (MarkerModel mm : markerModels) {
			markersInfo[i] = mm.getMarkerInfo();
			widths[i] = mm.getMarkerWidth();
			// System.out.println("Object " + i + ": marker info = " +
			// markersInfo[i]);
			i++;
		}

		try {
			detector = new NyARDetectMarker(params, markersInfo, widths,
					numMarkers, rasterRGB.getBufferReader().getBufferType());
			detector.setContinueMode(false); // no history stored; use
												// SmoothMatrix instead
		} catch (NyARException e) {
			System.out.println("Could not create markers detector");
			System.exit(1);
		}
	} // end of createDetector()

	public void updateModels(J3dNyARRaster_RGB rasterRGB)
	// move marker models using the detected marker positions inside the raster
	{
		int numDetections = getNumDetections(detector, rasterRGB);
		// System.out.println("numDetections: " + numDetections);

		try {
			StringBuffer statusInfo = new StringBuffer(); // for holding status
															// information

			// find the best detected match for each marker
			for (int mkIdx = 0; mkIdx < numMarkers; mkIdx++) {
				MarkerModel mm = markerModels.get(mkIdx);

				int[] detectInfo = findBestDetectedIdx(detector, numDetections,
						mkIdx); // look for mkIdx
				int bestDetectedIdx = detectInfo[0];
				double confidence = ((double) detectInfo[1]) / CONF_SIZE; // convert
																			// back
																			// to
																			// double

				if (bestDetectedIdx == -1) // marker not found so incr
											// numTimesLost
					mm.incrNumTimesLost();
				else { // marker found
					if (confidence >= MIN_CONF) { // detected a marker for mkIdx
													// with high confidence
						mm.resetNumTimesLost();
						// apply the transformation from the detected marker to
						// the marker's model
						// System.out.println("  For markers list index " +
						// mkIdx +
						// ": best detected index = " + bestDetectedIdx);
						detector.getTransmationMatrix(bestDetectedIdx, transMat);
						if (transMat.has_value)
							mm.moveModel(transMat);
						else
							System.out
									.println("Problem with transformation matrix");
					}
					// else // found a marker, but with low confidence
					// System.out.println("  ***** " + mkIdx + " conf: " +
					// confidence);
				}

				if (mm.getNumTimesLost() > MAX_NO_DETECTIONS) // marker not
																// detected too
																// many times
					mm.hideModel(); // make its model invisible

				statusInfo.append(mkIdx + ". " + mm.getNameInfo() + " ("
						+ confidence + ")\n");
				addToStatusInfo(mm, statusInfo);
			}
			top.setStatus(statusInfo.toString()); // display marker models
													// status in the GUI
		} catch (NyARException e) {
			System.out.println(e);
		}
	} // end of updateModels()

	private int getNumDetections(NyARDetectMarker detector,
			J3dNyARRaster_RGB rasterRGB) {
		int numDetections = 0;
		try {
			synchronized (rasterRGB) {
				if (rasterRGB.hasData())
					numDetections = detector.detectMarkerLite(rasterRGB, 100);
			}
		} catch (NyARException e) {
			System.out.println(e);
		}

		return numDetections;
	} // end of getNumDetections()

	private int[] findBestDetectedIdx(NyARDetectMarker detector,
			int numDetections, int markerIdx)
	/*
	 * return best detected marker index for marker markerIdx from all detected
	 * markers, along with its confidence value as an integer
	 */
	{
		int iBest = -1;
		double confBest = -1;

		// System.out.println("  Look at detections for marker " + markerIdx);
		for (int i = 0; i < numDetections; i++) { // check all detected markers
			int codesIdx = detector.getARCodeIndex(i);
			double conf = detector.getConfidence(i);
			// System.out.println("    detections index["+i+"] = code index " +
			// codesIdx + " -- conf: " + conf);

			if ((codesIdx == markerIdx) && (conf > confBest)) {
				iBest = i; // detected marker index with highest confidence
				confBest = conf;
			}
		}
		// System.out.println("    mark index "+ markerIdx+" iBest = " + iBest +
		// " conf: " + confBest);

		int[] detectInfo = { iBest, (int) (confBest * CONF_SIZE) };
		return detectInfo;
	} // end of findBestDetectedIdx()

	private void addToStatusInfo(MarkerModel mm, StringBuffer statusInfo)
	// add details about MarkerModel object to status info string
	{
		if (!mm.isVisible())
			statusInfo.append(" not visible\n");
		else { // model is visible, so report position and orientation
			Point3d pos = mm.getPos();
			if (pos != null)
				statusInfo.append("    at (" + pos.x + ", " + pos.y + ", "
						+ pos.z + ")\n");
			else
				statusInfo.append("    at an unknown position\n");

			Point3d rots = mm.getRots();
			if (rots != null)
				statusInfo.append("    rots (" + rots.x + ", " + rots.y + ", "
						+ rots.z + ")\n");
			else
				statusInfo.append("    with unknown rotations\n");
		}
	} // end of addToStatusInfo()

} // end of class DetectMarkers
