using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;

namespace EpsonRa.Bridge.Readiness
{
    public enum CheckStatus
    {
        PRESENT,
        MISSING,
        INVALID,
        UNVERIFIED
    }

    public sealed class ReadinessCheck
    {
        public ReadinessCheck(string name, CheckStatus status, string detail)
        {
            if (string.IsNullOrWhiteSpace(name))
            {
                throw new ArgumentException("Check name is required.", nameof(name));
            }

            Name = name;
            Status = status;
            Detail = detail ?? string.Empty;
        }

        public string Name { get; }
        public CheckStatus Status { get; }
        public string Detail { get; }
    }

    public sealed class ReadinessReport
    {
        private static readonly HashSet<string> InformationalChecks =
            new HashSet<string>(StringComparer.Ordinal)
            {
                "license",
                "nativeRuntime",
                "rcPlusVersion"
            };

        public ReadinessReport(string installRoot, IReadOnlyList<ReadinessCheck> checks)
        {
            InstallRoot = installRoot;
            var detached = (checks ?? Array.Empty<ReadinessCheck>()).ToArray();
            Checks = new ReadOnlyCollection<ReadinessCheck>(detached);

            var required = detached
                .Where(check => check != null && !InformationalChecks.Contains(check.Name))
                .ToArray();
            Ready = required.Length > 0 &&
                required.All(check => check.Status == CheckStatus.PRESENT);
        }

        public string InstallRoot { get; }
        public IReadOnlyList<ReadinessCheck> Checks { get; }
        public bool Ready { get; }
    }
}
