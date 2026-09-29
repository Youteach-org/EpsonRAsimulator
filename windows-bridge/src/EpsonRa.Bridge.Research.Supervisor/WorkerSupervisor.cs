using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace EpsonRa.Bridge.Research.Supervisor
{
    public sealed class WorkerRequest
    {
        public string WorkerPath { get; set; }
        public string RequestPath { get; set; }
        public int TimeoutSeconds { get; set; } = 30;
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
            Process process = null;
            bool started = false;
            string events = null;
            try
            {
                if (request == null || string.IsNullOrWhiteSpace(request.WorkerPath) ||
                    string.IsNullOrWhiteSpace(request.RequestPath) ||
                    !Path.IsPathRooted(request.WorkerPath) || !File.Exists(request.WorkerPath) ||
                    !File.Exists(request.RequestPath) || request.TimeoutSeconds < 1 || request.TimeoutSeconds > 120)
                    return Fail(64, "INVALID_ARGUMENTS", "InvalidArguments", null);
                events = Path.Combine(Path.GetTempPath(), "epson-ra-events-" + Guid.NewGuid().ToString("N") + ".jsonl");
                var arguments = new StringBuilder();
                if (request.ExtraArguments != null)
                    foreach (var extra in request.ExtraArguments) AppendQuoted(arguments, extra);
                AppendQuoted(arguments, "--request");
                AppendQuoted(arguments, Path.GetFullPath(request.RequestPath));
                AppendQuoted(arguments, "--events");
                AppendQuoted(arguments, events);
                process = new Process { StartInfo = new ProcessStartInfo {
                    FileName = request.WorkerPath, Arguments = arguments.ToString(),
                    UseShellExecute = false, CreateNoWindow = true,
                    RedirectStandardOutput = true, RedirectStandardError = true
                }};
                var clock = Stopwatch.StartNew();
                process.Start();
                started = true;
                var output = new BoundedRead(process.StandardOutput.BaseStream);
                var error = new BoundedRead(process.StandardError.BaseStream);
                while (true)
                {
                    if (output.Exceeded || error.Exceeded)
                    {
                        StopOwned(process);
                        return Fail(3, "FAILED", "OutputLimit", ExitCode(process));
                    }
                    if (process.HasExited && output.Done && error.Done) break;
                    if (clock.ElapsedMilliseconds >= request.TimeoutSeconds * 1000L)
                    {
                        StopOwned(process);
                        return Fail(124, "INCONCLUSIVE_TIMEOUT", "Timeout", ExitCode(process));
                    }
                    Thread.Sleep(10);
                }
                var exit = process.ExitCode;
                // Readers may finish between the loop's cap check and its completion check.
                if (output.Exceeded || error.Exceeded) return Fail(3, "FAILED", "OutputLimit", exit);
                if (output.Failed || error.Failed) return Fail(3, "FAILED", "OutputReadFailure", exit);
                if (exit != 0) return Fail(3, "FAILED", "WorkerNonZeroExit", exit);
                if (error.Length != 0) return Fail(3, "FAILED", "WorkerStderr", exit);
                ResearchResult worker;
                if (!TryReadResult(output.Text, out worker)) return Fail(3, "FAILED", "InvalidWorkerResult", exit);
                bool success = worker.Success && worker.Status == "COMPLETED" &&
                    worker.Cleanup == "CONFIRMED" && worker.Error == null;
                return new SupervisorResult {
                    Status = success ? "COMPLETED" : "FAILED", Success = success,
                    WorkerExitCode = exit, WorkerResult = worker, Cleanup = worker.Cleanup,
                    Error = success ? null : "WorkerReportedFailure", ExitCode = success ? 0 : 3
                };
            }
            catch
            {
                if (started) StopOwned(process);
                return Fail(3, "FAILED", "SupervisorFailure", started ? ExitCode(process) : null);
            }
            finally
            {
                if (process != null) process.Dispose();
                // External observation integration is pending.
                try { if (events != null && File.Exists(events)) File.Delete(events); } catch { }
            }
        }
        private static void StopOwned(Process process)
        {
            try { if (!process.HasExited) { process.Kill(); process.WaitForExit(2000); } } catch { }
        }
        private static int? ExitCode(Process process)
        {
            try { return process.HasExited ? (int?)process.ExitCode : null; } catch { return null; }
        }
        private static bool TryReadResult(string text, out ResearchResult result)
        {
            result = null;
            try
            {
                var serializer = new JavaScriptSerializer { MaxJsonLength = OutputCap };
                var dict = serializer.DeserializeObject(text) as Dictionary<string, object>;
                object schema, status, success, cleanup, error;
                if (dict == null || !dict.TryGetValue("schemaVersion", out schema) || !(schema is int) || (int)schema != 1 ||
                    !dict.TryGetValue("status", out status) || !(status is string) ||
                    !dict.TryGetValue("success", out success) || !(success is bool) ||
                    !dict.TryGetValue("cleanup", out cleanup) || !(cleanup is string)) return false;
                dict.TryGetValue("error", out error);
                if (error != null && !(error is string)) return false;
                // Never echo arbitrary worker messages.
                result = new ResearchResult { SchemaVersion = 1, Status = (string)status == "COMPLETED" ? "COMPLETED" : "FAILED",
                    Success = (bool)success, Cleanup = (string)cleanup == "CONFIRMED" ? "CONFIRMED" : "UNKNOWN",
                    Error = error == null ? null : "WorkerError" };
                return true;
            }
            catch { return false; }
        }
        private static SupervisorResult Fail(int code, string status, string error, int? workerExit)
        {
            return new SupervisorResult { Status = status, Success = false, WorkerExitCode = workerExit,
                Cleanup = "UNKNOWN", Error = error, ExitCode = code };
        }
        private static void AppendQuoted(StringBuilder builder, string value)
        {
            if (builder.Length != 0) builder.Append(' ');
            builder.Append('"');
            int slashes = 0;
            foreach (char c in value ?? string.Empty)
            {
                if (c == '\\') { slashes++; continue; }
                if (c == '"') builder.Append('\\', slashes * 2 + 1);
                else builder.Append('\\', slashes);
                slashes = 0;
                builder.Append(c);
            }
            builder.Append('\\', slashes * 2).Append('"');
        }
        private sealed class BoundedRead
        {
            private readonly MemoryStream bytes = new MemoryStream();
            private readonly Task task;
            public volatile bool Exceeded;
            public volatile bool Failed;
            public bool Done { get { return task.IsCompleted; } }
            public long Length { get { return bytes.Length; } }
            public string Text { get { return new UTF8Encoding(false, true).GetString(bytes.ToArray()); } }
            public BoundedRead(Stream stream) { task = Read(stream); }
            private async Task Read(Stream stream)
            {
                try
                {
                    var buffer = new byte[4096];
                    int count;
                    while ((count = await stream.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false)) != 0)
                    {
                        if (bytes.Length + count > OutputCap) { Exceeded = true; return; }
                        bytes.Write(buffer, 0, count);
                    }
                }
                catch { Failed = true; }
            }
        }
    }
}
