#!/bin/bash
#SBATCH --account=Project_2003564
#SBATCH --partition=gpu
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --mem=8G
#SBATCH --time=0:20:00
#SBATCH --gres=gpu:v100:1

module load pytorch/1.6
srun python3 train.py

