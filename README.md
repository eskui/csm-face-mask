# Computer Science Project - Forgot Your Face Mask app

Project for Computer Science Project course by University of Helsinki, Autumn 2020.

### Forgot Your Face Mask App

#### Android app

- [Android app skeleton](AndroidApp/)

- Currently triggering on unlock, but the camera only opens when the app is open

#### Mask detector

- [Mask/No-mask dataset from Kaggle](https://www.kaggle.com/alexandralorenzo/maskdetection)

##### YOLOv3 Installation with Anaconda3

```
conda env create --file yolo-env.yml
conda activate face-mask
git clone https://github.com/ultralytics/yolov3
cd yolov3
python detect.py
````

#### Meetings with Jukka

- Wed 30/9/2020 9:30
- Wed 28/10/2020 9:45

#### Links

- [TODO](doc/TODO.md)
- [Use Cases](doc/Use_Cases.md)

