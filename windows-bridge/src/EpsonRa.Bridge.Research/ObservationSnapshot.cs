namespace EpsonRa.Bridge.Research
{
    public sealed class ObservationSnapshot
    {
        public long MonotonicTicks { get; set; }
        public bool ProcessSampleAvailable { get; set; }
        public bool TcpSampleAvailable { get; set; }
        public bool OwnershipUnambiguous { get; set; }
        public int OwnedProcessCount { get; set; }
        public int OwnedTcpCount { get; set; }
        public int UnrelatedProcessCount { get; set; }
        public int UnrelatedTcpCount { get; set; }
    }

    public sealed class ObservationAssessment
    {
        public string Status { get; set; }
        public bool Conclusive { get; set; }
        public int OwnedProcessDelta { get; set; }
        public int OwnedTcpDelta { get; set; }
        public int UnrelatedProcessDelta { get; set; }
        public int UnrelatedTcpDelta { get; set; }
        public string EndpointDetails { get; set; }
        public string Limitation { get; set; }
        public bool EventTraceComplete { get; set; }
        public int EventCount { get; set; }
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
                before != null &&
                after != null &&
                before.ProcessSampleAvailable &&
                after.ProcessSampleAvailable &&
                before.TcpSampleAvailable &&
                after.TcpSampleAvailable &&
                before.OwnershipUnambiguous &&
                after.OwnershipUnambiguous;

            return new ObservationAssessment
            {
                Status = conclusive ? "OBSERVED" : "INCONCLUSIVE",
                Conclusive = conclusive,
                OwnedProcessDelta = Delta(before == null ? 0 : before.OwnedProcessCount, after == null ? 0 : after.OwnedProcessCount),
                OwnedTcpDelta = Delta(before == null ? 0 : before.OwnedTcpCount, after == null ? 0 : after.OwnedTcpCount),
                UnrelatedProcessDelta = Delta(before == null ? 0 : before.UnrelatedProcessCount, after == null ? 0 : after.UnrelatedProcessCount),
                UnrelatedTcpDelta = Delta(before == null ? 0 : before.UnrelatedTcpCount, after == null ? 0 : after.UnrelatedTcpCount),
                EndpointDetails = null,
                Limitation = PollingLimitation
            };
        }

        private static int Delta(int before, int after)
        {
            return after - before;
        }
    }
}
