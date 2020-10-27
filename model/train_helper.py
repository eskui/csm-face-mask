import torch
import torch.nn as nn
import torch.optim as optim
import torchvision

import numpy as np
import matplotlib.pyplot as plt
import time
import os

from torch.optim import lr_scheduler
from torchvision import datasets, models, transforms

class RandomIndividualApply():
    """
    Custom data augmentation policy.
    
    Apply randomly a list of transformations with a given probability

    Args:
        transforms (list or tuple): list of transformations
        p (float): probability
    """

    def __init__(self, transforms, p=0.5):
        self.p = p
        self.transforms = transforms

    def __call__(self, img):
        for t in self.transforms:
            if self.p < np.random.random():
                continue
            img = t(img)
        return img

    def __repr__(self):
        format_string = self.__class__.__name__ + '('
        format_string += '\n    p={}'.format(self.p)
        for t in self.transforms:
            format_string += '\n'
            format_string += '    {0}'.format(t)
        format_string += '\n)'
        return format_string


def load_data(data_dir='data', batch_size=1):
    data_transforms = {
        'train': transforms.Compose([
            transforms.RandomRotation((-45, 45)),
            transforms.Resize((224, 224)),
            RandomIndividualApply([
                transforms.RandomHorizontalFlip(p=1),
                transforms.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.3, hue=0.3),
                transforms.RandomGrayscale(p=0.1),
                transforms.RandomPerspective(),
            ], p=0.5),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'val': transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'test': transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
    }

    dataloaders = {}
    image_datasets = {}

    for phase in ['train', 'val', 'test', 'own']:
        path = os.path.join(data_dir, phase)

        if not (os.path.isdir(f'{path}/mask') and len(os.listdir(f'{path}/mask')) > 1):
            continue

        image_datasets[phase] = datasets.ImageFolder(path, data_transforms[phase])

        dataloaders[phase] = torch.utils.data.DataLoader(
            image_datasets[phase], batch_size=batch_size, shuffle=True, num_workers=4)

    class_names = image_datasets['train'].classes

    return dataloaders, class_names

def train_model(model, criterion, optimizer, scheduler, dataloaders, n_epochs=25):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    since = time.time()

    best_acc = 0.0

    for epoch in range(n_epochs):
        print(f'Epoch {epoch+1}/{n_epochs}')
        print('-' * 10)

        # Each epoch has a training and validation phase
        for phase in ['train', 'val']:
            if phase == 'train':
                model.train()  # Set model to training mode
            else:
                model.eval()   # Set model to evaluate mode

            running_loss = 0.0
            running_corrects = 0.0

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
                    loss = criterion(outputs, labels)

                    # backward + optimize only if in training phase
                    if phase == 'train':
                        loss.backward()
                        optimizer.step()
                        scheduler.step()

                # statistics
                _, preds = torch.max(outputs, 1)
                running_loss += loss.item() * inputs.size(0)
                n_correct = torch.sum(preds == labels)
                running_corrects += n_correct

            epoch_loss = running_loss / len(dataloaders[phase].dataset)
            epoch_acc = running_corrects / len(dataloaders[phase].dataset)

            print(f'{phase} Loss: {epoch_loss:.4f} Acc: {epoch_acc:.4f}')

        print()

    time_elapsed = time.time() - since
    print('Training complete in {:.0f}m {:.0f}s'.format(time_elapsed // 60, time_elapsed % 60))
    print(f'Best val Acc: {best_acc:.4f}')

    # load best model weights
    return model

def get_model(dataloaders, n_epochs=30):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    # load pretrained resnet model
    model = models.resnet18(pretrained=True)

    # replace output layer, we have two outputs
    model.fc = nn.Sequential(
        nn.Linear(model.fc.in_features, 2),
        nn.LogSoftmax(dim=1)
    )

    # transform to GPU if needed
    model = model.to(device)

    # loss function
    criterion = nn.NLLLoss()

    # Learning rate and momemtum will be overriden by scheduler
    optimizer = optim.SGD(model.parameters(), lr=0.001, momentum=0.9)

    # One Cycle Policy scheduler
    scheduler = torch.optim.lr_scheduler.OneCycleLR(
        optimizer,
        max_lr=0.005,
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

def load_model(fname, **args):
    return torch.load(fname, **args)
