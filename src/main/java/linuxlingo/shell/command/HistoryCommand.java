package linuxlingo.shell.command;

import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellLineReader;
import linuxlingo.shell.ShellSession;

/**
 * Displays the command history.
 *
 * <p>Usage: {@code history} — lists all commands in the current session history.</p>
 * <p>Usage: {@code history -c} — clears the command history.</p>
 * <p>Usage: {@code history N} — shows the last N commands.</p>
 */
public class HistoryCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        ShellLineReader reader = session.getLineReader();

        // If no ShellLineReader is available (e.g. non-interactive mode),
        // fall back to the in-memory history stored on ShellSession
        List<String> history;
        if (reader != null) {
            history = reader.getHistory();
        } else {
            history = session.getCommandHistory();
        }

        // Handle -c flag to clear history
        if (args.length > 0 && args[0].equals("-c")) {
            session.getCommandHistory().clear();
            return CommandResult.success("");
        }

        // Handle numeric argument to limit output
        int limit = history.size();
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
                if (limit < 0) {
                    return CommandResult.error("history: " + args[0]
                            + ": invalid option");
                }
            } catch (NumberFormatException e) {
                return CommandResult.error("history: " + args[0]
                        + ": numeric argument required");
            }
        }

        if (history.isEmpty()) {
            return CommandResult.success("");
        }

        int start = Math.max(0, history.size() - limit);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            sb.append(String.format("%5d  %s", i + 1, history.get(i)));
            if (i < history.size() - 1) {
                sb.append("\n");
            }
        }
        return CommandResult.success(sb.toString());
    }

    @Override
    public String getUsage() {
        return "history [-c] [N]";
    }

    @Override
    public String getDescription() {
        return "Display or manage command history";
    }
}
