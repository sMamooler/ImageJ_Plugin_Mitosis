# ImageJ_Plugin_Mitosis

## Project Description

The goal of this project is to implement an ImageJ plugin for the detection and tracking of nuclei and the detection of mitosis. As a result, this project consists of three main parts:

#### Detection of the nuclei

To detect the nuclei, we first pre-processed the raw images. Then, ImageJ's "Analyze particles" feature was used to find the regions of interest. 
The pre-processing step includes applying a median filter and converting it to a binary image. Binarization is done via thresholding with minimum and maximum thresholds chosen by inspecting the histogram. Finally, the Fill Holes algorithm is used to fill in the dark spots in binarized nuclei, followed by Watershed to separate the connected nuclei.

#### Tracking the nuclei

The nuclei are tracked using a distance and intensity-based cost function while keeping only the nearest neighboring nucleus. The distance is computed between the centroids of ROIs obtained in the detection step. 

#### Detection of mitosis

Two criteria are used to detect mitosis: Each ROI in a frame is linked to at most one other ROI in the previous frame and at most an ROI in the next frame. If a ROI has no link to the previous frame, it's the result of a mitosis. In addition, when mitosis happens, the nucleus becomes very bright, making the intensity of nucleus region another cue for detecting mitosis. 



## Setup

**TODO**: explain the jar file for plugins + make a jar file if possible


## User Manual



