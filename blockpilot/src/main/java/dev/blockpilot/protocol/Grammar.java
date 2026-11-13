package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Form;
import dev.blockpilot.api.Slot;
import java.util.ArrayList;
import java.util.List;

/**
 * The language's grammar as {@code help grammar} prints it: the core productions of the {@code command-language}
 * spec, then one production per command generated from its declaration, so the listing can't drift from the parser.
 */
final class Grammar {
    static final String COMMANDS_COMMENT = "/* Commands, from their declarations: items are tokens separated by "
        + "blanks; a command's forms are tried in order against all of a call's tokens, and its options "
        + "(<command>-option) may stand between any two items, each once. */";

    /** The core grammar, as the spec writes it. */
    static final String CORE = """
        /* Read as the parser reads it: alternatives are tried in order and the first
           that matches is taken; ?, * and + take as much as they can and never give
           it back; a production for a token matches the whole token; A - B matches
           what A matches unless B matches it too. [C] marks value limits: whole
           numbers are below 2^31, decimals have at most 9 digits before their point,
           and a duration is at most 2^31 - 1 ticks. */

        /* Line protocol: an auth line, then one request per line */
        connection   ::= auth-line request*
        auth-line    ::= "auth" #x20 auth-token line-end
        auth-token   ::= [^#x20#x9#xA#xD]+
        request      ::= ( "#" request-id ( #x20 line-script )? | line-script )
                         line-end
        request-id   ::= [^#x20#x9#xA#xD;]+
        line-script  ::= step ( ";" step )*
        line-end     ::= #xD? #xA

        /* Scripts; MCP's run tool also ends steps at line breaks */
        script       ::= step ( separator step )*
        separator    ::= ";" | #xA | #xD
        step         ::= blank* ( "?"? statement blank* )?
        blank        ::= #x20 | #x9
        statement    ::= wait | sample | assert | if | call

        /* Constructs */
        construct    ::= "wait" | "sample" | "assert" | "if"
        wait         ::= "wait" blank+ ( duration | "idle" | query blank+ "until"
                         blank+ predicates ( blank+ wait-option )* )
        wait-option  ::= ( "stable=" | "every=" ) duration
                       | "timeout=" ( duration | seconds ) | clock
        sample       ::= "sample" blank+ sample-query
                         ( blank+ "and" blank+ sample-query )*
                         blank+ "every" blank+ duration
                         ( blank+ count ( blank+ sample-option )* ( blank+ beside )?
                         | ( blank+ sample-option )* blank+ beside )
        beside       ::= "while" blank+ ( wait | call )
        clock        ::= "clock=" ( "server" | "client" )
        sample-option ::= clock | "fields=" name ( "," name )*
        assert       ::= "assert" blank+ query blank+ predicates
        if           ::= "if" blank+ query blank+ predicates blank+ "then" blank+
                         "?"? statement
        query        ::= ( command-name - construct ) ( blank+ ( token - stop ) )*
        sample-query ::= ( command-name - construct )
                         ( blank+ ( token - ( stop | "and" ) ) )*
        stop         ::= "ok" | "until" | "every" | "then" | test-shape

        /* Calls: the tokens are matched against the command's forms */
        call         ::= ( command-name - construct ) ( blank+ token )*
        command-name ::= ( namespace ":" )? name
        name         ::= [a-z] [a-z0-9_]*

        /* Predicates */
        predicates   ::= predicate ( blank+ ( "and" blank+ )? predicate )*
        predicate    ::= "ok" | test
        test         ::= field-path ( ( "==" | "!=" ) operand
                         | ( "<=" | ">=" | "<" | ">" ) ( decimal | reference ) )
        operand      ::= reference | value
        reference    ::= "$" field-path
        value        ::= ( token-char - [$<>=!&|] ) ( token-char - [<>=!&|] )*
        test-shape   ::= field-path comparison token-char+
        comparison   ::= "==" | "!=" | "<=" | ">=" | "<" | ">"
        field-path   ::= identifier ( "." ( identifier | digits ) )*      /* [C] */

        /* Tokens */
        token        ::= token-char+
        token-char   ::= "\\;" | [^#x20#x9#xA#xD;]
        identifier   ::= [A-Za-z_] [A-Za-z0-9_]*
        digits       ::= [0-9]+

        /* What forms are made of */
        option       ::= option-name "=" option-value
        option-name  ::= identifier ( ":" identifier )?
        option-value ::= ( token-char - "=" ) token-char*
        text         ::= token ( blank+ token )*
        word         ::= token - ( option | test-shape )

        /* Slot types */
        position     ::= "~"? decimal "," decimal "," decimal
        point        ::= decimal "," decimal
        entity       ::= "e" digits | "@" entity-name                     /* [C] */
        entity-name  ::= [a-zA-Z] [a-zA-Z0-9_.-]+
        widget       ::= "w" digits                                       /* [C] */
        menu-slot    ::= "s" digits                                       /* [C] */
        uuid         ::= hex8 "-" hex4 "-" hex4 "-" hex4 "-" hex8 hex4
        hex8         ::= hex4 hex4
        hex4         ::= hex hex hex hex
        hex          ::= [0-9a-fA-F]
        integer      ::= "-"? digits                                      /* [C] */
        number       ::= decimal
        decimal      ::= "-"? digits ( "." digits )?                      /* [C] */
        count        ::= "x" digits                                       /* [C] */
        duration     ::= digits "t" | seconds "s"                         /* [C] */
        seconds      ::= digits ( "." digits )?                           /* [C] */
        id           ::= namespace ":" path | bare-id
        bare-id      ::= path - ( decimal | count | duration | entity | widget
                                | menu-slot | uuid )
        namespace    ::= [a-z0-9_.-]+
        path         ::= [a-z0-9_./-]+
        tag          ::= "#" id
        pattern      ::= ( [a-z0-9_.:/-]* "*" )+ [a-z0-9_.:/-]*
        block-state  ::= ( pattern | tag | id ) "[" state ( "," state )* "]"
        state        ::= state-name "=" state-value
        state-name   ::= [a-z0-9_]+
        state-value  ::= [a-z0-9_]+
        """;

