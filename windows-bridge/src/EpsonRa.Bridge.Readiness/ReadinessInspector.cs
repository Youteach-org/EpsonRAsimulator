using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace EpsonRa.Bridge.Readiness
{
    public sealed class ReadinessInspector
    {
        private readonly IReadinessEnvironment environment;

        public ReadinessInspector(IReadinessEnvironment environment)
        {
            this.environment = environment ?? throw new ArgumentNullException(nameof(environment));
        }

        public ReadinessReport Inspect(string explicitRoot)
        {
            if (explicitRoot != null)
            {
                if (string.IsNullOrWhiteSpace(explicitRoot))
                {
                    return Failure(
                        null,
                        CheckStatus.INVALID,
                        "Explicit install root is blank.");
                }

                string normalized;
                if (!TryNormalizeAbsoluteRoot(explicitRoot, out normalized))
                {
                    return Failure(
                        null,
                        CheckStatus.INVALID,
                        "Explicit install root must be absolute.");
                }

                return InspectSelectedRoot(normalized);
            }

            IReadOnlyList<string> discovered;
            try
            {
                discovered = environment.DiscoverRoots() ?? Array.Empty<string>();
            }
            catch (UnauthorizedAccessException)
            {
                return Failure(null, CheckStatus.INVALID, "Installation discovery access denied.");
            }
            catch (IOException)
            {
                return Failure(null, CheckStatus.INVALID, "Installation discovery failed.");
            }
            catch (ArgumentException)
            {
                return Failure(null, CheckStatus.INVALID, "Installation discovery metadata is invalid.");
            }

            var normalizedRoots = new List<string>();
            foreach (var root in discovered)
            {
                string normalized;
                if (!TryNormalizeAbsoluteRoot(root, out normalized))
                {
                    return Failure(null, CheckStatus.INVALID, "Discovered install root is invalid.");
                }

                if (!normalizedRoots.Any(existing =>
                    string.Equals(existing, normalized, StringComparison.OrdinalIgnoreCase)))
                {
                    normalizedRoots.Add(normalized);
                }
            }

            if (normalizedRoots.Count == 0)
            {
                return Failure(null, CheckStatus.MISSING, "No EPSON RC+ installation root was discovered.");
            }

            if (normalizedRoots.Count != 1)
            {
                return Failure(null, CheckStatus.INVALID, "Multiple EPSON RC+ installation roots are ambiguous.");
            }

            return InspectSelectedRoot(normalizedRoots[0]);
        }

        private ReadinessReport InspectSelectedRoot(string root)
        {
            IReadOnlyList<ReadinessCheck> inspected;
            try
            {
                inspected = environment.InspectRoot(root) ?? Array.Empty<ReadinessCheck>();
            }
            catch (UnauthorizedAccessException)
            {
                return Failure(root, CheckStatus.INVALID, "Install root access denied.");
            }
            catch (IOException)
            {
                return Failure(root, CheckStatus.INVALID, "Install root metadata could not be read.");
            }
            catch (ArgumentException)
            {
                return Failure(root, CheckStatus.INVALID, "Install root metadata is invalid.");
            }

            var checks = inspected.Where(check => check != null).ToList();
            EnsureInformationalCheck(
                checks,
                "license",
                "License status is not inspected by readiness discovery.");
            EnsureInformationalCheck(
                checks,
                "nativeRuntime",
                "Native Epson runtime activation is not performed by readiness discovery.");
            return new ReadinessReport(root, checks);
        }

        private static ReadinessReport Failure(
            string root,
            CheckStatus status,
            string detail)
        {
            return new ReadinessReport(
                root,
                new[]
                {
                    new ReadinessCheck("installRoot", status, detail),
                    new ReadinessCheck(
                        "license",
                        CheckStatus.UNVERIFIED,
                        "License status is not inspected by readiness discovery."),
                    new ReadinessCheck(
                        "nativeRuntime",
                        CheckStatus.UNVERIFIED,
                        "Native Epson runtime activation is not performed by readiness discovery.")
                });
        }

        private static void EnsureInformationalCheck(
            ICollection<ReadinessCheck> checks,
            string name,
            string detail)
        {
            if (!checks.Any(check => string.Equals(check.Name, name, StringComparison.Ordinal)))
            {
                checks.Add(new ReadinessCheck(name, CheckStatus.UNVERIFIED, detail));
            }
        }

        private static bool IsFullyQualifiedWindowsPath(string candidate)
        {
            if (string.IsNullOrWhiteSpace(candidate) || !Path.IsPathRooted(candidate))
            {
                return false;
            }

            if (candidate.Length >= 2 &&
                IsDirectorySeparator(candidate[0]) &&
                IsDirectorySeparator(candidate[1]))
            {
                return true;
            }

            var root = Path.GetPathRoot(candidate);
            return !string.IsNullOrEmpty(root) &&
                root.Length >= 3 &&
                root[1] == Path.VolumeSeparatorChar &&
                IsDirectorySeparator(root[2]);
        }

        private static bool IsDirectorySeparator(char value)
        {
            return value == Path.DirectorySeparatorChar ||
                value == Path.AltDirectorySeparatorChar;
        }

        private static bool TryNormalizeAbsoluteRoot(
            string candidate,
            out string normalized)
        {
            normalized = null;
            if (!IsFullyQualifiedWindowsPath(candidate))
            {
                return false;
            }

            try
            {
                var full = Path.GetFullPath(candidate);
                var pathRoot = Path.GetPathRoot(full);
                if (!string.Equals(full, pathRoot, StringComparison.OrdinalIgnoreCase))
                {
                    full = full.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
                }

                normalized = full;
                return true;
            }
            catch (ArgumentException)
            {
                return false;
            }
            catch (NotSupportedException)
            {
                return false;
            }
            catch (PathTooLongException)
            {
                return false;
            }
        }
    }
}
