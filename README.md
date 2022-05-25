# ImageJ_Plugin_Mitosis

## Project Description

The goal of this project is to implement an ImageJ plugin for the detection and tracking of nuclei and the detection of mitosis. As a result, this project consists of three main parts:

### Detection of the nuclei

To detect the nuclei, we first pre-processed the raw images. Then, ImageJ's "Analyze particles" feature was used to find the regions of interest. 

### Tracking the nuclei

The nuclei are tracked using a distance and intensity-based cost function while keeping only the nearest neighboring nucleus. The distance is computed between the centroids of ROIs obtained in the detection step. 

### Detection of mitosis



## Setup

**TODO**: explain the jar file for plugins
