# Managing Infrastructure Code

A Java library to find files that have been modified independently in both a remote and a local Git branch since their merge base commit. This tool compares changes between a remote branch (branchA) and a local branch (branchB) and returns the list of files modified in both branches.

## Features

- Determines the merge base commit between two branches.
- Retrieves modified files in the local branch (branchB) since the merge base.
- Uses GitHub's API to retrieve modified files in the remote branch (branchA).
- Finds files modified in both branches since the merge base commit.
- Returns a sorted list of the common modified files.

## Requirements

- Java 11 or later
- Git installed locally
- GitHub access token (for API access)

## Setup

### Clone the Repository

```bash
git clone https://github.com/your-username/BranchDiffFinder.git
cd BranchDiffFinder
```

## Dependencies

This project uses Jackson for JSON parsing. Ensure you have the necessary dependencies in your build file.
```kts
    implementation(kotlin("stdlib"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // HTTP client for API requests
    implementation("org.apache.httpcomponents:httpclient:4.5.13")

    // JUnit for testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")

    // Mockito for mocking
    testImplementation("org.mockito:mockito-core:4.0.0")
    testImplementation("org.mockito:mockito-junit-jupiter:4.0.0")

    testImplementation("org.mockito:mockito-inline:4.0.0")
```

## Usage

### Command-Line Usage

To run the program from the command line, use the following format:

```bash
java -cp target/BranchDiffFinder.jar com.branchdiff.BranchDiffFinder <owner> <repo> <accessToken> <localRepoPath> <branchA> <branchB>
```

#### Parameters:
- `<owner>`: The owner of the GitHub repository.
- `<repo>`: The name of the GitHub repository.
- `<accessToken>`: GitHub personal access token.
- `<localRepoPath>`: Path to the local Git repository.
- `<branchA>`: The name of the remote branch.
- `<branchB>`: The name of the local branch.

Example:

```bash
java -cp target/BranchDiffFinder.jar com.branchdiff.BranchDiffFinder testowner testrepo <your_token> /path/to/repo branchA branchB
```
The program will output the common files modified in both branches since their merge base.

### Example Output:

If there are common changes in both branches:
```yaml
Common changed files: chaned_file_name.extension
```
If no common changes exist, the output will be:
```yaml
Common changed files
```
### Error Handling

If an error occurs, such as an invalid repository path or an API issue, the program will display an error message:
```plaintext
Error: <error_message>
```

## Tests

The project includes unit tests using JUnit 5. The tests verify that the program works as expected and handles edge cases like invalid paths and missing files. To run the tests:

1. Ensure you have JUnit 5 dependencies in your project.
2. Run the tests using your IDE or through the command line:

```bash
mvn test
```
