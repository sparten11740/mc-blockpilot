// The command language's lexer, lexicon, parser and grammar printer are covered completely by the unit tests (design
// D13): check fails below 100% of their lines and branches. Applied by both loaders' builds.
import java.math.BigDecimal
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

apply(plugin = "jacoco")

val parserClasses = listOf(
    "dev.blockpilot.api.Lexicon*",
    "dev.blockpilot.protocol.Lexer*",
    "dev.blockpilot.protocol.Parser*",
    "dev.blockpilot.protocol.Grammar*",
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    reports {
        html.required = true
        xml.required = true
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("test")
    violationRules {
        rule {
            element = "CLASS"
            includes = parserClasses
            limit {
                counter = "LINE"
                minimum = BigDecimal("1.00")
            }
            limit {
                counter = "BRANCH"
                minimum = BigDecimal("1.00")
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
