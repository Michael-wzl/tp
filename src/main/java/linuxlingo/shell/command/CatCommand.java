package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

/**
 * Displays file contents. Supports concatenating multiple files.
 * Syntax: cat [-n] &lt;file&gt; [file2...]
 *
 * <p><b>Owner: C</b></p>
 */
public class CatCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean showLineNumbers = false;
        List<String> files = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-n")) {
                showLineNumbers = true;
            } else {
                files.add(arg);
            }
        }

        String content;
        if (!files.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String file : files) {
                try {
                    String fileContent = session.getVfs().readFile(file, session.getWorkingDir());
                    sb.append(fileContent);
                } catch (VfsException e) {
                    return CommandResult.error("cat: " + e.getMessage());
                }
            }
            content = sb.toString();
        } else if (stdin != null) {
            content = stdin;
        } else {
            return CommandResult.error("cat: missing file operand");
        }

        if (showLineNumbers && !content.isEmpty()) {
            String[] lines = content.split("\n", -1);
            StringBuilder numbered = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    numbered.append("\n");
                }
                numbered.append(String.format("%6d\t%s", i + 1, lines[i]));
            }
            return CommandResult.success(numbered.toString());
        }

        return CommandResult.success(content);
    }

    @Override
    public String getUsage() {
        return "cat [-n] <file> [file2...]";
    }

    @Override
    public String getDescription() {
        return "Display file contents";
    }
}
