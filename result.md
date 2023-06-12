# Result

## Comparison Targets

This tool is evaluated by comparing to two other techniques.

- Ground Truth - Coverage-Based Minimization: Utilizes coverage data to check whether minimization using coverage data
  will be successful. Subjects are generally repairable by our member-granular minimization if and only if it is
  repairable by the coverage-based technique.
- Baseline - Class-Granular Minimization: Utilizes static analysis in class-level to perform minimization. This is
  generally the approach used by other dependency analysis techniques.

## Dataset

The entire Defects4J dataset is used for evaluation. Refer to each investigation for the subject selection criterion.

All experiments are executed in a machine with the following specifications:

- 128-thread AMD Ryzen Threadripper PRO 3995WX (with 48-threads allocated to JVM)
- 512GB RAM (with 32GB allocated to JVM)
- Docker 24.0.1
- OpenJDK 1.8.0_352, 17.0.5
- CentOS Stream 8

## Investigation

### Effectiveness of Repair

The subjects selected for this investigation are all Defects4J triggering tests where:

- The test case cannot be compiled without any modifications.
- The test case can be compiled after performing coverage-based minimization.

The evaluation metrics for the investigation are:

- Compilable: The test case can be compiled after running minimization.
- Test Match: The test case matches its execution result after running minimization.

#### Java 1.8, Source Level 1.7

| Project     | Count | Baseline - Compilable | Baseline - Test Match | Member Granular - Compilable | Member Granular - Test Match |
|-------------|-------|-----------------------|-----------------------|------------------------------|------------------------------|
| Codec       | 20    | 20 (100%)             | 20 (100%)             | 20 (100%)                    | 20 (100%)                    |
| Collections | 4     | 4 (100%)              | 4 (100%)              | 4 (100%)                     | 2 (50%)                      |
| Lang        | 67    | 64 (96%)              | 64 (96%)              | 67 (100%)                    | 67 (100%)                    |
| Math        | 39    | 39 (100%)             | 39 (100%)             | 39 (100%)                    | 29 (74%)                     |

#### Java 17, Source Level 17

| Project         | Count | Baseline - Compilable | Baseline - Test Match | Member Granular - Compilable | Member Granular - Test Match |
|-----------------|-------|-----------------------|-----------------------|------------------------------|------------------------------|
| Closure         | 511   | 511 (100\%)           | 511 (100\%)           | 511 (100\%)                  | 468 (92\%)                   |
| Codec           | 20    | 20 (100\%)            | 20 (100\%)            | 20 (100\%)                   | 20 (100\%)                   |
| Collections     | 7     | 3 (43\%)              | 3 (43\%)              | 7 (100\%)                    | 4 (57\%)                     |
| Compress        | 103   | 103 (100\%)           | 103 (100\%)           | 103 (100\%)                  | 94 (91\%)                    |
| JacksonDatabind | 219   | 199 (91\%)            | 197 (90\%)            | 219 (100\%)                  | 155 (71\%)                   |
| JacksonXml      | 12    | 0 (0\%)               | 0 (0\%)               | 12 (100\%)                   | 8 (67\%)                     |
| Lang            | 12    | 12 (100\%)            | 12 (100\%)            | 12 (100\%)                   | 12 (100\%)                   |
| Mockito         | 67    | 67 (100\%)            | 36 (54\%)             | 67 (100\%)                   | 35 (52\%)                    |

### Accuracy and Precision

The subjects selected for this investigation are all Defects4J triggering tests where:

- The test case cannot be compiled without any modifications.
- The test case can be compiled and correctly executed after performing coverage-based minimization.
- The test case can be compiled and correctly executed after performing member-granular minimization.

The evaluation metrics for the investigation are:

- False Positive Rate: The ratio of classes/methods which are retained but are actually not reachable.
- False Negative Rate: The ratio of classes/methods which are removed but are actually reachable.
- Accuracy
- Precision
- Recall
- F1-Score

#### Java 1.8, Source Level 1.7

- For Classes

