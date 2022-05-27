import java.awt.Point;
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

public class Tracking implements PlugIn {

	public void run(String arg) {
		ImagePlus original = IJ.getImage();
		IJ.run(original, "Duplicate...", "title=red_ds_copy.tif duplicate");
		IJ.selectWindow("red_ds_copy.tif");
		ImagePlus imp = IJ.getImage();
		int nt = imp.getNSlices();
		
		// step 1: detect nucleus
		ArrayList<Spot> spots[] = detect(original, imp, 4, 400, 16, 255);
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
		
		link(imp, spots, lambda, distance_link_limit);
		
		// step 3: detect mitosis		
		// criteria 1) a spot without a previous link means mitosis in the ideal case (no merging blobs, no cells running out of the frame)
		// criteria 2) when mitosis happens, the nucleus is very bright, so we set an intensity threshold to screen out false positive of mitosis 
		double intensity_threshold = 70;
		// function filter detects mitosis and store these spots in a list division_spots
		ArrayList<Spot>[] division_spots = filter(spots, original, intensity_threshold);
		// count the number of mitosis in each frame and store it in an array
		int nb_division[] = new int[nt];
		
		Plot plot = new Plot("Growth Rate", "Time", "growth rate");
		double[] frame_axis = new double[nt];
		double mitosis_rates[] = new double[nt];
		//plot.setLimits(0, nt+1, 0, 400);
		for (int t = 1; t < nt; t++) {
			nb_division[t] = division_spots[t].size();
			mitosis_rates[t] = (double)nb_division[t] /  nb_nucleus[t];
			frame_axis[t] = t;
			// print the number of nucleus and mitosis in each frame to check
			System.out.printf("t = %d, nucleus = %d, division = %d %n", t, nb_nucleus[t], nb_division[t]);  
		}
		plot.addPoints(frame_axis, mitosis_rates, Plot.LINE);
		plot.show();
		// step 4: draw the detected nucleus (red thin boundary) and the mitosis (green thick boundary)
		Overlay overlay = new Overlay();
		draw(overlay, spots, division_spots);
		System.out.println("finished drawing");
		original.setOverlay(overlay);
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
	private Spots[] detect(ImagePlus original, ImagePlus imp, int smooth_kernel_size, int particle_size, int threshold_min, int threshold_max) {
		
		// smoothing using the median filter with a certain kernel size 
		IJ.run(imp, "Median...", "radius="+ smooth_kernel_size +" stack");
		// thresholding by checking the histogram and manually setting the min and max threshold such that
		// the nucleus is well covered
		IJ.setAutoThreshold(imp, "Default dark");
		IJ.setRawThreshold(imp, threshold_min, threshold_max, null);
		IJ.run(imp, "Convert to Mask", "method=Default background=Dark");
		imp = imp.duplicate();
		// filling holes in nucleus for watershedding to work better
		IJ.run(imp, "Fill Holes", "stack");
		// watershedding to separate blobs of nucleus to improve the quality of tracking 
		IJ.run(imp, "Watershed", "stack");
	
		int nt = imp.getNSlices();
		Spots spots[] = new Spots[nt];
		
		// for each frame: use analyze particles to detect nucleus
		for(int t=0; t<nt; t++) {
			System.out.println("frame"+t);
			imp.setSlice(t+1);
			IJ.run("Set Measurements...", "centroid mean redirect=None decimal=3");
			// set a minimum threshold for particle size (pixel^2) to filter out small noise blobs
			// add detected particles to thr ROI manager
			IJ.run(imp, "Analyze Particles...", "size=" + particle_size +" -Infinity add");

			RoiManager rm =  RoiManager.getInstance();
			rm.deselect();
			ResultsTable measures = rm.multiMeasure(imp);
			ResultsTable measures_orig = rm.multiMeasure(original);
			System.out.println(measures);
			// overlay the original image with the drawing of ROIs
			IJ.run("From ROI Manager", "");
			
			// find the centroids of ROIs in each frame as the detected spots and store them in an array
			int nb_particles = rm.getCount();
			spots[t] = new Spots();
			
			for(int p=0; p<nb_particles; p++) {				
				Roi roi = rm.getRoi(p);
				double mean_intensity = measures_orig.getColumnAsDoubles(3*p)[t];
				int x = (int) measures.getColumnAsDoubles(3*p+1)[t];
				int y = (int) measures.getColumnAsDoubles(3*p+2)[t];
				Spot spot = new Spot(x, y, t, roi, mean_intensity);
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
				/*find the spot in the next frame with the minimum cost*/
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
	private ArrayList<Spot>[] filter(ArrayList<Spot> spots[], ImagePlus imp, double threshold) {
		int nt = spots.length;
		ImageProcessor ip = imp.getProcessor();
		// create an array list to store all the mitosis spots in each frame
		ArrayList<Spot> out[] = new Spots[nt];
		for (int t = 1; t < nt; t++) {
			imp.setSlice(t);
			out[t] = new Spots();
			// compute the mean intensity of the ROI of the this spot
			for (Spot spot : spots[t]) {
				double mean = spot.mean_intensity;
				System.out.println(mean);
//				for (Point p : spot.roi) {
//					mean = mean + ip.getPixelValue(p.x, p.y);
//				}
//				mean = mean / spot.roi.size();
				// we use 2 criteria to detect mitosis:
				// 1. if this spot does not have a previous link, this means mitosis happened
				// 2. thresholding the mean intensity to screen out false positives because 
				// when mitosis happens, the intensity of the cell is higher than others
				if ((spot.previous == null) && (mean > threshold)) {
					out[t].add(spot);
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
