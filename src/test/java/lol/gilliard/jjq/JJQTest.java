package lol.gilliard.jjq;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JJQTest {

    // NB there's a helper method to turn ' into " - just makes this easier to read
    private String obj = "{" +
        "'a': 'hello', " +
        "'b': [1,2,3],  " +
        "'c': {'d': ['e', 'f', 'g']}," +
        "'h': {'i': 'j'}," +
        "'k': {'l':'m', 'n':'o'}, " +
        "'aBLAH123-foo::$$': 'weird' ," +
        "'aA123': 'mixed'}";

    @Test
    public void identityOp() {
        List<JsonNode> result = JJQ.of("[1,2,3]", ".");
        assertEquals("[[1,2,3]]", result.toString());
    }

    @Test
    public void singleObjectAccess(){
        List<JsonNode> result = JJQ.of(obj, ".a");
        assertEquals("[\"hello\"]", result.toString());
    }

    @Test
    public void multiObjectAccess(){
        List<JsonNode> result = JJQ.of(obj, ".c.[\"d\"].[0]");
        assertEquals("[\"e\"]", result.toString());
    }

    @Test
    public void pipedObjectAccess(){
        List<JsonNode> result = JJQ.of(obj, ".h | .i");
        assertEquals("[\"j\"]", result.toString());
    }

    @Test
    public void arrayAccess(){
        List<JsonNode> result = JJQ.of(obj, ".b | .[0]");
        assertEquals("[1]", result.toString());
    }

    @Test
    public void arrayAccessWithNonInt(){
        assertThrows(NumberFormatException.class,
            () -> JJQ.of(obj, ".b | .[0.5]"));
    }

    @Test
    public void stringLiteral(){
        List<JsonNode> result = JJQ.of(obj, "\"literal\"");
        assertEquals("[\"literal\"]", result.toString());
    }

    @Test
    public void integerLiteralZero(){
        List<JsonNode> result = JJQ.of(obj, "0");
        assertEquals("[0]", result.toString());
    }

    @Test
    public void integerLiteral(){
        List<JsonNode> result = JJQ.of(obj, "12345");
        assertEquals("[12345]", result.toString());
    }

    @Test
    public void integerLiteralWithExponent(){
        List<JsonNode> result = JJQ.of(obj, "-123.456e5");
        assertEquals("[-12345600]", result.toString());
    }

    @Test
    public void doubleLiteral(){
        List<JsonNode> result = JJQ.of(obj, "-123.456e2");
        assertEquals("[-12345.6]", result.toString());
    }

    @Test
    public void commaFilter(){
        List<JsonNode> result = JJQ.of(obj, ".a , .b");
        assertEquals("[\"hello\", [1,2,3]]", result.toString());
    }

    @Test
    public void pipesAndCommaFilter(){
        List<JsonNode> result = JJQ.of(obj, ".k | .l , .n");
        assertEquals("[\"m\", \"o\"]", result.toString());
    }

    @Test
    public void pipesAndCommaFilterWithParenPrecedence(){
        List<JsonNode> result = JJQ.of(obj, "(.k | .l) , .a");
        assertEquals("[\"m\", \"hello\"]", result.toString());
    }

    @Test
    public void emptyArrayLiteral(){
        List<JsonNode> result = JJQ.of(obj, "[]");
        assertEquals("[[]]", result.toString());
    }

    @Test
    public void emptyArrayLiteralWithSpaces(){
        List<JsonNode> result = JJQ.of(obj, " [ ] ");
        assertEquals("[[]]", result.toString());
    }

    @Test
    public void arrayLiteralWithStringAndNumberLiteralsInside(){
        List<JsonNode> result = JJQ.of(obj, "[\"str\" , 1234]");
        assertEquals("[[\"str\",1234]]", result.toString());
    }

    @Test
    public void arrayLiteralWithAccessOperators(){
        List<JsonNode> result = JJQ.of(obj, "[.a,.a,(.h|.i)]");
        assertEquals("[[\"hello\",\"hello\",\"j\"]]", result.toString());
    }

    @Test
    public void emptyObjectLiteral(){
        List<JsonNode> result = JJQ.of(obj, "{}");
        assertEquals("[{}]", result.toString());
    }

    @Test
    public void nonemptyObjectLiteral(){
        List<JsonNode> result = JJQ.of(obj, "{\"a\":\"b\", \"d\": [1,2,3]}");
        assertEquals("[{\"d\":[1,2,3],\"a\":\"b\"}]", result.toString());
    }

    @Test
    public void filtersInObjectLiteral(){
        List<JsonNode> result = JJQ.of(obj, "{\"aa\": .a, \"bb\": .b, \"kk\": (.k|.n) }");
        assertEquals("[{\"aa\":\"hello\",\"bb\":[1,2,3],\"kk\":\"o\"}]", result.toString());
    }

    @Test
    public void objectLiteralWithCommasBracketed(){
        // NB JQ doesn't support unbracketed comma expressions as object values
        //    ie '{"AA": 1,2}' is a syntax error
        List<JsonNode> result = JJQ.of(obj, "{\"AA\": (1,2)}");
        assertEquals("[{\"AA\":1}, {\"AA\":2}]", result.toString());
    }

    @Test
    public void arraySyntaxObjectAccess(){
        List<JsonNode> result = JJQ.of(obj, ".[\"a\"]");
        assertEquals("[\"hello\"]", result.toString());
    }

    @Test
    public void arraySyntaxObjectAccessFunnyName(){
        List<JsonNode> result = JJQ.of(obj, ".[\"aBLAH123-foo::$$\"]");
        assertEquals("[\"weird\"]", result.toString());
    }

    @Test
    public void objectAccessCapitalsAndNumbers(){
        List<JsonNode> result = JJQ.of(obj, ".aA123");
        assertEquals("[\"mixed\"]", result.toString());
    }

    @Test
    public void objectCrossProductWithLiterals(){
        List<JsonNode> result = JJQ.of(obj, "{\"aa\": (1,2), \"bb\" : (\"a\",\"b\")}");
        assertEquals("[{\"aa\":1,\"bb\":\"a\"}, {\"aa\":1,\"bb\":\"b\"}, {\"aa\":2,\"bb\":\"a\"}, {\"aa\":2,\"bb\":\"b\"}]", result.toString());
    }

    @Test
    public void objectCrossProductWithAccessors(){
        List<JsonNode> result = JJQ.of(obj, "{\"aa\": (.a,.b), \"bb\" : ((.k|.l),(.k|.n))}");
        assertEquals("[{\"aa\":\"hello\",\"bb\":\"m\"}, {\"aa\":\"hello\",\"bb\":\"o\"}, {\"aa\":[1,2,3],\"bb\":\"m\"}, {\"aa\":[1,2,3],\"bb\":\"o\"}]", result.toString());
    }

}
