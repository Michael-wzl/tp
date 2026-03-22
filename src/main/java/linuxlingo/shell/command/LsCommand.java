package linuxlingo.shell.command;

import java.util.ArrayList;
import java.util.List;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.RegularFile;
import linuxlingo.shell.vfs.VfsException;

/**
 * Lists directory contents.
 * Supports: ls [-l] [-a] [-R] [path]
 *
 * <p><b>Owner: B</b></p>
 */
public class LsCommand implements Command {
    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        boolean longFormat = false;
        boolean showHidden = false;
        boolean recursive = false;
        String targetPath = session.getWorkingDir();
        boolean hasExplicitPath = false;

        for (String arg : args) {
            if (arg.startsWith("-") && arg.length() > 1) {
                for (int i = 1; i < arg.length(); i++) {
                    char option = arg.charAt(i);
                    if (option == 'l') {
                        longFormat = true;
                    } else if (option == 'a') {
                        showHidden = true;
                    } else if (option == 'R') {
                        recursive = true;
                    } else {
                        return CommandResult.error("ls: invalid option -- " + option);
                    }
                }
            } else if (!hasExplicitPath) {
                targetPath = arg;
                hasExplicitPath = true;
            } else {
                return CommandResult.error("ls: too many arguments");
            }
        }

        try {
            List<String> lines = new ArrayList<>();
            if (recursive) {
                listRecursive(session, targetPath, longFormat, showHidden, lines);
            } else {
                listDirectory(session, targetPath, longFormat, showHidden, lines);
            }
            return CommandResult.success(String.join("\n", lines));
        } catch (VfsException e) {
            return CommandResult.error("ls: " + e.getMessage());
        }
    }

    private void listDirectory(ShellSession session, String path,
                               boolean longFormat, boolean showHidden,
                               List<String> lines) {
        List<FileNode> children = session.getVfs().listDirectory(
                path, session.getWorkingDir(), showHidden);
        for (FileNode child : children) {
            String name = child.getName() + (child.isDirectory() ? "/" : "");
            if (!longFormat) {
                lines.add(name);
            } else {
                int size = child.isDirectory() ? 0 : ((RegularFile) child).getSize();
                lines.add(child.getPermission().toString() + "  " + size + "  " + name);
            }
        }
    }

    private void listRecursive(ShellSession session, String path,
                               boolean longFormat, boolean showHidden,
                               List<String> lines) {
        String absPath = session.getVfs().getAbsolutePath(path, session.getWorkingDir());
        lines.add(absPath + ":");
        List<FileNode> children = session.getVfs().listDirectory(
                path, session.getWorkingDir(), showHidden);
        List<String> subdirs = new ArrayList<>();
        for (FileNode child : children) {
            String name = child.getName() + (child.isDirectory() ? "/" : "");
            if (!longFormat) {
                lines.add(name);
            } else {
                int size = child.isDirectory() ? 0 : ((RegularFile) child).getSize();
                lines.add(child.getPermission().toString() + "  " + size + "  " + name);
            }
            if (child.isDirectory()) {
                subdirs.add(child.getAbsolutePath());
            }
        }
        for (String subdir : subdirs) {
            lines.add("");
            listRecursive(session, subdir, longFormat, showHidden, lines);
        }
    }

    @Override
    public String getUsage() {
        return "ls [-l] [-a] [-R] [path]";
    }

    @Override
    public String getDescription() {
        return "List directory contents";
    }
}
