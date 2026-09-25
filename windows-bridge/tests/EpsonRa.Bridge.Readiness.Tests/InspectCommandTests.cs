using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Web.Script.Serialization;
using EpsonRa.Bridge.Inspect;
using EpsonRa.Bridge.Readiness;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Readiness.Tests
{
    [TestClass]
    public class InspectCommandTests
    {
        [TestMethod]
        public void FailedReadinessWritesOneParseableJsonDocumentAndExit2()
        {
            var env = new CommandEnvironment();
            env.Checks = new[] {
                new ReadinessCheck("installRoot", CheckStatus.MISSING, "Directory absent")
            };
            var command = new InspectCommand(new ReadinessInspector(env));
            var output = new StringWriter();
            var errors = new StringWriter();

            var exit = command.Run(
                new[] { "inspect", "--install-root", @"C:\Missing" },
                output,
                errors);

            Assert.AreEqual(2, exit);
            var json = new JavaScriptSerializer().DeserializeObject(output.ToString());
            Assert.IsNotNull(json);
            Assert.AreEqual(string.Empty, errors.ToString());
            StringAssert.Contains(output.ToString(), "\"schemaVersion\":1");
            StringAssert.Contains(output.ToString(), "\"ready\":false");
        }

        [TestMethod]
        public void JsonEscapesDiagnosticQuotesNewlinesAndBackslashes()
        {
            var env = new CommandEnvironment();
            env.Checks = new[] {
                new ReadinessCheck(
                    "installRoot",
                    CheckStatus.MISSING,
                    "quote=\" line\npath=C:\\Denied")
            };
            var command = new InspectCommand(new ReadinessInspector(env));
            var output = new StringWriter();

            var exit = command.Run(
                new[] { "inspect", "--install-root", @"C:\Missing" },
                output,
                new StringWriter());

            Assert.AreEqual(2, exit);
            var json = new JavaScriptSerializer().DeserializeObject(output.ToString());
            Assert.IsNotNull(json);
        }

        [TestMethod]
        public void UnknownDuplicateAndMissingFlagsReturnUsageWithoutNativeAction()
        {
            var cases = new[] {
                new[] { "unknown" },
                new[] { "inspect", "--bad" },
                new[] { "inspect", "--install-root" },
                new[] { "inspect", "--install-root", @"C:EpsonRC70" },
                new[] { "inspect", "--install-root", @"\EpsonRC70" },
                new[] { "inspect", "--install-root", @"C:\A", "--install-root", @"C:\B" }
            };

            foreach (var args in cases)
            {
                var env = new CommandEnvironment();
                var command = new InspectCommand(new ReadinessInspector(env));
                var output = new StringWriter();
                var errors = new StringWriter();

                var exit = command.Run(args, output, errors);

                Assert.AreEqual(64, exit);
                Assert.AreEqual(0, env.DiscoveryCalls);
                Assert.AreEqual(0, env.InspectCalls);
                Assert.IsNotNull(new JavaScriptSerializer().DeserializeObject(output.ToString()));
                Assert.IsFalse(string.IsNullOrWhiteSpace(errors.ToString()));
            }
        }

        [TestMethod]
        public void UnexpectedHostErrorReturnsStableJsonWithoutSecretExceptionText()
        {
            var env = new CommandEnvironment
            {
                Unexpected = new InvalidOperationException(@"secret C:\Private\sdk failure")
            };
            var command = new InspectCommand(new ReadinessInspector(env));
            var output = new StringWriter();
            var errors = new StringWriter();

            var exit = command.Run(
                new[] { "inspect", "--install-root", @"C:\EpsonRC70" },
                output,
                errors);

            Assert.AreEqual(70, exit);
            Assert.IsNotNull(new JavaScriptSerializer().DeserializeObject(output.ToString()));
            Assert.IsFalse(output.ToString().Contains("secret"));
            Assert.IsFalse(output.ToString().Contains(@"C:\Private"));
            Assert.IsFalse(errors.ToString().Contains("secret"));
            Assert.IsFalse(errors.ToString().Contains(@"C:\Private"));
        }

        private sealed class CommandEnvironment : IReadinessEnvironment
        {
            public IReadOnlyList<ReadinessCheck> Checks { get; set; } = new[] {
                new ReadinessCheck("installRoot", CheckStatus.PRESENT, "Directory present"),
                new ReadinessCheck("rcPlusExecutable", CheckStatus.PRESENT, "Executable present"),
                new ReadinessCheck("apiAssembly", CheckStatus.PRESENT, "Assembly present")
            };

            public Exception Unexpected { get; set; }
            public int DiscoveryCalls { get; private set; }
            public int InspectCalls { get; private set; }

            public IReadOnlyList<string> DiscoverRoots()
            {
                DiscoveryCalls++;
                if (Unexpected != null) throw Unexpected;
                return new[] { @"C:\EpsonRC70" };
            }

            public IReadOnlyList<ReadinessCheck> InspectRoot(string root)
            {
                InspectCalls++;
                if (Unexpected != null) throw Unexpected;
                return Checks.ToArray();
            }
        }
    }
}
