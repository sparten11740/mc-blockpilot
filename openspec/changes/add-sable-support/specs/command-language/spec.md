# Spec Delta

## MODIFIED Requirements

### Requirement: Grammar
Requests and scripts SHALL follow this grammar, in W3C EBNF (XML 1.0, section
6) read the way the comment at its top says.

```ebnf
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
sample       ::= "sample" blank+ query blank+ "every" blank+ duration
                 ( blank+ count ( blank+ clock )? ( blank+ beside )?
                 | ( blank+ clock )? blank+ beside )
beside       ::= "while" blank+ ( wait | call )
clock        ::= "clock=" ( "server" | "client" )
assert       ::= "assert" blank+ query blank+ predicates
if           ::= "if" blank+ query blank+ predicates blank+ "then" blank+
                 "?"? statement
query        ::= ( command-name - construct ) ( blank+ ( token - stop ) )*
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
token-char   ::= "\;" | [^#x20#x9#xA#xD;]
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
entity       ::= "e" digits                                       /* [C] */
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
```

#### Scenario: A construct in a script
- **WHEN** an agent runs `wait block ~0,-1,0 until id==air timeout=5s; status`
- **THEN** the first step is a wait on the query `block ~0,-1,0` with the
  predicate `id==air` and the option `timeout=5s`, and the second is a call of
  `status`

#### Scenario: Escaped semicolon
- **WHEN** an agent runs `chat a\; b; status`
- **THEN** the chat message is `a; b` and `status` is a step of its own

### Requirement: Sampling beside an action
With `while <action>`, `sample` SHALL run the action as if written alone and
sample beside it until the action ends or n (at most 1,000) samples are taken:
from before the action's first tick when both run on one side, else from the
query side's next tick. The line is the action's result, success or error, with
the samples as its rows. A `wait` (a duration, `idle` or `until`) SHALL serve
as the action too.

#### Scenario: Sampling a walk
- **WHEN** an agent runs `sample status every 2t while move forward 3`
- **THEN** the result is the move's `ok moved 3.0`, with one row of `status`
  fields every 2 ticks from before the first step to the end of the walk

#### Scenario: Sampling across a wait
- **WHEN** an agent runs `sample status every 20t while wait 200t`
- **THEN** the result is the wait's `ok` with ten rows of `status` fields, the
  first taken before the wait began

#### Scenario: Something other than an action after while
- **WHEN** an agent runs `sample status every 2t while look`
- **THEN** the result is `err syntax` saying `while` takes an action or a
  `wait`
