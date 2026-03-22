package linuxlingo.shell.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for the history command and ShellSession command history tracking.
 */
public class HistoryCommandTest {
    private HistoryCommand command;
    private ShellSession session;

    @BeforeEach
    public void setUp() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        session = new ShellSession(vfs, null);
        command = new HistoryCommand();
    }

    @Nested
    class BasicHistory {
        @Test
        public void emptyHistory_returnsEmpty() {
            CommandResult result = command.execute(session,
                    new String[]{}, null);
            assertTrue(result.isSuccess());
            assertEquals("", result.getStdout());
        }

        @Test
        public void singleEntry_displaysNumbered() {
            session.getCommandHistory().add("ls -la");
            CommandResult result = command.execute(session,
                    new String[]{}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("1"));
            assertTrue(result.getStdout().contains("ls -la"));
        }

        @Test
        public void multipleEntries_displaysAllNumbered() {
            session.getCommandHistory().add("cd /tmp");
            session.getCommandHistory().add("ls");
            session.getCommandHistory().add("cat file.txt");

            CommandResult result = command.execute(session,
                    new String[]{}, null);
            assertTrue(result.isSuccess());
            String output = result.getStdout();
            assertTrue(output.contains("1"));
            assertTrue(output.contains("cd /tmp"));
            assertTrue(output.contains("2"));
            assertTrue(output.contains("ls"));
            assertTrue(output.contains("3"));
            assertTrue(output.contains("cat file.txt"));
        }

        @Test
        public void historyFormat_hasLineNumbers() {
            session.getCommandHistory().add("pwd");
            CommandResult result = command.execute(session,
                    new String[]{}, null);
            // Format: "    1  pwd"
            assertTrue(result.getStdout().matches("\\s+1\\s+pwd"));
        }
    }

    @Nested
    class HistoryLimit {
        @Test
        public void limitN_showsLastNEntries() {
            session.getCommandHistory().add("cmd1");
            session.getCommandHistory().add("cmd2");
            session.getCommandHistory().add("cmd3");
            session.getCommandHistory().add("cmd4");
            session.getCommandHistory().add("cmd5");

            CommandResult result = command.execute(session,
                    new String[]{"3"}, null);
            assertTrue(result.isSuccess());
            String output = result.getStdout();
            assertFalse(output.contains("cmd1"));
            assertFalse(output.contains("cmd2"));
            assertTrue(output.contains("cmd3"));
            assertTrue(output.contains("cmd4"));
            assertTrue(output.contains("cmd5"));
        }

        @Test
        public void limitZero_showsNothing() {
            session.getCommandHistory().add("cmd1");
            CommandResult result = command.execute(session,
                    new String[]{"0"}, null);
            assertTrue(result.isSuccess());
            assertEquals("", result.getStdout());
        }

        @Test
        public void limitLargerThanHistory_showsAll() {
            session.getCommandHistory().add("cmd1");
            session.getCommandHistory().add("cmd2");

            CommandResult result = command.execute(session,
                    new String[]{"100"}, null);
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("cmd1"));
            assertTrue(result.getStdout().contains("cmd2"));
        }

        @Test
        public void negativeLimit_returnsError() {
            CommandResult result = command.execute(session,
                    new String[]{"-5"}, null);
            assertFalse(result.isSuccess());
            assertTrue(result.getStderr().contains("invalid option"));
        }

        @Test
        public void nonNumericArg_returnsError() {
            CommandResult result = command.execute(session,
                    new String[]{"abc"}, null);
            assertFalse(result.isSuccess());
            assertTrue(result.getStderr().contains("numeric argument"));
        }
    }

    @Nested
    class HistoryClear {
        @Test
        public void clearFlag_emptiesHistory() {
            session.getCommandHistory().add("cmd1");
            session.getCommandHistory().add("cmd2");

            CommandResult result = command.execute(session,
                    new String[]{"-c"}, null);
            assertTrue(result.isSuccess());
            assertEquals(0, session.getCommandHistory().size());
        }

        @Test
        public void clearEmpty_succeeds() {
            CommandResult result = command.execute(session,
                    new String[]{"-c"}, null);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    class CommandMetadata {
        @Test
        public void getUsage_notEmpty() {
            assertFalse(command.getUsage().isEmpty());
        }

        @Test
        public void getDescription_notEmpty() {
            assertFalse(command.getDescription().isEmpty());
        }
    }

    @Nested
    class SessionHistoryTracking {
        @Test
        public void executeOnce_doesNotAddToHistory() {
            // executeOnce is for programmatic use, should NOT track
            session.executeOnce("ls");
            assertEquals(0, session.getCommandHistory().size());
        }

        @Test
        public void commandHistory_initiallyEmpty() {
            assertTrue(session.getCommandHistory().isEmpty());
        }

        @Test
        public void commandHistory_addAndRetrieve() {
            session.getCommandHistory().add("ls -la");
            session.getCommandHistory().add("pwd");
            assertEquals(2, session.getCommandHistory().size());
            assertEquals("ls -la", session.getCommandHistory().get(0));
            assertEquals("pwd", session.getCommandHistory().get(1));
        }
    }
}
