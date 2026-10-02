namespace EpsonRa.Bridge.Research
{
    public sealed class ObservationSnapshot
    {
        public long MonotonicTicks { get; set; }
        public bool ProcessSampleAvailable { get; set; }
        public bool TcpSampleAvailable { get; set; }
        public bool TcpIpv6SampleAvailable { get; set; }
        public bool OwnershipUnambiguous { get; set; }
        public int ProcessAccessGapCount { get; set; }
        public int ProcessAccessDeniedCount { get; set; }
        public int ProcessExitedCount { get; set; }
        public int ProcessUnsupportedCount { get; set; }
        public int ProcessModuleUnavailableCount { get; set; }
        public int ProcessOtherFailureCount { get; set; }
        public int OwnedProcessCount { get; set; }
        public int OwnedTcpCount { get; set; }
        public int UnrelatedProcessCount { get; set; }
        public int UnrelatedTcpCount { get; set; }
    }

    public sealed class ObservationAssessment
    {
        public string Status { get; set; }
        // Compatibility flag: usable sampled evidence, never proof of continuous absence.
        public bool Conclusive { get; set; }
        public int? OwnedProcessDelta { get; set; }
        public int? OwnedTcpDelta { get; set; }
        public int? UnrelatedProcessDelta { get; set; }
        public int? UnrelatedTcpDelta { get; set; }
        public string EndpointDetails { get; set; }
        public string Limitation { get; set; }
        public string[] InconclusiveReasons { get; set; }
        public bool EventTraceComplete { get; set; }
        public int EventCount { get; set; }
        public string[] StageEvents { get; set; }
    }

    public sealed class StageEvent
    {
        public string Name { get; set; }
        public long MonotonicTicks { get; set; }
    }

    public static class ObservationEvaluator
    {
        private const string PollingLimitation =
            "Polling cannot prove absence of short-lived traffic or USB communication.";

        public static ObservationAssessment Compare(ObservationSnapshot before, ObservationSnapshot after)
        {
            var reasons = new System.Collections.Generic.List<string>();
            var beforeValid = Valid(before);
            var afterValid = Valid(after);

            if (!beforeValid) reasons.Add("BEFORE_SNAPSHOT_INVALID");
            if (!afterValid) reasons.Add("AFTER_SNAPSHOT_INVALID");

            if (beforeValid && afterValid && after.MonotonicTicks < before.MonotonicTicks)
                reasons.Add("SNAPSHOT_WINDOW_INVALID");

            AddSnapshotReasons(reasons, before, "BEFORE");
            AddSnapshotReasons(reasons, after, "AFTER");

            var conclusive = reasons.Count == 0;

            return new ObservationAssessment
            {
                Status = conclusive ? "OBSERVED" : "INCONCLUSIVE",
                Conclusive = conclusive,
                OwnedProcessDelta = conclusive ? (int?)(after.OwnedProcessCount - before.OwnedProcessCount) : null,
                OwnedTcpDelta = conclusive ? (int?)(after.OwnedTcpCount - before.OwnedTcpCount) : null,
                UnrelatedProcessDelta = conclusive ? (int?)(after.UnrelatedProcessCount - before.UnrelatedProcessCount) : null,
                UnrelatedTcpDelta = conclusive ? (int?)(after.UnrelatedTcpCount - before.UnrelatedTcpCount) : null,
                EndpointDetails = null,
                Limitation = PollingLimitation,
                InconclusiveReasons = reasons.ToArray()
            };
        }

        private static void AddSnapshotReasons(
            System.Collections.Generic.List<string> reasons,
            ObservationSnapshot sample,
            string prefix)
        {
            if (sample == null)
                return;

            if (!sample.ProcessSampleAvailable)
                reasons.Add(prefix + "_PROCESS_SAMPLE_UNAVAILABLE");
            if (!sample.TcpSampleAvailable)
                reasons.Add(prefix + "_TCP_SAMPLE_UNAVAILABLE");
            if (!sample.TcpIpv6SampleAvailable)
                reasons.Add(prefix + "_TCP_IPV6_SAMPLE_UNAVAILABLE");
            if (sample.ProcessAccessGapCount > 0)
                reasons.Add(prefix + "_PROCESS_ACCESS_GAPS:" + sample.ProcessAccessGapCount);
            if (sample.ProcessAccessDeniedCount > 0)
                reasons.Add(prefix + "_PROCESS_ACCESS_DENIED:" + sample.ProcessAccessDeniedCount);
            if (sample.ProcessExitedCount > 0)
                reasons.Add(prefix + "_PROCESS_EXITED:" + sample.ProcessExitedCount);
            if (sample.ProcessUnsupportedCount > 0)
                reasons.Add(prefix + "_PROCESS_UNSUPPORTED:" + sample.ProcessUnsupportedCount);
            if (sample.ProcessModuleUnavailableCount > 0)
                reasons.Add(prefix + "_PROCESS_MODULE_UNAVAILABLE:" + sample.ProcessModuleUnavailableCount);
            if (sample.ProcessOtherFailureCount > 0)
                reasons.Add(prefix + "_PROCESS_OTHER_FAILURE:" + sample.ProcessOtherFailureCount);
            if (!sample.OwnershipUnambiguous)
                reasons.Add(prefix + "_OWNERSHIP_AMBIGUOUS");
        }

        private static bool Valid(ObservationSnapshot sample)
        {
            return sample != null && sample.MonotonicTicks >= 0 && sample.ProcessAccessGapCount >= 0 &&
                sample.ProcessAccessDeniedCount >= 0 && sample.ProcessExitedCount >= 0 &&
                sample.ProcessUnsupportedCount >= 0 && sample.ProcessModuleUnavailableCount >= 0 &&
                sample.ProcessOtherFailureCount >= 0 &&
                sample.OwnedProcessCount >= 0 && sample.OwnedTcpCount >= 0 &&
                sample.UnrelatedProcessCount >= 0 && sample.UnrelatedTcpCount >= 0;
        }
    }
}

