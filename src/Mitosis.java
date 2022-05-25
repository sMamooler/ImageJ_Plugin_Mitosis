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
		ArrayList<Spot> spots[] = detect(imp);
		
		int nb_nucleus[] = new int[nt];
		for(int t=0; t<nt; t++) {
			nb_nucleus[t] = spots[t].size();
		}
		
		// calculate dmax and fmax for each frame and store them in a list all_dmax and all_fmax
		Double all_dmax[] = new Double[nt];
		Double all_fmax[] = new Double[nt];
		// link the current spot with the next one using the nearest neighbor method
		
		/*lambda to be changed*/
		double lambda = 0.05; 
		ImageProcessor ip = imp.getProcessor();

		for (int t = 0; t < nt - 1; t++) {
			double dmax = 0;
			double fmax = 0;
			for (Spot current : spots[t]) {
				double c_init = Double.MAX_VALUE;
				double fc = ip.getPixelValue(current.x, current.y);
				Spot min_spot = null;
				imp.setSlice(t + 1);
				for (Spot next : spots[t+1]) {
					dmax = Math.max(dmax, current.distance(next));
					double fn = ip.getPixelValue(next.x, next.y);
					fmax = Math.max(fmax, Math.abs(fc - fn));
				}
				imp.setSlice(t);		
				/*find the spot in the next frame with the minimum cost*/
				for (Spot next : spots[t + 1]) {
					double c = cost_function(imp, t, current, next, dmax, fmax, lambda);
					if (c < c_init) {
						min_spot = next;
						c_init = c;
					}
				current.link(min_spot);
			    }
		    }
		}
		// post processing to find the division spots and link them to have the same color
		/*for (int t = 1; t < nt; t++) {
			int nb_spots = spots[t].size();
			for (int m = 0; m < nb_spots; m++) {
				Spot spot = spots[t].get(m);
				//check if a spot has a precedent link, if not, it is a division spot
				if (spot.previous == null) {
					double dmax = all_dmax[t];
					//find the closest precedent spot for the division spot
					Spot closest_spot = null;
					for (int n = 0; n < nb_spots; n++) {
						if (n != m) {
							Spot other = spots[t].get(n);
							double dist = spot.distance(other);
							if ((dist < dmax) && (other.previous != null)) {
								closest_spot = other;
								dmax = dist;
							}
						}
					}
					
					if (closest_spot != null) {
						spot.previous = closest_spot.previous;
						spot.set_color(closest_spot.get_color());
					}
				}
			}
		}
		*/
		Overlay overlay = new Overlay();
		draw(overlay, spots);
		System.out.println("finished drawing");
		original.setOverlay(overlay);
	}

	/*function to calculate a better cost function*/
	private double cost_function(ImagePlus imp, int t, Spot current, Spot next, double dmax, double fmax, double lambda) {
		ImageProcessor ip = imp.getProcessor();
		/*distance term*/
		double c1 = current.distance(next)/dmax;
		/*intensity term*/
		imp.setPosition(1, 1, t);
		double fc = ip.getPixelValue(current.x, current.y);
		imp.setPosition(1, 1, t + 1);
		double fn = ip.getPixelValue(next.x, next.y);
		double c2 = Math.abs(fc - fn)/fmax;
		/*better cost function weighted by lambda*/
		double c = (1 - lambda) * c1 + lambda * c2;
		
		return c;
	}
	
	private void draw(Overlay overlay, ArrayList<Spot> spots[]) {
		int nt = spots.length;
		for (int t = 0; t < nt; t++)
			for (Spot spot : spots[t])
				spot.draw(overlay);
	}

	
	private Spots[] detect(ImagePlus imp) {
		
		// pre-process 
		IJ.run(imp, "Median...", "radius=2 stack");
		IJ.run(imp, "Auto Local Threshold", "method=Bernsen radius=15 parameter_1=0 parameter_2=0 white stack");
		IJ.run(imp,  "Gray Morphology", "radius=1 type=circle operator=open");
		
		IJ.setBackgroundColor(0, 0, 0);
		
		IJ.run("Convert to Mask", "method=Default background=Default calculate");
		
	
		int nt = imp_copy.getNSlices();
		Spots spots[] = new Spots[nt];
		
		

		for(int t=0; t<nt; t++) {
			System.out.println("frame"+t);
			imp_copy.setSlice(t+1);
			IJ.run("Set Measurements...", "centroid redirect=None decimal=3");
			IJ.run(imp_copy, "Analyze Particles...", "size=36-Infinity add");
			
			RoiManager rm =  RoiManager.getInstance();
			rm.deselect();
			ResultsTable measures = rm.multiMeasure(imp_copy);
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
			IJ.run(imp_copy, "Select All", "");
			rm.runCommand(imp_copy,"Delete");
			
		}
		
		return spots;
	}

}
