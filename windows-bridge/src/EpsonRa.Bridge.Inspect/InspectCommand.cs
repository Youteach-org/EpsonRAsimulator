using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Web.Script.Serialization;
using EpsonRa.Bridge.Readiness;

namespace EpsonRa.Bridge.Inspect
{
    public sealed class InspectCommand
    {
        private const string Usage = "Usage: EpsonRa.Bridge.Inspect inspect [--install-root <absolutePath>]";
        private readonly ReadinessInspector inspector;
        private readonly JavaScriptSerializer serializer = new JavaScriptSerializer();

        public InspectCommand(ReadinessInspector inspector)
        {
            this.inspector = inspector ?? throw new ArgumentNullException(nameof(inspector));
        }

        public int Run(string[] args, TextWriter stdout, TextWriter stderr)
        {
            if (stdout == null) throw new ArgumentNullException(nameof(stdout));
            if (stderr == null) throw new ArgumentNullException(nameof(stderr));

            string installRoot;
            if (!TryParse(args, out installRoot))
            {
                WriteError(stdout, "INVALID_ARGUMENTS", "Command line is invalid.");
                stderr.WriteLine(Usage);
                return 64;
            }

            try
            {
                var report = inspector.Inspect(installRoot);
                WriteReport(stdout, report);
                return report.Ready ? 0 : 2;
            }
            catch
            {
                WriteError(stdout, "HOST_ERROR", "Readiness inspection failed.");
                return 70;
            }
        }

        private static bool TryParse(string[] args, out string installRoot)
        {
            installRoot = null;
            if (args == null || args.Length == 0 ||
                !string.Equals(args[0], "inspect", StringComparison.Ordinal))
            {
                return false;
            }

            if (args.Length == 1)
            {
                return true;
            }

            if (args.Length != 3 ||
                !string.Equals(args[1], "--install-root", StringComparison.Ordinal) ||
                !IsFullyQualifiedWindowsPath(args[2]))
            {
                return false;
            }

            installRoot = args[2];
            return true;
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

        private void WriteReport(TextWriter stdout, ReadinessReport report)
        {
            var checks = report.Checks
                .Where(check => check != null)
                .Select(check => new Dictionary<string, object>
                {
                    { "name", check.Name },
                    { "status", check.Status.ToString() },
                    { "detail", check.Detail }
                })
                .ToArray();

            var payload = new Dictionary<string, object>
            {
                { "schemaVersion", 1 },
                { "installRoot", report.InstallRoot },
                { "ready", report.Ready },
                { "checks", checks }
            };

            stdout.WriteLine(serializer.Serialize(payload));
        }

        private void WriteError(TextWriter stdout, string errorCode, string message)
        {
            var payload = new Dictionary<string, object>
            {
                { "schemaVersion", 1 },
                { "errorCode", errorCode },
                { "message", message }
            };

            stdout.WriteLine(serializer.Serialize(payload));
        }
    }
}
