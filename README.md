# Noun-Determiner diversity experiment
This is code that produces the results for the paper:

Silvey and Christodoulopoulos (2016), **Children’s Production of Determiners as a Test Case for 
Innate Syntactic Categories**, In *Proceedings of Evolang 11*

## Requirements
* Java 1.7 or later
    * Check the version by running `java -version`:
    <pre>
    java version "1.8.0_65"
    Java(TM) SE Runtime Environment (build 1.8.0_65-b17)
    Java HotSpot(TM) 64-Bit Server VM (build 25.65-b01, mixed mode)
    </pre>
* Apache [Maven](https://maven.apache.org/download.cgi)

## Installation
From the root directory run
```
mvn compile
```

## Experiments
### Real child-directed data (CHILDES)
The data files (in `data/tagged`) for Adam, Sarah, Eve, Naomi, Nina, Peter were downloaded
from the [CHILDES database](http://childes.psy.cmu.edu/) (XML version).

The were cleaned using `XMLCorpusCleaner` and tagged using the [Brill tagger](http://gposttl.sourceforge.net/).

To execute the replication experiment simply run:
```
mvn exec:java -Dexec.mainClass="determiners.YangReplicator"
```

### Simulated Zipfian samples
To run the model on simulated Zipfian samples use:
```
mvn exec:java -Dexec.mainClass="determiners.Simulation"
```

This will produce two files:

* *sim-average.csv*: The mean predicted and empirical values for a range of noun types 
* *sim-raw@XX.csv*: Raw numbers for a single simulation using *XX* noun types  