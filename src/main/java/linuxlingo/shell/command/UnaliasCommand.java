package linuxlingo.shell.command;

import java.util.Map;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;

/**
 * Removes shell aliases.
 * Syntax: unalias &lt;name&gt; [-a]
 */
public class UnaliasCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        if (args.length == 0) {
            return CommandResult.error("unalias: usage: unalias [-a] name [name ...]");
        }

        Map<String, String> aliases = session.getAliases();

        for (String arg : args) {
            if (arg.equals("-a")) {
                aliases.clear();
                return CommandResult.success("");
            }

            if (!aliases.containsKey(arg)) {
                return CommandResult.error("unalias: " + arg + ": not found");
            }
            aliases.remove(arg);
        }

        return CommandResult.success("");
    }

    @Override
    public String getUsage() {
        return "unalias [-a] <name>";
    }

    @Override
    public String getDescription() {
        return "Remove shell aliases";
    }
}
