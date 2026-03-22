package linuxlingo.shell.command;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;

/**
 * Displays the manual page for a given command.
 * Syntax: man &lt;command&gt;
 */
public class ManCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        if (args.length == 0) {
            return CommandResult.error("man: what manual page do you want?");
        }
        if (args.length > 1) {
            return CommandResult.error("man: too many arguments");
        }

        String cmdName = args[0];
        Command command = session.getRegistry().get(cmdName);
        if (command == null) {
            return CommandResult.error("man: no manual entry for " + cmdName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("NAME\n");
        sb.append("    ").append(cmdName).append(" - ").append(command.getDescription()).append("\n\n");
        sb.append("SYNOPSIS\n");
        sb.append("    ").append(command.getUsage()).append("\n\n");
        sb.append("DESCRIPTION\n");
        sb.append("    ").append(command.getDescription()).append("\n");

        return CommandResult.success(sb.toString());
    }

    @Override
    public String getUsage() {
        return "man <command>";
    }

    @Override
    public String getDescription() {
        return "Display manual page for a command";
    }
}
