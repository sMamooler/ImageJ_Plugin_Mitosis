import java.awt.Point;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class Mitosis implements PlugIn {

	public void run(String arg) {
		ImagePlus original = IJ.getImage();
		IJ.run(original, "Duplicate...", "title=red_ds_copy.tif duplicate");
		IJ.selectWindow("red_ds_copy.tif");
		ImagePlus imp = IJ.getImage();
		int nt = imp.getNSlices();
		ArrayList<Spot> spots[] = detect(imp, 4, 400, 16, 255);
		
		int nb_nucleus[] = new int[nt];
		for(int t=0; t<nt; t++) {
			nb_nucleus[t] = spots[t].size();
		}
		
		// link the current spot with the next one using the nearest neighbor method
		
		/*lambda to be changed*/
		double lambda = 0;
		double distance_link_limit = 60;
		
		link(imp, spots, lambda, distance_link_limit);
		double intensity_threshold = 150;
		ArrayList<Spot>[] division_spots = filter(spots, original, intensity_threshold);
		int nb_division[] = new int[nt];
		for (int t = 1; t < nt; t++) {
			nb_division[t] = division_spots[t].size();
			System.out.printf("t = %d, nucleus = %d, division = %d %n", t, nb_nucleus[t], nb_division[t]);  
		}
		
		Overlay overlay = new Overlay();
		draw(overlay, spots, division_spots);
		System.out.println("finished drawing");
		original.setOverlay(overlay);
	}

	/*function to calculate a better cost function*/
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
	
	private Spots[] detect(ImagePlus imp, int smooth_kernel_size, int particle_size, int threshold_min, int threshold_max) {
		
		// smoothing 
		IJ.run(imp, "Median...", "radius="+ smooth_kernel_size +" stack");
		// thresholding
		IJ.setAutoThreshold(imp, "Default dark");
		IJ.setRawThreshold(imp, threshold_min, threshold_max, null);
		IJ.run(imp, "Convert to Mask", "method=Default background=Dark");
		imp = imp.duplicate();
		// filling holes before watersheding
		IJ.run(imp, "Fill Holes", "stack");
		// separate blobs of nucleus
		IJ.run(imp, "Watershed", "stack");
	
		int nt = imp.getNSlices();
		Spots spots[] = new Spots[nt];
		
		

		for(int t=0; t<nt; t++) {
			System.out.println("frame"+t);
			imp.setSlice(t+1);
			IJ.run("Set Measurements...", "centroid redirect=None decimal=3");
			// set minimum threshold for particle size to filter out small noise blobs
			IJ.run(imp, "Analyze Particles...", "size=" + particle_size +" -Infinity add");

			
			RoiManager rm =  RoiManager.getInstance();
			rm.deselect();
			ResultsTable measures = rm.multiMeasure(imp);
			IJ.run("From ROI Manager", "");
			
			int nb_particles = rm.getCount();
			spots[t] = new Spots();
			
			for(int p=0; p<nb_particles; p++) {
				
				Roi roi = rm.getRoi(p);
				
				int x = (int) measures.getColumnAsDoubles(2*p)[t];
				int y = (int) measures.getColumnAsDoubles(2*p+1)[t];
				Spot spot = new Spot(x, y, t, roi);
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
	private ArrayList<Spot>[] filter(ArrayList<Spot> spots[], ImagePlus imp, double threshold) {
		int nt = spots.length;
		ImageProcessor ip = imp.getProcessor();
		ArrayList<Spot> out[] = new Spots[nt];
		for (int t = 1; t < nt; t++) {
			imp.setSlice(t);
			out[t] = new Spots();
			for (Spot spot : spots[t]) {
				double mean = 0;
				for (Point p : spot.roi) {
					mean = mean + ip.getPixelValue(p.x, p.y);
				}
				mean = mean / spot.roi.size();
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
