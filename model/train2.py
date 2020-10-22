#!/usr/bin/env python

import train_helper2 as train_helper

n_epochs = 30
data_dir = '../../data'

dataloaders, class_names = train_helper.load_data(data_dir)
model, criterion, optimizer, scheduler = train_helper.get_model(dataloaders, n_epochs)
model = train_helper.train_model(
        model,
        criterion,
        optimizer,
        scheduler,
        data_dir=data_dir,
        n_epochs=n_epochs)

train_helper.save_model(model, 'model.pt')
train_helper.save_mobile_model(model, 'model_mobile.pt')

