package com.branchdiff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A library to find common changed files between a remote branch (branchA) and a local branch (branchB)
 * since their merge base commit.
 */
public class BranchDiffFinder {

    /**
     * Custom exception to represent errors in branch diff operations.
     */
    public static class BranchDiffFinderException extends Exception {
        public BranchDiffFinderException(String message) {
            super(message);
        }
        public BranchDiffFinderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Runs a git command in the given repository and returns its standard output.
     *
     * @param localRepoPath The local repository path.
     * @param args          The git arguments.
     * @return The output from the git command.
     * @throws BranchDiffFinderException if the command fails.
     */
    public static String runGitCommand(String localRepoPath, List<String> args)
            throws BranchDiffFinderException {
        ProcessBuilder builder = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        builder.command(command);
        builder.directory(new File(localRepoPath));
        try {
            Process process = builder.start();
            // Capture stdout
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String output = reader.lines().collect(Collectors.joining("\n"));

            // Capture stderr
            BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            String errorOutput = errReader.lines().collect(Collectors.joining("\n"));

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BranchDiffFinderException("Git command failed: " + String.join(" ", args)
                        + "\nError: " + errorOutput);
            }
            return output.trim();
        } catch (IOException | InterruptedException e) {
            throw new BranchDiffFinderException("Failed to run git command: " + String.join(" ", args), e);
        }
    }

    /**
     * Determines the merge base commit between branchA and branchB.
     *
     * @param localRepoPath Local repository path.
     * @param branchA       The remote branch name.
     * @param branchB       The local branch name.
     * @return The merge base commit hash.
     * @throws BranchDiffFinderException if the merge base cannot be determined.
     */
    public static String getMergeBase(String localRepoPath, String branchA, String branchB)
            throws BranchDiffFinderException {
        if (branchA == null || branchA.isBlank() || branchB == null || branchB.isBlank()) {
            throw new BranchDiffFinderException("Invalid branch name or both branch names are empty");
        }
        // order is branchB then branchA to match the sample approach.
        String mergeBase = runGitCommand(localRepoPath, Arrays.asList("merge-base", branchB, branchA));
        if (mergeBase.isEmpty()) {
            throw new BranchDiffFinderException("Could not determine merge base between " + branchA + " and " + branchB);
        }
        return mergeBase;
    }

    /**
     * Returns a list of file paths with net changes in branchB relative to the merge base.
     * Files that were modified and then rolled back are ignored.
     *
     * @param localRepoPath Local repository path.
     * @param mergeBase     The merge base commit.
     * @param branchB       The local branch.
     * @return List of changed file paths.
     * @throws BranchDiffFinderException if git command fails.
     */
    public static List<String> getChangedFilesLocal(String localRepoPath, String mergeBase, String branchB)
            throws BranchDiffFinderException {
        String output = runGitCommand(localRepoPath, Arrays.asList("diff", "--name-only", mergeBase, branchB));
        return Arrays.stream(output.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of file paths with net changes in remote branchA relative to the merge base.
     * Uses GitHub's compare API.
     *
     * @param owner       GitHub repository owner.
     * @param repo        GitHub repository name.
     * @param accessToken GitHub access token.
     * @param mergeBase   The merge base commit.
     * @param branchA     The remote branch.
     * @return List of changed file paths.
     * @throws BranchDiffFinderException if the HTTP request fails or returns an error.
     */
    public static List<String> getChangedFilesRemote(String owner, String repo, String accessToken,
                                                     String mergeBase, String branchA)
            throws BranchDiffFinderException {
        String url = String.format("https://api.github.com/repos/%s/%s/compare/%s...%s", owner, repo, mergeBase, branchA);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "token " + accessToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new BranchDiffFinderException("GitHub API request failed with status " + response.statusCode()
                            + ": " + response.body());
                }
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                JsonNode filesNode = root.path("files");
                List<String> files = new ArrayList<>();
                if (filesNode.isArray()) {
                    for (JsonNode fileNode : filesNode) {
                        String status = fileNode.path("status").asText();
                        if (!"unchanged".equals(status)) {
                            String filename = fileNode.path("filename").asText();
                            if (!filename.isEmpty()) {
                                files.add(filename);
                            }
                        }
                    }
                }
                return files;
            } catch (IOException | InterruptedException e) {
                throw new BranchDiffFinderException("Error during GitHub API call", e);
            }
        }
    }

    /**
     * Finds files (by path) that have been modified independently in both the remote branchA and the local branchB
     * since their merge base commit.
     *
     * @param owner         GitHub repository owner.
     * @param repo          GitHub repository name.
     * @param accessToken   GitHub access token.
     * @param localRepoPath Local repository path.
     * @param branchA       Remote branch name.
     * @param branchB       Local branch name.
     * @return Sorted list of file paths that have changed in both branches.
     * @throws BranchDiffFinderException if any operation fails.
     */
    public static List<String> findCommonChangedFiles(String owner, String repo, String accessToken,
                                                      String localRepoPath, String branchA, String branchB)
            throws BranchDiffFinderException {
        if (owner == null || repo == null || accessToken == null ||
                localRepoPath == null || branchA == null || branchB == null) {
            throw new NullPointerException("One or more required parameters are null");
        }

        File repoDir = new File(localRepoPath);
        if (!repoDir.isDirectory()) {
            throw new BranchDiffFinderException("Local repository path does not exist: " + localRepoPath);
        }

        //merge base commit.
        String mergeBase = getMergeBase(localRepoPath, branchA, branchB);

        //list of files changed locally in branchB.
        Set<String> changedLocal = new HashSet<>(getChangedFilesLocal(localRepoPath, mergeBase, branchB));

        //list of files changed remotely in branchA.
        Set<String> changedRemote = new HashSet<>(getChangedFilesRemote(owner, repo, accessToken, mergeBase, branchA));

        //intersection and return sorted list.
        changedLocal.retainAll(changedRemote);
        List<String> commonFiles = new ArrayList<>(changedLocal);
        Collections.sort(commonFiles);
        return commonFiles;
    }

    /**
     * Main method for debugging and manual testing.
     */
    public static void main(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: java BranchDiffFinder <owner> <repo> <accessToken> <localRepoPath> <branchA> <branchB>");
            System.exit(1);
        }
        String owner = args[0];
        String repo = args[1];
        String accessToken = args[2];
        String localRepoPath = args[3];
        String branchA = args[4];
        String branchB = args[5];

        try {
            List<String> commonFiles = findCommonChangedFiles(owner, repo, accessToken, localRepoPath, branchA, branchB);
            System.out.println("Common changed files:");
            commonFiles.forEach(System.out::println);
        } catch (BranchDiffFinderException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
