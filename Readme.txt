Supplementary code for 'An ensemble for classification in multi-class streams with class-based concept drift'

Exclusively provided as part of submission for ECML 2019. Not to be used for any other purpose.

The first is the MOA implementation for the algorithm for CSE, and requires the following packages as well as at least MOA 2017/06.
com.yahoo.sketches.quantiles
org.apache.commons.math3

The second and third are MixedTypeGenerator and RandomUniformGenerator. These are the new synthetic generators described in the paper. The fourth is the multiclass Circles generator described in the paper.

The remaining files are used with existing MOA generators to create the synthetic stream variants with class drift described in the paper.