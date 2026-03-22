package linuxlingo.shell.command;

import linuxlingo.shell.CommandResult;
import linuxlingo.shell.ShellSession;
import linuxlingo.shell.vfs.Directory;
import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.VfsException;

/**
 * Displays a directory tree structure.
 * Syntax: tree [path]
 */
public class TreeCommand implements Command {

    @Override
    public CommandResult execute(ShellSession session, String[] args, String stdin) {
        String targetPath = args.length > 0 ? args[0] : session.getWorkingDir();

        try {
            FileNode node = session.getVfs().resolve(targetPath, session.getWorkingDir());
            if (!node.isDirectory()) {
                return CommandResult.error("tree: " + targetPath + ": not a directory");
            }

            Directory dir = (Directory) node;
            StringBuilder sb = new StringBuilder();
            sb.append(dir.getAbsolutePath());
            int[] counts = {0, 0}; // dirs, files
            buildTree(dir, sb, "", counts);
            sb.append("\n\n").append(counts[0]).append(" directories, ").append(counts[1]).append(" files");
            return CommandResult.success(sb.toString());
        } catch (VfsException e) {
            return CommandResult.error("tree: " + e.getMessage());
        }
    }

    private void buildTree(Directory dir, StringBuilder sb, String prefix, int[] counts) {
        var children = dir.getChildren();
        for (int i = 0; i < children.size(); i++) {
            FileNode child = children.get(i);
            boolean isLast = (i == children.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String name = child.getName() + (child.isDirectory() ? "/" : "");

            sb.append("\n").append(prefix).append(connector).append(name);

            if (child.isDirectory()) {
                counts[0]++;
                String newPrefix = prefix + (isLast ? "    " : "│   ");
                buildTree((Directory) child, sb, newPrefix, counts);
            } else {
                counts[1]++;
            }
        }
    }

    @Override
    public String getUsage() {
        return "tree [path]";
    }

    @Override
    public String getDescription() {
        return "Display directory tree structure";
    }
}
