package linuxlingo.shell.command;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;

/**
 * Shows the path of a command (whether it exists in the registry).
 * Syntax: which &lt;command&gt;
 */
public class WhichCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        if (args.length == 0) {
            return CommandResult.error("which: missing argument");
        }

        StringBuilder sb = new StringBuilder();
        boolean anyNotFound = false;

        for (String cmdName : args) {
            Command cmd = session.getRegistry().get(cmdName);
            if (cmd != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("/usr/bin/").append(cmdName);
            } else {
                anyNotFound = true;
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(cmdName).append(" not found");
            }
        }

        if (anyNotFound && args.length == 1) {
            return CommandResult.error("which: " + args[0] + " not found");
        }

        return CommandResult.success(sb.toString());
    }

    @Override
    public String getUsage() {
        return "which <command>";
    }

    @Override
    public String getDescription() {
        return "Show the path of a command";
    }
}
