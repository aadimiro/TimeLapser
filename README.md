# Time Lapser for Flashforge Adventurer 3 3D printer with an Android Smartphone

## How and why is this project started ?
On my current vacations, I wanted to explore how good OpenAI ChatGPT kann assist on software implementation.
Although I have many years experience in embedded software development (specially in C), 5 days ago I had no idea about Android Studio, Kotlin, Gradle and android development at all. Also I had no idea how gcode works for the 3D printer.
So I decided to take the challenge and see if I am able to implement make it to a running application in short time, assisted by ChatGPT.
And it worked :-)!

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
* The captured pictures are placed in separated the directories under the DCIM directory on the smartphone. This way, the video can also be made complicated outside the smartphone, having more processing possibilities.

## First results
See the first video:
https://youtube.com/shorts/npaiBbHT8cs?si=XNsPOFgp2joKy5DW


