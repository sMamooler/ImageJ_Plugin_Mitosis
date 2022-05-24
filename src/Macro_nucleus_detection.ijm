File.openSequence("/Users/yan/switchdrive/PhD/my courses/BioImInfo/project/mitosis/red/");
run("Median...", "radius=2 stack");
run("Duplicate...", "title=nucleus-copy.tif duplicate");
run("Auto Local Threshold", "method=Bernsen radius=15 parameter_1=0 parameter_2=0 white stack");
run("Gray Morphology", "radius=1 type=circle operator=open");
setOption("BlackBackground", false);
run("Convert to Mask", "method=Default background=Default calculate");
run("Set Measurements...", "mean centroid stack redirect=None decimal=3");
run("Analyze Particles...", "size=36-Infinity add stack");
roiManager("Show All without labels");
selectWindow("red");
run("From ROI Manager");



