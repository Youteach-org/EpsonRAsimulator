using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Web.Script.Serialization;
using Microsoft.Diagnostics.Runtime;

namespace EpsonRa.Bridge.Research.StackReport
{
    internal sealed class FrameDocument
    {
        public string display { get; set; }
    }

    internal sealed class ThreadDocument
    {
        public uint osThreadId { get; set; }
        public int managedThreadId { get; set; }
        public List<FrameDocument> frames { get; set; }
    }

    internal sealed class StackDocument
    {
        public int schemaVersion { get; set; }
        public string status { get; set; }
        public bool success { get; set; }
        public string error { get; set; }
        public string source { get; set; }
        public int? targetPid { get; set; }
        public string architecture { get; set; }
        public string clrVersion { get; set; }
        public string dacSource { get; set; }
        public List<ThreadDocument> threads { get; set; }
    }

    internal sealed class Options
    {
        public string DumpPath { get; set; }
        public int? ProcessId { get; set; }
        public string ExpectedImage { get; set; }
        public DateTime? ExpectedStartUtc { get; set; }

        public bool IsLiveSnapshot
        {
            get { return ProcessId.HasValue; }
        }
    }

    internal static class Program
    {
        private static int Main(string[] args)
        {
            Options options;
            string error;
            if (!TryParse(args, out options, out error))
            {
                Write(Failed("INVALID_ARGUMENTS", error, null, null));
                return 64;
            }

            if (Environment.Is64BitProcess)
            {
                Write(Failed("FAILED", "StackReportMustBeX86", options.IsLiveSnapshot ? "live-snapshot" : "dump", options.ProcessId));
                return 3;
            }

            try
            {
                if (options.IsLiveSnapshot)
                    return ReportLive(options);

                return ReportDump(options);
            }
            catch (Exception exception)
            {
                Write(Failed(
                    "FAILED",
                    exception.Message,
                    options.IsLiveSnapshot ? "live-snapshot" : "dump",
                    options.ProcessId));
                return 3;
            }
        }

        private static int ReportDump(Options options)
        {
            var fullPath = Path.GetFullPath(options.DumpPath);
            if (!File.Exists(fullPath))
                throw new InvalidOperationException("DumpMissing");

            using (var target = DataTarget.LoadDump(fullPath))
                return ReportTarget(target, "dump", null);
        }

