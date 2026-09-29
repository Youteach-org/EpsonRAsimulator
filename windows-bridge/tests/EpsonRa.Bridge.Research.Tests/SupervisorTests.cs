using System;
using System.Diagnostics;
using System.IO;
using EpsonRa.Bridge.Research.Supervisor;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class SupervisorTests
    {
        [TestMethod]
        public void NormalWorkerResultCompletes()
        {
            var result = Run("normal", 5);
            Assert.AreEqual(0, result.ExitCode);
            Assert.AreEqual("COMPLETED", result.Status);
            Assert.IsTrue(result.Success);
        }

        [TestMethod]
        public void NonzeroWorkerIsFailure()
        {
            Assert.AreEqual(3, Run("nonzero", 5).ExitCode);
        }

        [DataTestMethod]
        [DataRow("malformed")]
        [DataRow("null")]
        [DataRow("array")]
        [DataRow("multiple")]
        [DataRow("absent")]
        [DataRow("contradiction")]
        [DataRow("stderr")]
        [DataRow("wrong-schema")]
        public void InvalidWorkerResultIsFailure(string mode)
        {
            Assert.AreEqual(3, Run(mode, 5).ExitCode);
        }

        [TestMethod]
        public void FailedCleanupOverridesClaimedSuccess()
        {
            var result = Run("failed-cleanup", 5);
            Assert.AreEqual(3, result.ExitCode);
            Assert.IsFalse(result.Success);
        }

        [TestMethod]
        public void TimeoutReturns124AndUnknownCleanup()
        {
            var sw = Stopwatch.StartNew();
            var result = Run("timeout", 1);
            sw.Stop();
            Assert.AreEqual(124, result.ExitCode);
            Assert.AreEqual("INCONCLUSIVE_TIMEOUT", result.Status);
            Assert.AreEqual("UNKNOWN", result.Cleanup);
            Assert.IsTrue(sw.Elapsed < TimeSpan.FromSeconds(4));
        }

        [TestMethod]
        public void FloodedStdoutDoesNotDefeatDeadline()
        {
            var sw = Stopwatch.StartNew();
            var result = Run("flood", 1);
            sw.Stop();
            Assert.AreEqual(3, result.ExitCode);
            Assert.AreEqual("OutputLimit", result.Error);
            Assert.IsTrue(sw.Elapsed < TimeSpan.FromSeconds(4));
        }

        [TestMethod]
        public void InheritedPipeCannotExtendDeadline()
        {
            var watch = Stopwatch.StartNew();
            var result = Run("inherited", 1);
            Assert.AreEqual(124, result.ExitCode);
            Assert.IsFalse(result.Success);
            Assert.IsTrue(watch.Elapsed < TimeSpan.FromSeconds(4));
        }

        [TestMethod]
        public void InvalidTimeoutIsRejectedBeforeLaunch()
        {
            var result = Run("normal", 0);
            Assert.AreEqual(64, result.ExitCode);
        }

        private static SupervisorResult Run(string mode, int timeout)
        {
            var worker = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..",
                "EpsonRa.Bridge.Research.Fixture", "bin", "Release", "net48",
                "EpsonRa.Bridge.Research.Fixture.exe"));

            var requestFile = Path.GetTempFileName();
            File.WriteAllText(requestFile, "{\"stage\":\"MetadataOnly\",\"installRoot\":\"C:\\\\EpsonRC70\",\"target\":null,\"serverInstance\":null,\"approved\":false}");
            try
            {
                return WorkerSupervisor.Run(new WorkerRequest
                {
                    WorkerPath = worker,
                    RequestPath = requestFile,
                    TimeoutSeconds = timeout,
                    ExtraArguments = new[] { mode }
                });
            }
            finally
            {
                try { File.Delete(requestFile); } catch { }
            }
        }
    }
}
