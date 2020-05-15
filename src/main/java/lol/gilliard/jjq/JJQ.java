package lol.gilliard.jjq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JJQ {

    private static ObjectMapper mapper = new ObjectMapper();

    public static List<JsonNode> of(String json, String expression) {
        try {
            JsonNode node = mapper.readTree(quote(json));
            return getJq2Filter(expression).run(Stream.of(node)).collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Stream<JsonNode> runFilter(JsonNode input, String expression){
        return getJq2Filter(expression).run(Stream.of(input));
    }

    private static String quote(String s) {
        return s.replaceAll("'", "\"");
    }

    private static JQ2Filter getJq2Filter(String expression) {
        JQ2Lexer lexer = new JQ2Lexer(CharStreams.fromString(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JQ2Parser parser = new JQ2Parser(tokens);

        JQ2Visitor jq2Visitor = new JQ2Visitor();
        return jq2Visitor.visit(parser.jq());
    }

    private static class JQ2Visitor extends JQ2BaseVisitor<JQ2Filter> {

        private String stripQuotes(String s) {
            // we use this to avoid ambiguities between string contents and identifiers in the grammar
            // strings include the " marks as part of the token, but we don't actually want them in code
            return s.substring(1, s.length() - 1);
        }

        @Override
        public JQ2Filter visitIdentityOperation(JQ2Parser.IdentityOperationContext ctx) {
            return new JQ2IdentityOperation();
        }

        @Override
        public JQ2Filter visitPipedFilter(JQ2Parser.PipedFilterContext ctx) {

            if (ctx.comma_filter().size() == 1) {
                return this.visit(ctx.comma_filter(0));
            }

            List<JQ2Filter> subFilters = new ArrayList<>();

            for (JQ2Parser.Comma_filterContext subFilter : ctx.comma_filter()) {
                subFilters.add(this.visit(subFilter));
            }

            return new JQ2PipedFilter(subFilters);
        }

        @Override
        public JQ2Filter visitCommaFilter(JQ2Parser.CommaFilterContext ctx) {

            if (ctx.paren_op().size() == 1) {
                return this.visit(ctx.paren_op(0));
            }

            List<JQ2Filter> subFilters = new ArrayList<>();

            for (JQ2Parser.Paren_opContext op : ctx.paren_op()) {
                subFilters.add(this.visit(op));
            }

            return new JQ2CommaFilter(subFilters);
        }

        @Override
        public JQ2Filter visitParen_op(JQ2Parser.Paren_opContext ctx) {
            if (ctx.piped_filter() != null) {
                return this.visit(ctx.piped_filter());
            } else {
                return this.visit(ctx.operation());
            }
        }

        @Override
        public JQ2Filter visitObjectAccessOperation(JQ2Parser.ObjectAccessOperationContext ctx) {
            return new JQ2ObjectAccessOperation(ctx.object_access_operation().IDENTIFIER().getText());
        }

        @Override
        public JQ2Filter visitMultiAccessOperation(JQ2Parser.MultiAccessOperationContext ctx) {
            List<JQ2Filter> subFilters = new ArrayList<>();

            for (JQ2Parser.Access_operationContext subFilter : ctx.access_operation()) {
                subFilters.add(this.visit(subFilter));
            }

            return new JQ2PipedFilter(subFilters);
        }

        @Override
        public JQ2Filter visitObjectArrayAccessOperation(JQ2Parser.ObjectArrayAccessOperationContext ctx) {
            return new JQ2ObjectAccessOperation(
                stripQuotes(
                    ctx.object_array_access_operation().json_string().STRING().getText()));
        }

        @Override
        public JQ2Filter visitArrayAccessOperation(JQ2Parser.ArrayAccessOperationContext ctx) {
            return new JQ2ArrayAccessOperation(ctx.array_access_operation().json_number().getText());
        }

        @Override
        public JQ2Filter visitStringLiteralOperation(JQ2Parser.StringLiteralOperationContext ctx) {
            return new JQ2StringLiteralOperation(
                stripQuotes(
                    ctx.json_string().STRING().getText()));
        }

        @Override
        public JQ2Filter visitNumberLiteralOperation(JQ2Parser.NumberLiteralOperationContext ctx) {
            return new JQ2NumberLiteralOperation(ctx.json_number().getText());
        }

        @Override
        public JQ2Filter visitArrayLiteralOperation(JQ2Parser.ArrayLiteralOperationContext ctx) {
            if (ctx.comma_filter() == null) {
                return new JQ2EmptyArrayLiteralOperation();
            }
            return new JQ2ArrayLiteralOperation(this.visit(ctx.comma_filter()));
        }

        @Override
        public JQ2Filter visitObjectLiteralOperation(JQ2Parser.ObjectLiteralOperationContext ctx) {

            if (ctx.pair().isEmpty()) {
                return new JQ2EmptyObjectLiteralOperation();
            }

            List<JsonPair> pairs = ctx.pair().stream().map(pair ->
                new JsonPair(
                    pair.json_string().STRING().getText(),
                    this.visit(pair.paren_op())))
                .collect(Collectors.toList());

            return new JQ2ObjectLiteralOperation(pairs);
        }

    }

    private static class JsonPair {
        public final String key;
        public final JQ2Filter operation;

        private JsonPair(String key, JQ2Filter operation) {
            this.key = key.substring(1, key.length() - 1);
            this.operation = operation;
        }
    }

    // ----------------


    private interface JQ2Filter {
        Stream<JsonNode> run(Stream<JsonNode> inputs);
    }

    private static class JQ2PipedFilter implements JQ2Filter {
        private final List<JQ2Filter> subFilters;

        private JQ2PipedFilter(List<JQ2Filter> subFilters) {
            this.subFilters = subFilters;
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            for (JQ2Filter subFilter : subFilters) {
                inputs = subFilter.run(inputs);
            }
            return inputs;
        }
    }

    private static class JQ2CommaFilter implements JQ2Filter {
        private final List<JQ2Filter> subFilters;

        private JQ2CommaFilter(List<JQ2Filter> subFilters) {
            this.subFilters = subFilters;
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.flatMap(node ->
                subFilters.stream().flatMap(op ->
                    op.run(Stream.of(node))));
        }
    }

    private static class JQ2ObjectAccessOperation implements JQ2Filter {
        private final String opText;

        private JQ2ObjectAccessOperation(String opText) {
            this.opText = opText;
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.map(node -> node.get(opText));
        }
    }

    private static class JQ2ArrayAccessOperation implements JQ2Filter {
        private final int index;

        private JQ2ArrayAccessOperation(String i) {
            this.index = Integer.parseInt(i);
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.map(node -> node.get(index));
        }
    }

    private static class JQ2StringLiteralOperation implements JQ2Filter {
        private final String s;

        private JQ2StringLiteralOperation(String s) {
            this.s = s;
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.map(node -> new TextNode(s));
        }
    }

    private static class JQ2NumberLiteralOperation implements JQ2Filter {
        private final double d;

        private JQ2NumberLiteralOperation(String s) {
            // dirty. assumes Double.parseDouble can handle all JS-format numbers
            // cosmic coincidence?
            this.d = Double.parseDouble(s);
        }


        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {

            if (d % 1 == 0) {
                // also dirty - the number might not fit in an int
                return inputs.map(node -> new IntNode((int) d));
            }

            return inputs.map(node -> new DoubleNode(d));
        }
    }

    private static class JQ2EmptyArrayLiteralOperation implements JQ2Filter {
        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.map(node -> new ArrayNode(null));
        }
    }

    private static class JQ2ArrayLiteralOperation implements JQ2Filter {
        private final JQ2Filter contents;

        private JQ2ArrayLiteralOperation(JQ2Filter contents) {
            this.contents = contents;
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.map(node -> new ArrayNode(null, contents.run(Stream.of(node)).collect(Collectors.toList())));
        }
    }

    private static class JQ2EmptyObjectLiteralOperation implements JQ2Filter {
        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.map(i -> new ObjectNode(null));
        }
    }

    private static class JQ2ObjectLiteralOperation implements JQ2Filter {
        private final List<JsonPair> pairs;

        private JQ2ObjectLiteralOperation(List<JsonPair> pairs) {
            this.pairs = pairs;
        }

        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs.flatMap(node -> {

                    Map<String, Stream<JsonNode>> entries = new HashMap<>();

                    pairs.forEach(pair ->
                        entries.put(
                            pair.key,
                            pair.operation.run(Stream.of(node)))
                    );

                    return crossProduct(entries).map(m -> new ObjectNode(null, m));
                }
            );
        }

        private static <T> Stream<Map<String, T>> crossProduct(Map<String, Stream<T>> orig) {

            // This awful awful function makes a cross-product of the values in orig
            // eg:
            //   {a:[b]}            -> [{a:b}]
            //   {a:[b,c]}          -> [{a:b}, {a:c}]
            //   {a:[b,c], x:[y]}   -> [{a:b, x:y}, {a:c, x:y}]
            //   {a:[b,c], x:[y,z]} -> [{a:b, x:y}, {a:b, x:z}, {a:c, x:y}, {a:c, x:z}]
            // etc

            List<Map<String, T>> ms = new ArrayList<>();

            for (Map.Entry<String, Stream<T>> origEntry : orig.entrySet()) {

                List<Map<String, T>> expanded = new ArrayList<>();

                // expand Map<String, List<T>> into List<Map<String, T>>
                origEntry.getValue().forEach(thisValue -> {
                    Map<String, T> newMap = new HashMap<>();
                    newMap.put(origEntry.getKey(), thisValue);
                    expanded.add(newMap);
                });

                if (ms.isEmpty()) {
                    // populate an empty map
                    ms.addAll(expanded);


                } else {
                    // or, if there is already stuff, create a new List...
                    List<Map<String, T>> newMs = new ArrayList<>();


                    // ...populate it with new maps
                    //  which are the cross-product of what's already there and what's new
                    for (Map<String, T> m : ms) {
                        for (Map<String, T> exp : expanded) {
                            Map<String, T> newM = new HashMap<>();
                            newM.putAll(m);
                            newM.putAll(exp);
                            newMs.add(newM);
                        }
                    }

                    ms = newMs;
                }

            }

            return ms.stream();
        }
    }

    private static class JQ2IdentityOperation implements JQ2Filter {
        @Override
        public Stream<JsonNode> run(Stream<JsonNode> inputs) {
            return inputs;
        }
    }


}
