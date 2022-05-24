import java.util.ArrayList;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class Tracking implements PlugIn {

	public void run(String arg) {
		double sigma = 2;
		double threshold = 10;
		/*lambda to be changed*/
		double lambda = 0; 
		ImagePlus imp = IJ.getImage();
		int nt = imp.getNFrames();
		ImagePlus dog = dog(imp, sigma);
		ArrayList<Spot> localmax[] = localMax(dog);
		ArrayList<Spot> spots[] = filter(dog, localmax, threshold);
		
		// calculate dmax and fmax for each frame and store them in a list all_dmax and all_fmax
		Double all_dmax[] = new Double[nt];
		Double all_fmax[] = new Double[nt];
		ImageProcessor ip = imp.getProcessor();

		for (int t = 0; t < nt - 1; t++) {
			double dmax = 0;
			double fmax = 0;
			for (Spot current : spots[t]) {
				for (Spot next : spots[t+1]) {
					/*set dmax*/
					if (current.distance(next) > dmax)
						dmax = current.distance(next);
					/*set fmax*/
					imp.setPosition(1, 1, t);
					double fc = ip.getPixelValue(current.x, current.y);
					imp.setPosition(1, 1, t + 1);
					double fn = ip.getPixelValue(next.x, next.y);
					if (Math.abs(fc - fn) > fmax)
						fmax = Math.abs(fc - fn);
				}
			}
			all_dmax[t] = dmax;
			all_fmax[t] = fmax;
		}

		// link the current spot with the next one using the nearest neighbor method
		for (int t = 0; t < nt - 1; t++) {
			for (Spot current : spots[t]) {
				double dmax = all_dmax[t];
				double fmax = all_fmax[t];
				double c_init = Double.MAX_VALUE;
				Spot min_spot = null;
						
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

	private ArrayList<Spot>[] filter(ImagePlus dog, ArrayList<Spot> spots[], double threshold) {
		int nt = spots.length;
		ArrayList<Spot> out[] = new Spots[nt];
		for (int t = 0; t < nt; t++) {
			out[t] = new Spots();
			for (Spot spot : spots[t]) {
				dog.setPosition(1, 1, t + 1);
				double value = dog.getProcessor().getPixelValue(spot.x, spot.y);
				if (value > threshold)
					out[t].add(spot);
			}
		}
		return out;
	}

	private ImagePlus dog(ImagePlus imp, double sigma) {
		ImagePlus g1 = imp.duplicate();
		ImagePlus g2 = imp.duplicate();
		IJ.run(g1, "Gaussian Blur...", "sigma=" + sigma + " stack");
		//IJ.run(g2, "Gaussian Blur...", "sigma=" + (Math.sqrt(2) * sigma) + " stack");
		IJ.run(g2, "Gaussian Blur...", "sigma=" + (50 * sigma) + " stack");
		ImagePlus dog = ImageCalculator.run(g1, g2, "Subtract create stack");
		dog.show();
		return dog;
	}


	private Spots[] localMax(ImagePlus imp) {
		int nt = imp.getNFrames();
		int nx = imp.getWidth();
		int ny = imp.getHeight();
		Spots spots[] = new Spots[nt];
		for (int t = 0; t < nt; t++) {
			imp.setPosition(1, 1, t + 1);
			ImageProcessor ip = imp.getProcessor();
			spots[t] = new Spots();
			for (int x = 1; x < nx - 1; x++) {
				for (int y = 1; y < ny - 1; y++) {
					double v = ip.getPixelValue(x, y);
					double max = -1;
					for (int k = -20; k <= 20; k++)
						for (int l = -20; l <= 20; l++)
							max = Math.max(max, ip.getPixelValue(x + k, y + l));
					if (v == max)
						spots[t].add(new Spot(x, y, t));
				}
			}
		}
		return spots;
	}

}
