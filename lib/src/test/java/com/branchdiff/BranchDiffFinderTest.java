package com.branchdiff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.branchdiff.BranchDiffFinder.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BranchDiffFinderTest {

    private String owner;
    private String repo;
    private String accessToken;
    private String localRepoPath;
    private String branchA;
    private String branchB;
    private String mergeBase;

    @BeforeEach
    public void setUp() {
        owner = "testowner";
        repo = "testrepo";
        accessToken = "dummy_token";
        localRepoPath = System.getProperty("java.io.tmpdir") + File.separator + "dummyRepo";
        branchA = "branchA";
        branchB = "branchB";
        mergeBase = "abc123";

        new File(localRepoPath).mkdirs();
    }

    @Test
    void testGetMergeBaseSuccess() throws BranchDiffFinderException {
        try (MockedStatic<BranchDiffFinder> mockedGit = mockStatic(BranchDiffFinder.class, CALLS_REAL_METHODS)) {

            mockedGit.when(() -> runGitCommand(eq(localRepoPath), eq(Arrays.asList("merge-base", branchB, branchA))))
                    .thenReturn(mergeBase);

            String result = getMergeBase(localRepoPath, branchA, branchB);
            assertEquals(mergeBase, result);
        }
    }

    @Test
    void testFindCommonChangedFiles() throws BranchDiffFinderException {
        try (MockedStatic<BranchDiffFinder> mockedStatic = mockStatic(BranchDiffFinder.class, CALLS_REAL_METHODS)) {

            mockedStatic.when(() -> getMergeBase(localRepoPath, branchA, branchB))
                    .thenReturn(mergeBase);
            mockedStatic.when(() -> getChangedFilesLocal(localRepoPath, mergeBase, branchB))
                    .thenReturn(Arrays.asList("common.txt", "local_only.txt"));
            mockedStatic.when(() -> getChangedFilesRemote(owner, repo, accessToken, mergeBase, branchA))
                    .thenReturn(Arrays.asList("common.txt", "remote_only.txt"));

            List<String> commonFiles = findCommonChangedFiles(owner, repo, accessToken, localRepoPath, branchA, branchB);
            assertEquals(Collections.singletonList("common.txt"), commonFiles);
        }
    }

    @Test
    void testLocalRepoPathInvalid() {
        String invalidPath = "/invalid/path/for/repo";
        BranchDiffFinderException ex = assertThrows(BranchDiffFinderException.class, () -> findCommonChangedFiles(owner, repo, accessToken, invalidPath, branchA, branchB));
        assertTrue(ex.getMessage().contains("Local repository path does not exist"));
    }

    @Test
    void testEmptyMergeBase() {
        try (MockedStatic<BranchDiffFinder> mocked = mockStatic(BranchDiffFinder.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> runGitCommand(eq(localRepoPath), eq(Arrays.asList("merge-base", branchB, branchA))))
                    .thenReturn("");

            BranchDiffFinderException ex = assertThrows(BranchDiffFinderException.class,
                    () -> getMergeBase(localRepoPath, branchA, branchB));
            assertTrue(ex.getMessage().contains("Could not determine merge base"));
        }
    }

    @Test
    void testNoChangedFiles() throws BranchDiffFinderException {
        try (MockedStatic<BranchDiffFinder> mocked = mockStatic(BranchDiffFinder.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> getMergeBase(localRepoPath, branchA, branchB)).thenReturn(mergeBase);
            mocked.when(() -> getChangedFilesLocal(localRepoPath, mergeBase, branchB)).thenReturn(Collections.emptyList());
            mocked.when(() -> getChangedFilesRemote(owner, repo, accessToken, mergeBase, branchA)).thenReturn(Collections.emptyList());

            List<String> result = findCommonChangedFiles(owner, repo, accessToken, localRepoPath, branchA, branchB);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testInvalidBranchName() {
        BranchDiffFinderException ex = assertThrows(BranchDiffFinderException.class,
                () -> getMergeBase(localRepoPath, "", branchB));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid") || ex.getMessage().toLowerCase().contains("empty"));
    }

    @Test
    void testNullInputs() {
        assertThrows(NullPointerException.class,
                () -> findCommonChangedFiles(null, repo, accessToken, localRepoPath, branchA, branchB));
        assertThrows(NullPointerException.class,
                () -> findCommonChangedFiles(owner, null, accessToken, localRepoPath, branchA, branchB));
        assertThrows(NullPointerException.class,
                () -> findCommonChangedFiles(owner, repo, null, localRepoPath, branchA, branchB));
    }
}
