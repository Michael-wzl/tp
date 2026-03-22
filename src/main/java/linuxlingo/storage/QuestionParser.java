package linuxlingo.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import linuxlingo.exam.Checkpoint;
import linuxlingo.exam.QuestionBank;
import linuxlingo.exam.question.FitbQuestion;
import linuxlingo.exam.question.McqQuestion;
import linuxlingo.exam.question.PracQuestion;
import linuxlingo.exam.question.PracQuestion.SetupItem;
import linuxlingo.exam.question.Question;

/**
 * Parses question bank {@code .txt} files into {@link Question} objects.
 *
 * <p><b>Owner: D</b></p>
 *
 * <h3>File format</h3>
 * Each non-comment, non-blank line is pipe-delimited with up to 6 fields:
 * <pre>
 * TYPE | DIFFICULTY | QUESTION_TEXT | ANSWER | OPTIONS | EXPLANATION
 * </pre>
 *
 * <h4>TYPE-specific ANSWER formats</h4>
 * <ul>
 *   <li><b>MCQ</b>  — single letter: {@code B}</li>
 *   <li><b>FITB</b> — accepted answers separated by {@code |}: {@code pwd|PWD}</li>
 *   <li><b>PRAC</b> — checkpoints: {@code /path:TYPE,/path2:TYPE} where TYPE is DIR or FILE</li>
 * </ul>
 *
 * <h4>OPTIONS (MCQ only)</h4>
 * <pre>
 * A:some text B:other text C:more text D:last text
 * </pre>
 *
 * <h3>Dependencies</h3>
 * Uses {@link Storage#readLines(Path)} to read the file (infrastructure).
 *
 * @see QuestionBank#load(Path)
 */
public class QuestionParser {
    private static final Logger LOGGER = Logger.getLogger(QuestionParser.class.getName());

