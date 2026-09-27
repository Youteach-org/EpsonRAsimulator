using System;
using System.Collections.Generic;
using System.Linq;

namespace EpsonRa.Bridge.Readiness
{
    public enum TargetKind
    {
        Virtual,
        Physical,
        Unknown
    }

    public sealed class TargetDescriptor
    {
        public TargetDescriptor(string id, TargetKind kind)
        {
            Id = id;
            Kind = kind;
        }

        public string Id { get; }
        public TargetKind Kind { get; }
    }

    public enum Eligibility
    {
        Eligible,
        Missing,
        Ambiguous,
        Rejected
    }

    public static class VirtualTargetPolicy
    {
        public static Eligibility Select(
            string requestedId,
            IReadOnlyList<TargetDescriptor> candidates)
        {
            if (string.IsNullOrWhiteSpace(requestedId) || candidates == null)
            {
                return Eligibility.Rejected;
            }

            var snapshot = candidates.ToArray();
            if (snapshot.Any(candidate => candidate == null))
            {
                return Eligibility.Rejected;
            }

            var matches = snapshot
                .Where(candidate =>
                    string.Equals(
                        candidate.Id,
                        requestedId,
                        StringComparison.Ordinal))
                .ToArray();

            if (matches.Length == 0)
            {
                return Eligibility.Missing;
            }

            if (matches.Length > 1)
            {
                return Eligibility.Ambiguous;
            }

            return matches[0].Kind == TargetKind.Virtual
                ? Eligibility.Eligible
                : Eligibility.Rejected;
        }
    }
}
