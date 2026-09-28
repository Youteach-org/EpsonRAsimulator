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
            int timeout = 30;

            for (var i = 0; i < args.Length; i++)
            {
                if (args[i] == "--worker" && i + 1 < args.Length) worker = args[++i];
                else if (args[i] == "--request" && i + 1 < args.Length) request = args[++i];
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

            var result = WorkerSupervisor.Run(new WorkerRequest
            {
                WorkerPath = worker,
                RequestPath = request,
                TimeoutSeconds = timeout
            });

            Console.WriteLine(new JavaScriptSerializer().Serialize(result));
            return result.ExitCode;
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
