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
conda create --name face-mask --file yolo-env.txt
conda activate face-mask
git clone https://github.com/ultralytics/yolov3
cd yolov3
python detect.py
````

#### Links

- [TODO](doc/TODO.md)
- [Use Cases](doc/Use_Cases.md)

