package linuxlingo.shell;

import java.util.List;
import java.util.logging.Logger;

/**
 * Transforms a raw input string into a structured execution plan.
 *
 * <p><b>Owner: A</b></p>
 *
 * <p>Parsing pipeline:</p>
 * <pre>
 *   Raw Input String
 *       │
 *       ▼
 *   Tokenizer        — split by whitespace, respecting quotes ("..." and '...')
 *       │
 *       ▼
 *   Operator Splitter — split on operators: |  &gt;  &gt;&gt;  &amp;&amp;  ;
 *       │
 *       ▼
 *   Command Segment List — each segment = command name + arguments
 *       │
 *       ▼
 *   Execution Engine  — execute segments, connecting via pipes/redirects
 * </pre>
 */
public class ShellParser {

    private static final Logger LOGGER =  Logger.getLogger(ShellParser.class.getName());

    public enum TokenType {
        WORD, PIPE, REDIRECT, APPEND, AND, SEMICOLON, OR, INPUT_REDIRECT
    }

    public static class Token {
        public final String value;
        public final TokenType type;

        public Token(String value, TokenType type) throws IllegalArgumentException {
            if (value == null) {
                throw new IllegalArgumentException("Token value must not be null");
            }
            if (type == null) {
                throw new IllegalArgumentException("Token type must not be null");
            }

            this.value = value;
            this.type = type;
        }

        @Override
        public String toString() {

            return type + ":" + value;
        }
    }

    public static class RedirectInfo {
        public final String operator; // ">" or ">>"
        public final String target;   // file path

        public RedirectInfo(String operator, String target) {
            if (!">".equals(operator) && !">>".equals(operator)) {
                throw new IllegalArgumentException("RedirectInfo operator must be '>' or '>>', got: " + operator);
            }
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("RedirectInfo target must no be null or blank");
            }
            this.operator = operator;
            this.target = target;
        }

