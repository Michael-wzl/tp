package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

/**
 * Counts lines, words, and/or characters in file(s).
 * Syntax: wc [-l] [-w] [-c] &lt;file&gt; [file2...]
 *
 * <p><b>Owner: C</b></p>
 */
public class WcCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean countLines = false;
        boolean countWords = false;
        boolean countChars = false;

        List<String> files = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-l")) {
                countLines = true;
            } else if (arg.equals("-w")) {
                countWords = true;
            } else if (arg.equals("-c")) {
                countChars = true;
            } else if (!arg.startsWith("-")) {
                files.add(arg);
            } else {
                return CommandResult.error("wc: " + getUsage());
            }
        }

        if (!countLines && !countWords && !countChars) {
            countLines = true;
            countWords = true;
            countChars = true;
        }

        // Multiple files mode
        if (files.size() > 1) {
            return handleMultipleFiles(session, files, countLines, countWords, countChars);
        }

        // Single file or stdin
        String content;
        String fileName = null;
        if (!files.isEmpty()) {
            fileName = files.get(0);
            try {
                content = session.getVfs().readFile(fileName, session.getWorkingDir());
            } catch (VfsException e) {
                return CommandResult.error("wc: " + e.getMessage());
            }
        } else if (stdin != null) {
            content = stdin;
        } else {
            return CommandResult.error("wc: missing file operand");
        }

        String result = formatWcLine(content, fileName, countLines, countWords, countChars);
        return CommandResult.success(result);
    }

    private CommandResult handleMultipleFiles(ShellSession session, List<String> files,
                                              boolean countLines, boolean countWords,
                                              boolean countChars) {
        List<String> results = new ArrayList<>();
        int totalLines = 0;
        int totalWords = 0;
        int totalChars = 0;

        for (String file : files) {
            String content;
            try {
                content = session.getVfs().readFile(file, session.getWorkingDir());
            } catch (VfsException e) {
                return CommandResult.error("wc: " + e.getMessage());
            }

            int lines = content.isEmpty() ? 0 : content.split("\n", -1).length;
            int words = content.isBlank() ? 0 : content.trim().split("\\s+").length;
            int chars = content.length();

            totalLines += lines;
            totalWords += words;
            totalChars += chars;

            results.add(formatWcLine(content, file, countLines, countWords, countChars));
        }

        // Add total line
        List<String> totalParts = new ArrayList<>();
        if (countLines) {
            totalParts.add(String.valueOf(totalLines));
        }
        if (countWords) {
            totalParts.add(String.valueOf(totalWords));
        }
        if (countChars) {
            totalParts.add(String.valueOf(totalChars));
        }
        totalParts.add("total");
        results.add(String.join(" ", totalParts));

        return CommandResult.success(String.join("\n", results));
    }

    private String formatWcLine(String content, String fileName,
                                boolean countLines, boolean countWords, boolean countChars) {
        int lines = content.isEmpty() ? 0 : content.split("\n", -1).length;
        int words = content.isBlank() ? 0 : content.trim().split("\\s+").length;
        int chars = content.length();

        List<String> parts = new ArrayList<>();
        if (countLines) {
            parts.add(String.valueOf(lines));
        }
        if (countWords) {
            parts.add(String.valueOf(words));
        }
        if (countChars) {
            parts.add(String.valueOf(chars));
        }
        if (fileName != null) {
            parts.add(fileName);
        }

        return String.join(" ", parts);
    }

    @Override
    public String getUsage() {
        return "wc [-l] [-w] [-c] <file> [file2...]";
    }

    @Override
    public String getDescription() {
        return "Count lines, words, or characters";
    }
}
