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
            var conclusive =
                Valid(before) && Valid(after) &&
                after.MonotonicTicks >= before.MonotonicTicks &&
                before.ProcessSampleAvailable &&
                after.ProcessSampleAvailable &&
                before.TcpSampleAvailable &&
                after.TcpSampleAvailable &&
                before.TcpIpv6SampleAvailable &&
                after.TcpIpv6SampleAvailable &&
                before.ProcessAccessGapCount == 0 &&
                after.ProcessAccessGapCount == 0 &&
                before.OwnershipUnambiguous &&
                after.OwnershipUnambiguous;

            return new ObservationAssessment
            {
                Status = conclusive ? "OBSERVED" : "INCONCLUSIVE",
                Conclusive = conclusive,
                OwnedProcessDelta = conclusive ? (int?)(after.OwnedProcessCount - before.OwnedProcessCount) : null,
                OwnedTcpDelta = conclusive ? (int?)(after.OwnedTcpCount - before.OwnedTcpCount) : null,
                UnrelatedProcessDelta = conclusive ? (int?)(after.UnrelatedProcessCount - before.UnrelatedProcessCount) : null,
                UnrelatedTcpDelta = conclusive ? (int?)(after.UnrelatedTcpCount - before.UnrelatedTcpCount) : null,
                EndpointDetails = null,
                Limitation = PollingLimitation
            };
        }

        private static bool Valid(ObservationSnapshot sample)
        {
            return sample != null && sample.MonotonicTicks >= 0 && sample.ProcessAccessGapCount >= 0 &&
                sample.OwnedProcessCount >= 0 && sample.OwnedTcpCount >= 0 &&
                sample.UnrelatedProcessCount >= 0 && sample.UnrelatedTcpCount >= 0;
        }
    }
}

