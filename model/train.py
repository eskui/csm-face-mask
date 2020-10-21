#!/usr/bin/env python

import train_helper

n_epochs = 30

dataloaders, class_names = train_helper.load_data()
model, criterion, optimizer, scheduler = train_helper.get_model(dataloaders, n_epochs)
model = train_helper.train_model(model, criterion, optimizer, scheduler, dataloaders, n_epochs=n_epochs)

train_helper.save_model('model.pt')
train_helper.save_mobile_model('model_mobile.pt')

