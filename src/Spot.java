import java.awt.Color;

import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;

public class Spot {
	public int x;
	public int y;
	public Roi roi;
	public double red_mean_intensity;
	public double green_mean_intensity;

	public int t;
	private Spot next = null;
	public Spot previous = null;
	public Color color;
	
	// define the attributes of a spot
	public Spot(int x, int y, int t, Roi roi, double red_mean_intensity, double green_mean_intensity) {
		this.x = x;
		this.y = y;
		this.roi = roi;
		this.red_mean_intensity = red_mean_intensity;
		this.green_mean_intensity = green_mean_intensity;
		this.t = t;
		color = Color.getHSBColor((float)Math.random(), 1f, 1f);
		this.color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 120);
	}
	
	// define the method to calculate the distance between two spots
	public double distance(Spot spot) {
		double dx = x - spot.x;
		double dy = y - spot.y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	// define the method to draw overlays
	public void draw(Overlay overlay, boolean mitosis) {
		// if this spot is mitosis, draw with a green thick stroke
		this.roi.setPosition(this.t+1); // display roi in one frame
		if (mitosis == true) {
			this.roi.setStrokeColor(new Color(255, 0, 0, 200));
			this.roi.setStrokeWidth(5);
		}
		// if this spot is not mitosis, draw with a red think stroke
		else {
			this.roi.setStrokeColor(new Color(255, 255, 255, 120));
			this.roi.setStrokeWidth(2);
		}
		
		overlay.add(this.roi);
	
		if (next != null) {
			Line line = new Line(this.x, this.y, next.x, next.y);
			line.setStrokeColor(this.color);
			line.setStrokeWidth(2);
			overlay.add(line);
		}
	}
	
	// define the method to extend the link of two spots to a double link: current <-> next
	public void link(Spot next) {
		if (next != null) {
			this.next = next;
			next.previous = this;
			next.set_color(this.color);
		}
	}
	
	// define two functions to get color and set color
	public Color get_color() {
		return this.color;
	}
	
	public void set_color(Color color) {
		this.color = color;
	}
	
	public String toString() {
		return "(" + x + ", " + y + ", " + t + ")";
	}
}

