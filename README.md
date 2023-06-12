# Test Dependency Minimization

Demo implementation for test dependency minimization.

Test dependency minimization aims to repair build failures by removing all methods that are not used when executing a 
test case. 

## Build Instructions

### Prerequisites

- JDK 1.8+
- Defects4J
- (Optional) Docker

### Building the Application

At the root of the project directory, run the following command:

```sh
./gradlew :javaparser:install && ./gradlew shadowJar
```

The fat JAR will be generated in `entrypoint/build/libs/test-dependency-minimization-all.jar`.

## Running the Application

There are two ways this application can be executed.

- Minimization Mode: Performs a minimization on a project given its source root, classpath, and entrypoint(s).
    - See the `minimize` subcommand for more details.
- Comparison Mode: Executes comparison/evaluation using subjects from Defects4J.
    -  See the `d4j compare` subcommand for more details.

Other commands are also available; Refer to the help text for more information.

### Running in Defects4J Docker Image

This is the preferred method of running comparison mode, as it ensures a consistent and replicable environment for 
experimentation.

To use the Defects4J Docker image, do the following:

```sh
# Assume that the directory of this repository is in $repo_dir
repo_dir=$(pwd)

# Step 1: Clone the Defects4J repository
git clone https://github.com/rjust/defects4j.git
cd defects4j

# Step 2: Check out the latest tagged version (Optional)
git checkout v2.0.0

# Step 3 patches the image to update some library versions. This step may be skipped if the unmodified version of 
# Defects4J is desired.

# Step 3a: Original Version - Update Cobertura version
git apply "$repo_dir/scripts/00-orig.patch"

# Step 3b: JDK-Modified Version - Patch to use newer version of JDK
git apply "$repo_dir/scripts/00-separate-images.patch"

# Step 4: Build the Image; Add/Modify `-t` argument as necessary to tag the images
docker build -t defects4j:defects4j-2.0.0-orig .

# Step 5: Bind-Mount the JAR into the Docker container
docker run -it \
    --mount type=bind,src="$repo_dir/entrypoint/build/libs/test-dependency-minimization-all.jar",target=/toolkit.jar,readonly \
    defects4j:defects4j-2.0.0-orig
```

### Running Test Cases

Test cases can be run using the following command:

```sh
./gradlew test
```

## Project Structure

The bulk of the minimization algorithms (including the ground truth and baseline) are implemented in the `tool.reducer`
package of the `entrypoint` module.
