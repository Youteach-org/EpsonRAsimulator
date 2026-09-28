using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

namespace EpsonRa.Bridge.Research.Supervisor
{
    public sealed class WorkerRequest
    {
        public string WorkerPath { get; set; }
        public string RequestPath { get; set; }
        public int TimeoutSeconds { get; set; }
        public string[] ExtraArguments { get; set; }
    }

    public sealed class SupervisorResult
    {
        public int SchemaVersion { get; set; } = 1;
        public string Status { get; set; }
        public bool Success { get; set; }
        public int? WorkerExitCode { get; set; }
        public object WorkerResult { get; set; }
        public string Cleanup { get; set; }
        public string Error { get; set; }
        public int ExitCode { get; set; }
    }

    public static class WorkerSupervisor
    {
        private const int OutputCap = 1024 * 1024;

        public static SupervisorResult Run(WorkerRequest request)
        {
            if (request == null ||
                string.IsNullOrWhiteSpace(request.WorkerPath) ||
                string.IsNullOrWhiteSpace(request.RequestPath) ||
                request.TimeoutSeconds < 1 ||
                request.TimeoutSeconds > 120)
            {
                return Fail(64, "INVALID_ARGUMENTS", "UNKNOWN", "InvalidArguments", null);
            }

            var resultFile = Path.Combine(Path.GetTempPath(), "epson-ra-worker-" + Guid.NewGuid().ToString("N") + ".json");
            Process process = null;
            try
            {
                var args = new StringBuilder();
                if (request.ExtraArguments != null)
                {
                    foreach (var extra in request.ExtraArguments)
                        AppendQuoted(args, extra);
                }
                AppendQuoted(args, resultFile);

                process = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = request.WorkerPath,
                        Arguments = args.ToString(),
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        RedirectStandardOutput = true,
                        RedirectStandardError = true
                    },
                    EnableRaisingEvents = true
                };

                var output = new BoundedCollector(OutputCap);
                var error = new BoundedCollector(OutputCap);
                process.OutputDataReceived += (s, e) => output.Add(e.Data);
                process.ErrorDataReceived += (s, e) => error.Add(e.Data);

                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();

                if (!process.WaitForExit(request.TimeoutSeconds * 1000))
                {
                    try
                    {
                        process.Kill();
                        process.WaitForExit(2000);
                    }
                    catch { }
                    return Fail(124, "INCONCLUSIVE_TIMEOUT", "UNKNOWN", "Timeout", null);
                }

                var workerExit = process.ExitCode;
                if (workerExit != 0)
                    return Fail(3, "FAILED", "UNKNOWN", "WorkerNonZeroExit", workerExit);

                ResearchResult workerResult;
                if (!TryReadResult(resultFile, out workerResult))
                    return Fail(3, "FAILED", "UNKNOWN", "InvalidWorkerResult", workerExit);

                if (!workerResult.Success || workerResult.Cleanup != "CONFIRMED")
                {
                    return new SupervisorResult
                    {
                        SchemaVersion = 1,
                        Status = workerResult.Status ?? "FAILED",
                        Success = false,
                        WorkerExitCode = workerExit,
                        WorkerResult = workerResult,
                        Cleanup = workerResult.Cleanup ?? "UNKNOWN",
                        Error = workerResult.Error ?? "WorkerReportedFailure",
                        ExitCode = 3
                    };
                }

                return new SupervisorResult
                {
                    SchemaVersion = 1,
                    Status = workerResult.Status ?? "COMPLETED",
                    Success = true,
                    WorkerExitCode = workerExit,
                    WorkerResult = workerResult,
                    Cleanup = workerResult.Cleanup,
                    Error = null,
                    ExitCode = 0
                };
            }
            catch
            {
                return Fail(3, "FAILED", "UNKNOWN", "SupervisorFailure", process != null && process.HasExited ? (int?)process.ExitCode : null);
            }
            finally
            {
                try { if (File.Exists(resultFile)) File.Delete(resultFile); } catch { }
                if (process != null) process.Dispose();
            }
        }

        private static bool TryReadResult(string path, out ResearchResult result)
        {
            result = null;
            try
            {
                if (!File.Exists(path))
                    return false;

                var text = File.ReadAllText(path);
                if (string.IsNullOrWhiteSpace(text))
                    return false;

                var serializer = new JavaScriptSerializer();
                var parsed = serializer.DeserializeObject(text);
                var dict = parsed as System.Collections.Generic.Dictionary<string, object>;
                if (dict == null)
                    return false;

                object schema;
                if (!dict.TryGetValue("schemaVersion", out schema) || Convert.ToInt32(schema) != 1)
                    return false;

                result = serializer.Deserialize<ResearchResult>(text);
                return result != null;
            }
            catch
            {
                return false;
            }
        }

        private static SupervisorResult Fail(int exitCode, string status, string cleanup, string error, int? workerExit)
        {
            return new SupervisorResult
            {
                SchemaVersion = 1,
                Status = status,
                Success = false,
                WorkerExitCode = workerExit,
                WorkerResult = null,
                Cleanup = cleanup,
                Error = error,
                ExitCode = exitCode
            };
        }

        private static void AppendQuoted(StringBuilder builder, string value)
        {
            if (builder.Length > 0) builder.Append(' ');
            builder.Append((char)34).Append(value ?? string.Empty).Append((char)34);
        }

        private sealed class BoundedCollector
        {
            private readonly int _cap;
            private int _count;

            public BoundedCollector(int cap) { _cap = cap; }

            public void Add(string line)
            {
                if (line == null || _count >= _cap) return;
                var remaining = _cap - _count;
                _count += Math.Min(remaining, line.Length + Environment.NewLine.Length);
            }
        }
    }
}
