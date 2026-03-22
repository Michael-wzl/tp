package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

/**
 * Removes adjacent duplicate lines.
 * Syntax: uniq [-c] &lt;file&gt;
 *
 * <p><b>Owner: C</b></p>
 */
public class UniqCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean countOccurrences = false;
        boolean duplicatesOnly = false;
        String file = null;

        for (String arg : args) {
            if (arg.equals("-c")) {
                countOccurrences = true;
            } else if (arg.equals("-d")) {
                duplicatesOnly = true;
            } else if (!arg.startsWith("-") && file == null) {
                file = arg;
            }
        }

        String content;
        if (file != null) {
            try {
                content = session.getVfs().readFile(file, session.getWorkingDir());
            } catch (VfsException e) {
                return CommandResult.error("uniq: " + e.getMessage());
            }
        } else if (stdin != null) {
            content = stdin;
        } else {
            return CommandResult.error("uniq: missing file operand");
        }

        if (content.isEmpty()) {
            return CommandResult.success("");
        }

        String[] linesArray = content.split("\n", -1);
        List<String> results = new ArrayList<>();

        if (linesArray.length > 0) {
            String currentLine = linesArray[0];
            int count = 1;

            for (int i = 1; i < linesArray.length; i++) {
                if (linesArray[i].equals(currentLine)) {
                    count++;
                } else {
                    addUniqResult(results, currentLine, count, countOccurrences, duplicatesOnly);
                    currentLine = linesArray[i];
                    count = 1;
                }
            }

            addUniqResult(results, currentLine, count, countOccurrences, duplicatesOnly);
        }

        return CommandResult.success(String.join("\n", results));
    }

    private void addUniqResult(List<String> results, String line, int count,
                               boolean countOccurrences, boolean duplicatesOnly) {
        if (duplicatesOnly && count < 2) {
            return;
        }
        if (countOccurrences) {
            results.add(String.format("%7d %s", count, line));
        } else {
            results.add(line);
        }
    }

    @Override
    public String getUsage() {
        return "uniq [-c] [-d] <file>";
    }

    @Override
    public String getDescription() {
        return "Remove adjacent duplicate lines";
    }
}
