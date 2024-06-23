grammar FunctionCraft;

// Lexer rules
// The lexer rules define patterns for recognizing tokens like numbers, booleans, strings,
// comments, keywords, identifiers, and operators in the input text. These rules are used
// by the lexer to break the input into a token stream.

program
    : (function | pattern)* main
    ;


main
    :
    FUNCTION
    MAIN { System.out.println("MAIN"); }
    LPAR
    RPAR
    function_body
    END
    ;

function_ptr
    :
    METHOD
    LPAR
    ':'
    (
    IDENTIFIER
    |lambda_function
    )
    RPAR
    ;


statement
    :
    (
        | expr
        | assignment
    ) SEMICOLON
    | if_condition
    | for_loop
    | do_loop
    ;

primitive_function_call
    :
    (
    push
    | puts
    | len
    | chop
    | chomp
    )
    ;


return_function
    : RETURN { System.out.println("RETURN"); }
    (expr)?
    SEMICOLON
    ;

function
    :
    FUNCTION
    name = IDENTIFIER { System.out.println("FuncDec: " + $name.text); }
    function_params
    function_body
    END
    ;

function_params
    :
    LPAR
    (
    function_named_params
    | function_default_params
    | function_named_params COMMA function_default_params
    |
    )
    RPAR
    ;

function_named_params
    :
    IDENTIFIER
    (
    COMMA
    IDENTIFIER
    )*
    ;

function_default_params
    :
    LBRACKET
    IDENTIFIER
    ASSIGN
    (expr)
        (
        COMMA
        IDENTIFIER
        ASSIGN
        (expr)
        )*
    RBRACKET
    ;

function_body
    :
    statement * //TODO
    return_function ?
    ;

lambda_function //TODO
    :
    ARROW { System.out.println("Structure: LAMBDA"); }
    function_params
    LBRACE
    function_body // TODO
    RBRACE
    ;

function_call_util
    :
    (LBRACKET
    (
         list_index
       | expr_arith_plus_minus
    )
    RBRACKET
    (
    LBRACKET
        (
            list_index
           | expr_arith_plus_minus
        )
    RBRACKET
    )*
    (
    LPAR
    (
    expr
        (
        COMMA
        expr
        )*
    )?
    RPAR
    function_call_util
    ))
    |
    (
    LPAR
    (
    expr
        (
        COMMA
        expr
        )*
    )?
    RPAR
    function_call_util
    )
    |
    ;


function_call
    :
    (
    IDENTIFIER
    |lambda_function
    |
    (
    (IDENTIFIER | list)
    (expr_append_util)
    LBRACKET
    (
         list_index
       | expr_arith_plus_minus
    )
    RBRACKET
    (
    LBRACKET
        (
            list_index
           | expr_arith_plus_minus
        )
    RBRACKET
    )*
    )
    )
    { System.out.println("FunctionCall"); }
    LPAR
    (
    expr
        (
        COMMA
        expr
        )*
    )?
    RPAR
    function_call_util
    ;

puts
    :
    PUTS { System.out.println("Built-In: PUTS"); }
    LPAR
    expr
    RPAR
    ;

list
    :
    LBRACKET
    (
        expr
        (
        COMMA expr
        )*
        |
    )
    RBRACKET
    ;

push
    :
    PUSH { System.out.println("Built-In: PUSH"); }
    LPAR
    (
    expr
    COMMA
    expr
    )
    RPAR
    ;

len
    :
    LEN { System.out.println("Built-In: LEN"); }
    LPAR
    expr
    RPAR
    ;

chop
    :
    CHOP { System.out.println("Built-In: CHOP"); }
    LPAR
    expr
    RPAR
    ;

chomp
    :
    CHOMP { System.out.println("Built-In: CHOMP"); }
    LPAR
    expr
    RPAR
    ;

list_index
    :
    (IDENTIFIER | list | function_call)
    (expr_append_util)
    LBRACKET
    (
         list_index
       | expr_arith_plus_minus
    )
    RBRACKET
    (
    LBRACKET
        (
            list_index
           | expr_arith_plus_minus
        )
    RBRACKET
    )*
    ;

expr
  : expr_append
  ;

expr_append
  : expr_logic_or expr_append_util
  ;

expr_append_util
  : APPEND expr_logic_or {System.out.println("Operator: <<");} expr_append_util 
  |
  ;

expr_logic_or
    : LPAR expr RPAR OR LPAR expr RPAR { System.out.println("Operator: ||"); }
    |expr_logic_and
    ;

