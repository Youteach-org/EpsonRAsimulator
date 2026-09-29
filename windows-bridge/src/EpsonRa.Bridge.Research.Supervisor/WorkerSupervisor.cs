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
        public ObservationAssessment Observation { get; set; }
    }

    public static class WorkerSupervisor
    {
        private const int OutputCap = 1024 * 1024;
        private const int RequestCap = 64 * 1024;

        public static SupervisorResult Run(WorkerRequest request)
        {
            return Run(request, new SystemObservationMonitorFactory());
        }

        public static SupervisorResult Run(
            WorkerRequest request,
            IObservationMonitorFactory observationFactory)
        {
            Process process = null;
            IObservationMonitor monitor = null;
            bool started = false;
            bool observationFault = false;
            string events = null;
            Stage observationStage = Stage.MetadataOnly;
            bool observationRequired = false;

            try
            {
                if (request == null ||
                    string.IsNullOrWhiteSpace(request.WorkerPath) ||
                    string.IsNullOrWhiteSpace(request.RequestPath) ||
                    !Path.IsPathRooted(request.WorkerPath) ||
                    !File.Exists(request.WorkerPath) ||
                    !File.Exists(request.RequestPath) ||
                    request.TimeoutSeconds < 1 ||
                    request.TimeoutSeconds > 120)
                {
                    return Fail(64, "INVALID_ARGUMENTS", "InvalidArguments", null);
                }

                string installRoot;
                if (TryReadObservationRequest(
                    request.RequestPath,
                    out observationStage,
                    out installRoot) &&
                    IsActivating(observationStage))
                {
                    observationRequired = true;
                    if (observationFactory == null)
                        return InconclusiveObservation(null, null, null);

                    try
                    {
                        monitor = observationFactory.Create(installRoot);
                    }
                    catch
                    {
                        return InconclusiveObservation(null, null, null);
                    }
                }

                events = Path.Combine(
                    Path.GetTempPath(),
                    "epson-ra-events-" + Guid.NewGuid().ToString("N") + ".jsonl");

                var arguments = new StringBuilder();
                if (request.ExtraArguments != null)
                {
                    foreach (var extra in request.ExtraArguments)
                        AppendQuoted(arguments, extra);
                }

                AppendQuoted(arguments, "--request");
                AppendQuoted(arguments, Path.GetFullPath(request.RequestPath));
                AppendQuoted(arguments, "--events");
                AppendQuoted(arguments, events);

                process = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = request.WorkerPath,
                        Arguments = arguments.ToString(),
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        RedirectStandardOutput = true,
                        RedirectStandardError = true
                    }
                };

                var clock = Stopwatch.StartNew();
                process.Start();
                started = true;

                if (monitor != null)
                {
                    try { monitor.WorkerStarted(process.Id); }
                    catch { observationFault = true; }
                }

                var output = new BoundedRead(process.StandardOutput.BaseStream);
                var error = new BoundedRead(process.StandardError.BaseStream);

                while (true)
                {
                    if (monitor != null && !observationFault)
                    {
                        try { monitor.Poll(); }
                        catch { observationFault = true; }
                    }

                    if (output.Exceeded || error.Exceeded)
                    {
                        StopOwned(process);
                        return Fail(3, "FAILED", "OutputLimit", ExitCode(process));
                    }

                    if (process.HasExited && output.Done && error.Done)
                        break;

                    if (clock.ElapsedMilliseconds >= request.TimeoutSeconds * 1000L)
                    {
                        StopOwned(process);
                        return Fail(
                            124,
                            "INCONCLUSIVE_TIMEOUT",
                            "Timeout",
                            ExitCode(process));
                    }

                    Thread.Sleep(10);
                }

                if (monitor != null && !observationFault)
                {
                    try { monitor.Poll(); }
                    catch { observationFault = true; }
                }

                var exit = process.ExitCode;

                if (output.Exceeded || error.Exceeded)
                    return Fail(3, "FAILED", "OutputLimit", exit);

                if (output.Failed || error.Failed)
                    return Fail(3, "FAILED", "OutputReadFailure", exit);

                if (exit != 0)
                    return Fail(3, "FAILED", "WorkerNonZeroExit", exit);

                if (error.Length != 0)
                    return Fail(3, "FAILED", "WorkerStderr", exit);

                ResearchResult worker;
                if (!TryReadResult(output.Text, out worker))
                    return Fail(3, "FAILED", "InvalidWorkerResult", exit);

                ObservationAssessment observation = null;
                if (observationRequired)
                {
                    if (!observationFault && monitor != null)
                    {
                        try
                        {
                            observation = ExternalObservation.Evaluate(
                                monitor.Before,
                                monitor.After,
                                events,
                                observationStage);
                        }
                        catch
                        {
                            observationFault = true;
                        }
                    }

                    if (observationFault ||
                        observation == null ||
                        !observation.Conclusive)
                    {
                        return InconclusiveObservation(
                            worker,
                            exit,
                            observation);
                    }
                }

                var success =
                    worker.Success &&
                    worker.Status == "COMPLETED" &&
                    worker.Cleanup == "CONFIRMED" &&
                    worker.Error == null;

                return new SupervisorResult
                {
                    Status = success ? "COMPLETED" : "FAILED",
                    Success = success,
                    WorkerExitCode = exit,
                    WorkerResult = worker,
                    Cleanup = worker.Cleanup,
                    Error = success ? null : "WorkerReportedFailure",
                    ExitCode = success ? 0 : 3,
                    Observation = observation
                };
            }
            catch
            {
                if (started)
                    StopOwned(process);

                return Fail(
                    3,
                    "FAILED",
                    "SupervisorFailure",
                    started ? ExitCode(process) : null);
            }
            finally
            {
                if (monitor != null)
                {
                    try { monitor.Dispose(); } catch { }
                }

                if (process != null)
                    process.Dispose();

                try
                {
                    if (events != null && File.Exists(events))
                        File.Delete(events);
                }
                catch { }
            }
        }

        private static SupervisorResult InconclusiveObservation(
            ResearchResult worker,
            int? workerExit,
            ObservationAssessment observation)
        {
            return new SupervisorResult
            {
                Status = "INCONCLUSIVE_OBSERVATION",
                Success = false,
                WorkerExitCode = workerExit,
                WorkerResult = worker,
                Cleanup = worker == null ? "UNKNOWN" : worker.Cleanup,
                Error = "ObservationInconclusive",
                ExitCode = 3,
                Observation = observation
            };
        }

        private static bool TryReadObservationRequest(
            string path,
            out Stage stage,
            out string installRoot)
        {
            stage = Stage.MetadataOnly;
            installRoot = null;

            try
            {
                var info = new FileInfo(path);
                if (!info.Exists || info.Length <= 0 || info.Length > RequestCap)
                    return false;

                var serializer = new JavaScriptSerializer
                {
                    MaxJsonLength = RequestCap
                };

                var dict = serializer.DeserializeObject(
                    File.ReadAllText(path, Encoding.UTF8))
                    as Dictionary<string, object>;

                object rawStage;
                object rawRoot;

                if (dict == null ||
                    !dict.TryGetValue("stage", out rawStage) ||
                    !(rawStage is string) ||
                    !dict.TryGetValue("installRoot", out rawRoot) ||
                    !(rawRoot is string))
                {
                    return false;
                }

                if (!Enum.TryParse((string)rawStage, false, out stage) ||
                    !Enum.IsDefined(typeof(Stage), stage))
                {
                    return false;
                }

                installRoot = (string)rawRoot;
                if (string.IsNullOrWhiteSpace(installRoot) ||
                    !Path.IsPathRooted(installRoot))
                {
                    return false;
                }

                installRoot = Path.GetFullPath(installRoot);
                return true;
            }
            catch
            {
                stage = Stage.MetadataOnly;
                installRoot = null;
                return false;
            }
        }

        private static bool IsActivating(Stage stage)
        {
            return stage == Stage.InitializeObserve ||
                   stage == Stage.Inventory ||
                   stage == Stage.Connect;
        }

        private static void StopOwned(Process process)
        {
            try
            {
                if (!process.HasExited)
                {
                    process.Kill();
                    process.WaitForExit(2000);
                }
            }
            catch { }
        }

        private static int? ExitCode(Process process)
        {
            try { return process.HasExited ? (int?)process.ExitCode : null; }
            catch { return null; }
        }

        private static bool TryReadResult(string text, out ResearchResult result)
        {
            result = null;

            try
            {
                var serializer = new JavaScriptSerializer
                {
                    MaxJsonLength = OutputCap
                };

                var dict = serializer.DeserializeObject(text)
                    as Dictionary<string, object>;

                object schema;
                object status;
                object success;
                object cleanup;
                object error;

                if (dict == null ||
                    !dict.TryGetValue("schemaVersion", out schema) ||
                    !(schema is int) ||
                    (int)schema != 1 ||
                    !dict.TryGetValue("status", out status) ||
                    !(status is string) ||
                    !dict.TryGetValue("success", out success) ||
                    !(success is bool) ||
                    !dict.TryGetValue("cleanup", out cleanup) ||
                    !(cleanup is string))
                {
                    return false;
                }

                dict.TryGetValue("error", out error);
                if (error != null && !(error is string))
                    return false;

                int? machine;
                long? corFlags;
                string architecture;
                string eligibleName;
                int? eligibleConnectionNumber;
                int? eligibleTypeNumber;

                if (!TryOptionalInt(dict, "machine", out machine) ||
                    !TryOptionalLong(dict, "corFlags", out corFlags) ||
                    !TryOptionalString(dict, "architecture", out architecture) ||
                    !TryOptionalString(dict, "eligibleName", out eligibleName) ||
                    !TryOptionalInt(dict, "eligibleConnectionNumber", out eligibleConnectionNumber) ||
                    !TryOptionalInt(dict, "eligibleTypeNumber", out eligibleTypeNumber))
                    return false;

                result = new ResearchResult
                {
                    SchemaVersion = 1,
                    Status = (string)status == "COMPLETED"
                        ? "COMPLETED"
                        : "FAILED",
                    Success = (bool)success,
                    Cleanup = (string)cleanup == "CONFIRMED"
                        ? "CONFIRMED"
                        : "UNKNOWN",
                    Error = error == null ? null : "WorkerError",
                    Machine = machine,
                    CorFlags = corFlags,
                    Architecture = architecture,
                    EligibleName = eligibleName,
                    EligibleConnectionNumber = eligibleConnectionNumber,
                    EligibleTypeNumber = eligibleTypeNumber
                };

                return true;
            }
            catch
            {
                return false;
            }
        }

        private static bool TryOptionalString(
            Dictionary<string, object> dict,
            string key,
            out string value)
        {
            value = null;
            object raw;
            if (!dict.TryGetValue(key, out raw) || raw == null)
                return true;
            if (!(raw is string))
                return false;
            value = (string)raw;
            return true;
        }

        private static bool TryOptionalInt(
            Dictionary<string, object> dict,
            string key,
            out int? value)
        {
            value = null;
            object raw;
            if (!dict.TryGetValue(key, out raw) || raw == null)
                return true;

            if (raw is int)
            {
                value = (int)raw;
                return true;
            }

            if (raw is long)
            {
                var number = (long)raw;
                if (number < int.MinValue || number > int.MaxValue)
                    return false;
                value = (int)number;
                return true;
            }

            return false;
        }

        private static bool TryOptionalLong(
            Dictionary<string, object> dict,
            string key,
            out long? value)
        {
            value = null;
            object raw;
            if (!dict.TryGetValue(key, out raw) || raw == null)
                return true;

            if (raw is int)
            {
                value = (int)raw;
                return true;
            }

            if (raw is long)
            {
                value = (long)raw;
                return true;
            }

            return false;
        }

        private static SupervisorResult Fail(
            int code,
            string status,
            string error,
            int? workerExit)
        {
            return new SupervisorResult
            {
                Status = status,
                Success = false,
                WorkerExitCode = workerExit,
                Cleanup = "UNKNOWN",
                Error = error,
                ExitCode = code
            };
        }

        private static void AppendQuoted(StringBuilder builder, string value)
        {
            if (builder.Length != 0)
                builder.Append(' ');

            builder.Append('"');
            var slashes = 0;

            foreach (var c in value ?? string.Empty)
            {
                if (c == '\\')
                {
                    slashes++;
                    continue;
                }

                if (c == '"')
                    builder.Append('\\', slashes * 2 + 1);
                else
                    builder.Append('\\', slashes);

                slashes = 0;
                builder.Append(c);
            }

            builder.Append('\\', slashes * 2).Append('"');
        }

        private sealed class BoundedRead
        {
            private readonly MemoryStream _bytes = new MemoryStream();
            private readonly Task _task;

            public volatile bool Exceeded;
            public volatile bool Failed;

            public bool Done
            {
                get { return _task.IsCompleted; }
            }

            public long Length
            {
                get { return _bytes.Length; }
            }

            public string Text
            {
                get
                {
                    return new UTF8Encoding(false, true)
                        .GetString(_bytes.ToArray());
                }
            }

            public BoundedRead(Stream stream)
            {
                _task = Read(stream);
            }

            private async Task Read(Stream stream)
            {
                try
                {
                    var buffer = new byte[4096];
                    int count;

                    while ((count = await stream.ReadAsync(
                        buffer,
                        0,
                        buffer.Length).ConfigureAwait(false)) != 0)
                    {
                        if (_bytes.Length + count > OutputCap)
                        {
                            Exceeded = true;
                            return;
                        }

                        _bytes.Write(buffer, 0, count);
                    }
                }
                catch
                {
                    Failed = true;
                }
            }
        }
    }
}