        private static int ReportLive(Options options)
        {
            var expectedFull = Path.GetFullPath(options.ExpectedImage);
            if (!File.Exists(expectedFull))
                throw new InvalidOperationException("ExpectedImageMissing");

            using (var process = Process.GetProcessById(options.ProcessId.Value))
            {
                if (process.HasExited)
                    throw new InvalidOperationException("TargetAlreadyExited");
                if (process.Id == Process.GetCurrentProcess().Id)
                    throw new InvalidOperationException("SelfSnapshotRejected");

                string actualImage;
                try
                {
                    actualImage = process.MainModule == null ? null : process.MainModule.FileName;
                }
                catch (Exception exception)
                {
                    throw new InvalidOperationException("TargetImageUnavailable:" + exception.GetType().Name);
                }

                if (string.IsNullOrEmpty(actualImage) ||
                    !string.Equals(Path.GetFullPath(actualImage), expectedFull, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidOperationException("TargetImageMismatch");

                DateTime actualStartUtc;
                try
                {
                    actualStartUtc = process.StartTime.ToUniversalTime();
                }
                catch (Exception exception)
                {
                    throw new InvalidOperationException("TargetStartTimeUnavailable:" + exception.GetType().Name);
                }

                var expectedStartUtc = options.ExpectedStartUtc.Value.ToUniversalTime();
                if (actualStartUtc.ToFileTimeUtc() != expectedStartUtc.ToFileTimeUtc())
                    throw new InvalidOperationException("TargetStartTimeMismatch");

                using (var target = DataTarget.CreateSnapshotAndAttach(process.Id))
                    return ReportTarget(target, "live-snapshot", process.Id);
            }
        }

        private static int ReportTarget(DataTarget target, string source, int? targetPid)
        {
            if (target.DataReader.PointerSize != 4 || IntPtr.Size != 4)
                throw new InvalidOperationException("ArchitectureMismatch");
            if (target.ClrVersions.Length == 0)
                throw new InvalidOperationException("ClrRuntimeMissing");

            var info = target.ClrVersions[0];
            var windowsDirectory = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
            var dacPath = Path.Combine(
                windowsDirectory,
                "Microsoft.NET",
                "Framework",
                "v4.0.30319",
                "mscordacwks.dll");
            if (string.IsNullOrEmpty(windowsDirectory) || !File.Exists(dacPath))
                throw new InvalidOperationException("LocalX86DacUnavailable");

            using (var runtime = info.CreateRuntime(dacPath))
            {
                var threads = new List<ThreadDocument>();
                foreach (var thread in runtime.Threads)
                {
                    if (!thread.IsAlive)
                        continue;

                    var frames = thread.EnumerateStackTrace(false, 128)
                        .Select(frame => new FrameDocument { display = Sanitize(frame == null ? null : frame.ToString()) })
                        .Where(frame => !string.IsNullOrEmpty(frame.display))
                        .ToList();

                    threads.Add(new ThreadDocument
                    {
                        osThreadId = thread.OSThreadId,
                        managedThreadId = thread.ManagedThreadId,
                        frames = frames
                    });
                }

                Write(new StackDocument
                {
                    schemaVersion = 1,
                    status = "COMPLETED",
                    success = true,
                    error = null,
                    source = source,
                    targetPid = targetPid,
                    architecture = "x86",
                    clrVersion = info.Version.ToString(),
                    dacSource = "local",
                    threads = threads
                });
                return 0;
            }
        }

        private static bool TryParse(string[] args, out Options options, out string error)
        {
            options = null;
            error = null;

            if (args != null && args.Length == 2 && args[0] == "--dump" && !string.IsNullOrEmpty(args[1]))
            {
                if (!Path.IsPathRooted(args[1]))
                {
                    error = "DumpPathMustBeAbsolute";
                    return false;
                }

                options = new Options { DumpPath = args[1] };
                return true;
            }

            if (args == null || args.Length != 6)
            {
                error = "Expected --dump <absolute-existing-path> or --pid <pid> --expected-image <absolute-path> --expected-start-utc <roundtrip-utc>";
                return false;
            }

            string pidText = null;
            string expectedImage = null;
            string expectedStartText = null;
            for (var i = 0; i < args.Length; i += 2)
            {
                var name = args[i];
                var value = args[i + 1];
                if (string.IsNullOrEmpty(value))
                {
                    error = "EmptyArgumentValue";
                    return false;
                }

                if (name == "--pid" && pidText == null) pidText = value;
                else if (name == "--expected-image" && expectedImage == null) expectedImage = value;
                else if (name == "--expected-start-utc" && expectedStartText == null) expectedStartText = value;
                else
                {
                    error = "UnknownOrDuplicateArgument:" + name;
                    return false;
                }
            }

            int pid;
            if (!int.TryParse(pidText, NumberStyles.None, CultureInfo.InvariantCulture, out pid) || pid <= 0)
            {
                error = "InvalidPid";
                return false;
            }

            if (string.IsNullOrEmpty(expectedImage) || !Path.IsPathRooted(expectedImage))
            {
                error = "ExpectedImageMustBeAbsolute";
                return false;
            }

            DateTime expectedStartUtc;
            if (!DateTime.TryParseExact(
                expectedStartText,
                "o",
                CultureInfo.InvariantCulture,
                DateTimeStyles.RoundtripKind,
                out expectedStartUtc))
            {
                error = "ExpectedStartUtcInvalid";
                return false;
            }

            if (expectedStartUtc.Kind != DateTimeKind.Utc)
            {
                error = "ExpectedStartUtcMustBeUtc";
                return false;
            }

            options = new Options
            {
                ProcessId = pid,
                ExpectedImage = expectedImage,
                ExpectedStartUtc = expectedStartUtc
            };
            return true;
        }

        private static string Sanitize(string value)
        {
            if (string.IsNullOrEmpty(value))
                return value;

            value = value.Replace("\r", " ").Replace("\n", " ");
            if (value.Length > 512)
                value = value.Substring(0, 512);
            return value;
        }

        private static StackDocument Failed(string status, string error, string source, int? targetPid)
        {
            return new StackDocument
            {
                schemaVersion = 1,
                status = status,
                success = false,
                error = error,
                source = source,
                targetPid = targetPid,
                architecture = Environment.Is64BitProcess ? "x64" : "x86",
                clrVersion = null,
                dacSource = null,
                threads = new List<ThreadDocument>()
            };
        }

        private static void Write(StackDocument document)
        {
            Console.Write(new JavaScriptSerializer().Serialize(document));
        }
    }
}
