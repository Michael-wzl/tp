package linuxlingo.shell.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VirtualFileSystem;

public class FindCommandTest {
    private FindCommand command;
    private ShellSession session;
    private VirtualFileSystem vfs;

    @BeforeEach
    public void setUp() {
        command = new FindCommand();
        vfs = new VirtualFileSystem();
        session = new ShellSession(vfs, null);
        vfs.createFile("/tmp/match.txt", "/");
        vfs.createFile("/tmp/other.log", "/");
    }

    @Test
    public void findCommand_matchingPattern_returnsPaths() {
        String[] args = {"/tmp", "-name", "*.txt"};
        CommandResult result = command.execute(session, args, null);

        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains("/tmp/match.txt"));
        assertFalse(result.getStdout().contains("/tmp/other.log"));
    }

    @Test
    public void findCommand_noNameFlag_listAllFiles() {
        String[] args = {"/tmp"};
        CommandResult result = command.execute(session, args, null);

        // v2.0: find without -name defaults to *, listing all files
        assertTrue(result.isSuccess());
        assertTrue(result.getStdout().contains("/tmp/match.txt"));
        assertTrue(result.getStdout().contains("/tmp/other.log"));
    }

    @Test
    public void cpCommand_wrongArgsOrder_returnsError() {
        String[] args = {"-name", "/tmp", "*.txt"};
        CommandResult result = command.execute(session, args, null);

        assertFalse(result.isSuccess());
    }
}
