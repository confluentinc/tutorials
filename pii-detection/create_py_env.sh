#!/bin/sh

echo "creating a virtual environment"
VENV_PATH='./pii-detection-tutorial-venv'
python3 -m venv $VENV_PATH
source $VENV_PATH/bin/activate

echo "loading libraries"
pip install -r ./code/requirements.txt

echo "done"