# PROMAR

This is an implementation of [PROMAR](PROMAR.pdf) on Android. PROMAR aims at providing multi-user AR applications on off-the-shelf smartphones without special hardware. Our demo persuasively proves PROMAR capable of offering consistent AR experience in regardless of moderate view angle changes and distance change.

![](demo_video/demo_1.gif)

The above gif image is a display of our PROMAR Android application. For more demos, please refer to [demo_video/](https://github.com/PROMAR2019/PROMAR_Android/tree/master/demo_video).

## Requirement

* SDK version 24 or higher
* JAVA 8 or higher
* [ARCore supported mobile phone](https://developers.google.com/ar/discover/supported-devices)


# Step by Step

Below are instructions for running RPOMAR on your smartphone.


## 1. Find reference object and place virtual object

When TensorFlow successfully recognizes objects, use them as references and press PLACE VO button to place virtual object.

<img src="https://github.com/PROMAR2019/PROMAR_Android/blob/master/img/step_1.png" width="370" height="640">

## 2. Extract feature points and save data to local

Use the bar on right of the screen to adjust virtual object distance. After you feeling satisfied with the virtual object, press CONFIRM button. Take a breath, the application takes a few seconds to finish this process, but you will see that pays off.

<img src="https://github.com/PROMAR2019/PROMAR_Android/blob/master/img/step_2.png" width="370" height="640">

## 3. Swith to Viewer mode and load data

After the virtual object owner finished cooking, now it's time to serve it to the viewer. Press the Viewer box at the bottom of screen to switch to viewer mode and click button RETRIEVE to load data. Hold tight, magic time!

<img src="https://github.com/PROMAR2019/PROMAR_Android/blob/master/img/step_3.png" width="370" height="640">

## 4. Scan the reference object to retrieve the virtual object

When the message "Data loaded" toasts on screen, scan the reference object and say hello to your new/old friend.

<img src="https://github.com/PROMAR2019/PROMAR_Android/blob/master/img/step_4.png" width="370" height="640">


# Time Consumption

Below is the average time consumption of each step in PROMAR running on OnePlus 5.

<img src="https://github.com/PROMAR2019/PROMAR_Android/blob/master/img/time.png" width="640" height="370">