    /**
     * Parse a single question bank file into a list of questions.
     *
     * @param path the {@code .txt} file to parse
     * @return list of parsed questions (may be empty if file has no valid lines)
     * @throws StorageException if the file cannot be read
     */
    public static List<Question> parseFile(Path path) throws StorageException {
        Path validatedPath = Objects.requireNonNull(path, "path must not be null");
        List<String> lines = Storage.readLines(validatedPath);
        List<Question> questions = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            String[] fields = trimmedLine.split("\\s+\\|\\s+", 6);
            if (fields.length < 4) {
                LOGGER.log(Level.WARNING, "Skipping malformed question line {0} in {1}",
                        new Object[]{i + 1, validatedPath});
                continue;
            }

            String type = fields[0].trim().toUpperCase(Locale.ROOT);
            Question.Difficulty difficulty = parseDifficulty(fields[1].trim());
            String questionText = fields[2].trim();
            String answer = fields[3].trim();
            String options = fields.length >= 5 ? fields[4].trim() : "";
            String explanation = fields.length >= 6 ? fields[5].trim() : "";

            try {
                switch (type) {
                case "MCQ":
                    questions.add(parseMcq(questionText, answer, options, explanation, difficulty));
                    break;
                case "FITB":
                    questions.add(parseFitb(questionText, answer, explanation, difficulty));
                    break;
                case "PRAC":
                    questions.add(parsePrac(questionText, answer, options, explanation, difficulty));
                    break;
                default:
                    LOGGER.log(Level.WARNING, "Skipping unknown question type ''{0}'' at line {1} in {2}",
                            new Object[]{type, i + 1, validatedPath});
                    break;
                }
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING,
                        "Skipping invalid question entry at line " + (i + 1) + " in " + validatedPath, e);
            }
        }

        assert questions.stream().noneMatch(Objects::isNull) : "Parsed question list should not contain null entries";

        return questions;
    }

    /**
     * Parse a single MCQ line into a {@link McqQuestion}.
     *
     * <p>Options string format: {@code "A:text B:text C:text D:text"}.
     * Split by regex {@code (?=[A-D]:)} to separate individual options.</p>
     */
    private static McqQuestion parseMcq(String questionText, String answer,
                                        String options, String explanation,
                                        Question.Difficulty difficulty) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new IllegalArgumentException("MCQ answer must not be blank");
        }
        LinkedHashMap<Character, String> optionMap = new LinkedHashMap<>();
        String[] optParts = options.split("(?=[A-D]:)");
        for (String part : optParts) {
            String trimmedPart = part.trim();
            if (trimmedPart.length() >= 2 && trimmedPart.charAt(1) == ':') {
                optionMap.put(trimmedPart.charAt(0), trimmedPart.substring(2).trim());
            }
        }
        if (optionMap.isEmpty()) {
            throw new IllegalArgumentException("MCQ options must not be empty");
        }
        char correctAnswer = answer.charAt(0);
        if (!optionMap.containsKey(Character.toUpperCase(correctAnswer))) {
            throw new IllegalArgumentException("MCQ correct answer not found in options");
        }
        return new McqQuestion(questionText, explanation, difficulty, optionMap, correctAnswer);
    }

    /**
     * Parse a single FITB line into a {@link FitbQuestion}.
     *
     * <p>The answer field may contain multiple accepted answers separated by
     * {@code |}. For example: {@code "pwd|PWD"}.</p>
     *
     * <p>Escaped pipes ({@code \|}) are treated as literal pipe characters in
     * the accepted answer (e.g. {@code "\\|"} → accepted answer is {@code "|"}).</p>
     */
    private static FitbQuestion parseFitb(String questionText, String answer,
                                          String explanation, Question.Difficulty difficulty) {
        // Split by unescaped pipe: use negative lookbehind so \| is not treated as separator
        String[] answers = answer.split("(?<!\\\\)\\|");
        List<String> accepted = new ArrayList<>();
        for (String acceptedAnswer : answers) {
            // Unescape \| to literal |
            String trimmedAnswer = acceptedAnswer.trim().replace("\\|", "|");
            if (!trimmedAnswer.isEmpty()) {
                accepted.add(trimmedAnswer);
            }
        }
        if (accepted.isEmpty()) {
            throw new IllegalArgumentException("FITB accepted answer list must not be empty");
        }
        return new FitbQuestion(questionText, explanation, difficulty, accepted);
    }

    /**
     * Parse a single PRAC line into a {@link PracQuestion}.
     *
     * <p>The answer field contains comma-separated checkpoints:
     * {@code "/path:TYPE,/path2:TYPE"} where TYPE is {@code DIR}, {@code FILE},
     * {@code NOT_EXISTS}, {@code CONTENT_EQUALS=content}, or {@code PERM=rwxr-x---}.</p>
     *
     * <p>The options field (field 5) may contain semicolon-separated setup items:
     * {@code "MKDIR:/path;FILE:/file.txt=content;PERM:/file.txt=rwxr-x---"}</p>
     */
    private static PracQuestion parsePrac(String questionText, String answer,
                                          String options, String explanation,
                                          Question.Difficulty difficulty) {
        String[] parts = answer.split(",");
        List<Checkpoint> checkpoints = new ArrayList<>();
        for (String part : parts) {
            checkpoints.add(parseCheckpoint(part.trim()));
        }
        if (checkpoints.isEmpty()) {
            throw new IllegalArgumentException("PRAC checkpoints must not be empty");
        }

        // Parse optional setup items from the options field
        List<SetupItem> setupItems = new ArrayList<>();
        if (options != null && !options.isEmpty()) {
            String[] setupParts = options.split(";");
            for (String setupPart : setupParts) {
                SetupItem item = parseSetupItem(setupPart.trim());
                if (item != null) {
                    setupItems.add(item);
                }
            }
        }

        return new PracQuestion(questionText, explanation, difficulty, checkpoints, setupItems);
    }

    /**
     * Parse a single checkpoint string.
     *
     * <p>Formats:</p>
     * <ul>
     *   <li>{@code /path:DIR} or {@code /path:FILE}</li>
     *   <li>{@code /path:NOT_EXISTS}</li>
     *   <li>{@code /path:CONTENT_EQUALS=expected content}</li>
     *   <li>{@code /path:PERM=rwxr-x---}</li>
     * </ul>
     */
    private static Checkpoint parseCheckpoint(String checkpoint) {
        // Split at first colon after the path
        // Paths start with / so we find the colon that separates path from type
        int colonIdx = findTypeColon(checkpoint);
        if (colonIdx < 0) {
            throw new IllegalArgumentException("Invalid PRAC checkpoint format: " + checkpoint);
        }

        String path = checkpoint.substring(0, colonIdx).trim();
        String typeStr = checkpoint.substring(colonIdx + 1).trim();

        if (typeStr.equalsIgnoreCase("DIR")) {
            return new Checkpoint(path, Checkpoint.NodeType.DIR);
        } else if (typeStr.equalsIgnoreCase("FILE")) {
            return new Checkpoint(path, Checkpoint.NodeType.FILE);
        } else if (typeStr.equalsIgnoreCase("NOT_EXISTS")) {
            return new Checkpoint(path, Checkpoint.NodeType.NOT_EXISTS);
        } else if (typeStr.toUpperCase(Locale.ROOT).startsWith("CONTENT_EQUALS=")) {
            String content = typeStr.substring("CONTENT_EQUALS=".length());
            return new Checkpoint(path, Checkpoint.NodeType.CONTENT_EQUALS, content, null);
        } else if (typeStr.toUpperCase(Locale.ROOT).startsWith("PERM=")) {
            String perm = typeStr.substring("PERM=".length()).trim();
            return new Checkpoint(path, Checkpoint.NodeType.PERM, null, perm);
        } else {
            // Default to FILE for backward compatibility
            return new Checkpoint(path, Checkpoint.NodeType.FILE);
        }
    }

    /**
     * Find the colon separating path from type in a checkpoint string.
     * Skips the first character to handle absolute paths starting with /.
     */
    private static int findTypeColon(String checkpoint) {
        // Path always starts with / — scan from right for last colon
        // But CONTENT_EQUALS and PERM have = inside, so find the colon
        // after the last path segment
        // Strategy: find the last colon that is NOT inside a type value
        // Simple approach: paths can have multiple parts like /a/b/c:TYPE
        // The TYPE part never contains / so we can split at last : after last /
        int lastSlash = checkpoint.lastIndexOf('/');
        if (lastSlash < 0) {
            // no slash at all — use first colon
            return checkpoint.indexOf(':');
        }
        // Find first colon after the last slash
        int idx = checkpoint.indexOf(':', lastSlash);
        return idx;
    }

    /**
     * Parse a single setup item string.
     *
     * <p>Formats: {@code MKDIR:/path}, {@code FILE:/path=content},
     * {@code PERM:/path=rwxr-x---}</p>
     */
    private static SetupItem parseSetupItem(String item) {
        if (item.isEmpty()) {
            return null;
        }
        int colonIdx = item.indexOf(':');
        if (colonIdx < 0) {
            return null;
        }
        String typeStr = item.substring(0, colonIdx).trim().toUpperCase(Locale.ROOT);
        String rest = item.substring(colonIdx + 1).trim();

        switch (typeStr) {
        case "MKDIR":
            return new SetupItem(SetupItem.SetupType.MKDIR, rest, null);
        case "FILE": {
            int eqIdx = rest.indexOf('=');
            if (eqIdx >= 0) {
                return new SetupItem(SetupItem.SetupType.FILE,
                        rest.substring(0, eqIdx).trim(),
                        rest.substring(eqIdx + 1));
            }
            return new SetupItem(SetupItem.SetupType.FILE, rest, "");
        }
        case "PERM": {
            int eqIdx = rest.indexOf('=');
            if (eqIdx >= 0) {
                return new SetupItem(SetupItem.SetupType.PERM,
                        rest.substring(0, eqIdx).trim(),
                        rest.substring(eqIdx + 1).trim());
            }
            return null; // PERM requires a value
        }
        default:
            return null;
        }
    }

    /**
     * Convert a difficulty string to the enum value.
     * Defaults to {@code MEDIUM} for unknown values.
     */
    private static Question.Difficulty parseDifficulty(String diff) {
        if (diff == null) {
            return Question.Difficulty.MEDIUM;
        }
        return switch (diff.toUpperCase(Locale.ROOT)) {
        case "EASY" -> Question.Difficulty.EASY;
        case "MEDIUM" -> Question.Difficulty.MEDIUM;
        case "HARD" -> Question.Difficulty.HARD;
        default -> Question.Difficulty.MEDIUM;
        };
    }

    /**
     * Derive the topic name from a question bank file path.
     * Strips the {@code .txt} extension from the file name.
     *
     * @param path path to the question bank file
     * @return topic name (e.g. "navigation", "permissions")
     */
    public static String getTopicName(Path path) {
        Path validatedPath = Objects.requireNonNull(path, "path must not be null");
        if (validatedPath.getFileName() == null) {
            throw new IllegalArgumentException("path must include a file name");
        }
        String fileName = validatedPath.getFileName().toString();
        if (fileName.endsWith(".txt")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
