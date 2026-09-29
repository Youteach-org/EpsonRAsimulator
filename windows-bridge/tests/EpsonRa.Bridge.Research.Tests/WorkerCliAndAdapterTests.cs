using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Web.Script.Serialization;
using EpsonRa.Bridge.Research.Worker;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class WorkerCliAndAdapterTests
    {
        [TestMethod]
        public void MetadataOnlyCliProducesOneCompletedJson()
        {
            var root = NewTempRoot();
            try
            {
                var request = Path.Combine(root, "request.json");
                var events = Path.Combine(root, "events.jsonl");
                WriteRequest(request, "MetadataOnly", root, false);

                var run = RunWorker("--request", request, "--events", events);

                Assert.AreEqual(0, run.ExitCode);
                Assert.AreEqual(string.Empty, run.Stderr);
                var json = ParseObject(run.Stdout);
                Assert.AreEqual(1, json["schemaVersion"]);
                Assert.AreEqual("COMPLETED", json["status"]);
                Assert.AreEqual(true, json["success"]);
                Assert.AreEqual("CONFIRMED", json["cleanup"]);
                Assert.IsTrue(File.Exists(events));
                Assert.AreEqual(0, File.ReadAllLines(events).Length);
            }
            finally { TryDelete(root); }
        }

        [TestMethod]
        public void LoadOnlyCliLoadsSyntheticAssemblyAndEmitsOnlyLoadEvents()
        {
            var root = CreateSyntheticInstallRoot();
            try
            {
                var request = Path.Combine(root, "request.json");
                var events = Path.Combine(root, "events.jsonl");
                WriteRequest(request, "LoadOnly", root, true);

                var run = RunWorker("--request", request, "--events", events);

                Assert.AreEqual(0, run.ExitCode);
                Assert.AreEqual(string.Empty, run.Stderr);
                Assert.AreEqual("COMPLETED", ParseObject(run.Stdout)["status"]);

                var lines = File.ReadAllLines(events);
                Assert.AreEqual(2, lines.Length);
                Assert.AreEqual("before:Load", ParseObject(lines[0])["name"]);
                Assert.AreEqual("after:Load", ParseObject(lines[1])["name"]);
            }
            finally { TryDelete(root); }
        }

        [TestMethod]
        public void WorkerCliRejectsUnknownArgumentsWithOneStructuredResult()
        {
            var run = RunWorker("--unknown", "value");

            Assert.AreEqual(64, run.ExitCode);
            Assert.AreEqual(string.Empty, run.Stderr);
            var json = ParseObject(run.Stdout);
            Assert.AreEqual("INVALID_ARGUMENTS", json["status"]);
            Assert.AreEqual(false, json["success"]);
        }

        [TestMethod]
        public void ReflectionAdapterMapsDescriptorAndUsesStringOverload()
        {
            var root = CreateSyntheticInstallRoot();
            try
            {
                using (var api = new InstalledApiAdapter())
                {
                    api.Load(root);
                    api.Construct();
                    api.SetServerInstance(7);
                    api.Initialize();

                    var connections = api.GetConnections();
                    Assert.AreEqual(2, connections.Count);
                    Assert.AreEqual("C4 Sample", connections[1].Name);
                    Assert.AreEqual(2, connections[1].ConnectionNumber);
                    Assert.AreEqual(3, connections[1].TypeNumber);
                    Assert.AreEqual("Virtual", connections[1].TypeName);

                    api.ConnectByName("C4 Sample");
                    var current = api.GetCurrentConnection();
                    Assert.AreEqual("C4 Sample", current.Name);
                    Assert.AreEqual(2, current.ConnectionNumber);
                    Assert.AreEqual(3, current.TypeNumber);
                    api.Disconnect();
                }
            }
            finally { TryDelete(root); }
        }

        [TestMethod]
        public void ReflectionAdapterRejectsMissingAssemblyAndWrongConnectName()
        {
            var missing = NewTempRoot();
            try
            {
                using (var api = new InstalledApiAdapter())
                    ExpectException<FileNotFoundException>(() => api.Load(missing));
            }
            finally { TryDelete(missing); }

            var root = CreateSyntheticInstallRoot();
            try
            {
                using (var api = new InstalledApiAdapter())
                {
                    api.Load(root);
                    api.Construct();
                    ExpectException<InvalidOperationException>(() => api.ConnectByName("Other"));
                }
            }
            finally { TryDelete(root); }
        }

        private static void ExpectException<T>(Action action) where T : Exception
        {
            try
            {
                action();
                Assert.Fail("Expected exception " + typeof(T).FullName + ".");
            }
            catch (T)
            {
            }
        }

        private static void WriteRequest(string path, string stage, string root, bool approved)
        {
            var json = new Dictionary<string, object>
            {
                { "stage", stage },
                { "installRoot", root },
                { "target", null },
                { "serverInstance", null },
                { "approved", approved }
            };
            File.WriteAllText(path, new JavaScriptSerializer().Serialize(json));
        }

        private static Dictionary<string, object> ParseObject(string text)
        {
            var value = new JavaScriptSerializer().DeserializeObject(text) as Dictionary<string, object>;
            Assert.IsNotNull(value, "Expected exactly one JSON object.");
            return value;
        }

        private static string CreateSyntheticInstallRoot()
        {
            var root = NewTempRoot();
            var exe = Path.Combine(root, "exe");
            Directory.CreateDirectory(exe);
            File.Copy(SyntheticFixturePath(), Path.Combine(exe, "RCAPINet.dll"));
            return root;
        }

        private static string SyntheticFixturePath()
        {
            return Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..",
                "fixtures", "RCAPINet.TestFixture", "bin", "Release", "net48", "RCAPINet.dll"));
        }

        private static CliRun RunWorker(params string[] args)
        {
            var psi = new ProcessStartInfo
            {
                FileName = typeof(InstalledApiAdapter).Assembly.Location,
                Arguments = QuoteArguments(args),
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };

            using (var process = Process.Start(psi))
            {
                var stdout = process.StandardOutput.ReadToEnd();
                var stderr = process.StandardError.ReadToEnd();
                if (!process.WaitForExit(5000))
                {
                    process.Kill();
                    process.WaitForExit(1000);
                    Assert.Fail("Worker CLI exceeded test deadline.");
                }
                return new CliRun { ExitCode = process.ExitCode, Stdout = stdout.Trim(), Stderr = stderr };
            }
        }

        private static string QuoteArguments(string[] args)
        {
            var quoted = new string[args.Length];
            for (var i = 0; i < args.Length; i++)
                quoted[i] = "\"" + (args[i] ?? string.Empty).Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";
            return string.Join(" ", quoted);
        }

        private static void TryDelete(string path)
        {
            try
            {
                if (Directory.Exists(path))
                    Directory.Delete(path, true);
            }
            catch (UnauthorizedAccessException)
            {
                // Assembly.LoadFrom keeps the synthetic DLL locked until the test AppDomain exits.
            }
            catch (IOException)
            {
                // Same lifetime constraint; CI workspace cleanup owns the remaining synthetic temp file.
            }
        }

        private static string NewTempRoot()
        {
            var path = Path.Combine(Path.GetTempPath(), "epson-ra-worker-test-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(path);
            return path;
        }

        private sealed class CliRun
        {
            public int ExitCode;
            public string Stdout;
            public string Stderr;
        }
    }
}
