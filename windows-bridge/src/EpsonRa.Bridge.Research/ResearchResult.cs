namespace EpsonRa.Bridge.Research
{
    public sealed class ResearchResult
    {
        public int SchemaVersion { get; set; } = 1;
        public string Status { get; set; }
        public bool Success { get; set; }
        public string Cleanup { get; set; }
        public string Error { get; set; }

        public int? Machine { get; set; }
        public long? CorFlags { get; set; }
        public string Architecture { get; set; }

        public string EligibleName { get; set; }
        public int? EligibleConnectionNumber { get; set; }
        public int? EligibleTypeNumber { get; set; }
    }
}
