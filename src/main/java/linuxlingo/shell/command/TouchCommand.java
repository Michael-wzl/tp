package linuxlingo.shell.command;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

public class TouchCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        if (args.length == 0) {
            return CommandResult.error("touch: missing file operand");
        }
        for (String arg : args) {
            try {
                session.getVfs().createFile(arg, session.getWorkingDir());
            } catch (VfsException e) {
                return CommandResult.error("touch: " + e.getMessage());
            }
        }
        return CommandResult.success("");
    }

    @Override
    public String getUsage() {
        return "touch <file> [file2...]";
    }

    @Override
    public String getDescription() {
        return "Create empty files";
    }
}
