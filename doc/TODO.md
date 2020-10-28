# TODO

## Possible problems
- [x] Can camera be used on background? - Yes, it can!
- [x] Can camera be activate on unlock or keypresses? - Yes, it can!
- [x] Does YOLO (or another similar library) support images with different sizes? - We can scale the images.

## Generic stuff
- [ ] Move use cases to human-centred manner as advised in design thinking?
- [ ] Minimal documentation before the end/demo

## Open questions
- [x] Do inference on the phone or use APIs to backend server? - We'll try to do this on the phone.
- [x] Develop on home computers or somewhere else? - Seems like everyone will have their own development environment.
- [x] Should we detect from video or from static picture? - We'll use a static picture to start with.

## Mobile frontend
- [x] App that works on background and displays a 'Hello World!' notification when the phone is unlocked
- [x] App that works on background and takes an image with the front camera when the phoneis unlocked
- [x] App that takes a photo and detects images using a model pretrained on ImageNet
- [x] App that takes a photo and detects images using a model pretrained on Imagenet when the phone is unlocked
- [ ] App that takes a photo and detects if a face is present or not
- [x] App that takes a photo and detects bounding box for the face (not needed)
- [ ] App that takes a photo and crops the face from the image using the bounding box information
- [ ] App that detects from a face, cropped from a photo, if it has mask or not
- [ ] Geolocation #additional functionality
- [ ] Test with another andoir phone


## Data Science
- [x] YOLO installation instructions for Anaconda
- [x] Collect images with and without masks
- [ ] Train a "face or not" model (do we need?)
- [ ] Train a model that can get bounding box for face (this can possible be combined with the "face or not" model)
- [x] Train a model for detecting if mask is on or not
- [ ] Evaluate augmentation options and specify parameters
- [x] Scrape more data from the web (Done, Esa has over 800 pictures)
- [x] Incorporate scraped training data
- [ ] Test hyperparameters like learning rate and momentum
- [ ] Add no-face test-images
- [ ] Detect if a mask is correctly put on #additional functionality


## Required by university
- [x] Book time for 30/9/2020 meeting with Jukka
- [x] Preparation for 7/10/2020 meeting
- [x] Book time for 28/10/2020 meeting with Jukka
- [ ] Preparation for 14/10/2020 meeting
- [ ] Preparation for 25/11/2020 demo session

## Learning diary updates
- [x] Update for 23/9/2020
- [x] Update for 30/9/2020
- [x] Update for 7/10/2020
- [x] Update for 14/10/2020
- [x] Update for 21/10/2020
- [ ] Update for 28/10/2020
- [ ] Update for 4/11/2020
- [ ] Update for 11/11/2020
- [ ] Update for 18/11/2020
