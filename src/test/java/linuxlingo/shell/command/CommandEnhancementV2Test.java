package linuxlingo.shell.command;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import linuxlingo.cli.Ui;
import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for all v2.0 command enhancements:
 * grep -E, echo -n, cat -n, touch multi, mkdir multi,
 * find -type/-size, chmod -R, ls -R, head/tail multi-file,
 * sort -u, uniq -d, wc multi-file.
 */
public class CommandEnhancementV2Test {
    private ShellSession session;
    private VirtualFileSystem vfs;

    @BeforeEach
    public void setUp() {
        vfs = new VirtualFileSystem();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outStream);
        Ui ui = new Ui(new ByteArrayInputStream(new byte[0]), out);
        session = new ShellSession(vfs, ui);
        session.setWorkingDir("/home/user");
    }

    // ─── grep -E (regex) ────────────────────────────────────────

    @Nested
    class GrepEnhancements {
        private GrepCommand command = new GrepCommand();

        @BeforeEach
        public void setUpFile() {
            vfs.createFile("/home/user/data.txt", "/");
            vfs.writeFile("/home/user/data.txt", "/",
                    "apple\nbanana\ncherry\napricot\nblueberry", false);
        }

        @Test
        public void grep_regexFlag_matchesPattern() {
            String[] args = {"-E", "^a", "data.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("apple"));
            assertTrue(result.getStdout().contains("apricot"));
            assertFalse(result.getStdout().contains("banana"));
        }

        @Test
        public void grep_regexWithPipe_matchesAlternation() {
            String[] args = {"-E", "apple|cherry", "data.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("apple"));
            assertTrue(result.getStdout().contains("cherry"));
            assertFalse(result.getStdout().contains("banana"));
        }

        @Test
        public void grep_regexCaseInsensitive_combined() {
            vfs.writeFile("/home/user/data.txt", "/", "Apple\nbanana\nAPPLE", false);
            String[] args = {"-E", "-i", "apple", "data.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("Apple"));
            assertTrue(result.getStdout().contains("APPLE"));
        }
    }

    // ─── cat -n (line numbers) ──────────────────────────────────

    @Nested
    class CatEnhancements {
        private CatCommand command = new CatCommand();

        @BeforeEach
        public void setUpFile() {
            vfs.createFile("/home/user/test.txt", "/");
            vfs.writeFile("/home/user/test.txt", "/", "line1\nline2\nline3", false);
        }

        @Test
        public void cat_lineNumberFlag_showsNumbers() {
            String[] args = {"-n", "test.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("1"));
            assertTrue(result.getStdout().contains("2"));
            assertTrue(result.getStdout().contains("3"));
            assertTrue(result.getStdout().contains("line1"));
        }

        @Test
        public void cat_withoutFlag_noNumbers() {
            String[] args = {"test.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertEquals("line1\nline2\nline3", result.getStdout());
        }
    }

    // ─── touch multiple files ───────────────────────────────────

    @Nested
    class TouchEnhancements {
        private TouchCommand command = new TouchCommand();

        @Test
        public void touch_multipleFiles_createsAll() {
            String[] args = {"a.txt", "b.txt", "c.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(vfs.exists("/home/user/a.txt", "/"));
            assertTrue(vfs.exists("/home/user/b.txt", "/"));
            assertTrue(vfs.exists("/home/user/c.txt", "/"));
        }
    }

    // ─── mkdir multiple directories ─────────────────────────────

    @Nested
    class MkdirEnhancements {
        private MkdirCommand command = new MkdirCommand();

        @Test
        public void mkdir_multipleDirectories_createsAll() {
            String[] args = {"dir1", "dir2", "dir3"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(vfs.exists("/home/user/dir1", "/"));
            assertTrue(vfs.exists("/home/user/dir2", "/"));
            assertTrue(vfs.exists("/home/user/dir3", "/"));
        }

        @Test
        public void mkdir_multipleWithParentFlag_createsNested() {
            String[] args = {"-p", "a/b/c", "d/e"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(vfs.exists("/home/user/a/b/c", "/"));
            assertTrue(vfs.exists("/home/user/d/e", "/"));
        }
    }

    // ─── find -type, -size ──────────────────────────────────────

    @Nested
    class FindEnhancements {
        private FindCommand command = new FindCommand();

        @BeforeEach
        public void setUpFiles() {
            vfs.createDirectory("/home/user/project", "/", true);
            vfs.createFile("/home/user/project/app.java", "/");
            vfs.writeFile("/home/user/project/app.java", "/", "hello world long content here yes", false);
            vfs.createDirectory("/home/user/project/src", "/", true);
        }

        @Test
        public void find_typeFile_onlyFiles() {
            String[] args = {"/home/user/project", "-type", "f"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("app.java"));
            assertFalse(result.getStdout().contains("src"));
        }

        @Test
        public void find_typeDir_onlyDirs() {
            String[] args = {"/home/user/project", "-type", "d"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("src"));
            // app.java should not appear (it's a file)
        }

        @Test
        public void find_withNameAndType_combined() {
            String[] args = {"/home/user/project", "-name", "*.java", "-type", "f"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("app.java"));
        }
    }

    // ─── chmod -R (recursive) ───────────────────────────────────

    @Nested
    class ChmodEnhancements {
        private ChmodCommand command = new ChmodCommand();

        @Test
        public void chmod_recursiveFlag_appliesRecursively() {
            vfs.createDirectory("/home/user/project", "/", true);
            vfs.createFile("/home/user/project/file.txt", "/");

            String[] args = {"-R", "777", "/home/user/project"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
        }
    }

    // ─── ls -R (recursive listing) ─────────────────────────────

    @Nested
    class LsEnhancements {
        private LsCommand command = new LsCommand();

        @Test
        public void ls_recursiveFlag_listsSubdirectories() {
            vfs.createDirectory("/home/user/project", "/", true);
            vfs.createDirectory("/home/user/project/src", "/", true);
            vfs.createFile("/home/user/project/src/Main.java", "/");

            String[] args = {"-R", "/home/user/project"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("src"));
            assertTrue(result.getStdout().contains("Main.java"));
        }
    }

    // ─── head/tail multi-file ───────────────────────────────────

    @Nested
    class HeadTailEnhancements {
        private HeadCommand headCommand = new HeadCommand();
        private TailCommand tailCommand = new TailCommand();

        @BeforeEach
        public void setUpFiles() {
            vfs.createFile("/home/user/a.txt", "/");
            vfs.createFile("/home/user/b.txt", "/");
            vfs.writeFile("/home/user/a.txt", "/", "a1\na2\na3", false);
            vfs.writeFile("/home/user/b.txt", "/", "b1\nb2\nb3", false);
        }

        @Test
        public void head_multipleFiles_showsHeaders() {
            String[] args = {"a.txt", "b.txt"};
            CommandResult result = headCommand.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("==> a.txt <=="));
            assertTrue(result.getStdout().contains("==> b.txt <=="));
            assertTrue(result.getStdout().contains("a1"));
            assertTrue(result.getStdout().contains("b1"));
        }

        @Test
        public void tail_multipleFiles_showsHeaders() {
            String[] args = {"a.txt", "b.txt"};
            CommandResult result = tailCommand.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("==> a.txt <=="));
            assertTrue(result.getStdout().contains("==> b.txt <=="));
        }

        @Test
        public void head_singleFile_noHeaders() {
            String[] args = {"a.txt"};
            CommandResult result = headCommand.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertFalse(result.getStdout().contains("==>"));
        }
    }

    // ─── sort -u (unique) ───────────────────────────────────────

    @Nested
    class SortEnhancements {
        private SortCommand command = new SortCommand();

        @Test
        public void sort_uniqueFlag_removesDuplicates() {
            vfs.createFile("/home/user/data.txt", "/");
            vfs.writeFile("/home/user/data.txt", "/", "banana\napple\nbanana\ncherry\napple", false);

            String[] args = {"-u", "data.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            String output = result.getStdout();
            assertEquals("apple\nbanana\ncherry", output);
        }
    }

    // ─── uniq -d (duplicates only) ─────────────────────────────

    @Nested
    class UniqEnhancements {
        private UniqCommand command = new UniqCommand();

        @Test
        public void uniq_duplicatesOnlyFlag_showsDuplicates() {
            vfs.createFile("/home/user/data.txt", "/");
            vfs.writeFile("/home/user/data.txt", "/", "apple\napple\nbanana\ncherry\ncherry", false);

            String[] args = {"-d", "data.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("apple"));
            assertTrue(result.getStdout().contains("cherry"));
            assertFalse(result.getStdout().contains("banana"));
        }
    }

    // ─── wc multi-file ─────────────────────────────────────────

    @Nested
    class WcEnhancements {
        private WcCommand command = new WcCommand();

        @Test
        public void wc_multipleFiles_showsTotalLine() {
            vfs.createFile("/home/user/a.txt", "/");
            vfs.createFile("/home/user/b.txt", "/");
            vfs.writeFile("/home/user/a.txt", "/", "hello world", false);
            vfs.writeFile("/home/user/b.txt", "/", "foo bar baz\nline two", false);

            String[] args = {"a.txt", "b.txt"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("total"));
        }
    }

    // ─── echo -n (no trailing newline) ─────────────────────────

    @Nested
    class EchoEnhancements {
        private EchoCommand command = new EchoCommand();

        @Test
        public void echo_dashN_parsesFlag() {
            String[] args = {"-n", "hello"};
            CommandResult result = command.execute(session, args, null);
            assertTrue(result.isSuccess());
            assertEquals("hello", result.getStdout());
        }
    }
}
