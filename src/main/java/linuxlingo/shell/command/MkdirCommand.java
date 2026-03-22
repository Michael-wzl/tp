package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.VfsException;

public class MkdirCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean parents = false;
        List<String> paths = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-p")) {
                parents = true;
            } else if (!arg.startsWith("-")) {
                paths.add(arg);
            }
        }

        if (paths.isEmpty()) {
            return CommandResult.error("mkdir: missing operand");
        }

        for (String path : paths) {
            try {
                session.getVfs().createDirectory(path, session.getWorkingDir(), parents);
            } catch (VfsException e) {
                return CommandResult.error("mkdir: " + e.getMessage());
            }
        }
        return CommandResult.success("");
    }

    @Override
    public String getUsage() {
        return "mkdir [-p] <path> [path2...]";
    }

    @Override
    public String getDescription() {
        return "Create directories";
    }
}
