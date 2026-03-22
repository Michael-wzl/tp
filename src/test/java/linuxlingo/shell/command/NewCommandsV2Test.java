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
 * Tests for all v2.0 new commands:
 * man, tree, which, whoami, date, alias, unalias, tee, diff.
 */
public class NewCommandsV2Test {
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

    // ─── ManCommand ──────────────────────────────────────────────

    @Nested
    class ManTests {
        private ManCommand command = new ManCommand();

        @Test
        public void man_knownCommand_showsManPage() {
            CommandResult result = command.execute(session, new String[]{"ls"}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("NAME"));
            assertTrue(result.getStdout().contains("SYNOPSIS"));
            assertTrue(result.getStdout().contains("DESCRIPTION"));
            assertTrue(result.getStdout().contains("ls"));
        }

        @Test
        public void man_unknownCommand_returnsError() {
            CommandResult result = command.execute(session, new String[]{"fakecmd"}, null);
            assertFalse(result.isSuccess());
            assertTrue(result.getStderr().contains("no manual entry"));
        }

        @Test
        public void man_noArgs_returnsError() {
            CommandResult result = command.execute(session, new String[]{}, null);
            assertFalse(result.isSuccess());
        }

        @Test
        public void man_usageAndDescription_areValid() {
            assertTrue(command.getUsage().contains("man"));
            assertFalse(command.getDescription().isEmpty());
        }
    }

    // ─── TreeCommand ────────────────────────────────────────────

    @Nested
    class TreeTests {
        private TreeCommand command = new TreeCommand();

        @Test
        public void tree_defaultDir_showsTree() {
            // Create some structure
            vfs.createDirectory("/home/user/project", "/", true);
            vfs.createFile("/home/user/project/README.md", "/");
            session.setWorkingDir("/home/user");

            CommandResult result = command.execute(session, new String[]{}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("project"));
            assertTrue(result.getStdout().contains("README.md"));
            assertTrue(result.getStdout().contains("director"));
            assertTrue(result.getStdout().contains("file"));
        }

        @Test
        public void tree_specificPath_showsTree() {
            vfs.createDirectory("/tmp/a", "/", true);
            vfs.createFile("/tmp/a/b.txt", "/");

            CommandResult result = command.execute(session, new String[]{"/tmp"}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("a"));
            assertTrue(result.getStdout().contains("b.txt"));
        }

        @Test
        public void tree_emptyDir_showsNoItems() {
            vfs.createDirectory("/home/user/empty", "/", true);
            CommandResult result = command.execute(session, new String[]{"/home/user/empty"}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("0 director"));
            assertTrue(result.getStdout().contains("0 file"));
        }

        @Test
        public void tree_invalidPath_returnsError() {
            CommandResult result = command.execute(session, new String[]{"/nonexistent"}, null);
            assertFalse(result.isSuccess());
        }
    }

    // ─── WhichCommand ───────────────────────────────────────────

    @Nested
    class WhichTests {
        private WhichCommand command = new WhichCommand();

        @Test
        public void which_knownCommand_showsPath() {
            CommandResult result = command.execute(session, new String[]{"ls"}, null);
            assertTrue(result.isSuccess());
            assertEquals("/usr/bin/ls", result.getStdout());
        }

        @Test
        public void which_unknownCommand_returnsNotFound() {
            CommandResult result = command.execute(session, new String[]{"fakecmd"}, null);
            assertFalse(result.isSuccess());
            assertTrue(result.getStderr().contains("not found"));
        }

        @Test
        public void which_noArgs_returnsError() {
            CommandResult result = command.execute(session, new String[]{}, null);
            assertFalse(result.isSuccess());
        }
    }

    // ─── WhoamiCommand ──────────────────────────────────────────

    @Nested
    class WhoamiTests {
        private WhoamiCommand command = new WhoamiCommand();

        @Test
        public void whoami_returnsUser() {
            CommandResult result = command.execute(session, new String[]{}, null);
            assertTrue(result.isSuccess());
            assertEquals("user", result.getStdout());
        }

        @Test
        public void whoami_withArgs_returnsError() {
            CommandResult result = command.execute(session, new String[]{"extra"}, null);
            assertFalse(result.isSuccess());
        }
    }

    // ─── DateCommand ────────────────────────────────────────────

    @Nested
    class DateTests {
        private DateCommand command = new DateCommand();

        @Test
        public void date_returnsFormattedDate() {
            CommandResult result = command.execute(session, new String[]{}, null);
            assertTrue(result.isSuccess());
            // Should contain a year number
            assertTrue(result.getStdout().matches(".*\\d{4}.*"));
        }
    }

    // ─── AliasCommand ───────────────────────────────────────────

    @Nested
    class AliasTests {
        private AliasCommand command = new AliasCommand();

        @Test
        public void alias_noArgs_listsEmpty() {
            CommandResult result = command.execute(session, new String[]{}, null);
            assertTrue(result.isSuccess());
            assertEquals("", result.getStdout());
        }

        @Test
        public void alias_setAlias_storesCorrectly() {
            CommandResult result = command.execute(session, new String[]{"ll=ls -la"}, null);
            assertTrue(result.isSuccess());
            assertEquals("ls -la", session.getAliases().get("ll"));
        }