expr_logic_and
    : LPAR expr RPAR AND LPAR expr RPAR     { System.out.println("Operator: &&"); }
    |expr_rel_eq_neq
    ;

expr_rel_eq_neq
    : expr_rel_cmp expr_rel_eq_neq_util
    ;

expr_rel_eq_neq_util
    : EQL expr_rel_cmp { System.out.println("Operator: =="); } expr_rel_eq_neq_util      
    | NEQ expr_rel_cmp { System.out.println("Operator: !="); } expr_rel_eq_neq_util      
    |
    ;

expr_rel_cmp
    : expr_arith_plus_minus expr_rel_cmp_util
    ;

expr_rel_cmp_util
    : GTR expr_arith_plus_minus  { System.out.println("Operator: >"); } expr_rel_cmp_util     
    | LES expr_arith_plus_minus  { System.out.println("Operator: <"); } expr_rel_cmp_util      
    | GEQ expr_arith_plus_minus  { System.out.println("Operator: >="); } expr_rel_cmp_util      
    | LEQ expr_arith_plus_minus  { System.out.println("Operator: <="); } expr_rel_cmp_util      
    |
    ;

expr_arith_plus_minus
    : expr_arith_mult_div expr_arith_plus_minus_util
    ;

expr_arith_plus_minus_util
    : PLUS expr_arith_mult_div  { System.out.println("Operator: +"); } expr_arith_plus_minus_util       
    | MINUS expr_arith_mult_div { System.out.println("Operator: -"); } expr_arith_plus_minus_util      
    |
    ;

expr_arith_mult_div
    : expr_unary_plus_minus_not_inc_dec expr_arith_mult_div_util
    ;

expr_arith_mult_div_util
    : MULT expr_unary_plus_minus_not_inc_dec { System.out.println("Operator: *"); } expr_arith_mult_div_util       
    | DIV expr_unary_plus_minus_not_inc_dec  { System.out.println("Operator: /"); } expr_arith_mult_div_util        
    | MOD expr_unary_plus_minus_not_inc_dec  { System.out.println("Operator: %"); } expr_arith_mult_div_util        
    |
    ;

expr_unary_plus_minus_not_inc_dec
    : MINUS { System.out.println("Operator: -"); } expr_other
    | NOT   { System.out.println("Operator: !"); } expr_other
    | expr_other
    ;

expr_other
    :
    (
         list_index
        | IDENTIFIER
        | LPAR expr RPAR

    )
    (
          INC       { System.out.println("Operator: ++"); }
        | DEC       { System.out.println("Operator: --"); }
    ) ?
    | FLOAT_VAL
    | INT_VAL
    | function_call
    | lambda_function
    | primitive_function_call
    | function_ptr
    | pattern_call
    | BOOLEAN_VAL
    | STRING_VAL
    | list
    ;

assignment_LHS
    :
    IDENTIFIER
    |
    list_index
    ;

assignment
    :
    name = assignment_LHS
    (
          ASSIGN { System.out.println("Assignment: " + $name.text); }
        | MULTEQ { System.out.println("Assignment: " + $name.text); }
        | DIVEQ  { System.out.println("Assignment: " + $name.text); }
        | MINEQ  { System.out.println("Assignment: " + $name.text); }
        | PLEQ   { System.out.println("Assignment: " + $name.text); }
        | MODEQ  { System.out.println("Assignment: " + $name.text); }
    )
    (expr)//TODO
    ;

if_statement
    :
    expr
    | if_statement_util OR if_statement_util { System.out.println("Operator: ||"); }
    | if_statement_util AND if_statement_util { System.out.println("Operator: &&"); }
    ;

if_statement_util
    :
    LPAR
    if_statement
    RPAR
    ;

if_condition
    : IF     { System.out.println("Decision: IF"); }
      if_statement
      function_body
      (
        ELSEIF    { System.out.println("Decision: ELSEIF"); }
        if_statement
        function_body
      )*
      (
        ELSE    { System.out.println("Decision: ELSE"); }
        function_body
      )?
      END
    ;

function_body_in_loop
    :
    (
    statement
    |NEXT SEMICOLON { System.out.println("Control: NEXT"); }
    |BREAK SEMICOLON { System.out.println("Control: BREAK"); }
    |NEXT IF if_statement SEMICOLON { System.out.println("Control: NEXT"); }
    |BREAK IF if_statement SEMICOLON { System.out.println("Control: BREAK"); }
    )*
    return_function ?
    ;

