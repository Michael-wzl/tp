package linuxlingo.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.jline.reader.Candidate;

import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for ShellCompleter: command name completion, path completion,
 * alias completion, and edge cases.
 */
public class ShellCompleterTest {
    private VirtualFileSystem vfs;
    private ShellSession session;
    private ShellCompleter completer;

    @BeforeEach
    public void setUp() {
        vfs = new VirtualFileSystem();
        session = new ShellSession(vfs, null);
        session.setWorkingDir("/home/user");
        completer = new ShellCompleter(session);
    }

    // ─── Command name completion ────────────────────────────────

    @Nested
    class CommandNameCompletion {
        @Test
        public void emptyPrefix_returnsAllCommands() {
            SortedSet<String> results = completer.getCommandCompletions("");
            assertFalse(results.isEmpty());
            assertTrue(results.contains("ls"));
            assertTrue(results.contains("cd"));
            assertTrue(results.contains("cat"));
            assertTrue(results.contains("history"));
        }

        @Test
        public void prefixL_returnsLCommands() {
            SortedSet<String> results = completer.getCommandCompletions("l");
            assertTrue(results.contains("ls"));
            assertTrue(results.contains("load"));
            assertFalse(results.contains("cd"));
            assertFalse(results.contains("cat"));
        }

        @Test
        public void prefixCa_returnsCatOnly() {
            SortedSet<String> results = completer.getCommandCompletions("ca");
            assertTrue(results.contains("cat"));
            assertFalse(results.contains("cd"));
        }

        @Test
        public void exactMatch_returnsCommand() {
            SortedSet<String> results = completer.getCommandCompletions("ls");
            assertTrue(results.contains("ls"));
        }

        @Test
        public void noMatch_returnsEmpty() {
            SortedSet<String> results = completer.getCommandCompletions("zzz");
            assertTrue(results.isEmpty());
        }

        @Test
        public void aliasCompletion_includesAliases() {
            session.getAliases().put("ll", "ls -la");
            SortedSet<String> results = completer.getCommandCompletions("ll");
            assertTrue(results.contains("ll"));
        }

        @Test
        public void aliasAndCommand_bothReturned() {
            session.getAliases().put("lx", "ls -la");
            SortedSet<String> results = completer.getCommandCompletions("l");
            assertTrue(results.contains("ls"));
            assertTrue(results.contains("load"));
            assertTrue(results.contains("lx"));
        }
    }

    // ─── Path completion ────────────────────────────────────────

    @Nested
    class PathCompletion {
        @Test
        public void absolutePath_rootChildren() {
            SortedSet<String> results = completer.getPathCompletions("/");
            assertTrue(results.contains("/home/"));
            assertTrue(results.contains("/tmp/"));
            assertTrue(results.contains("/etc/"));
        }

        @Test
        public void absolutePath_withPrefix() {
            SortedSet<String> results = completer.getPathCompletions("/h");
            assertTrue(results.contains("/home/"));
            assertEquals(1, results.size());
        }

        @Test
        public void absolutePath_nestedDir() {
            SortedSet<String> results = completer.getPathCompletions("/home/");
            assertTrue(results.contains("/home/user/"));
        }

        @Test
        public void relativePath_fromWorkingDir() {
            // Working dir is /home/user, create a file there
            vfs.createFile("/home/user/notes.txt", "/");
            vfs.createDirectory("/home/user/docs", "/", false);

            SortedSet<String> results = completer.getPathCompletions("n");
            assertTrue(results.contains("notes.txt"));
        }

        @Test
        public void relativePath_directoryAppendSlash() {
            vfs.createDirectory("/home/user/docs", "/", false);
            SortedSet<String> results = completer.getPathCompletions("d");
            assertTrue(results.contains("docs/"));
        }

        @Test
        public void relativePath_fileNoSlash() {
            vfs.createFile("/home/user/readme.md", "/");
            SortedSet<String> results = completer.getPathCompletions("r");
            assertTrue(results.contains("readme.md"));
            assertFalse(results.contains("readme.md/"));
        }

        @Test
        public void nonexistentDir_returnsEmpty() {
            SortedSet<String> results =
                    completer.getPathCompletions("/nonexistent/");
            assertTrue(results.isEmpty());
        }

        @Test
        public void emptyPartial_listsWorkingDirChildren() {
            vfs.createFile("/home/user/file1.txt", "/");
            vfs.createFile("/home/user/file2.txt", "/");

            SortedSet<String> results = completer.getPathCompletions("");
            assertTrue(results.contains("file1.txt"));
            assertTrue(results.contains("file2.txt"));
        }

        @Test
        public void multipleMatches_returnsAll() {
            vfs.createFile("/home/user/test1.txt", "/");
            vfs.createFile("/home/user/test2.txt", "/");
            vfs.createDirectory("/home/user/tests", "/", false);

            SortedSet<String> results = completer.getPathCompletions("test");
            assertEquals(3, results.size());
            assertTrue(results.contains("test1.txt"));
            assertTrue(results.contains("test2.txt"));
            assertTrue(results.contains("tests/"));
        }
    }

    // ─── JLine Candidate integration ────────────────────────────

    @Nested
    class CandidateIntegration {
        @Test
        public void completeCommandName_addsCandidates() {
            List<Candidate> candidates = new ArrayList<>();
            completer.completeCommandName("gr", candidates);
            assertTrue(candidates.stream()
                    .anyMatch(c -> c.value().equals("grep")));
        }

        @Test
        public void completePath_addsCandidates() {
            vfs.createFile("/home/user/data.txt", "/");
            List<Candidate> candidates = new ArrayList<>();
            completer.completePath("da", candidates);
            assertTrue(candidates.stream()
                    .anyMatch(c -> c.value().equals("data.txt")));
        }

        @Test
        public void completePath_dirCandidateNotComplete() {
            // Directory candidates should have complete=false
            // so JLine doesn't add a space after them
            List<Candidate> candidates = new ArrayList<>();
            completer.completePath("/h", candidates);
            Candidate homeCand = candidates.stream()
                    .filter(c -> c.value().equals("/home/"))
                    .findFirst()
                    .orElse(null);
            assertTrue(homeCand != null);
            assertFalse(homeCand.complete());
        }

        @Test
        public void completePath_fileCandidateIsComplete() {
            vfs.createFile("/home/user/file.txt", "/");
            List<Candidate> candidates = new ArrayList<>();
            completer.completePath("fi", candidates);
            Candidate fileCand = candidates.stream()
                    .filter(c -> c.value().equals("file.txt"))
                    .findFirst()
                    .orElse(null);
            assertTrue(fileCand != null);
            assertTrue(fileCand.complete());
        }
    }
}
