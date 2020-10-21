import torch
import torch.nn as nn
import torch.optim as optim
import torchvision

import numpy as np
import matplotlib.pyplot as plt
import time
import os
import copy

from torch.optim import lr_scheduler
from torchvision import datasets, models, transforms

def load_data(data_dir='data', batch_size=1):
    # data normalization
    data_transforms = {
        'train': transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'val': transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'test': transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
    }

    image_datasets = {}
    dataloaders = {}

    for phase in ['train', 'val', 'test']:
        image_datasets[phase] = datasets.ImageFolder(
            os.path.join(data_dir, phase), data_transforms[phase])

        dataloaders[phase] = torch.utils.data.DataLoader(
            image_datasets[phase], batch_size=batch_size, shuffle=True, num_workers=4)

    class_names = image_datasets['train'].classes

    return dataloaders, class_names

def train_model(model, criterion, optimizer, scheduler, dataloaders, n_epochs=25):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    since = time.time()

    best_model_weights = copy.deepcopy(model.state_dict())
    best_acc = 0.0

    for epoch in range(n_epochs):
        print('Epoch {}/{}'.format(epoch, n_epochs - 1))
        print('-' * 10)

        # Each epoch has a training and validation phase
        for phase in ['train', 'val']:
            if phase == 'train':
                model.train()  # Set model to training mode
            else:
                model.eval()   # Set model to evaluate mode

            running_loss = 0.0
            running_corrects = 0

            # Iterate over data.
            for inputs, labels in dataloaders[phase]:
                inputs = inputs.to(device)
                labels = labels.to(device)

                # zero the parameter gradients
                optimizer.zero_grad()

                # forward
                # track history if only in train
                with torch.set_grad_enabled(phase == 'train'):
                    outputs = model(inputs)
                    _, preds = torch.max(outputs, 1)
                    loss = criterion(outputs, labels)

                    # backward + optimize only if in training phase
                    if phase == 'train':
                        loss.backward()
                        optimizer.step()

                # statistics
                running_loss += loss.item() * inputs.size(0)
                running_corrects += torch.sum(preds == labels.data)
            if phase == 'train':
                scheduler.step()

            epoch_loss = running_loss / len(dataloaders[phase])
            epoch_acc = running_corrects.double() / len(dataloaders[phase])

            print('{} Loss: {:.4f} Acc: {:.4f}'.format(
                phase, epoch_loss, epoch_acc))

            # deep copy the model
            if phase == 'val' and epoch_acc > best_acc:
                best_acc = epoch_acc
                best_model_weights = copy.deepcopy(model.state_dict())

        print()

    time_elapsed = time.time() - since
    print('Training complete in {:.0f}m {:.0f}s'.format(time_elapsed // 60, time_elapsed % 60))
    print('Best val Acc: {:4f}'.format(best_acc))

    # load best model weights
    model.load_state_dict(best_model_weights)
    return model

def get_model(dataloaders, n_epochs=30):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    # load pretrained resnet model
    model = models.resnet18(pretrained=True)

    # freeze all parameters
    for param in model.parameters():
        param.requires_grad = False

    # replace output layer, we have two outputs
    model.fc = nn.Linear(model.fc.in_features, 2)

    # transform to GPU if needed
    model = model.to(device)

    # loss function
    criterion = nn.CrossEntropyLoss()

    # Learning rate and momemtum will be overriden by scheduler
    optimizer = optim.SGD(model.parameters(), lr=0.01, momentum=0.9)

    # One Cycle Policy scheduler
    scheduler = torch.optim.lr_scheduler.OneCycleLR(
        optimizer,
        max_lr=0.1,
        base_momentum=0.5,
        max_momentum=0.95,
        steps_per_epoch=len(dataloaders['train']),
        epochs=n_epochs,
    )

    return model, criterion, optimizer, scheduler

def save_mobile_model(model, fname):
    model.to('cpu')
    model.eval()
    example = torch.rand(1, 3, 224, 224)
    traced_script_module = torch.jit.trace(model, example)
    traced_script_module.save(fname)

def save_model(model, fname):
    torch.save(model, fname)
