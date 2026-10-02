using System;
using System.IO;
using System.Web.Script.Serialization;

namespace EpsonRa.Bridge.Research.Supervisor
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            string worker = null;
            string request = null;
            string resultFile = null;
            int timeout = 30;

            for (var i = 0; i < args.Length; i++)
            {
                if (args[i] == "--worker" && i + 1 < args.Length) worker = args[++i];
                else if (args[i] == "--request" && i + 1 < args.Length) request = args[++i];
                else if (args[i] == "--result-file" && i + 1 < args.Length && resultFile == null) resultFile = args[++i];
                else if (args[i] == "--timeout-seconds" && i + 1 < args.Length)
                {
                    int parsed;
                    if (!int.TryParse(args[++i], out parsed))
                    {
                        var invalid = Invalid();
                        Console.WriteLine(new JavaScriptSerializer().Serialize(invalid));
                        return invalid.ExitCode;
                    }
                    timeout = parsed;
                }
                else
                {
                    var invalid = Invalid();
                    Console.WriteLine(new JavaScriptSerializer().Serialize(invalid));
                    return invalid.ExitCode;
                }
            }

            // Reserve evidence before starting a worker. CreateNew prevents a replay from
            // overwriting an earlier attempt, including an empty/crashed capture.
            FileStream durable = null;
            if (resultFile != null)
            {
                try
                {
                    if (!Path.IsPathRooted(resultFile)) return 64;
                    durable = new FileStream(resultFile, FileMode.CreateNew, FileAccess.Write, FileShare.Read);
                }
                catch { return 64; }
            }

            using (durable)
            {
                var result = WorkerSupervisor.Run(new WorkerRequest
                {
                    WorkerPath = worker,
                    RequestPath = request,
                    EventsPath = resultFile == null ? null : resultFile + ".events.jsonl",
                    TimeoutSeconds = timeout
                });

                var json = new JavaScriptSerializer().Serialize(result);
                if (durable == null) Console.WriteLine(json);
                else
                {
                    try
                    {
                        var bytes = new System.Text.UTF8Encoding(false).GetBytes(json + Environment.NewLine);
                        durable.Write(bytes, 0, bytes.Length);
                        durable.Flush(true);
                    }
                    catch { return 74; } // Capture failure is not native success.
                }
                return result.ExitCode;
            }
        }

        private static SupervisorResult Invalid()
        {
            return new SupervisorResult
            {
                SchemaVersion = 1,
                Status = "INVALID_ARGUMENTS",
                Success = false,
                Cleanup = "UNKNOWN",
                Error = "InvalidArguments",
                ExitCode = 64
            };
        }
    }
}
