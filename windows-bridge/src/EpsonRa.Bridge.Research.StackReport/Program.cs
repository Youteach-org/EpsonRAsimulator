using System;
using System.Collections.Generic;
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
        public string architecture { get; set; }
        public string clrVersion { get; set; }
        public string dacSource { get; set; }
        public List<ThreadDocument> threads { get; set; }
    }

    internal static class Program
    {
        private static int Main(string[] args)
        {
            string dumpPath;
            string error;
            if (!TryParse(args, out dumpPath, out error))
            {
                Write(Failed("INVALID_ARGUMENTS", error));
                return 64;
            }

            if (Environment.Is64BitProcess)
            {
                Write(Failed("FAILED", "StackReportMustBeX86"));
                return 3;
            }

            try
            {
                var fullPath = Path.GetFullPath(dumpPath);
                if (!File.Exists(fullPath))
                    throw new InvalidOperationException("DumpMissing");

                using (var target = DataTarget.LoadDump(fullPath))
                {
                    if (target.DataReader.PointerSize != 4 || IntPtr.Size != 4)
                        throw new InvalidOperationException("ArchitectureMismatch");
                    if (target.ClrVersions.Length == 0)
                        throw new InvalidOperationException("ClrRuntimeMissing");

                    var info = target.ClrVersions[0];
                    var dacPath = info.DacInfo.LocalDacPath;
                    if (string.IsNullOrEmpty(dacPath) || !File.Exists(dacPath))
                        throw new InvalidOperationException("LocalDacUnavailable");

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
                            architecture = "x86",
                            clrVersion = info.Version.ToString(),
                            dacSource = "local",
                            threads = threads
                        });
                        return 0;
                    }
                }
            }
            catch (Exception exception)
            {
                Write(Failed("FAILED", exception.Message));
                return 3;
            }
        }

        private static bool TryParse(string[] args, out string dumpPath, out string error)
        {
            dumpPath = null;
            error = null;
            if (args == null || args.Length != 2 || args[0] != "--dump" || string.IsNullOrEmpty(args[1]))
            {
                error = "Expected --dump <absolute-existing-path>";
                return false;
            }

            if (!Path.IsPathRooted(args[1]))
            {
                error = "DumpPathMustBeAbsolute";
                return false;
            }

            dumpPath = args[1];
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

        private static StackDocument Failed(string status, string error)
        {
            return new StackDocument
            {
                schemaVersion = 1,
                status = status,
                success = false,
                error = error,
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
