using System;
using System.Collections.Generic;
using System.IO;
using EpsonRa.Bridge.Research;
using EpsonRa.Bridge.Research.Supervisor;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class ExternalObservationTests
    {
        [TestMethod]
        public void ValidInitializeTraceAndCoverageAreObservedWithoutEndpoints()
        {
            var events = TempEvents(ValidInitializeEvents());
            try
            {
                var result = ExternalObservation.Evaluate(
                    Snapshot(0, 0, 10, 20, true, true, true),
                    Snapshot(1, 2, 12, 24, true, true, true),
                    events,
                    Stage.InitializeObserve);

                Assert.AreEqual("OBSERVED", result.Status);
                Assert.IsTrue(result.Conclusive);
                Assert.IsTrue(result.EventTraceComplete);
                Assert.AreEqual(10, result.EventCount);
                Assert.AreEqual(1, result.OwnedProcessDelta);
                Assert.AreEqual(2, result.OwnedTcpDelta);
                Assert.IsNull(result.EndpointDetails);
            }
            finally { File.Delete(events); }
        }

        [TestMethod]
        public void MissingMalformedOrNonMonotonicEventsAreInconclusive()
        {
            var missing = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".jsonl");
            Assert.AreEqual("INCONCLUSIVE", ExternalObservation.Evaluate(
                Snapshot(0,0,0,0,true,true,true),
                Snapshot(0,0,0,0,true,true,true),
                missing,
                Stage.InitializeObserve).Status);

            var malformed = TempEvents(new[] { "{not-json" });
            var nonMonotonic = TempEvents(new[]
            {
                "{\"name\":\"before:Load\",\"monotonicTicks\":2}",
                "{\"name\":\"after:Load\",\"monotonicTicks\":1}"
            });
            try
            {
                Assert.AreEqual("INCONCLUSIVE", ExternalObservation.Evaluate(
                    Snapshot(0,0,0,0,true,true,true),
                    Snapshot(0,0,0,0,true,true,true),
                    malformed,
                    Stage.InitializeObserve).Status);
                Assert.AreEqual("INCONCLUSIVE", ExternalObservation.Evaluate(
                    Snapshot(0,0,0,0,true,true,true),
                    Snapshot(0,0,0,0,true,true,true),
                    nonMonotonic,
                    Stage.InitializeObserve).Status);
            }
            finally
            {
                File.Delete(malformed);
                File.Delete(nonMonotonic);
            }
        }

        [TestMethod]
        public void SamplingGapOverridesCompleteEventTrace()
        {
            var events = TempEvents(ValidInitializeEvents());
            try
            {
                var result = ExternalObservation.Evaluate(
                    Snapshot(0,0,0,0,false,true,true),
                    Snapshot(0,0,0,0,true,true,true),
                    events,
                    Stage.InitializeObserve);

                Assert.AreEqual("INCONCLUSIVE", result.Status);
                Assert.IsFalse(result.Conclusive);
            }
            finally { File.Delete(events); }
        }

        [TestMethod]
        public void SupervisorFailsClosedWhenActivatingObservationIsInconclusive()
        {
            var requestFile = Path.GetTempFileName();
            try
            {
                File.WriteAllText(requestFile,
                    "{\"stage\":\"InitializeObserve\",\"installRoot\":\"C:\\\\SyntheticEpson\",\"target\":\"C4 Sample\",\"serverInstance\":1,\"approved\":true}");

                var result = WorkerSupervisor.Run(
                    new WorkerRequest
                    {
                        WorkerPath = WorkerPath(),
                        RequestPath = requestFile,
                        TimeoutSeconds = 5,
                        ExtraArguments = new[] { "observed" }
                    },
                    new FixedObservationMonitorFactory(
                        Snapshot(0,0,0,0,false,true,true),
                        Snapshot(0,0,0,0,true,true,true)));

                Assert.IsFalse(result.Success);
                Assert.AreEqual(3, result.ExitCode);
                Assert.AreEqual("INCONCLUSIVE_OBSERVATION", result.Status);
                Assert.IsNotNull(result.Observation);
                Assert.AreEqual("INCONCLUSIVE", result.Observation.Status);
            }
            finally { File.Delete(requestFile); }
        }

        private static ObservationSnapshot Snapshot(
            int ownedProcesses, int ownedTcp, int unrelatedProcesses, int unrelatedTcp,
            bool processAvailable, bool tcpAvailable, bool ownershipUnambiguous)
        {
            return new ObservationSnapshot
            {
                MonotonicTicks = 1,
                OwnedProcessCount = ownedProcesses,
                OwnedTcpCount = ownedTcp,
                UnrelatedProcessCount = unrelatedProcesses,
                UnrelatedTcpCount = unrelatedTcp,
                ProcessSampleAvailable = processAvailable,
                TcpSampleAvailable = tcpAvailable,
                TcpIpv6SampleAvailable = tcpAvailable,
                OwnershipUnambiguous = ownershipUnambiguous
            };
        }

        private static string[] ValidInitializeEvents()
        {
            return new[]
            {
                "{\"name\":\"before:Load\",\"monotonicTicks\":1}",
                "{\"name\":\"after:Load\",\"monotonicTicks\":2}",
                "{\"name\":\"before:Construct\",\"monotonicTicks\":3}",
                "{\"name\":\"after:Construct\",\"monotonicTicks\":4}",
                "{\"name\":\"before:SetServerInstance\",\"monotonicTicks\":5}",
                "{\"name\":\"after:SetServerInstance\",\"monotonicTicks\":6}",
                "{\"name\":\"before:Initialize\",\"monotonicTicks\":7}",
                "{\"name\":\"after:Initialize\",\"monotonicTicks\":8}",
                "{\"name\":\"before:Dispose\",\"monotonicTicks\":9}",
                "{\"name\":\"after:Dispose\",\"monotonicTicks\":10}"
            };
        }

        private static string TempEvents(string[] lines)
        {
            var path = Path.GetTempFileName();
            File.WriteAllLines(path, lines);
            return path;
        }

        private static string WorkerPath()
        {
            return Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..",
                "EpsonRa.Bridge.Research.Fixture", "bin", "Release", "net48",
                "EpsonRa.Bridge.Research.Fixture.exe"));
        }

        private sealed class FixedObservationMonitorFactory : IObservationMonitorFactory
        {
            private readonly ObservationSnapshot _before;
            private readonly ObservationSnapshot _after;

            public FixedObservationMonitorFactory(ObservationSnapshot before, ObservationSnapshot after)
            {
                _before = before;
                _after = after;
            }

            public IObservationMonitor Create(string installRoot)
            {
                return new FixedObservationMonitor(_before, _after);
            }
        }

        private sealed class FixedObservationMonitor : IObservationMonitor
        {
            public FixedObservationMonitor(ObservationSnapshot before, ObservationSnapshot after)
            {
                Before = before;
                After = after;
            }

            public ObservationSnapshot Before { get; private set; }
            public ObservationSnapshot After { get; private set; }
            public void WorkerStarted(int processId) { }
            public void Poll() { }
            public void Dispose() { }
        }
    }
}