| Project | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|---------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Codec   | 10    | 0.012               | 0.114               | 0.973    | 0.936     | 0.886  | 0.902    |
| Lang    | 29    | 0.002               | 0.179               | 0.992    | 0.957     | 0.821  | 0.864    |
| Math    | 13    | 0.004               | 0.116               | 0.991    | 0.844     | 0.884  | 0.856    |

- For Methods

| Project | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|---------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Codec   | 10    | 0.004               | 0.002               | 0.996    | 0.896     | 0.998  | 0.943    |
| Lang    | 29    | 0.002               | 0.043               | 0.998    | 0.860     | 0.957  | 0.887    |
| Math    | 13    | 0.003               | 0.054               | 0.996    | 0.562     | 0.946  | 0.687    |

#### Java 17, Source Level 17

- For Classes

| Project         | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|-----------------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Closure         | 90    | 0.364               | 0.145               | 0.715    | 0.580     | 0.855  | 0.639    |
| Codec           | 10    | 0.012               | 0.114               | 0.973    | 0.936     | 0.886  | 0.902    |
| Compress        | 43    | 0.054               | 0.249               | 0.928    | 0.796     | 0.751  | 0.727    |
| JacksonDatabind | 16    | 0.111               | 0.061               | 0.892    | 0.417     | 0.940  | 0.570    |
| JacksonXml      | 2     | 0.029               | 0.000               | 0.974    | 0.719     | 1.000  | 0.835    |
| Lang            | 5     | 0.001               | 0.061               | 0.996    | 0.988     | 0.939  | 0.960    |
| Mockito         | 2     | 0.000               | 0.000               | 1.000    | 1.000     | 1.000  | 1.000    |

- For Methods

| Project         | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|-----------------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Closure         | 90    | 0.218               | 0.041               | 0.796    | 0.291     | 0.959  | 0.426    |
| Codec           | 10    | 0.004               | 0.002               | 0.996    | 0.896     | 0.998  | 0.943    |
| Compress        | 43    | 0.056               | 0.101               | 0.945    | 0.543     | 0.899  | 0.616    |
| JacksonDatabind | 16    | 0.362               | 0.015               | 0.655    | 0.122     | 0.985  | 0.216    |
| JacksonXml      | 2     | 0.218               | 0.000               | 0.818    | 0.451     | 1.000  | 0.621    |
| Lang            | 5     | 0.001               | 0.029               | 0.999    | 0.953     | 0.971  | 0.962    |
| Mockito         | 2     | 0.000               | 0.000               | 1.000    | 0.900     | 1.000  | 0.945    |

### Execution Time

The subjects selected for this investigation are all Defects4J triggering tests where:

- The test case cannot be compiled without any modifications.
- The test case can be compiled after performing coverage-based minimization.

The evaluation metrics for the investigation are:

- Time Taken: The time taken (in seconds) to execute minimization on the test case, including all passes where
  applicable.
- Time Relative to Compilation: The relative amount of time taken compared to the time required for compiling the
  subject.

#### Java 1.8, Source Level 1.7

| Project     | Count | Compilation | Baseline      | Member-Granular |
|-------------|-------|-------------|---------------|-----------------|
| Codec       | 20    | 1.5         | 0.374 (25\%)  | 0.497 (33\%)    |
| Collections | 4     | 3.7         | 2.050 (55\%)  | 10.646 (288\%)  |
| Lang        | 67    | 3.4         | 1.701 (50\%)  | 2.196 (77\%)    |
| Math        | 39    | 1.1         | 2.436 (221\%) | 12.049 (1095\%) |

#### Java 17, Source Level 17

| Project         | Count | Compilation | Baseline       | Member-Granular |
|-----------------|-------|-------------|----------------|-----------------|
| Closure         | 511   | 5.2         | 15.395 (296\%) | 20.379 (392\%)  |
| Codec           | 20    | 1.7         | 0.344 (20\%)   | 0.405 (24\%)    |
| Collections     | 7     | 2.1         | 2.078 (99\%)   | 8.583 (409\%)   |
| Compress        | 103   | 1.3         | 1.126 (87\%)   | 1.703 (131\%)   |
| JacksonDatabind | 219   | 2.2         | 13.863 (630\%) | 16.697 (759\%)  |
| JacksonXml      | 12    | 2.4         | 0.983 (41\%)   | 0.676 (28\%)    |
| Lang            | 12    | 0.9         | 1.831 (203\%)  | 1.854 (206\%)   |
| Mockito         | 67    | 5.5         | 0.923 (17\%)   | 1.283 (23\%)    |

