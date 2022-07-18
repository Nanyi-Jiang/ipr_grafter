# IPR Grafter

Grafter for IPR 

## Installation
```
git clone 
```
After clone to local, please import to Eclipse with `Maven > Existing Maven Projects`
## Dataset Download Address
Please download the [dataset](http://web.cs.ucla.edu/~tianyi.zhang/grafter/grafter-dataset.zip)

## Adjust Path
To use the Grafter, please change the directory address refer to TODO mark in the following files:
`./code/Grafter.config`
`./code/Grafter(Ant).config`
`./code/src/main/java/edu/ucla/cs/grafter/config/GrafterConfig.java`
`./code/src/main/java/edu/ucla/cs/grafter/graft/analysis/CloneVisitor.java`
`./code/src/main/java/edu/ucla/cs/grafter/instrument/CloneInstrument.java`
> Some path are the path to [dataset](http://web.cs.ucla.edu/~tianyi.zhang/grafter/grafter-dataset.zip) and some path are the ones to ipr_grafter

## Run
To run test demo, please go to the CloneInstrument.java and run the `main()`
