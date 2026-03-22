package linuxlingo.shell;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import linuxlingo.shell.vfs.Directory;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.VirtualFileSystem;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * JLine completer for the LinuxLingo shell.
 *
 * <p>Provides tab-completion for:</p>
 * <ul>
 *   <li>Command names (from {@link CommandRegistry})</li>
 *   <li>VFS file/directory paths (absolute and relative)</li>
 * </ul>
 */
public class ShellCompleter implements Completer {
    private final ShellSession session;

    public ShellCompleter(ShellSession session) {
        this.session = session;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        int cursor = line.cursor();

        // Determine if we're completing the command name (first word) or an argument
        String beforeCursor = buffer.substring(0, cursor);
        String[] tokens = beforeCursor.split("\\s+", -1);

        if (tokens.length <= 1) {
            // Completing the command name
            String prefix = tokens.length == 0 ? "" : tokens[0];
            completeCommandName(prefix, candidates);
        } else {
            // Completing an argument (treat as a path)
            String partial = tokens[tokens.length - 1];
            completePath(partial, candidates);
        }
    }

    /**
     * Add matching command names to the candidate list.
     *
     * @param prefix the partial command name typed so far
     * @param candidates the candidate list to populate
     */
    void completeCommandName(String prefix, List<Candidate> candidates) {
        Set<String> names = session.getRegistry().getAllNames();
        for (String name : names) {
            if (name.startsWith(prefix)) {
                candidates.add(new Candidate(name, name, null,
                        null, null, null, true));
            }
        }
        // Also complete alias names
        for (String alias : session.getAliases().keySet()) {
            if (alias.startsWith(prefix)) {
                candidates.add(new Candidate(alias, alias, "alias",
                        null, null, null, true));
            }
        }
    }

    /**
     * Add matching VFS paths to the candidate list.
     *
     * @param partial the partial path typed so far
     * @param candidates the candidate list to populate
     */
    void completePath(String partial, List<Candidate> candidates) {
        VirtualFileSystem vfs = session.getVfs();
        String workingDir = session.getWorkingDir();

        // Split partial into directory part and name prefix
        String dirPart;
        String namePrefix;
        int lastSlash = partial.lastIndexOf('/');
        if (lastSlash >= 0) {
            dirPart = partial.substring(0, lastSlash);
            if (dirPart.isEmpty()) {
                dirPart = "/";
            }
            namePrefix = partial.substring(lastSlash + 1);
        } else {
            dirPart = workingDir;
            namePrefix = partial;
        }

        // Resolve the directory
        try {
            FileNode dirNode = vfs.resolve(dirPart, workingDir);
            if (!dirNode.isDirectory()) {
                return;
            }
            Directory dir = (Directory) dirNode;
            SortedSet<String> sorted = new TreeSet<>();
            for (FileNode child : dir.getChildren()) {
                if (child.getName().startsWith(namePrefix)) {
                    sorted.add(child.getName());
                }
            }
            for (String name : sorted) {
                FileNode child = dir.getChild(name);
                // Build the full completion value preserving user's prefix
                String value;
                if (lastSlash >= 0) {
                    String base = partial.substring(0, lastSlash + 1);
                    value = base + name;
                } else {
                    value = name;
                }
                // Append '/' for directories
                boolean isDir = child.isDirectory();
                if (isDir) {
                    value = value + "/";
                }
                candidates.add(new Candidate(value, name, null,
                        null, null, null, !isDir));
            }
        } catch (Exception e) {
            // Directory doesn't exist — no completions
        }
    }

    /**
     * Get command name completions for the given prefix.
     * Useful for testing without JLine Candidate objects.
     *
     * @param prefix the partial command name
     * @return sorted set of matching command names
     */
    public SortedSet<String> getCommandCompletions(String prefix) {
        SortedSet<String> results = new TreeSet<>();
        Set<String> names = session.getRegistry().getAllNames();
        for (String name : names) {
            if (name.startsWith(prefix)) {
                results.add(name);
            }
        }
        for (String alias : session.getAliases().keySet()) {
            if (alias.startsWith(prefix)) {
                results.add(alias);
            }
        }
        return results;
    }

    /**
     * Get path completions for the given partial path.
     * Useful for testing without JLine Candidate objects.
     *
     * @param partial the partial path
     * @return sorted set of matching paths
     */
    public SortedSet<String> getPathCompletions(String partial) {
        SortedSet<String> results = new TreeSet<>();
        VirtualFileSystem vfs = session.getVfs();
        String workingDir = session.getWorkingDir();

        String dirPart;
        String namePrefix;
        int lastSlash = partial.lastIndexOf('/');
        if (lastSlash >= 0) {
            dirPart = partial.substring(0, lastSlash);
            if (dirPart.isEmpty()) {
                dirPart = "/";
            }
            namePrefix = partial.substring(lastSlash + 1);
        } else {
            dirPart = workingDir;
            namePrefix = partial;
        }

        try {
            FileNode dirNode = vfs.resolve(dirPart, workingDir);
            if (!dirNode.isDirectory()) {
                return results;
            }
            Directory dir = (Directory) dirNode;
            for (FileNode child : dir.getChildren()) {
                if (child.getName().startsWith(namePrefix)) {
                    String value;
                    if (lastSlash >= 0) {
                        String base = partial.substring(0, lastSlash + 1);
                        value = base + child.getName();
                    } else {
                        value = child.getName();
                    }
                    if (child.isDirectory()) {
                        value = value + "/";
                    }
                    results.add(value);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return results;
    }
}
