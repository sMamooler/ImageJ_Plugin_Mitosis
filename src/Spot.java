import java.awt.Color;

import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;

public class Spot {
	public int x;
	public int y;
	public Roi roi;
	public int t;
	private Spot next = null;
	public Spot previous = null;
	public Color color;
	/* a spot is associated with a random color */
	public Spot(int x, int y, int t, Roi roi) {
		this.x = x;
		this.y = y;
		this.roi = roi;
		this.t = t;
		color = Color.getHSBColor((float)Math.random(), 1f, 1f);
		this.color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 120);
	}

	public double distance(Spot spot) {
		double dx = x - spot.x;
		double dy = y - spot.y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	public void draw(Overlay overlay, boolean mitosis) {

		this.roi.setPosition(this.t+1); // display roi in one frame
		if (mitosis == true) {
			this.roi.setStrokeColor(new Color(0, 255, 0, 120));
			this.roi.setStrokeWidth(5);
		}
		else {
			this.roi.setStrokeColor(new Color(255, 0, 0, 120));
			this.roi.setStrokeWidth(1);
		}
		
		overlay.add(this.roi);
	
		if (next != null) {
			Line line = new Line(this.x, this.y, next.x, next.y);
			line.setStrokeColor(this.color);
			line.setStrokeWidth(2);
			overlay.add(line);
		}
		
		//TextRoi text = new TextRoi(x, y-10, "" + value);
		//text.setPosition(t+1);
		//overlay.add(text);
	}
	/*extended the link to a double link: current <-> next*/
	public void link(Spot next) {
		if (next != null) {
			this.next = next;
			next.previous = this;
			next.set_color(this.color);
		}
	}
	/*added two functions to get color and set color*/
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

