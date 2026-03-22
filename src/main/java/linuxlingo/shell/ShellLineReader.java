package linuxlingo.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Wrapper around JLine's {@link LineReader} that provides:
 * <ul>
 *   <li>Tab completion via {@link ShellCompleter}</li>
 *   <li>Persistent command history (up/down arrow keys)</li>
 *   <li>Graceful fallback when JLine terminal cannot be created</li>
 * </ul>
 *
 * <p>Create via {@link #create(ShellSession)} for interactive use, or
 * via {@link #createDumb(ShellSession)} for testing/non-interactive use.</p>
 */
public class ShellLineReader {
    private static final Logger LOGGER = Logger.getLogger(ShellLineReader.class.getName());

    private final LineReader lineReader;
    private final Terminal terminal;
    private final DefaultHistory history;

    ShellLineReader(LineReader lineReader, Terminal terminal, DefaultHistory history) {
        this.lineReader = lineReader;
        this.terminal = terminal;
        this.history = history;
    }

    /**
     * Create an interactive ShellLineReader with system terminal.
     * Falls back to a dumb terminal if the system terminal is unavailable.
     *
     * @param session the shell session to provide completions for
     * @return a new ShellLineReader
     */
    public static ShellLineReader create(ShellSession session) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            return buildReader(session, terminal);
        } catch (IOException e) {
            LOGGER.warning("Cannot create system terminal, falling back to dumb: "
                    + e.getMessage());
            return createDumb(session);
        }
    }

    /**
     * Create a ShellLineReader with a dumb terminal (for testing or non-interactive use).
     *
     * @param session the shell session to provide completions for
     * @return a new ShellLineReader with dumb terminal
     */
    public static ShellLineReader createDumb(ShellSession session) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .dumb(true)
                    .build();
            return buildReader(session, terminal);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dumb terminal", e);
        }
    }

    private static ShellLineReader buildReader(ShellSession session, Terminal terminal) {
        DefaultHistory history = new DefaultHistory();
        ShellCompleter completer = new ShellCompleter(session);
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .history(history)
                .build();

        // Disable audible bell, use visible bell instead
        reader.setOpt(LineReader.Option.AUTO_FRESH_LINE);

        return new ShellLineReader(reader, terminal, history);
    }

    /**
     * Read a line of input with the given prompt.
     *
     * @param prompt the prompt string to display
     * @return the line read, or {@code null} on EOF (Ctrl-D)
     */
    public String readLine(String prompt) {
        try {
            return lineReader.readLine(prompt);
        } catch (UserInterruptException e) {
            // Ctrl-C → return null to signal interruption
            return null;
        } catch (EndOfFileException e) {
            // Ctrl-D → return null to signal EOF
            return null;
        }
    }

    /**
     * Get the command history as an unmodifiable list of strings.
     * Entries are in chronological order (oldest first).
     *
     * @return list of history entries
     */
    public List<String> getHistory() {
        List<String> entries = new ArrayList<>();
        history.forEach(entry -> entries.add(entry.line()));
        return Collections.unmodifiableList(entries);
    }

    /**
     * Get the number of entries in the history.
     *
     * @return history size
     */
    public int getHistorySize() {
        return history.size();
    }

    /**
     * Add a line to the history manually (useful for testing).
     *
     * @param line the command line to add
     */
    public void addToHistory(String line) {
        history.add(line);
    }

    /**
     * Get the underlying JLine LineReader (for advanced usage).
     *
     * @return the JLine LineReader
     */
    public LineReader getJLineReader() {
        return lineReader;
    }

    /**
     * Close the terminal when done.
     */
    public void close() {
        try {
            terminal.close();
        } catch (IOException e) {
            LOGGER.warning("Error closing terminal: " + e.getMessage());
        }
    }
}