        @Test
        public void alias_setMultipleAliases_listsAll() {
            command.execute(session, new String[]{"ll=ls -la"}, null);
            command.execute(session, new String[]{"c=clear"}, null);

            CommandResult result = command.execute(session, new String[]{}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("ll='ls -la'"));
            assertTrue(result.getStdout().contains("c='clear'"));
        }

        @Test
        public void alias_lookupSingle_showsValue() {
            command.execute(session, new String[]{"ll=ls -la"}, null);
            CommandResult result = command.execute(session, new String[]{"ll"}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("ll='ls -la'"));
        }

        @Test
        public void alias_lookupUnknown_returnsError() {
            CommandResult result = command.execute(session, new String[]{"unknown"}, null);
            assertFalse(result.isSuccess());
            assertTrue(result.getStderr().contains("not found"));
        }

        @Test
        public void alias_withQuotedValue_stripsQuotes() {
            command.execute(session, new String[]{"g='grep -i'"}, null);
            assertEquals("grep -i", session.getAliases().get("g"));
        }
    }

    // ─── UnaliasCommand ─────────────────────────────────────────

    @Nested
    class UnaliasTests {
        private UnaliasCommand command = new UnaliasCommand();
        private AliasCommand aliasCmd = new AliasCommand();

        @Test
        public void unalias_removesExisting() {
            aliasCmd.execute(session, new String[]{"ll=ls -la"}, null);
            CommandResult result = command.execute(session, new String[]{"ll"}, null);
            assertTrue(result.isSuccess());
            assertFalse(session.getAliases().containsKey("ll"));
        }

        @Test
        public void unalias_nonExisting_returnsError() {
            CommandResult result = command.execute(session, new String[]{"nope"}, null);
            assertFalse(result.isSuccess());
            assertTrue(result.getStderr().contains("not found"));
        }

        @Test
        public void unalias_dashA_clearsAll() {
            aliasCmd.execute(session, new String[]{"a=b"}, null);
            aliasCmd.execute(session, new String[]{"c=d"}, null);
            CommandResult result = command.execute(session, new String[]{"-a"}, null);
            assertTrue(result.isSuccess());
            assertTrue(session.getAliases().isEmpty());
        }

        @Test
        public void unalias_noArgs_returnsError() {
            CommandResult result = command.execute(session, new String[]{}, null);
            assertFalse(result.isSuccess());
        }
    }

    // ─── TeeCommand ────────────────────────────────────────────

    @Nested
    class TeeTests {
        private TeeCommand command = new TeeCommand();

        @Test
        public void tee_writesStdinToFileAndStdout() {
            CommandResult result = command.execute(session, new String[]{"output.txt"}, "hello world");
            assertTrue(result.isSuccess());
            assertEquals("hello world", result.getStdout());
            assertEquals("hello world", vfs.readFile("/home/user/output.txt", "/"));
        }

        @Test
        public void tee_appendMode_appendsContent() {
            vfs.createFile("/home/user/log.txt", "/");
            vfs.writeFile("/home/user/log.txt", "/", "line1\n", false);

            CommandResult result = command.execute(session, new String[]{"-a", "log.txt"}, "line2");
            assertTrue(result.isSuccess());
            assertTrue(vfs.readFile("/home/user/log.txt", "/").contains("line1"));
            assertTrue(vfs.readFile("/home/user/log.txt", "/").contains("line2"));
        }

        @Test
        public void tee_noStdin_returnsEmpty() {
            CommandResult result = command.execute(session, new String[]{"out.txt"}, null);
            assertTrue(result.isSuccess());
            assertEquals("", result.getStdout());
        }

        @Test
        public void tee_noArgs_returnsError() {
            CommandResult result = command.execute(session, new String[]{}, "data");
            assertFalse(result.isSuccess());
        }
    }

    // ─── DiffCommand ────────────────────────────────────────────

    @Nested
    class DiffTests {
        private DiffCommand command = new DiffCommand();

        @Test
        public void diff_identicalFiles_returnsEmpty() {
            vfs.createFile("/home/user/a.txt", "/");
            vfs.createFile("/home/user/b.txt", "/");
            vfs.writeFile("/home/user/a.txt", "/", "same content", false);
            vfs.writeFile("/home/user/b.txt", "/", "same content", false);

            CommandResult result = command.execute(session, new String[]{"a.txt", "b.txt"}, null);
            assertTrue(result.isSuccess());
            assertEquals("", result.getStdout());
        }

        @Test
        public void diff_differentFiles_showsDiff() {
            vfs.createFile("/home/user/a.txt", "/");
            vfs.createFile("/home/user/b.txt", "/");
            vfs.writeFile("/home/user/a.txt", "/", "line1\nline2", false);
            vfs.writeFile("/home/user/b.txt", "/", "line1\nline3", false);

            CommandResult result = command.execute(session, new String[]{"a.txt", "b.txt"}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("<") || result.getStdout().contains(">"));
        }

        @Test
        public void diff_missingFile_returnsError() {
            vfs.createFile("/home/user/a.txt", "/");
            vfs.writeFile("/home/user/a.txt", "/", "content", false);

            CommandResult result = command.execute(session, new String[]{"a.txt", "nonexistent.txt"}, null);
            assertFalse(result.isSuccess());
        }

        @Test
        public void diff_wrongArgCount_returnsError() {
            CommandResult result = command.execute(session, new String[]{"only_one.txt"}, null);
            assertFalse(result.isSuccess());
        }
    }
}
