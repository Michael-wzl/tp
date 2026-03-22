package linuxlingo.shell;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import linuxlingo.shell.command.CatCommand;
import linuxlingo.shell.command.CdCommand;
import linuxlingo.shell.command.ChmodCommand;
import linuxlingo.shell.command.ClearCommand;
import linuxlingo.shell.command.Command;
import linuxlingo.shell.command.CpCommand;
import linuxlingo.shell.command.DateCommand;
import linuxlingo.shell.command.DiffCommand;
import linuxlingo.shell.command.EchoCommand;
import linuxlingo.shell.command.EnvDeleteCommand;
import linuxlingo.shell.command.EnvListCommand;
import linuxlingo.shell.command.FindCommand;
import linuxlingo.shell.command.GrepCommand;
import linuxlingo.shell.command.HeadCommand;
import linuxlingo.shell.command.HelpCommand;
import linuxlingo.shell.command.LoadCommand;
import linuxlingo.shell.command.LsCommand;
import linuxlingo.shell.command.ManCommand;
import linuxlingo.shell.command.MkdirCommand;
import linuxlingo.shell.command.MvCommand;
import linuxlingo.shell.command.PwdCommand;
import linuxlingo.shell.command.ResetCommand;
import linuxlingo.shell.command.RmCommand;
import linuxlingo.shell.command.SaveCommand;
import linuxlingo.shell.command.SortCommand;
import linuxlingo.shell.command.TailCommand;
import linuxlingo.shell.command.TeeCommand;
import linuxlingo.shell.command.TouchCommand;
import linuxlingo.shell.command.TreeCommand;
import linuxlingo.shell.command.UnaliasCommand;
import linuxlingo.shell.command.AliasCommand;
import linuxlingo.shell.command.UniqCommand;
import linuxlingo.shell.command.WcCommand;
import linuxlingo.shell.command.WhichCommand;
import linuxlingo.shell.command.WhoamiCommand;

/**
 * Registry that maps command name strings to {@link Command} instances.
 *
 * <p><b>Owner: B &amp; C — each member registers the commands they own.</b></p>
 *
 * <p>All commands are registered here so that ShellSession can look them up by name.
 * The constructor wires up every command. Additional commands can be registered
 * at runtime via {@link #register(String, Command)}.</p>
 */
public final class CommandRegistry {
    private final Map<String, Command> commands;

    public CommandRegistry() {
        commands = new LinkedHashMap<>();

        // ── Tier 1 — Navigation (Owner: B) ──────────────────────
        register("ls", new LsCommand());
        register("cd", new CdCommand());
        register("pwd", new PwdCommand());
        register("mkdir", new MkdirCommand());
        register("touch", new TouchCommand());

        // ── Tier 2 — File Operations (Owner: C) ─────────────────
        register("cat", new CatCommand());
        register("echo", new EchoCommand());
        register("rm", new RmCommand());
        register("cp", new CpCommand());
        register("mv", new MvCommand());
        register("head", new HeadCommand());
        register("tail", new TailCommand());

        // ── Tier 3 — Text Processing & Permissions (Owner: C) ───
        register("grep", new GrepCommand());
        register("find", new FindCommand());
        register("chmod", new ChmodCommand());
        register("wc", new WcCommand());
        register("sort", new SortCommand());
        register("uniq", new UniqCommand());

        // ── Built-in Commands (Owner: B) ─────────────────────────
        register("help", new HelpCommand());
        register("clear", new ClearCommand());

        // ── Environment Management (Owner: B) ────────────────────
        register("save", new SaveCommand());
        register("load", new LoadCommand());
        register("reset", new ResetCommand());
        register("envlist", new EnvListCommand());
        register("envdelete", new EnvDeleteCommand());

        // ── v2.0 — New Commands ──────────────────────────────────
        register("man", new ManCommand());
        register("tree", new TreeCommand());
        register("which", new WhichCommand());
        register("whoami", new WhoamiCommand());
        register("date", new DateCommand());
        register("alias", new AliasCommand());
        register("unalias", new UnaliasCommand());
        register("tee", new TeeCommand());
        register("diff", new DiffCommand());
    }

    public void register(String name, Command cmd) {
        commands.put(name, cmd);
    }

    public Command get(String name) {
        return commands.get(name);
    }

    public Set<String> getAllNames() {
        return new TreeSet<>(commands.keySet());
    }

    /**
     * Returns an ordered map of command-name → description for the help screen.
     */
    public Map<String, String> getHelpText() {
        Map<String, String> help = new LinkedHashMap<>();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            help.put(entry.getKey(), entry.getValue().getDescription());
        }
        return help;
    }
}
