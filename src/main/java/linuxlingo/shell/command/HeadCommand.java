package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

/**
 * Displays the first N lines of file(s) (default 10).
 * Syntax: head [-n N] &lt;file&gt; [file2...]
 *
 * <p><b>Owner: C</b></p>
 */
public class HeadCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        int n = 10;
        List<String> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n")) {
                if (i + 1 >= args.length) {
                    return CommandResult.error("head: option requires an argument -- n");
                }

                try {
                    n = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException e) {
                    return CommandResult.error("head: invalid number of lines: " + args[i + 1]);
                }
            } else {
                files.add(args[i]);
            }
        }

        if (!files.isEmpty()) {
            boolean multipleFiles = files.size() > 1;
            List<String> allOutput = new ArrayList<>();

            for (int f = 0; f < files.size(); f++) {
                String file = files.get(f);
                try {
                    String content = session.getVfs().readFile(file, session.getWorkingDir());
                    if (multipleFiles) {
                        if (f > 0) {
                            allOutput.add("");
                        }
                        allOutput.add("==> " + file + " <==");
                    }
                    if (!content.isEmpty()) {
                        String[] linesArray = content.split("\n", -1);
                        int end;
                        if (n >= 0) {
                            end = Math.min(n, linesArray.length);
                        } else {
                            end = Math.max(0, linesArray.length + n);
                        }
                        for (int i = 0; i < end; i++) {
                            allOutput.add(linesArray[i]);
                        }
                    }
                } catch (VfsException e) {
                    return CommandResult.error("head: " + e.getMessage());
                }
            }

            return CommandResult.success(String.join("\n", allOutput));
        }

        // Stdin fallback
        String content;
        if (stdin != null) {
            content = stdin;
        } else {
            return CommandResult.error("head: missing file operand");
        }

        if (content.isEmpty()) {
            return CommandResult.success("");
        }

        String[] linesArray = content.split("\n", -1);
        int end;
        if (n >= 0) {
            end = Math.min(n, linesArray.length);
        } else {
            end = Math.max(0, linesArray.length + n);
        }

        List<String> results = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            results.add(linesArray[i]);
        }

        return CommandResult.success(String.join("\n", results));
    }

    @Override
    public String getUsage() {
        return "head [-n N] <file> [file2...]";
    }

    @Override
    public String getDescription() {
        return "Display first N lines of file(s) (default 10)";
    }
}
