package linuxlingo.shell.command;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

/**
 * Reads from stdin and writes to both stdout and a file.
 * Syntax: tee [-a] &lt;file&gt;
 */
public class TeeCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean append = false;
        String file = null;

        for (String arg : args) {
            if (arg.equals("-a")) {
                append = true;
            } else if (!arg.startsWith("-")) {
                if (file == null) {
                    file = arg;
                }
            } else {
                return CommandResult.error("tee: invalid option -- " + arg);
            }
        }

        if (file == null) {
            return CommandResult.error("tee: " + getUsage());
        }

        String content = (stdin != null) ? stdin : "";

        try {
            session.getVfs().writeFile(file, session.getWorkingDir(), content, append);
        } catch (VfsException e) {
            return CommandResult.error("tee: " + e.getMessage());
        }

        // tee outputs the same content to stdout
        return CommandResult.success(content);
    }

    @Override
    public String getUsage() {
        return "tee [-a] <file>";
    }

    @Override
    public String getDescription() {
        return "Read from stdin and write to both stdout and a file";
    }
}
