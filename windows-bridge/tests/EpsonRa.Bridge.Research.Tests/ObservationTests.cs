using EpsonRa.Bridge.Research;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class ObservationTests
    {
        [TestMethod]
        public void CompleteCoverageProducesObservedCountsOnly()
        {
            var before = Snapshot(10, 2, 40, 5, true, true, true);
            var after = Snapshot(11, 3, 42, 8, true, true, true);

            var result = ObservationEvaluator.Compare(before, after);

            Assert.AreEqual("OBSERVED", result.Status);
            Assert.IsTrue(result.Conclusive);
            Assert.AreEqual(1, result.OwnedProcessDelta);
            Assert.AreEqual(1, result.OwnedTcpDelta);
            Assert.AreEqual(2, result.UnrelatedProcessDelta);
            Assert.AreEqual(3, result.UnrelatedTcpDelta);
            Assert.IsNull(result.EndpointDetails);
        }

        [TestMethod]
        public void MissingProcessOrTcpCoverageIsInconclusive()
        {
            foreach (var snapshot in new[]
            {
                Snapshot(1, 1, 0, 0, false, true, true),
                Snapshot(1, 1, 0, 0, true, false, true)
            })
            {
                var result = ObservationEvaluator.Compare(snapshot, Snapshot(1, 1, 0, 0, true, true, true));
                Assert.AreEqual("INCONCLUSIVE", result.Status);
                Assert.IsFalse(result.Conclusive);
            }
        }

        [TestMethod]
        public void ProcessAccessGapOrMissingIpv6CoverageIsInconclusive()
        {
            var processGap = Snapshot(1, 1, 0, 0, true, true, true);
            processGap.ProcessAccessGapCount = 1;
            Assert.AreEqual("INCONCLUSIVE", ObservationEvaluator.Compare(
                processGap,
                Snapshot(1, 1, 0, 0, true, true, true)).Status);

            var ipv6Gap = Snapshot(1, 1, 0, 0, true, true, true);
            ipv6Gap.TcpIpv6SampleAvailable = false;
            Assert.AreEqual("INCONCLUSIVE", ObservationEvaluator.Compare(
                ipv6Gap,
                Snapshot(1, 1, 0, 0, true, true, true)).Status);
        }

        [TestMethod]
        public void AmbiguousPidOwnershipIsInconclusive()
        {
            var result = ObservationEvaluator.Compare(
                Snapshot(1, 1, 0, 0, true, true, false),
                Snapshot(1, 1, 0, 0, true, true, false));

            Assert.AreEqual("INCONCLUSIVE", result.Status);
            Assert.IsFalse(result.Conclusive);
        }

        [TestMethod]
        public void UnrelatedActivityIsRecordedSeparatelyNotAttributedToOwnedWorker()
        {
            var result = ObservationEvaluator.Compare(
                Snapshot(2, 4, 10, 20, true, true, true),
                Snapshot(2, 4, 13, 25, true, true, true));

            Assert.AreEqual(0, result.OwnedProcessDelta);
            Assert.AreEqual(0, result.OwnedTcpDelta);
            Assert.AreEqual(3, result.UnrelatedProcessDelta);
            Assert.AreEqual(5, result.UnrelatedTcpDelta);
        }

        [TestMethod]
        public void InconclusiveReasonsExposeProcessAccessGapCounts()
        {
            var before = Snapshot(0, 0, 0, 0, true, true, true);
            var after = Snapshot(0, 0, 0, 0, true, true, true);
            before.ProcessAccessGapCount = 197;
            after.ProcessAccessGapCount = 185;

            var result = ObservationEvaluator.Compare(before, after);

            Assert.AreEqual("INCONCLUSIVE", result.Status);
            Assert.IsFalse(result.Conclusive);
            CollectionAssert.Contains(result.InconclusiveReasons, "BEFORE_PROCESS_ACCESS_GAPS:197");
            CollectionAssert.Contains(result.InconclusiveReasons, "AFTER_PROCESS_ACCESS_GAPS:185");
        }

        [TestMethod]
        public void InconclusiveReasonsExposeCoverageOwnershipAndWindowFailures()
        {
            var before = Snapshot(0, 0, 0, 0, false, false, false);
            var after = Snapshot(0, 0, 0, 0, true, true, true);
            before.TcpIpv6SampleAvailable = false;
            before.MonotonicTicks = 200;
            after.MonotonicTicks = 100;

            var result = ObservationEvaluator.Compare(before, after);

            CollectionAssert.Contains(result.InconclusiveReasons, "SNAPSHOT_WINDOW_INVALID");
            CollectionAssert.Contains(result.InconclusiveReasons, "BEFORE_PROCESS_SAMPLE_UNAVAILABLE");
            CollectionAssert.Contains(result.InconclusiveReasons, "BEFORE_TCP_SAMPLE_UNAVAILABLE");
            CollectionAssert.Contains(result.InconclusiveReasons, "BEFORE_TCP_IPV6_SAMPLE_UNAVAILABLE");
            CollectionAssert.Contains(result.InconclusiveReasons, "BEFORE_OWNERSHIP_AMBIGUOUS");
        }

        [TestMethod]
        public void ObservedAssessmentHasNoInconclusiveReasons()
        {
            var result = ObservationEvaluator.Compare(
                Snapshot(0, 0, 0, 0, true, true, true),
                Snapshot(0, 0, 0, 0, true, true, true));

            Assert.IsNotNull(result.InconclusiveReasons);
            Assert.AreEqual(0, result.InconclusiveReasons.Length);
        }

        [TestMethod]
        public void PollingLimitationIsAlwaysExplicit()
        {
            var result = ObservationEvaluator.Compare(
                Snapshot(0, 0, 0, 0, true, true, true),
                Snapshot(0, 0, 0, 0, true, true, true));

            StringAssert.Contains(result.Limitation, "short-lived traffic");
            StringAssert.Contains(result.Limitation, "USB");
        }

        private static ObservationSnapshot Snapshot(
            int ownedProcesses,
            int ownedTcp,
            int unrelatedProcesses,
            int unrelatedTcp,
            bool processSampleAvailable,
            bool tcpSampleAvailable,
            bool ownershipUnambiguous)
        {
            return new ObservationSnapshot
            {
                MonotonicTicks = 100,
                ProcessSampleAvailable = processSampleAvailable,
                TcpSampleAvailable = tcpSampleAvailable,
                TcpIpv6SampleAvailable = tcpSampleAvailable,
                OwnershipUnambiguous = ownershipUnambiguous,
                OwnedProcessCount = ownedProcesses,
                OwnedTcpCount = ownedTcp,
                UnrelatedProcessCount = unrelatedProcesses,
                UnrelatedTcpCount = unrelatedTcp
            };
        }
    }
}
