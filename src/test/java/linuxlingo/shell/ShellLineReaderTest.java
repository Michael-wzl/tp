package linuxlingo.shell;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for ShellLineReader: creation, history management.
 * Uses dumb terminal mode for headless test execution.
 */
public class ShellLineReaderTest {
    private VirtualFileSystem vfs;
    private ShellSession session;

    @BeforeEach
    public void setUp() {
        vfs = new VirtualFileSystem();
        session = new ShellSession(vfs, null);
    }

    @Test
    public void createDumb_returnsNonNull() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        assertNotNull(reader);
        reader.close();
    }

    @Test
    public void getJLineReader_returnsNonNull() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        assertNotNull(reader.getJLineReader());
        reader.close();
    }

    @Test
    public void history_initiallyEmpty() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        assertEquals(0, reader.getHistorySize());
        assertTrue(reader.getHistory().isEmpty());
        reader.close();
    }

    @Test
    public void addToHistory_increasesSize() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        reader.addToHistory("ls -la");
        reader.addToHistory("cd /tmp");
        assertEquals(2, reader.getHistorySize());
        reader.close();
    }

    @Test
    public void getHistory_returnsChronologicalOrder() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        reader.addToHistory("first");
        reader.addToHistory("second");
        reader.addToHistory("third");

        List<String> history = reader.getHistory();
        assertEquals(3, history.size());
        assertEquals("first", history.get(0));
        assertEquals("second", history.get(1));
        assertEquals("third", history.get(2));
        reader.close();
    }

    @Test
    public void history_duplicatesPreserved() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        reader.addToHistory("ls");
        reader.addToHistory("ls");
        reader.addToHistory("ls");
        assertEquals(3, reader.getHistorySize());
        reader.close();
    }

    @Test
    public void close_noException() {
        ShellLineReader reader = ShellLineReader.createDumb(session);
        reader.close(); // should not throw
    }
}