### Multiple Passes

- The test case cannot be compiled without any modifications.
- The test case can be compiled after performing coverage-based minimization.

The evaluation metrics for the investigation are:

- Delta Values of all metrics used in [Accuracy and Precision](#accuracy-and-precision): The difference in false
  positive rate, false negative rate, accuracy, precision, recall, and F-1 score comparing the first pass and the last
  pass.

#### Java 1.8, Source Level 1.7

- For Classes

| Project     | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|-------------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Codec       | 20    | 0.000               | 0.000               | 0.000    | 0.000     | 0.000  | 0.000    |
| Collections | 4     | -0.001              | 0.000               | +0.001   | +0.004    | 0.000  | +0.003   |
| Lang        | 67    | -0.000              | 0.000               | +0.000   | +0.024    | 0.000  | +0.015   |
| Math        | 39    | -0.001              | +0.000              | +0.001   | +0.013    | -0.000 | +0.006   |

- For Methods

| Project     | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|-------------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Codec       | 20    | -0.000              | 0.000               | +0.000   | +0.003    | 0.000  | +0.002   |
| Collections | 4     | -0.001              | 0.000               | +0.002   | +0.016    | 0.000  | +0.008   |
| Lang        | 67    | -0.000              | 0.000               | +0.000   | +0.030    | 0.000  | +0.020   |
| Math        | 39    | -0.001              | +0.001              | +0.001   | +0.029    | -0.001 | +0.025   |

#### Java 17, Source Level 17

- For Classes

| Project         | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|-----------------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Closure         | 511   | -0.008              | +0.006              | +0.004   | +0.017    | -0.006 | +0.004   |
| Codec           | 20    | 0.000               | 0.000               | 0.000    | 0.000     | 0.000  | 0.000    |
| Collections     | 7     | -0.000              | 0.000               | +0.001   | +0.003    | 0.000  | +0.001   |
| Compress        | 103   | -0.006              | +0.007              | +0.003   | +0.008    | -0.007 | +0.003   |
| JacksonDatabind | 219   | -0.008              | 0.000               | +0.007   | +0.019    | 0.000  | +0.015   |
| JacksonXml      | 12    | 0.000               | 0.000               | 0.000    | 0.000     | 0.000  | 0.000    |
| Lang            | 12    | -0.002              | 0.000               | +0.002   | +0.133    | 0.000  | +0.083   |
| Mockito         | 67    | -0.021              | +0.025              | +0.014   | +0.068    | -0.025 | +0.009   |

- For Methods

| Project         | Count | False Positive Rate | False Negative Rate | Accuracy | Precision | Recall | F1-Score |
|-----------------|-------|---------------------|---------------------|----------|-----------|--------|----------|
| Closure         | 511   | -0.010              | +0.002              | +0.009   | +0.026    | -0.002 | +0.024   |
| Codec           | 20    | -0.000              | 0.000               | +0.000   | +0.003    | 0.000  | +0.002   |
| Collections     | 7     | -0.001              | +0.004              | +0.001   | +0.015    | -0.004 | -0.000   |
| Compress        | 103   | -0.008              | +0.002              | +0.008   | +0.028    | -0.002 | +0.019   |
| JacksonDatabind | 219   | -0.031              | 0.000               | +0.028   | +0.017    | 0.000  | +0.021   |
| JacksonXml      | 12    | 0.000               | 0.000               | 0.000    | 0.000     | 0.000  | 0.000    |
| Lang            | 12    | -0.000              | 0.000               | +0.000   | +0.167    | 0.000  | +0.111   |
| Mockito         | 67    | -0.018              | +0.026              | +0.015   | +0.078    | -0.026 | +0.016   |
