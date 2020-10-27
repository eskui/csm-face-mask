#!/usr/bin/env python

import train_helper

n_epochs = 20
batch_size = 32

dataloaders, class_names = train_helper.load_data('../../data', batch_size=batch_size)
model, criterion, optimizer, scheduler = train_helper.get_model(dataloaders, n_epochs)
model = train_helper.train_model(model, criterion, optimizer, scheduler, dataloaders, n_epochs=n_epochs)

train_helper.save_model(model, 'model.pt')
train_helper.save_mobile_model(model, 'model_mobile.pt')

