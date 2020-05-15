package lol.gilliard.jjq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JJQExample {

    public static void main(String[] args) {

        JsonNode input = getInputNode();
        //  { "a": "hello",
        //    "b": {"c": ["d", "e", "f"]},
        //    "g": [1, 2, {"h": "i"}]}

        Stream<JsonNode> outputs = JJQ.runFilter(input,
            ".a , { \"new-value\": .b.c.[1] }, [( .g | .[2] | .h , .)]");

        // outputs is a Stream<JsonNode> containing:
        //   "hello"                <--- json string literal
        //   {"new-value": "e"}     <--- json object
        //   ["i", {"h": "i"}]      <--- json array


        System.out.println(outputs.collect(Collectors.toList()));
    }


    private static JsonNode getInputNode() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            return mapper.readTree("{ \"a\": \"hello\"," +
                "\"b\": {\"c\": [\"d\", \"e\", \"f\"]}," +
                "\"g\": [1, 2, {\"h\": \"i\"}]}");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