    private Grammar() {
    }

    /** One line per comment or production of the core grammar, blanks collapsed outside quoted literals. */
    static List<String> coreLines() {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inComment = false;
        for (String line : CORE.split("\n")) {
            String trimmed = line.strip();
            boolean starts = !inComment && (trimmed.startsWith("/*") || trimmed.matches("[a-z][a-z0-9-]*\\s+::=.*"));
            if (starts && !current.isEmpty()) {
                lines.add(collapse(current.toString()));
                current.setLength(0);
            }
            if (!trimmed.isEmpty()) {
                current.append(current.isEmpty() ? "" : " ").append(trimmed);
            }
            if (trimmed.startsWith("/*")) {
                inComment = true;
            }
            if (trimmed.endsWith("*/")) {
                inComment = false;
            }
        }
        lines.add(collapse(current.toString()));
        return lines;
    }

    private static String collapse(String text) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (char character : text.toCharArray()) {
            if (!quoted && character == ' ' && out.charAt(out.length() - 1) == ' ') {
                continue;
            }
            quoted ^= character == '"';
            out.append(character);
        }
        return out.toString();
    }

    /** The command's production, {@code <command>-command}, and its options' when it takes any. */
    static List<String> command(CommandRegistry.Entry entry) {
        String name = entry.qualifiedName().replace(':', '.');
        CommandSpec spec = entry.spec();
        List<String> forms = new ArrayList<>();
        boolean bare = false;
        for (Form form : spec.forms()) {
            String items = items(form.elements());
            if (items.isEmpty()) {
                bare = true;
            } else {
                forms.add(items);
            }
        }
        String body = forms.size() == 1 && !bare ? " " + forms.get(0)
            : forms.isEmpty() ? "" : " ( " + String.join(" | ", forms) + " )" + (bare ? "?" : "");
        List<String> productions = new ArrayList<>(List.of(name + "-command ::= " + quote(entry.qualifiedName())
            + body));
        List<String> options = new ArrayList<>();
        if (spec.isDiffable()) {
            options.add(quote("full"));
        }
        spec.options().values().forEach(option -> options.add(option(option)));
        if (spec.kind() == CommandSpec.Kind.ACTION) {
            options.add(option(CommandSpec.TIMEOUT));
        }
        if (!options.isEmpty()) {
            productions.add(name + "-option ::= " + String.join(" | ", options));
        }
        return productions;
    }

    private static String items(List<Form.Element> elements) {
        List<String> items = new ArrayList<>();
        for (Form.Element element : elements) {
            if (element instanceof Form.Group group) {
                items.add("( " + items(group.elements()) + " )" + (group.repeats() ? "*" : "?"));
            } else {
                Slot slot = (Slot) element;
                items.add(value(slot) + (slot.optional() ? "?" : ""));
            }
        }
        return String.join(" ", items);
    }

    /** What a slot takes: its words quoted and its types' productions, as one choice. */
    private static String value(Slot slot) {
        List<String> choices = new ArrayList<>();
        slot.words().forEach(word -> choices.add(quote(word)));
        slot.types().stream().filter(type -> type != ArgType.KEYWORD).forEach(type -> choices.add(type.production()));
        return choices.size() == 1 ? choices.get(0) : "( " + String.join(" | ", choices) + " )";
    }

    private static String option(CommandSpec.Option option) {
        String value = option.value().isText() ? "option-value" : value(option.value());
        return option.isFamily() ? quote(option.name().replace("*", "")) + " identifier \"=\" " + value
            : quote(option.name() + "=") + " " + value;
    }

    private static String quote(String text) {
        return text.contains("\"") ? "'" + text + "'" : "\"" + text + "\"";
    }
}
