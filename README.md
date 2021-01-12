# Computer Science Project - Forgot Your Face Mask app

Project for Computer Science Project course by University of Helsinki, Autumn 2020.

## What it is?

An Android app that detects if a user has a face mask on or not. It works in the background and automatically takes pictures when users unlocks or interacts with the phone. The inference is done locally on the phone, so no images are transferred over the internet.

If the user is not wearing a mask a notification is given. There is also a possibility to disable the detection for certain location (e.g. home).

## The AI?

There are two neural network models: one for detecting if a face is in the picture, and another one for detecting if the face has mask one.

More details of the AI behind the Face Mask App can be found in the [presentation slides](doc/face-mask-app-ai.pdf).

## How ready it is?

This is a proof of concept. The face mask detection works fully, but features such as geolocation for disabling the app for certain location needs improvement. For example, the distance for disabling the detection is hard-coded and once the detection has been disabled for a location it can not be enabled again.

## Forgot Your Face Mask App

- [Android app](AndroidApp/)
- [Models & training code](model/)

## Links

- [TODO](doc/TODO.md)
- [Kaggle dataset used as a base](https://www.kaggle.com/alexandralorenzo/maskdetection)

