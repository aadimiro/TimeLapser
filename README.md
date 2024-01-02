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


# Description of main module CameraActivitd.kt


The `CameraActivity` class is the main activity of the TimeElapser Android application, responsible for camera preview, picture capturing, video compilation, and bed arrival detection. Below is a breakdown of its structure and functionality.

## Dependencies and Imports

The activity imports necessary Android and third-party libraries, including OpenCV and FFmpeg.

## Properties

- `timeStampvid`: String - Timestamp used for naming video files.
- `camera`: Camera - Represents the device camera.
- `surfaceView`: SurfaceView - Provides a dedicated drawing surface for the camera preview.
- `surfaceHolder`: SurfaceHolder - Manages the surface of `surfaceView`.
- `grayImageView`: ImageView - Displays grayscale images.
- `rgba`: Mat - OpenCV matrix for RGBA color space.
- `gray`: Mat - OpenCV matrix for grayscale images.
- `logTextView`: TextView - Displays log messages.
- `ffmpeg`: FFmpeg - Manages FFmpeg functionality.
- `cameraPermissionCode`: Int - Permission code for camera access.
- `pictureNumber`: Int - Tracks the number of captured pictures.
- `lastTriggerTime`: Long - Records the timestamp of the last trigger event.
- `triggerAvailable`: Boolean - Flag indicating if a trigger event is available.
- `debounceTime1`: Long - Configurable debounce time for trigger events.
- `debounceTime2`: Long - Configurable debounce time for preventing rapid triggers.
- `videoProcOngoing`: Boolean - Flag indicating if video compilation is in progress.

## onCreate Method

- Initializes OpenCV.
- Sets up UI elements and permissions.
- Initializes timestamp and starts camera preview.

## onKeyDown Method

- Handles volume key events for picture capturing and video compilation.

## compileVideo Method

- Compiles images into a video using FFmpeg.
- Uses coroutine for asynchronous execution.

## createOutputVideoPath Method

- Generates the output path for the compiled video.

## capturePicture Method

- Captures a picture using the camera.
- Calls `savePicture` and restarts the camera preview.

## savePictureAsync Method

- Saves the captured picture asynchronously.

## savePicture Method

- Calls `savePictureAsync` on the main thread.

## getOutputMediaFile Method

- Creates and returns the output file for saved pictures.

## startCameraPreview Method

- Initializes and starts the camera preview.
- Configures camera parameters, preview size, and focus modes.
- Sets touch focus listener on the `SurfaceView`.
- Sets the preview callback for frame processing.

## setCameraDisplayOrientation Method

- Sets the display orientation of the camera based on device rotation.

## stopCameraPreview Method

- Stops and releases the camera preview.

## setCameraZoom Method

- Sets the camera's focus mode and zoom level.

## handleTouchFocus Method

- Handles touch events for setting the focus area.
- Uses camera parameters to set focus areas and metering areas.
- Calls auto focus to apply changes.

## calculateFocusRect Method

- Converts touch coordinates to camera coordinates.
- Calculates a focus area rectangle.

## configureCameraPreviewSize Method

- Configures the camera preview size based on desired dimensions.

## findBestPreviewSize Method

- Finds the closest supported preview size to the desired size.

## surfaceCreated Method

- Initializes OpenCV matrices.
- Starts the camera preview and sets the preview callback.

## PreviewCallbackImpl Inner Class

- Implements `Camera.PreviewCallback`.
- Processes frame data with OpenCV.

## surfaceDestroyed Method

- Stops the camera preview.
- Releases OpenCV matrices.

## processDataWithOpenCV Method

- Converts frame data to OpenCV Mat.
- Calls `isBedArrived` for bed arrival detection.

## isBedArrived Method

- Implements bed arrival detection logic.
- Converts grayscale image to OpenCV Mat.
- Calculates average intensity and triggers based on threshold and debounce times.
- Updates the grayscale image in `grayImageView`.

## updateGrayImage Method

- Converts OpenCV Mat to Bitmap.
- Displays the Bitmap in `grayImageView`.