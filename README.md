# Time Lapser for Flashforge Adventurer 3D printer with an Android Smartphone

## Applications and Scripts
1) Main application for Android Smartphone
   * Implemented in Android Studio with Kotlin
   * Intefgrated ffmpeg for video generation and OpenCV for image processing
3) Python script to patch gcode of 3d printer

## How To use
* The application will monitor the camera preview and detect the moment when the printer bed reaches the front of the printer. 
* This is implemented as a simple threshold of the average intensity of the bottom part of the image: when the black printer bed arrives to the front, the intensity ecrease to a lever lower than a Threshold.
* The python script patches the gcode in order to bring the printer bed to the front after each layer is printed. Also the camera head is placed on the back and right of the printer.
* To generate the video, just press the "volume down" button on the smartphone and wait.
* The captured pictures are placed in separated the directories under the DCIM directory on the smartphone



