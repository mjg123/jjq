grammar JQ2;

jq: piped_filter;

piped_filter
  : comma_filter ('|' comma_filter)*  #pipedFilter
  ;

comma_filter
  : paren_op (',' paren_op)*  #commaFilter
  ;

paren_op
  : '(' piped_filter ')'
  | operation
  ;

operation
  : '.'                                   #identityOperation

  | access_operation+                     #multiAccessOperation

  | json_string                           #stringLiteralOperation
  | json_number                           #numberLiteralOperation
  | ('[' ']' | '[' comma_filter ']')      #arrayLiteralOperation
  | ('{' '}' | '{' pair (',' pair)* '}')  #objectLiteralOperation
  ;

access_operation
  : object_access_operation        #objectAccessOperation
  | array_access_operation         #arrayAccessOperation
  | object_array_access_operation  #objectArrayAccessOperation
  ;

object_access_operation: '.' IDENTIFIER;
array_access_operation: '.[' json_number ']';  // not quite right, should only allow INTs, will get NumberFormatExceptions at runtime
object_array_access_operation: '.[' json_string ']';

pair
  : json_string ':' paren_op
  ;

json_string
  : STRING
  ;

STRING
  : '"' (ESC | SAFECODEPOINT)* '"'
  ;

json_number : NUMBER;

IDENTIFIER
  : [a-zA-Z$_][a-zA-Z0-9$_]*
  ;

fragment SAFECODEPOINT
   : ~ ["\\\u0000-\u001F]
   ;

fragment ESC
   : '\\' (["\\/bfnrt] | UNICODE)
   ;

fragment UNICODE
   : 'u' HEX HEX HEX HEX
   ;

fragment HEX
   : [0-9a-fA-F]
   ;

NUMBER
  : '-'? INT ('.' DECIMAL_PART)? EXP?
  ;

DECIMAL_PART
  : [0-9]+
  ;

// this is a fragment, which means it can't match by
// itself, only as part of another token (ie NUMBER)
// If not like this then (eg) 123 is ambiguous as it's
// a NUMBER _and_ an INT
fragment INT
  : '0'
  | [1-9] [0-9]*
  ;

EXP
   : [Ee] [+\-]? INT
   ;

WS
   : [ \t\n\r] + -> skip
   ;