if_in_loop
    : IF     { System.out.println("Decision: IF"); }
      if_statement
      function_body_in_loop |
      END
      (
        ELSEIF    { System.out.println("Decision: ELSEIF"); }
        if_statement
        function_body_in_loop
        END

      )*
      (
        ELSE    { System.out.println("Decision: ELSE"); }
        function_body_in_loop
        END
      )?
    ;


loop_body
    :
    (
    statement
    |if_in_loop
    |NEXT SEMICOLON { System.out.println("Control: NEXT"); }
    |BREAK SEMICOLON { System.out.println("Control: BREAK"); }
    |NEXT IF if_statement SEMICOLON { System.out.println("Control: NEXT"); }
    |BREAK IF if_statement SEMICOLON { System.out.println("Control: BREAK"); }
    )*
    return_function?
    ;

do_loop
    :
    LOOP { System.out.println("Loop: DO"); }
    DO
    loop_body
    END
    ;

range
    :
    LPAR
    expr
    DOT
    DOT
    expr
    RPAR
    ;

for_loop
    :
    FOR   { System.out.println("Loop: FOR"); }
    IDENTIFIER
    IN
    (range | expr)
    loop_body
    END
    ;


pattern
    :
    PATTERN
    name = IDENTIFIER { System.out.println("PatternDec: " + $name.text); }
    LPAR
    function_named_params
    RPAR
    (PATTERN_INDENT if_statement ASSIGN expr)+
    SEMICOLON
    ;

pattern_call
    :
    IDENTIFIER
    DOT
    MATCH   { System.out.println("Built-In: MATCH"); }
    LPAR
    (
    expr
        (
        COMMA
        expr
        )*
    )?
    RPAR
    ;

// Parser rules
// The parser rules start with the program rule, which defines the overall structure of a
// valid program. They then specify how tokens can be combined to form declarations, control
// structures, expressions, assignments, function calls, and other constructs within a program.
// The parser rules collectively define the syntax of the language.

// Keywords

MAIN:     'main';
FUNCTION: 'def';
PRINT:    'print';
FOR:      'for';
RETURN:   'return';
END:      'end';
IF:       'if';
ELSE:     'else';
ELSEIF:   'elseif';
CHOP:     'chop';
CHOMP:    'chomp';
PUSH:     'push';
PUTS:     'puts';
METHOD:   'method';
LEN:      'len';
PATTERN:  'pattern';
MATCH:    'match';
NEXT:     'next';
BREAK:    'break';
LOOP:     'loop';
DO:       'do';
IN:       'in';

// Type Values

INT_VAL:     [1-9][0-9]* | [0];
FLOAT_VAL:   INT_VAL '.' [0-9]+ | '0.' [0-9]*;
STRING_VAL:  '"' (~ ["\\\r\n] | '\n' | '\t' | '\b')* '"'; //TODO
CHAR_VAL:    '"' (~ ["\\\r\n] | '\n' | '\t' | '\b') '"';
BOOLEAN_VAL: 'true' | 'false';

// Parenthesis

LPAR: '(';
RPAR: ')';

// Brackets (array element access)

LBRACKET: '[';
RBRACKET: ']';

// Arithmetic Operators

PLUS:  '+';
MINUS: '-';
MULT:  '*';
DIV:   '/';
MOD:   '%';
INC:   '++';
DEC:   '--';

// Relational Operators

GEQ: '>=';
LEQ: '<=';
GTR: '>';
LES: '<';
EQL: '==';
NEQ: '!=';

// Logical Operators

AND: '&&';
OR:  '||';
NOT: '!';

// Other Operators

ASSIGN:      '=';
MULTEQ:     '*=';
DIVEQ:      '/=';
MODEQ:      '%=';
PLEQ:       '+=';
MINEQ:      '-=';

// Symbols

LBRACE:    '{';
RBRACE:    '}';
COMMA:     ',';
DOT:       '.';
COLON:     ':';
SEMICOLON: ';';
QUESTION:  '?';


// Other

IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;
ARROW:      '->';
APPEND:     '<<';
PATTERN_INDENT:  ('\r'?'\n')('\t|' | '    |');
SINGLELINECOMMENT:    '#' ~[\r\n]* -> skip;
MULTILINECOMMENT:     '=begin' .*? '=end' -> skip;
WS:         [ \t\r\n]+ -> skip;

// Types

INT_TYPE:       'int';
FLOAT_TYPE:     'float';
STRING_TYPE:     'string';
BOOLEAN_TYPE:   'boolean';
LIST_TYPE:       'list';
FPTR_TYPE:       'fptr';