        public boolean isAppend() {
            return ">>".equals(operator);
        }
    }

    public static class Segment {
        public final String commandName;
        public final String[] args;
        public final RedirectInfo redirect;
        public final String inputRedirect; // file for < input redirect

        public Segment(String commandName, String[] args, RedirectInfo redirect) {
            this(commandName, args, redirect, null);
        }

        public Segment(String commandName, String[] args, RedirectInfo redirect, String inputRedirect) {
            if (commandName == null || commandName.isBlank()) {
                throw new IllegalArgumentException("Segment commandName must not be null or blank");
            }
            if (args == null) {
                throw new IllegalArgumentException("Segment args must not be null");
            }

            this.commandName = commandName;
            this.args = args;
            this.redirect = redirect;
            this.inputRedirect = inputRedirect;
        }
    }

    public static class ParsedPlan {
        public final List<Segment> segments;
        public final List<TokenType> operators;

        public ParsedPlan(List<Segment> segments, List<TokenType> operators) {
            if (segments == null) {
                throw new IllegalArgumentException("ParsedPlan segments must not be null");
            }
            if (operators == null) {
                throw new IllegalArgumentException("ParsedPLan operators must not be null");
            }

            assert operators.size() == Math.max(0,segments.size() - 1)
                : "operators.size()=" + operators.size()
                        + " but segments.size()=" + segments.size();

            this.segments = segments;
            this.operators = operators;
        }
    }

    /**
     * Parse a raw input string into a {@link ParsedPlan}.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Tokenize input — split by whitespace, respecting single/double quotes.
     *       Recognize operators: {@code |}, {@code >}, {@code >>}, {@code &&}, {@code ;}.</li>
     *   <li>Split token list by inter-segment operators (PIPE, AND, SEMICOLON).</li>
     *   <li>Within each segment, extract command name, args, and optional redirect info.</li>
     * </ol>
     */
    public ParsedPlan parse(String input) throws IllegalArgumentException {
        List<Segment> segments = new java.util.ArrayList<>();
        List<TokenType> operators = new java.util.ArrayList<>();

        // Edge case
        if (input == null || input.trim().isEmpty()) {
            LOGGER.fine("parse() called with null or blank input - returning empty plan");
            return new ParsedPlan(segments, operators);
        }

        LOGGER.fine("Parsing input: " + input);

        // Tokenizer (char-by-char state machine)
        List<Token> tokens = new java.util.ArrayList<>();

        enum State { NORMAL, IN_SINGLE_QUOTE, IN_DOUBLE_QUOTE}
        State state = State.NORMAL;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            switch(state) {
            case NORMAL:
                switch(c) {
                case ' ', '\t':
                    flushCurrentToken(current, tokens);
                    break;
                case '|':
                    flushCurrentToken(current, tokens);
                    if (i + 1 < input.length() && input.charAt(i + 1) == '|') {
                        tokens.add(new Token("||", TokenType.OR));
                        i++; // skip second '|'
                    } else {
                        tokens.add(new Token("|", TokenType.PIPE));
                    }
                    break;
                case '<':
                    flushCurrentToken(current, tokens);
                    tokens.add(new Token("<", TokenType.INPUT_REDIRECT));
                    break;
                case ';':
                    flushCurrentToken(current, tokens);
                    tokens.add(new Token(";", TokenType.SEMICOLON));
                    break;
                case '&':
                    if (i + 1 < input.length() && input.charAt(i + 1) == '&') {
                        flushCurrentToken(current, tokens);
                        tokens.add(new Token("&&", TokenType.AND));
                        i++; // to skip the second '&'
                    } else {
                        current.append(c); // treat lone '&' symbol as an ordinary char
                    }
                    break;
                case '>':
                    flushCurrentToken(current, tokens);
                    if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                        tokens.add(new Token(">>", TokenType.APPEND));
                        i++; // to skip the second '>'
                    } else {
                        tokens.add(new Token(">", TokenType.REDIRECT));
                    }
                    break;
                case '\'':
                    state = State.IN_SINGLE_QUOTE;
                    break;// do not add quote to current
                case '"':
                    state = State.IN_DOUBLE_QUOTE;
                    break;// do not add quote to current
                default:
                    current.append(c);
                }
                break;
            case IN_SINGLE_QUOTE:
                if (c == '\'') {
                    state = State.NORMAL; // closing quote, not to be added to current
                } else {
                    current.append(c); // everything inside single quotes is literal
                }
                break;
            case IN_DOUBLE_QUOTE:
                if (c == '"') {
                    state = State.NORMAL; // closing quote, not to be added to current
                } else {
                    current.append(c); // everything inside double quotes is literal
                }
                break;
            default:
                assert false : "Unhandled tokenizer state: " + state;
                break;
            }
        }

        // Flushing any remaining token
        flushCurrentToken(current, tokens);

        // Splitting tokens into Segment objects
        // Traverse the token list accumulating WORDs into the current segment
        // On REDIRECT/APPEND: consume the next WORD as the redirect target
        // ON PIPE/AND/SEMICOLON: finalize the current segment, record operator
        List<String> currentWords = new java.util.ArrayList<>();
        RedirectInfo currentRedirect = null;
        String currentInputRedirect = null;
        boolean expectRedirectTarget = false;
        boolean expectInputRedirectTarget = false;
        String pendingRedirectOp = null;

        for (Token tok : tokens) {
            if (expectRedirectTarget) {
                // The token immediately after > or >> is considered the target file path
                if (tok.type == TokenType.WORD) {
                    currentRedirect = new RedirectInfo(pendingRedirectOp, tok.value);
                    expectRedirectTarget = false;
                    pendingRedirectOp = null;
                    continue;
                }
                // For improper input i.e. no target provided, reset

                expectRedirectTarget = false;
                pendingRedirectOp = null;
            }

            if (expectInputRedirectTarget) {
                if (tok.type == TokenType.WORD) {
                    currentInputRedirect = tok.value;
                    expectInputRedirectTarget = false;
                    continue;
                }
                expectInputRedirectTarget = false;
            }

            switch (tok.type) {
            case WORD:
                currentWords.add(tok.value);
                break;
            case REDIRECT:
                expectRedirectTarget = true;
                pendingRedirectOp = ">";
                break;
            case APPEND:
                expectRedirectTarget = true;
                pendingRedirectOp = ">>";
                break;
            case INPUT_REDIRECT:
                expectInputRedirectTarget = true;
                break;
            case PIPE, AND, SEMICOLON, OR:
                // Finalizing the current segment before recording the operator
                if (!currentWords.isEmpty()) {
                    segments.add(buildSegment(currentWords, currentRedirect, currentInputRedirect));
                    currentWords = new java.util.ArrayList<>();
                    currentRedirect = null;
                    currentInputRedirect = null;
                }
                operators.add(tok.type);
                break;
            default: // unreachable
                break;
            }
        }

        // Finalizing the last segment (no trailing operator)
        if (!currentWords.isEmpty()) {
            segments.add(buildSegment(currentWords, currentRedirect, currentInputRedirect));
        }

        assert operators.size() == Math.max(0, segments.size() - 1)
                : "Invariant broken after parse: operators=" + operators.size()
                + " segments=" + segments.size();

        LOGGER.fine(() -> "Parse complete: " + segments.size()
                + " segment(s), " + operators.size() + " operator(s)");

        return new ParsedPlan(segments, operators);
    }

    private Segment buildSegment(List<String> words, RedirectInfo redirect) {
        return buildSegment(words, redirect, null);
    }

    /**
     * build a segment from an accumulated word list.
     * @param words the accumulated word list
     * @param redirect optional output redirect
     * @param inputRedirect optional input redirect file
     * @return the segment
     */
    private Segment buildSegment(List<String> words, RedirectInfo redirect, String inputRedirect) {
        assert words != null && !words.isEmpty()
            : "buildSegmend() requires a non-empty word list";

        String commandName = words.get(0);
        String[] args = new String[words.size()-1];
        for(int i = 1; i < words.size(); i++) {
            args[i-1] = words.get(i);
        }

        return new Segment(commandName, args, redirect, inputRedirect);
    }

    private void flushCurrentToken(StringBuilder current, List<Token> tokens) throws IllegalArgumentException {
        assert current != null : "current StringBuilder must not be null";
        assert tokens != null : "tokens list must not be null";
        if (!current.isEmpty()){
            tokens.add(new Token(current.toString(), TokenType.WORD));
            current.setLength(0);
        }
    }
}
