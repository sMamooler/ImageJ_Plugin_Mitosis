import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class Mitosis implements PlugIn {

	public void run(String arg) {
		IJ.selectWindow("red.tif");
		ImagePlus red = IJ.getImage();
		IJ.run(red, "Duplicate...", "title=red_copy.tif duplicate");
		IJ.selectWindow("red_copy.tif");
		ImagePlus imp = IJ.getImage();
		int nt = imp.getNSlices();
		
		IJ.selectWindow("green.tif");
		ImagePlus green = IJ.getImage();
	
		
		// step 1: detect nucleus
		ArrayList<Spot> spots[] = detect(red, green, imp, 8, 200, 16, 255);
		// count the number of nucleus in each frame and store it in an array
		int nb_nucleus[] = new int[nt];
		for(int t=0; t<nt; t++) {
			nb_nucleus[t] = spots[t].size();
		}
		
		// step 2: tracking nucleus		
		// lambda = 0 : only consider distance, lambda = 1 : only consider intensity
		double lambda = 0;
		// set a maximum linking distance to avoid false linking (inspired by trackmate)
		double distance_link_limit = 60;
		// we extended the method link for the class Spot to a double link for the following detection of mitosis
		link(imp, spots, lambda, distance_link_limit);
		
		// step 3: detect mitosis
		// methodology: 
		// 1) when mitosis happens, one parent spot splits into 2 child spots in the next frame,
		// the link function links only one of the child spot to the parent spot,
		// so when a spot has no precedent link, it is classified as mitosis.
		// We hence use if (Spot.prev == null) as the main criterion for mitosis.
		// 2) on the real data set, due to imperfect detection, e.g. blobs of cells are detected
		// as one particle or multiple particles in neighboring frames, this leads to many false positives.
		// We observed that when mitosis happens the cell becomes much brighter and the area of the cell becomes
		// much smaller compared to the previous frame. Hence, we add 2 thresholds of these 2 criteria to screen out false positives.
		
		double intensity_threshold = 70;
		double area_threshold = 300;
		// function filter detects mitosis and store these spots in a list division_spots
		ArrayList<Spot>[] division_spots = filter(spots, red, intensity_threshold, area_threshold);
		// count the number of mitosis in each frame and store it in an array
		int nb_division[] = new int[nt];
		
		// step 4: plot the rate of growth
		Plot plot = new Plot("Rate of Growth of Cells", "Frame", "growth rate");
		double[] frame_axis = new double[nt];
		double mitosis_rates[] = new double[nt];
		for (int t = 1; t < nt; t++) {
			nb_division[t] = division_spots[t].size();
			mitosis_rates[t] = (double)nb_division[t] /  nb_nucleus[t];
			frame_axis[t] = t;
			// print the number of nucleus and mitosis in each frame to check
			System.out.printf("t = %d, nucleus = %d, division = %d %n", t, nb_nucleus[t], nb_division[t]);  
		}
		plot.addPoints(frame_axis, mitosis_rates, Plot.LINE);
		plot.show();
		
		// step 5: draw the detected nucleus
		Overlay overlay = new Overlay();
		draw(overlay, spots, division_spots);
		System.out.println("finished drawing");
		IJ.run(red, "Merge Channels...", "c1=red.tif c2=green.tif keep");
		IJ.selectWindow("RGB");
		ImagePlus rgb = IJ.getImage();
		rgb.setOverlay(overlay);
	}
	
	//-------------functions used in main---------------
	
	// function to calculate the cost function for tracking
	private double cost_function(ImagePlus imp, Spot current, Spot next, double dmax, double fmax, double lambda) {
		ImageProcessor ip = imp.getProcessor();
		/*distance term*/
		double c1 = current.distance(next)/dmax;
		/*intensity term*/
		imp.setSlice(current.t);
		double fc = ip.getPixelValue(current.x, current.y);
		imp.setSlice(next.t);
		double fn = ip.getPixelValue(next.x, next.y);
		double c2 = Math.abs(fc - fn)/fmax;
		/*better cost function weighted by lambda*/
		double c = (1 - lambda) * c1 + lambda * c2;
		return c;
	}
	
	// function to detect the nucleus
	private Spots[] detect(ImagePlus original_red, ImagePlus original_green, ImagePlus imp, int smooth_kernel_size, int particle_size, int threshold_min, int threshold_max) {
		
		// smoothing using the median filter with a certain kernel size 
		IJ.run(imp, "Median...", "radius="+ smooth_kernel_size +" stack");
		// thresholding
		// we use a Plugin Auto Local Threshold:
		// radius: sets the radius of the local domain over which the threshold will be computed
		// parameter 1: is the contrast threshold. The default value is 15. Any number different than 0 will change the default value.
        // parameter 2: not used, ignored.
		// method: Bernsen works the best for our data set (https://imagej.net/plugins/auto-local-threshold)
		IJ.run(imp, "Auto Local Threshold", "method=Bernsen radius=30 parameter_1=0 parameter_2=0 stack");
		IJ.run(imp, "Convert to Mask", "method=Default background=Dark");
		imp = imp.duplicate();
		// watershedding to separate blobs of nucleus to improve the quality of detection
		IJ.run(imp, "Watershed", "stack");
	
		int nt = imp.getNSlices();
		Spots spots[] = new Spots[nt];
		
		// for each frame we use analyze particles to detect nucleus
		for(int t=0; t<nt; t++) {
			System.out.println("frame"+t);
			imp.setSlice(t+1);
			IJ.run("Set Measurements...", "centroid mean area redirect=None decimal=3");
			// set a minimum threshold for particle size (pixel^2) to filter out small noise blobs
			// add detected particles to the ROI manager
			IJ.run(imp, "Analyze Particles...", "size=" + particle_size +" -Infinity add");

			RoiManager rm =  RoiManager.getInstance();
			rm.deselect();
			ResultsTable measures = rm.multiMeasure(imp);
			ResultsTable measures_orig_red = rm.multiMeasure(original_red);
			ResultsTable measures_orig_green = rm.multiMeasure(original_green);
			// overlay the original image with the drawing of ROIs
			IJ.run("From ROI Manager", "");
			
			// extract properties from the ROIs to define the corresponding array spots
			int nb_particles = rm.getCount();
			spots[t] = new Spots();
			
			for(int p=0; p<nb_particles; p++) {
				// get the ROI from the ROI manager
				Roi roi = rm.getRoi(p);
				// calculate the area of the ROI
				double area = measures.getColumnAsDoubles(4*p)[t];
				// calculate the mean intensity of the ROI in both channels
				double red_mean_intensity = measures_orig_red.getColumnAsDoubles(4*p+1)[t];
				double green_mean_intensity = measures_orig_green.getColumnAsDoubles(4*p+1)[t];
				// find the centroid of the ROI as the location of the spot
				int x = (int) measures.getColumnAsDoubles(4*p+2)[t];
				int y = (int) measures.getColumnAsDoubles(4*p+3)[t];
				Spot spot = new Spot(x, y, t, roi, red_mean_intensity, green_mean_intensity, area);
				spots[t].add(spot);	
			}
		
			// clean ROI manager for the next iteration
			IJ.run(imp, "Select All", "");
			rm.runCommand(imp,"Delete");
			
		}
		
		RoiManager rm = RoiManager.getRoiManager();
		rm.close();
		
		return spots;
	}
	
	// function to track nucleus: link the current spot with the next one using the nearest neighbor method
	private void link (ImagePlus imp, ArrayList<Spot> spots[], double lambda, double distance_link_limit) {
		ImageProcessor ip = imp.getProcessor();
		int nt = imp.getNSlices();
		for (int t = 0; t < nt - 1; t++) {
			double dmax = 0;
			double fmax = 0;
			for (Spot current : spots[t]) {
				double c_init = Double.MAX_VALUE;
				imp.setSlice(t);
				double fc = ip.getPixelValue(current.x, current.y);
				Spot min_spot = null;
				imp.setSlice(t + 1);
				for (Spot next : spots[t+1]) {
					dmax = Math.max(dmax, current.distance(next));
					double fn = ip.getPixelValue(next.x, next.y);
					fmax = Math.max(fmax, Math.abs(fc - fn));
				}		
				//find the spot in the next frame with the minimum cost
				for (Spot next : spots[t + 1]) {
					// check the maximum linking distance criterion before computing the cost function
					// to confine the search for the min_spot in the neighborhood of the current spot
					
					if (current.distance(next) < distance_link_limit) {
						double c = cost_function(imp, current, next, dmax, fmax, lambda);
						if (c < c_init) {
							min_spot = next;
							c_init = c;
						}
					}
					current.link(min_spot);
			    }
		    }
		}
	}
		
	// function to detect mitosis
	private ArrayList<Spot>[] filter(ArrayList<Spot> spots[], ImagePlus imp, double intensity_threshold, double area_threshold) {
		int nt = spots.length;
		// create an array list to store all the mitosis spots in each frame
		ArrayList<Spot> out[] = new Spots[nt];
		for (int t = 1; t < nt; t++) {
			imp.setSlice(t);
			out[t] = new Spots();
			// compute the mean intensity of the red channel of the ROI of the this spot
			for (Spot spot : spots[t]) {
				double red_mean = spot.red_mean_intensity;
				// check if the previous link is empty 
				if (spot.previous==null) {
					// prune false positives by checking the absolute values of the mean intensity and the area of the ROI
					if (red_mean > intensity_threshold && spot.area < area_threshold) {
						out[t].add(spot);
						System.out.println("no previous link");
					}	
				} else {
					// further prune false positives with a previous link by checking the local mean and local area
					if (red_mean > 1.5*spot.previous.red_mean_intensity && spot.area < spot.previous.area/2) {
						out[t].add(spot);
						System.out.println("getting smaller and brighter");
					}
				}
					
				
			}
		}
		return out;
	}
	
	// function to draw the overlay
	private void draw(Overlay overlay, ArrayList<Spot> spots[], ArrayList<Spot> division_spots[]) {
			int nt = spots.length;
			for (int t = 0; t < nt; t++) {
				for (Spot spot : spots[t]) {
					boolean mitosis = ((division_spots[t] != null) && (division_spots[t].contains(spot)));
					spot.draw(overlay, mitosis);
				}
			}
		}

}
