# Cyber Robot Brain

This Android application has been developed during an University Class and the aim was to provide a "brain" to the Cyber Robot, a cheap sensorless toy robot sell by Clementoni. More information about the robot can be found [on the official website](https://www.amazon.it/Scienza-e-Gioco-13941-Clementoni/dp/B010VB0IQS).

More specifically, the aim was to reverse-engineer the communication protocol of the robot via the Bluetooth HCI snoop log and then build an app that guides it to reach a target object that is framed by the phone’s camera.

## How it works:

To move the target we have used three different markers: one for the target and the others two to recognize the left and the right side of the robot. The recognition has been exploited using [openCV Library](http://opencv.org/platforms/android/).

You can checkout the behauvior of the application by watching [a video on YouTube](https://www.youtube.com/watch?v=xStDo5KmYf8)

## Test Environment:

The recognition works in the following test environment.

We have used the following disposition of markers: Target = Red, Right = Green and Left = Blue.

**Test condition**: the light on the markers can’t change too much in the test field and due to the fact that the back of robot is green and in some light condition can interfere, we cover it with a piece of white paper.

<img src="https://raw.githubusercontent.com/prof18/CyberRobotBrain/master/images/image2.png" width="50%" height="50%">

Also the color of the field where we have tested the app is uniform and has a neutral color (we use a large piece of paper); the use of paper removes some noise due to light reflection.

<img src="https://raw.githubusercontent.com/prof18/CyberRobotBrain/master/images/image3.png" width="50%" height="50%">

**Calibration**: To better recognize the markers, it is necessary a calibration phase. It is required to take a photo at 15 cm from the target marker as explain in the calibration message inside the app. We placed all the 3 markers close together and took the picture at 15 cm. The most precise this picture is taken, the most precise the application should work.

<img src="https://raw.githubusercontent.com/prof18/CyberRobotBrain/master/images/image1.png" width="50%" height="50%">

Last advice: if the app doesn’t recognize some of the markers or the robot seems to move in completely wrong direction we suggest to change illumination and calibrate again colors.

## License
```
   Copyright 2017 Biasin Mattia, Dominutti Giulio, Gomiero Marco

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
