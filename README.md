# JJQ - jq for Java

This is a proof-of-concept of an expression language for manipulating JSON in Java, heavily inspired by  [jq](https://stedolan.github.io/jq/).
With JJQ you can filter, manipulate and transform JSON, and return multiple results if you need to. It allows code like this:

```java
JsonNode input = getInputNode();
  //  { "a": "hello",
  //    "b": {"c": ["d", "e", "f"]},
  //    "g": [1, 2, {"h": "i"}]}

Stream<JsonNode> outputs = JJQ.runFilter(
      input,
      ".a, { \"new-value\": .b.c.[1] }, [( .g | .[2] | .h , .)]");

  // outputs is a Stream<JsonNode> containing:
  //   "hello"                <--- json string literal
  //   {"new-value": "e"}     <--- json object
  //   ["i", {"h": "i"}]      <--- json array
```

[[this code in the repo](https://github.com/mjg123/jjq/blob/master/src/main/java/lol/gilliard/jjq/JJQExample.java#L14-L25)]

## A request

If a library like this would be useful for you, please let me know by telling me on twitter, I am [@MaximumGilliard](https://twitter.com/MaximumGilliard). I'd love to hear from you and find out what you'd like.

## Filters

Like `jq`, this works on the principle of **filters**. A filter takes one or more JSON nodes and processes them independently, each time returning one or more JSON nodes. The current implementation uses Jackson's `JsonNode` as its basic type.

Filters can extract values from their input, for example `.a` is a filter which turns `{"a": 123}` into `123`.

Filters can be joined together using a `|` (pipe) operator which passes the output of the filter to the _left_ into the filter on the _right_, or a `,` (comma) operator which passes the same input to filters on both sides and collects the results. Commas have higher precedence than pipes, but you can use `()` (parens) to control this.

Filters can also create new JSON objects, arrays and values from scratch. You can combine all this to define really powerful manipulations on JSON in
a short amount of code. 

## Examples

Here are a few types of filters which are already possible with this POC

### Extracting values from JSON

|Filter| Input | Filter expression | Output |
|---|---|---|---|
|Identity|`{"a": 123}`|`.`|`{"a": 123}`|
|Object access|`{"a": 123}`|`.a`|`123`|
|Object access (array syntax)|`{"a": 123}`|`.["a"]`|`123`|
|Array access|`["one", "two", "three"]`|`.[0]`|`"one"`|
|Chained accessors|`{"a": ["one", "two", "three"]}`|`.a.[1]`|`"two"`|

### Creating new JSON values

These ignore their input and produce constant output

|Filter| Input | Filter expression | Output |
|---|---|---|---|
|String literal|any|`"new string"`|`"new string"`|
|Numeric literal|any|`123.456e3`|`123456`|
|Array literal|any|`[1, 2, 3]`|`[1, 2, 3]`|
|Object literal|any|`{"name": "Matthew"}`|`{"name": "Matthew"}`|

### Extraction into new JSON values

Values in object literals can be supplied by filters which operate on their input

|Filter| Input | Filter expression | Output |
|---|---|---|---|
|New array|`{"a": "one", "b": "two"}`|`[.a, .b]`|`["one", "two"]`|
|New object|`{"a": "one", "b": "two"}`|`{"new-a": .a, "new-b": .b]`|`{"new-a": "one", "new-b": "two"}]`|

### Combining filters with pipes and commas

Here we start to see filters that can produce multiple outputs. The current implementation returns these as a `Stream<JsonNode>`.

|Filter| Input | Filter expression | Output(s) |
|---|---|---|---|
|Pipe operator|`{"a": [1, 2, 3]}`|`.a \| .[0]`|`1`|
|Comma operator|`{"a": "one", "b": "two"}`|`.a, .b`|`"one"` `"two"`|
|Pipes and commas|`{"a": [1,2,3], "b": [4,5,6]}`|`.a, .b \| .[1]`| `2` `5` |

### Controlling precedence with parens

|Filter| Input | Filter expression | Output(s) |
|---|---|---|---|
|Without parens|`{"a": {"b": "one"}, "c": "two"`|`.a \| .b , .c`|`"one"` `null`|
|With parens|`{"a": {"b": "one"}, "c": "two"`|`(.a \| .b) , .c`|`"one"` `"two"`|



## More complex stuff

The examples above are not a complete list of what's possible. Generally filters can be combined in any way that makes sense, and to any depth. This means you can take a really large JSON document and filter it down to the shape you want, containing only the things you need before turning it into Java objects, which can save a lot of work dealing with traversing `JsonNode` instances in your code. In this way it's similar to [JsonPath](https://github.com/json-path/JsonPath), but more powerful and the syntax is more regular.

## To-do

There is a lot to do, after all this is a POC. Very near the top of my list would be the ability to map the results onto custom classes, and the addition of a few of the more complex filters that `jq` has. I do not intend to implement _all_ of the power of `jq` - in fact `jq` is incredibly powerful and rather complex - it's [a dynamically-typed functional programming language with second-class higher-order functions of dynamic extent](https://github.com/stedolan/jq/wiki/jq-Language-Description#The-jq-Language), which is a lot more than I think is needed for a Java library. The goal here is to provide an easy and powerful way to manipulate JSON before turning it over to your Java code.

## License
This project is under the MIT license.


Made with 💚 by Matthew



















