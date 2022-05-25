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
		
		/*lambda to be changed*/
		double lambda = 0; 
		ImagePlus imp = IJ.getImage();
		int nt = imp.getNSlices();
		ArrayList<Spot> spots[] = detect(imp);
		
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
		imp.setOverlay(overlay);
		
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
		IJ.run(imp, "Duplicate...", "title=nucleus-copy.tif duplicate");
		IJ.run(imp, "Auto Local Threshold", "method=Bernsen radius=15 parameter_1=0 parameter_2=0 white stack");
		IJ.run(imp,  "Gray Morphology", "radius=1 type=circle operator=open");
		
		// TODO: to be checked, not sure. The Macro is setOption("BlackBackground", false);
		IJ.setBackgroundColor(0, 0, 0);
		
		IJ.run("Convert to Mask", "method=Default background=Default calculate");
		
	
		int nt = imp.getNSlices();
		Spots spots[] = new Spots[nt];
		

		for(int t=0; t<nt; t++) {
			System.out.println("frame"+t);
			imp.setSlice(t+1);
			IJ.run("Set Measurements...", "centroid redirect=None decimal=3");
			IJ.run(imp, "Analyze Particles...", "size=36-Infinity add");
			
			RoiManager rm =  RoiManager.getInstance();
			rm.deselect();
			ResultsTable measures = rm.multiMeasure(imp);
			IJ.run("From ROI Manager", "");
			
			int nb_particles = rm.getCount();
			spots[t] = new Spots();
			
			for(int p=0; p<nb_particles; p++) {
				
				// get the periphery by xoring the roi with a one 5 times bigger
				// TODO: change the color to green (outer) and red (inner)
				Roi roi = rm.getRoi(p);
				
				int x = (int) measures.getColumnAsDoubles(2*p)[t];
				int y = (int) measures.getColumnAsDoubles(2*p+1)[t];
				Spot spot = new Spot(x, y, t, roi);
				spots[t].add(spot);
				//Roi big_roi = RoiEnlarger.enlarge(roi, 5);
				//rm.addRoi(big_roi);
				//rm.setSelectedIndexes(new int[] {t,nb_particles});
			
				
			}
		
			// clean ROI manager for the next iteration
			IJ.run(imp, "Select All", "");
			rm.runCommand(imp,"Delete");
			
		}
		
		return spots;
	}

//	private ArrayList<Spot>[] filter(ImagePlus dog, ArrayList<Spot> spots[], double threshold) {
//		int nt = spots.length;
//		ArrayList<Spot> out[] = new Spots[nt];
//		for (int t = 0; t < nt; t++) {
//			out[t] = new Spots();
//			for (Spot spot : spots[t]) {
//				dog.setPosition(1, 1, t + 1);
//				double value = dog.getProcessor().getPixelValue(spot.x, spot.y);
//				if (value > threshold)
//					out[t].add(spot);
//			}
//		}
//		return out;
//	}
//
//	private ImagePlus dog(ImagePlus imp, double sigma) {
//		ImagePlus g1 = imp.duplicate();
//		ImagePlus g2 = imp.duplicate();
//		IJ.run(g1, "Gaussian Blur...", "sigma=" + sigma + " stack");
//		//IJ.run(g2, "Gaussian Blur...", "sigma=" + (Math.sqrt(2) * sigma) + " stack");
//		IJ.run(g2, "Gaussian Blur...", "sigma=" + (50 * sigma) + " stack");
//		ImagePlus dog = ImageCalculator.run(g1, g2, "Subtract create stack");
//		dog.show();
//		return dog;
//	}
	
//	private Spots[] localMax(ImagePlus imp) {
//		int nt = imp.getNFrames();
//		int nx = imp.getWidth();
//		int ny = imp.getHeight();
//		Spots spots[] = new Spots[nt];
//		for (int t = 0; t < nt; t++) {
//			imp.setPosition(1, 1, t + 1);
//			ImageProcessor ip = imp.getProcessor();
//			spots[t] = new Spots();
//			for (int x = 1; x < nx - 1; x++) {
//				for (int y = 1; y < ny - 1; y++) {
//					double v = ip.getPixelValue(x, y);
//					double max = -1;
//					for (int k = -20; k <= 20; k++)
//						for (int l = -20; l <= 20; l++)
//							max = Math.max(max, ip.getPixelValue(x + k, y + l));
//					if (v == max)
//						spots[t].add(new Spot(x, y, t));
//				}
//			}
//		}
//		return spots;
//	}

}
