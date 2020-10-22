import torch
import torch.nn as nn
import torch.optim as optim
import torchvision
import torchvision.transforms.functional as F

import numpy as np
import matplotlib.pyplot as plt
import time
import os

from torch.optim import lr_scheduler
from torchvision import datasets, models, transforms

class RandomResize():
    """
    Custom data augmentation policy.

    Randomly resizes the images given a scale.

    Args:
        scale (tuple): minimum and maximum scales
    """

    def __init__(self, scale):
        self.min_scale = scale[0]
        self.max_scale = scale[1]

    def __call__(self, img):
        x, y = img.size
        scale_x = np.random.uniform(self.min_scale, self.max_scale)
        scale_y = np.random.uniform(self.min_scale, self.max_scale)

        new_x = int(x * scale_x)
        new_y = int(y * scale_y)

        return F.resize(img, (new_x, new_y))

    def __repr__(self):
        return f'{self.__class__.__name__} (min_scale={self.min_scale}, max_scale={self.max_scale})'

def load_data(data_dir='data', batch_size=1):
    # data normalization
    data_transforms = {
        'train': transforms.Compose([
            RandomResize((0.5, 2.0)),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'val': transforms.Compose([
            RandomResize((0.5, 2.0)),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'test': transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ]),
        'own': transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
        ])
    }

    image_datasets = {}
    dataloaders = {}

    for phase in ['train', 'val', 'test', 'own']:
        path = os.path.join(data_dir, phase)

        if not (os.path.isdir(f'{path}/mask') and len(os.listdir(f'{path}/mask')) > 1):
            continue

        image_datasets[phase] = datasets.ImageFolder(path, data_transforms[phase])

        dataloaders[phase] = torch.utils.data.DataLoader(
            image_datasets[phase], batch_size=batch_size, shuffle=True, num_workers=4)

    class_names = image_datasets['train'].classes

    return dataloaders, class_names

def train_model(model, criterion, optimizer, scheduler, n_epochs=25, data_dir='data', test=False):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    since = time.time()

    for epoch in range(n_epochs):
        dataloaders, _ = load_data(data_dir)
        print(f'Epoch {epoch+1}/{n_epochs}')
        print('-' * 10)

        if test:
            phases = ['test']
        else:
            phases = ['train', 'val']

        for phase in phases:
            i = 0
            running_loss = 0.0
            running_corrects = 0.0

            if phase == 'train':
                model.train()
            else:
                model.eval()

            torch.set_grad_enabled(phase == 'train')

            for img, label in dataloaders[phase]:

                img = img.to(device)
                label = label.to(device, dtype=torch.float)

                pred = model(img)[0]
                loss = criterion(pred, label)
                if phase == 'train':
                    loss.backward()
                    optimizer.step()
                    scheduler.step()
                    optimizer.zero_grad()

                # statistics
                running_loss += loss.item()
                pred_label = 1 if pred > 0.5 else 0
                running_corrects += int(pred_label == label.data)

                i += 1

            epoch_loss = running_loss / i
            epoch_acc = running_corrects / i

            print(f'\tPhase: {phase}, Loss: {epoch_loss:.4f}, Acc: {epoch_acc:.4f}')
        print()

    time_elapsed = time.time() - since
    print('Training complete in {:.0f}m {:.0f}s'.format(time_elapsed // 60, time_elapsed % 60))

    return model

def get_model(dataloaders, n_epochs=30):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    # For some reason pretrained models fail here.
    # load pretrained resnet model
    model = models.resnet18(pretrained=False)

    # replace output layer
    # we have one output (probability of mask or not)
    model.fc = nn.Sequential(
            nn.Linear(model.fc.in_features, 1),
            nn.Sigmoid()
    )

    # transform to GPU if needed
    model = model.to(device)

    # loss function
    criterion = nn.BCELoss(reduction='none').to(device)

    # Learning rate and momemtum will be overriden by the scheduler
    optimizer = optim.SGD(model.parameters(), lr=0.1, momentum=0.9)

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

def load_model(fname, **args):
    return torch.load(fname, **args)
