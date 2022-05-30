# ImageJ_Plugin_Mitosis

## Project Description

The goal of this project is to implement an ImageJ plugin for the detection and tracking of nuclei and the detection of mitosis. 


## Setup

1. Download [ImageJ_Plugin_Mitosis.jar](https://github.com/sMamooler/ImageJ_Plugin_Mitosis/blob/main/ImageJ_Plugin_Mitosis.jar) and put it in the **plugins** folder of your **fiji** directory. This is where all other plugins exist.

**NOTE**: we have use the [Auto Local Threshold plugin](https://imagej.net/plugins/auto-local-threshold) which is probably already in your ImageJ plugins. if not, you can download [auto_threshold.jar](https://github.com/sMamooler/ImageJ_Plugin_Mitosis/blob/main/auto_threshold.jar) and put it in the **plugins** folder of your **fiji** directory.

3. Download the files **red.tif** and **green.tif** from [this shared drive](https://drive.switch.ch/index.php/s/VzpRO3yznYIPfi0?path=%2Foriginal_data).

**NOTE**: **red.tif** and **green.tif** include the entire dataset (205 frames). You can also find sub-samples containing [the first 50 frames](https://drive.switch.ch/index.php/s/VzpRO3yznYIPfi0?path=%2Fsample_1to49), and [the middle 50 frames](https://drive.switch.ch/index.php/s/VzpRO3yznYIPfi0?path=%2Fsample_100to149) in the shared drive.

## User Manual
1. Open ImageJ.
2. Drag and drop **red.tif** and **green.tif**
3. Go to **Plugins** menu and click on **Mitosis_Detection**. 
4. Click on **Mitosis** to start the analysis.
5. Once the analysis is done you can find the detection and tracking results in the **RGB** window.Mitosis is indicated with tick red lines.
6. The growth rate of all frames can be found in **Rate of Growth of Cells**



