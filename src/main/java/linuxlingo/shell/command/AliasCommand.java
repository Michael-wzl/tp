package linuxlingo.shell.command;

import java.util.Map;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;

/**
 * Creates or displays shell aliases.
 * Syntax: alias [name=value]
 */
public class AliasCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        Map<String, String> aliases = session.getAliases();

        // No args: list all aliases
        if (args.length == 0) {
            if (aliases.isEmpty()) {
                return CommandResult.success("");
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("alias ").append(entry.getKey()).append("='").append(entry.getValue()).append("'");
            }
            return CommandResult.success(sb.toString());
        }

        // Process each argument
        for (String arg : args) {
            int eqIndex = arg.indexOf('=');
            if (eqIndex == -1) {
                // Display a specific alias
                String value = aliases.get(arg);
                if (value == null) {
                    return CommandResult.error("alias: " + arg + ": not found");
                }
                return CommandResult.success("alias " + arg + "='" + value + "'");
            }

            String name = arg.substring(0, eqIndex);
            String value = arg.substring(eqIndex + 1);

            // Strip surrounding quotes if present
            if ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\""))) {
                value = value.substring(1, value.length() - 1);
            }

            if (name.isEmpty()) {
                return CommandResult.error("alias: invalid alias name");
            }

            aliases.put(name, value);
        }

        return CommandResult.success("");
    }

    @Override
    public String getUsage() {
        return "alias [name=value]";
    }

    @Override
    public String getDescription() {
        return "Create or display shell aliases";
    }
}
