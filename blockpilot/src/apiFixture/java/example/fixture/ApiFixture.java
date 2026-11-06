package example.fixture;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;

/** An integration as a mod built against blockpilot-api 2.0.0 would write it; compiled against that release's jar. */
public final class ApiFixture {
    private ApiFixture() {
    }

    public static void register() {
        BlockpilotApi.register("fixture", CommandSpec.query("greet", (context, args) -> {
            String greeting = (args.has("loud") ? "HELLO " : "hello ") + args.word("name");
            return Result.ok(new Fields().put("greeting", greeting, greeting));
        }).form(form -> form.arg("name", ArgType.WORD).optional(loud -> loud.keyword("loud", "loud"))));
    }
}
